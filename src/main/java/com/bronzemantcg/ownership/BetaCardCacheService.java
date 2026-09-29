package com.bronzemantcg.ownership;

import com.bronzemantcg.BronzemanTcgConfig;
import com.bronzemantcg.interop.BetaCardLookupClient;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;

/** Profile-scoped historical Beta ownership backed only by explicit player refreshes. */
@Singleton
public final class BetaCardCacheService implements BetaCardUnlockSource
{
	static final String PRIVATE_ALBUM_MESSAGE = "Please turn on public album sharing in OSRS TCG, "
		+ "then refresh again. You can turn sharing off after Bronzeman saves the Beta names.";
	private static final BetaCardUnlockSource.View DISABLED_GAMEPLAY_VIEW =
		new BetaCardUnlockSource.View(0L, Collections.emptySet(), Collections.emptySet());

	private final Client client;
	private final ClientThread clientThread;
	private final BronzemanTcgConfig config;
	private final ProfileAccess profileAccess;
	private final BetaCardLookupClient lookupClient;
	private final BetaCardCacheStore cacheStore;
	private final Gson gson;
	private final ScheduledExecutorService executor;
	private final LongSupplier clock;
	private final Map<String, ParentResolution> parentByBetaName;
	private final AtomicReference<BetaCardLookupClient.FetchHandle> activeFetch =
		new AtomicReference<>();

	private boolean running;
	private long generation;
	private String loadedProfile;
	private State state = State.noProfile();
	private BetaCardUnlockSource.View gameplayView;
	private volatile Listener listener = Listener.NONE;

	@Inject
	public BetaCardCacheService(Client client, ClientThread clientThread,
		BronzemanTcgConfig config, ConfigManager configManager,
		BetaCardLookupClient lookupClient, BetaCardCacheStore cacheStore,
		BundledCardIdentityCatalog bundledCatalog, Gson gson,
		ScheduledExecutorService executor)
	{
		this(client, clientThread, config, new ConfigManagerProfileAccess(configManager),
			lookupClient, cacheStore, bundledCatalog, gson, executor,
			System::currentTimeMillis);
	}

