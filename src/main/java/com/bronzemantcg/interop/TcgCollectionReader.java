package com.bronzemantcg.interop;

import com.bronzemantcg.ownership.CardEntityKind;
import com.bronzemantcg.ownership.TcgOwnershipSnapshot;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Single source of truth for the player's osrs-tcg collection, with two ways in:
 *
 * 1. Preferred: osrs-tcg's PluginMessage API. The plugin forwards "owned-names" /
 *    "owned-names-changed" payloads here via {@link #onApiOwnership}, giving an
 *    already-decoded, push-updated snapshot with no polling.
 * 2. Fallback: decoding osrs-tcg's persisted ConfigManager state, for hub versions
 *    that predate the API. No compile-time dependency either way - just published
 *    config group/key names (the standard pattern for unrelated Hub plugins).
 *
 * Once any API payload arrives, it wins until {@link #invalidate()} (profile switch),
 * after which we fall back to config until the next payload.
 *
 * The fallback is cached and refreshed lazily rather than decoded on every menu click,
 * since gzip decode on every single click would be wasteful. A short cache window is
 * fine here: worst case you can attack something you *just* unlocked for a few seconds
 * longer than necessary, which is a harmless direction to be stale in.
 */
@Slf4j
@Singleton
public class TcgCollectionReader
{
	private static final String TCG_CONFIG_GROUP = "osrstcg";
	private static final String TCG_STATE_KEY = "state";
	private static final long CACHE_MILLIS = 5_000L;

	private final ConfigManager configManager;
	private final Gson gson;

	private Set<String> cachedOwnedLowerCaseNames = Collections.emptySet();
	private TcgOwnershipSnapshot cachedFallbackOwnership =
		TcgOwnershipSnapshot.namesOnly(Collections.emptySet());
	private PersistedBetaCollection cachedPersistedBetaCollection =
		PersistedBetaCollection.unavailable();
	private boolean stateAvailable;
	private long lastRefreshMs = 0L;
	// Null until the first API payload lands; non-null means the API path is live.
	private TcgOwnershipSnapshot apiOwnership;

	@Inject
	public TcgCollectionReader(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	/**
	 * @return lower-cased set of every card name the player currently owns (foil or not,
	 *         collapsed - owning either counts as "collected" for restriction purposes).
	 */
	public synchronized Set<String> getOwnedCardNamesLowerCase()
	{
		if (apiOwnership != null)
		{
			return apiOwnership.getOwnedCardNamesLowerCase();
		}
		ensureFresh();
		return cachedOwnedLowerCaseNames;
	}

	/** False when osrs-tcg has no readable state (not installed, no data yet, or decode failure). */
	public synchronized boolean isStateAvailable()
	{
		if (apiOwnership != null)
		{
			return true;
		}
		ensureFresh();
		return stateAvailable;
	}

	/** True once an API payload has arrived; the plugin stops re-querying at that point. */
	public synchronized boolean hasApiData()
	{
		return apiOwnership != null;
	}

	/** A live v1-capable payload supplies both ID namespaces; empty lists are valid. */
	public synchronized boolean hasLiveV1Capability()
	{
		return apiOwnership != null
			&& apiOwnership.hasEntityIds(CardEntityKind.ITEM)
			&& apiOwnership.hasEntityIds(CardEntityKind.NPC);
	}

	/** Force the persisted-state fallback to refresh for an explicit user snapshot save. */
	public synchronized void refreshNow()
	{
		if (apiOwnership == null)
		{
			lastRefreshMs = 0L;
			refresh();
		}
	}

	/**
	 * Exact beta provenance retained in osrs-tcg's profile-scoped persisted state.
	 * This remains readable after the live API takes ownership precedence and is used
	 * only to repair/protect the historical Beta Collection snapshot.
	 */
	public synchronized PersistedBetaCollection getPersistedBetaCollection()
	{
		ensureFresh();
		return cachedPersistedBetaCollection;
	}

	/**
	 * Feed in the ownership payload from osrs-tcg's PluginMessage API. Elements are
	 * validated individually rather than trusting the cast - the data map is untyped, and
	 * a malformed payload should degrade to the config fallback, not throw on a click.
	 */
	public synchronized void onApiOwnership(List<?> names, List<?> itemIds,
		List<?> npcIds, String groupKey)
	{
		if (names == null)
		{
			return;
		}
		apiOwnership = TcgOwnershipSnapshot.fromApi(names, itemIds, npcIds, groupKey);
	}

	/** Current API snapshot, or a name-only view of the persisted-state fallback. */
	public synchronized TcgOwnershipSnapshot getOwnershipSnapshot()
	{
		if (apiOwnership != null)
		{
			return apiOwnership;
		}
		ensureFresh();
		return cachedFallbackOwnership;
	}

	private void ensureFresh()
	{
		if (System.currentTimeMillis() - lastRefreshMs >= CACHE_MILLIS)
		{
			refresh();
		}
	}

	/**
	 * Call after profile switches / logins so a stale cache from a different account never
	 * lingers. Drops API data too - it described the previous profile's collection - so we
	 * serve the config fallback until the re-query for the new profile is answered.
	 */
	public synchronized void invalidate()
	{
		lastRefreshMs = 0L;
		apiOwnership = null;
	}

	private void refresh()
	{
		lastRefreshMs = System.currentTimeMillis();
		try
		{
			String raw = configManager.getRSProfileConfiguration(TCG_CONFIG_GROUP, TCG_STATE_KEY);
			PersistedState parsed = parsePersistedState(raw, gson);
			cachedPersistedBetaCollection = parsed.betaCollection;
			String json = parsed.json;
			if (json.isEmpty())
			{
				cachedOwnedLowerCaseNames = Collections.emptySet();
				cachedFallbackOwnership = TcgOwnershipSnapshot.namesOnly(cachedOwnedLowerCaseNames);
				stateAvailable = false;
				return;
			}

			if (!parsed.collectionPresent)
			{
				cachedOwnedLowerCaseNames = Collections.emptySet();
				cachedFallbackOwnership = TcgOwnershipSnapshot.namesOnly(cachedOwnedLowerCaseNames);
				stateAvailable = false;
				return;
			}
			stateAvailable = true;

			Set<String> names = new HashSet<>(parsed.ownedNames);
			cachedOwnedLowerCaseNames = Collections.unmodifiableSet(names);
			cachedFallbackOwnership = TcgOwnershipSnapshot.namesOnly(cachedOwnedLowerCaseNames);
		}
		catch (Exception ex)
		{
			// osrs-tcg not installed, no data yet, or its storage format changed upstream.
			// Fail safe to "own nothing known" rather than crash the client.
			log.debug("Could not read osrs-tcg collection state", ex);
			cachedOwnedLowerCaseNames = Collections.emptySet();
			cachedFallbackOwnership = TcgOwnershipSnapshot.namesOnly(cachedOwnedLowerCaseNames);
			stateAvailable = false;
			cachedPersistedBetaCollection = PersistedBetaCollection.unavailable();
		}
	}

	static PersistedState parsePersistedState(String raw, Gson gson)
	{
		String json = TcgStateDecoder.decode(raw);
		if (json.isEmpty() || gson == null)
		{
			return PersistedState.unavailable();
		}

		TcgStateDto dto = gson.fromJson(json, TcgStateDto.class);
		if (dto == null)
		{
			return PersistedState.unavailable();
		}

		Set<String> ownedNames = new HashSet<>();
		Set<String> betaNames = new HashSet<>();
		boolean collectionPresent;
		if (dto.cardEntries != null
			&& (!dto.cardEntries.isEmpty() || dto.cardInstances == null))
		{
			collectionPresent = true;
			for (TcgStateDto.CardEntryDto entry : dto.cardEntries)
			{
				String name = normalizedName(entry == null ? null : entry.cardName);
				if (name == null || entry.variants == null)
				{
					continue;
				}
				boolean owned = false;
				boolean beta = false;
				for (TcgStateDto.CardVariantDto variant : entry.variants)
				{
					if (variant == null || (variant.quantity != null && variant.quantity <= 0))
					{
						continue;
					}
					owned = true;
					beta |= Boolean.TRUE.equals(variant.beta);
				}
				if (owned)
				{
					ownedNames.add(name);
				}
				if (beta)
				{
					betaNames.add(name);
				}
			}
		}
		else if (dto.cardInstances != null)
		{
			collectionPresent = true;
			boolean betaMetadataPresent = false;
			for (TcgStateDto.OwnedCardInstanceDto instance : dto.cardInstances)
			{
				if (instance != null && instance.beta != null)
				{
					betaMetadataPresent = true;
					break;
				}
			}
			for (TcgStateDto.OwnedCardInstanceDto instance : dto.cardInstances)
			{
				String name = normalizedName(instance == null ? null : instance.cardName);
				if (name == null)
				{
					continue;
				}
				ownedNames.add(name);
				if (betaMetadataPresent ? Boolean.TRUE.equals(instance.beta)
					: raw != null && raw.startsWith(TcgStateDecoder.STORAGE_PREFIX_V2))
				{
					betaNames.add(name);
				}
			}
		}
		else
		{
			collectionPresent = false;
		}

		PersistedBetaCollection betaCollection = collectionPresent
			? PersistedBetaCollection.available(betaNames)
			: PersistedBetaCollection.unavailable();
		return new PersistedState(json, collectionPresent, ownedNames, betaCollection);
	}

	private static String normalizedName(String value)
	{
		if (value == null || value.trim().isEmpty())
		{
			return null;
		}
		return value.trim().toLowerCase(Locale.ROOT);
	}

	static final class PersistedState
	{
		private final String json;
		private final boolean collectionPresent;
		private final Set<String> ownedNames;
		private final PersistedBetaCollection betaCollection;

		private PersistedState(String json, boolean collectionPresent,
			Set<String> ownedNames, PersistedBetaCollection betaCollection)
		{
			this.json = json;
			this.collectionPresent = collectionPresent;
			this.ownedNames = Collections.unmodifiableSet(new HashSet<>(ownedNames));
			this.betaCollection = betaCollection;
		}

		private static PersistedState unavailable()
		{
			return new PersistedState("", false, Collections.emptySet(),
				PersistedBetaCollection.unavailable());
		}

		boolean isCollectionPresent()
		{
			return collectionPresent;
		}

		Set<String> getOwnedNames()
		{
			return ownedNames;
		}

		PersistedBetaCollection getBetaCollection()
		{
			return betaCollection;
		}
	}

	public static final class PersistedBetaCollection
	{
		private final boolean available;
		private final Set<String> ownedNamesLowerCase;

		private PersistedBetaCollection(boolean available, Set<String> ownedNamesLowerCase)
		{
			this.available = available;
			this.ownedNamesLowerCase = Collections.unmodifiableSet(
				new HashSet<>(ownedNamesLowerCase));
		}

		private static PersistedBetaCollection available(Set<String> names)
		{
			return new PersistedBetaCollection(true, names);
		}

		private static PersistedBetaCollection unavailable()
		{
			return new PersistedBetaCollection(false, Collections.emptySet());
		}

		public boolean isAvailable()
		{
			return available;
		}

		public Set<String> getOwnedNamesLowerCase()
		{
			return ownedNamesLowerCase;
		}
	}
}