	BetaCardCacheService(Client client, ClientThread clientThread,
		BronzemanTcgConfig config, ProfileAccess profileAccess,
		BetaCardLookupClient lookupClient, BetaCardCacheStore cacheStore,
		BundledCardIdentityCatalog bundledCatalog, Gson gson,
		ScheduledExecutorService executor, LongSupplier clock)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
		this.profileAccess = profileAccess;
		this.lookupClient = lookupClient;
		this.cacheStore = cacheStore;
		this.gson = gson;
		this.executor = executor;
		this.clock = clock;
		this.parentByBetaName = buildParentIndex(bundledCatalog);
		this.gameplayView = emptyGameplayView(0L);
	}

	public void setListener(Listener listener)
	{
		this.listener = listener == null ? Listener.NONE : listener;
	}

	/** Loads the active profile's cache without making a network request. */
	public void startUp()
	{
		synchronized (this)
		{
			running = true;
		}
		reloadForCurrentProfile();
	}

	public void onProfileChanged()
	{
		reloadForCurrentProfile();
	}

	public void shutDown()
	{
		synchronized (this)
		{
			running = false;
			generation++;
			loadedProfile = null;
			state = State.noProfile();
			gameplayView = emptyGameplayView(generation);
		}
		cancelActiveFetch();
	}

	public void onLookupConsentChanged()
	{
		if (config.allowBetaCardLookup())
		{
			return;
		}
		boolean changed = false;
		synchronized (this)
		{
			if (state.status == Status.REFRESHING)
			{
				generation++;
				state = state.failed("Beta refresh was cancelled because lookup consent "
					+ "was disabled. The previous cache was kept.");
				changed = true;
			}
		}
		cancelActiveFetch();
		if (changed)
		{
			notifyChanged();
		}
	}

	private void reloadForCurrentProfile()
	{
		String profile = currentProfileKey();
		cancelActiveFetch();
		synchronized (this)
		{
			generation++;
			loadedProfile = profile;
			if (!running || blank(profile))
			{
				state = State.noProfile();
				gameplayView = emptyGameplayView(generation);
			}
			else
			{
				try
				{
					BetaCardCacheStore.Record record = cacheStore.load(profile);
					state = record == null ? State.noCache() : State.cached(record);
					gameplayView = record == null
						? emptyGameplayView(generation)
						: projectGameplayView(state.betaNamesLowerCase, generation);
				}
				catch (IOException | RuntimeException ex)
				{
					state = State.corrupt();
					gameplayView = emptyGameplayView(generation);
				}
			}
		}
		notifyChanged();
	}

	/** Captures the logged-in identity on RuneLite's client thread, then starts one request. */
	public void refresh()
	{
		if (!config.allowBetaCardLookup())
		{
			setFailure("Enable the Beta player lookup privacy setting before refreshing.");
			return;
		}
		clientThread.invokeLater(() ->
		{
			Player local = client.getGameState() == GameState.LOGGED_IN
				? client.getLocalPlayer() : null;
			String displayName = local == null ? null : local.getName();
			String profile = currentProfileKey();
			if (blank(displayName) || blank(profile))
			{
				setFailure("Log in to RuneScape before refreshing Beta cards.");
				return;
			}
			beginRefresh(profile, displayName.trim());
		});
	}

	void beginRefresh(String profile, String displayName)
	{
		long requestGeneration;
		synchronized (this)
		{
			if (!running || !config.allowBetaCardLookup()
				|| !profile.equals(loadedProfile))
			{
				return;
			}
			generation++;
			requestGeneration = generation;
			state = state.refreshing();
		}
		notifyChanged();
		cancelActiveFetch();
		BetaCardLookupClient.FetchHandle handle;
		try
		{
			handle = lookupClient.fetch(displayName, new BetaCardLookupClient.Listener()
			{
				@Override
				public void onResponse(BetaCardLookupClient.LookupResponse response)
				{
					executor.execute(() -> handleResponse(requestGeneration, profile,
						displayName, response));
				}

				@Override
				public void onFailure(Throwable cause)
				{
					executor.execute(() -> finishFailure(requestGeneration,
						"Could not reach osrs-tcg.net. The previous Beta cache was kept."));
				}
			});
		}
		catch (RuntimeException ex)
		{
			finishFailure(requestGeneration,
				"Could not start the Beta lookup. The previous Beta cache was kept.");
			return;
		}
		activeFetch.set(handle);
		if (!isCurrent(requestGeneration, profile))
		{
			handle.cancel();
		}
	}

	private void handleResponse(long requestGeneration, String profile, String requestedName,
		BetaCardLookupClient.LookupResponse response)
	{
		if (!isCurrent(requestGeneration, profile))
		{
			return;
		}
		if (response.getStatusCode() == 404 && isPlayerNotFound(response.getBody()))
		{
			finishFailure(requestGeneration, PRIVATE_ALBUM_MESSAGE);
			return;
		}
		if (response.getStatusCode() != 200)
		{
			finishFailure(requestGeneration,
				"OSRS TCG could not return the Beta names. The previous cache was kept.");
			return;
		}

		ValidatedLookup lookup;
		try
		{
			LookupDto dto = gson.fromJson(new String(response.getBody(), StandardCharsets.UTF_8),
				LookupDto.class);
			lookup = validateLookup(dto, requestedName);
		}
		catch (JsonParseException | IllegalStateException | LookupValidationException ex)
		{
			finishFailure(requestGeneration,
				"OSRS TCG returned invalid Beta names. The previous cache was kept.");
			return;
		}

		long savedAt = clock.getAsLong();
		synchronized (this)
		{
			if (!isCurrentLocked(requestGeneration, profile))
			{
				return;
			}
			try
			{
				cacheStore.save(profile, lookup.displayName, lookup.revision,
					savedAt, lookup.cardNames);
			}
			catch (IOException | RuntimeException ex)
			{
				state = state.failed("Bronzeman could not save the Beta names. "
					+ "The previous cache was kept.");
				notifyChanged();
				return;
			}
			generation++;
			state = State.cached(lookup.displayName, lookup.revision,
				savedAt, lookup.cardNames);
			gameplayView = projectGameplayView(state.betaNamesLowerCase, generation);
		}
		notifyChanged();
	}

	/** Clears only Bronzeman's replaceable local historical Beta ownership cache. */
	public void clear()
	{
		String profile;
		synchronized (this)
		{
			profile = loadedProfile;
			if (!running || blank(profile))
			{
				return;
			}
			generation++;
			cancelActiveFetch();
			try
			{
				cacheStore.delete(profile);
				state = State.noCache("Cached Beta names were cleared.");
				gameplayView = emptyGameplayView(generation);
			}
			catch (IOException | RuntimeException ex)
			{
				state = state.failed("Bronzeman could not clear the Beta cache.");
			}
		}
		notifyChanged();
	}

	public synchronized State getState()
	{
		return state;
	}

	@Override
	public synchronized BetaCardUnlockSource.View getBetaCardUnlocks()
	{
		return config.disableBetaVariants() ? DISABLED_GAMEPLAY_VIEW : gameplayView;
	}

	private BetaCardUnlockSource.View projectGameplayView(Set<String> betaNames,
		long revision)
	{
		Map<CardEntityKind, Set<String>> parents = new EnumMap<>(CardEntityKind.class);
		for (CardEntityKind kind : CardEntityKind.values())
		{
			parents.put(kind, new LinkedHashSet<>());
		}
		for (String betaName : betaNames)
		{
			ParentResolution resolution = parentByBetaName.get(normalizeCardName(betaName));
			if (resolution != null)
			{
				parents.get(resolution.kind).add(resolution.parentNameLowerCase);
			}
		}
		return new BetaCardUnlockSource.View(revision,
			parents.get(CardEntityKind.ITEM), parents.get(CardEntityKind.NPC));
	}

	private BetaCardUnlockSource.View emptyGameplayView(long revision)
	{
		return new BetaCardUnlockSource.View(revision,
			Collections.emptySet(), Collections.emptySet());
	}

	private static Map<String, ParentResolution> buildParentIndex(
		BundledCardIdentityCatalog catalog)
	{
		if (catalog == null)
		{
			throw new IllegalArgumentException("bundledCatalog is required");
		}
		Map<String, ParentResolution> index = new HashMap<>();
		Set<String> ambiguous = new HashSet<>();
		for (ImmutableCardIdentityCatalog.Entry entry : catalog.getEntries())
		{
			CardIdentity identity = entry.getIdentity();
			addParentName(index, ambiguous, identity.getCardName(), identity);
			for (String legacyName : identity.getLegacyCardNames())
			{
				addParentName(index, ambiguous, legacyName, identity);
			}
		}
		for (String name : ambiguous)
		{
			index.remove(name);
		}
		return Collections.unmodifiableMap(index);
	}

	private static void addParentName(Map<String, ParentResolution> index,
		Set<String> ambiguous, String betaName, CardIdentity identity)
	{
		String normalized = normalizeCardName(betaName);
		if (normalized.isEmpty() || ambiguous.contains(normalized))
		{
			return;
		}
		ParentResolution resolution = new ParentResolution(identity.getKind(),
			normalizeCardName(identity.getCardName()));
		ParentResolution previous = index.putIfAbsent(normalized, resolution);
		if (previous != null && !previous.equals(resolution))
		{
			ambiguous.add(normalized);
			index.remove(normalized);
		}
	}

	private ValidatedLookup validateLookup(LookupDto dto, String requestedName)
		throws LookupValidationException
	{
		if (dto == null || blank(dto.displayName) || dto.revision == null
			|| dto.revision < 0
			|| dto.cardNames == null || !samePlayer(requestedName, dto.displayName)
			|| dto.cardNames.size() > 10_000)
		{
			throw new LookupValidationException();
		}
		Set<String> seen = new HashSet<>();
		List<String> names = new ArrayList<>(dto.cardNames.size());
		for (String name : dto.cardNames)
		{
			if (blank(name) || name.length() > 256)
			{
				throw new LookupValidationException();
			}
			String trimmed = name.trim();
			if (!seen.add(normalizeCardName(trimmed)))
			{
				throw new LookupValidationException();
			}
			names.add(trimmed);
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return new ValidatedLookup(dto.displayName.trim(), dto.revision, names);
	}

	private boolean isPlayerNotFound(byte[] bytes)
	{
		try
		{
			ErrorRootDto dto = gson.fromJson(new String(bytes, StandardCharsets.UTF_8),
				ErrorRootDto.class);
			return dto != null && dto.error != null
				&& "player_not_found".equals(dto.error.code);
		}
		catch (JsonParseException | IllegalStateException ex)
		{
			return false;
		}
	}

	private void finishFailure(long requestGeneration, String message)
	{
		synchronized (this)
		{
			if (requestGeneration != generation)
			{
				return;
			}
			state = state.failed(message);
		}
		notifyChanged();
	}

	private void setFailure(String message)
	{
		synchronized (this)
		{
			state = state.failed(message);
		}
		notifyChanged();
	}

	private synchronized boolean isCurrent(long requestGeneration, String profile)
	{
		return isCurrentLocked(requestGeneration, profile);
	}

	private boolean isCurrentLocked(long requestGeneration, String profile)
	{
		return running && requestGeneration == generation && profile.equals(loadedProfile);
	}

	private void cancelActiveFetch()
	{
		BetaCardLookupClient.FetchHandle handle = activeFetch.getAndSet(null);
		if (handle != null)
		{
			handle.cancel();
		}
	}

	private String currentProfileKey()
	{
		try
		{
			return profileAccess.currentProfileKey();
		}
		catch (RuntimeException ex)
		{
			return null;
		}
	}

	private void notifyChanged()
	{
		try
		{
			listener.onBetaCacheChanged();
		}
		catch (RuntimeException ignored)
		{
			// A presentation listener must never invalidate cached Beta ownership state.
		}
	}

	private static boolean samePlayer(String expected, String actual)
	{
		return normalizePlayerName(expected).equals(normalizePlayerName(actual));
	}

	private static String normalizePlayerName(String value)
	{
		return value == null ? "" : value.trim().replace('_', ' ')
			.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	private static String normalizeCardName(String value)
	{
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private static boolean blank(String value)
	{
		return value == null || value.trim().isEmpty();
	}

	public enum Status
	{
		NO_PROFILE,
		NO_CACHE,
		CACHED,
		REFRESHING,
		FAILED,
		CORRUPT
	}

	public static final class State
	{
		private final Status status;
		private final String displayName;
		private final long revision;
		private final Set<String> betaNamesLowerCase;
		private final long savedAtEpochMillis;
		private final String message;

		private State(Status status, String displayName, long revision,
			Set<String> betaNamesLowerCase, long savedAtEpochMillis, String message)
		{
			this.status = status;
			this.displayName = displayName;
			this.revision = revision;
			this.betaNamesLowerCase = Collections.unmodifiableSet(
				new LinkedHashSet<>(betaNamesLowerCase));
			this.savedAtEpochMillis = savedAtEpochMillis;
			this.message = message;
		}

		private static State noProfile()
		{
			return new State(Status.NO_PROFILE, null, -1, Collections.emptySet(), 0,
				"Log in to load or refresh Beta names.");
		}

		private static State noCache()
		{
			return noCache("No cached Beta names for this profile.");
		}

		private static State noCache(String message)
		{
			return new State(Status.NO_CACHE, null, -1, Collections.emptySet(), 0, message);
		}

		private static State corrupt()
		{
			return new State(Status.CORRUPT, null, -1, Collections.emptySet(), 0,
				"The cached Beta names are invalid. Refresh to replace them safely.");
		}

		private static State cached(BetaCardCacheStore.Record record)
		{
			return cached(record.getDisplayName(), record.getRevision(),
				record.getSavedAtEpochMillis(), record.getCardNames());
		}

		private static State cached(String displayName, long revision, long savedAt,
			List<String> names)
		{
			Set<String> normalized = new LinkedHashSet<>();
			for (String name : names)
			{
				normalized.add(normalizeCardName(name));
			}
			return new State(Status.CACHED, displayName, revision, normalized, savedAt, null);
		}

		private State refreshing()
		{
			return new State(Status.REFRESHING, displayName, revision, betaNamesLowerCase,
				savedAtEpochMillis, "Refreshing Beta names...");
		}

		private State failed(String message)
		{
			return new State(Status.FAILED, displayName, revision, betaNamesLowerCase,
				savedAtEpochMillis, message);
		}

		public Status getStatus()
		{
			return status;
		}

		public String getDisplayName()
		{
			return displayName;
		}

		public long getRevision()
		{
			return revision;
		}

		public Set<String> getBetaNamesLowerCase()
		{
			return betaNamesLowerCase;
		}

		public int getCachedCount()
		{
			return betaNamesLowerCase.size();
		}

		public long getSavedAtEpochMillis()
		{
			return savedAtEpochMillis;
		}

		public Instant getSavedAt()
		{
			return savedAtEpochMillis <= 0 ? null : Instant.ofEpochMilli(savedAtEpochMillis);
		}

		public String getMessage()
		{
			return message;
		}
	}

	public interface Listener
	{
		Listener NONE = () -> { };

		void onBetaCacheChanged();
	}

	interface ProfileAccess
	{
		String currentProfileKey();
	}

	private static final class ConfigManagerProfileAccess implements ProfileAccess
	{
		private final ConfigManager configManager;

		private ConfigManagerProfileAccess(ConfigManager configManager)
		{
			this.configManager = configManager;
		}

		@Override
		public String currentProfileKey()
		{
			return configManager.getRSProfileKey();
		}
	}

	private static final class ValidatedLookup
	{
		private final String displayName;
		private final long revision;
		private final List<String> cardNames;

		private ValidatedLookup(String displayName, long revision, List<String> cardNames)
		{
			this.displayName = displayName;
			this.revision = revision;
			this.cardNames = List.copyOf(cardNames);
		}
	}

	private static final class ParentResolution
	{
		private final CardEntityKind kind;
		private final String parentNameLowerCase;

		private ParentResolution(CardEntityKind kind, String parentNameLowerCase)
		{
			this.kind = kind;
			this.parentNameLowerCase = parentNameLowerCase;
		}

		@Override
		public boolean equals(Object other)
		{
			if (this == other)
			{
				return true;
			}
			if (!(other instanceof ParentResolution))
			{
				return false;
			}
			ParentResolution resolution = (ParentResolution) other;
			return kind == resolution.kind
				&& parentNameLowerCase.equals(resolution.parentNameLowerCase);
		}

		@Override
		public int hashCode()
		{
			return 31 * kind.hashCode() + parentNameLowerCase.hashCode();
		}
	}

	@SuppressWarnings("unused")
	private static final class LookupDto
	{
		private String displayName;
		private Long revision;
		private List<String> cardNames;
	}

	@SuppressWarnings("unused")
	private static final class ErrorRootDto
	{
		private ErrorDto error;
	}

	@SuppressWarnings("unused")
	private static final class ErrorDto
	{
		private String code;
	}

	private static final class LookupValidationException extends Exception
	{
	}
}
