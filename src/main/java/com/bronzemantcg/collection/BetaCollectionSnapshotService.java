package com.bronzemantcg.collection;

import com.bronzemantcg.BronzemanTcgConfig;
import java.util.Base64;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/** Captures and freezes a profile-scoped beta collection before OSRS TCG v1 migration. */
@Slf4j
@Singleton
public final class BetaCollectionSnapshotService
{
	static final String CONFIG_KEY = "betaCollectionSnapshotV1";
	private static final int SCHEMA_VERSION = 1;

	private final Persistence persistence;
	private final LongSupplier currentTimeMillis;
	private final List<String> identityNames;
	private final Set<String> identityNameSet;
	private final String identityFingerprint;

	private Snapshot snapshot = Snapshot.none();

	@Inject
	public BetaCollectionSnapshotService(ConfigManager configManager,
		BetaCollectionIdentityCatalog catalog)
	{
		this(new ConfigPersistence(configManager), catalog, System::currentTimeMillis);
	}

	BetaCollectionSnapshotService(Persistence persistence,
		BetaCollectionIdentityCatalog catalog, LongSupplier currentTimeMillis)
	{
		this.persistence = persistence;
		this.currentTimeMillis = currentTimeMillis;
		identityNames = catalog.getNames();
		identityNameSet = catalog.getNameSet();
		identityFingerprint = catalog.getFingerprint();
	}

	/** Reloads the snapshot stored for the current RuneScape profile. */
	public synchronized void reload()
	{
		String raw;
		try
		{
			raw = persistence.load();
		}
		catch (RuntimeException ex)
		{
			log.warn("Could not load the beta collection snapshot", ex);
			snapshot = Snapshot.incompatible();
			return;
		}
		if (raw == null || raw.isEmpty())
		{
			snapshot = Snapshot.none();
			return;
		}
		try
		{
			snapshot = decode(raw);
		}
		catch (IllegalArgumentException ex)
		{
			log.warn("Stored beta collection snapshot is incompatible; leaving it untouched", ex);
			snapshot = Snapshot.incompatible();
		}
	}

	/** Mirrors the latest readable legacy collection, including an intentional empty reset. */
	public synchronized boolean observeLegacy(Set<String> ownedNames, boolean stateAvailable)
	{
		if (!stateAvailable || snapshot.status.isTerminal())
		{
			return false;
		}
		Set<String> observed = intersect(ownedNames);
		if (snapshot.status == Status.PROVISIONAL && snapshot.ownedNames.equals(observed))
		{
			return false;
		}
		return install(new Snapshot(Status.PROVISIONAL, observed,
			currentTimeMillis.getAsLong()));
	}

	/**
	 * Accepts a PluginMessage update. Both ID lists must be present before it marks the v1
	 * boundary. A complete empty v1 collection overrides stale provisional ownership.
	 */
	public synchronized boolean observeApi(Set<String> ownedNames, boolean completeV1Payload)
	{
		if (snapshot.status.isTerminal())
		{
			return false;
		}
		if (!completeV1Payload)
		{
			return observeLegacy(ownedNames, true);
		}

		Set<String> observed = intersect(ownedNames);
		Status status = snapshot.status == Status.PROVISIONAL
			? Status.FROZEN_CAPTURED : Status.FROZEN_INFERRED;
		Set<String> frozen = observed.isEmpty()
			? Collections.emptySet()
			: snapshot.status == Status.PROVISIONAL ? snapshot.ownedNames : observed;
		return install(new Snapshot(status, frozen, currentTimeMillis.getAsLong()));
	}

	/** Explicit user save. It refreshes the provisional timestamp but never freezes early. */
	public synchronized SaveResult saveCurrent(Set<String> ownedNames, boolean stateAvailable)
	{
		if (snapshot.status == Status.INCOMPATIBLE)
		{
			return new SaveResult(SaveOutcome.INCOMPATIBLE, view());
		}
		if (snapshot.status.isFrozen())
		{
			return new SaveResult(SaveOutcome.ALREADY_FROZEN, view());
		}
		if (!stateAvailable)
		{
			return new SaveResult(SaveOutcome.UNAVAILABLE, view());
		}
		Snapshot next = new Snapshot(Status.PROVISIONAL, intersect(ownedNames),
			currentTimeMillis.getAsLong());
		if (!install(next))
		{
			return new SaveResult(SaveOutcome.PERSISTENCE_FAILED, view());
		}
		return new SaveResult(SaveOutcome.SAVED, view());
	}

	public synchronized SnapshotView getView()
	{
		return view();
	}

	public synchronized Set<String> getOwnedBetaNamesLowerCase()
	{
		return snapshot.ownedNames;
	}

	public String getIdentityFingerprint()
	{
		return identityFingerprint;
	}

	private SnapshotView view()
	{
		return new SnapshotView(snapshot.status, snapshot.ownedNames.size(),
			snapshot.capturedAtEpochMillis);
	}

	private boolean install(Snapshot next)
	{
		try
		{
			persistence.save(encode(next));
			snapshot = next;
			return true;
		}
		catch (RuntimeException ex)
		{
			log.warn("Could not persist the beta collection snapshot", ex);
			return false;
		}
	}

	private String encode(Snapshot value)
	{
		BitSet bits = new BitSet(identityNames.size());
		for (int i = 0; i < identityNames.size(); i++)
		{
			if (value.ownedNames.contains(identityNames.get(i)))
			{
				bits.set(i);
			}
		}
		return SCHEMA_VERSION + "|" + value.status.name() + "|"
			+ value.capturedAtEpochMillis + "|" + identityFingerprint + "|"
			+ Base64.getEncoder().encodeToString(bits.toByteArray());
	}

	private Snapshot decode(String raw)
	{
		String[] parts = raw.split("\\|", -1);
		if (parts.length != 5 || !Integer.toString(SCHEMA_VERSION).equals(parts[0]))
		{
			throw new IllegalArgumentException("unknown snapshot schema");
		}
		Status status;
		long capturedAt;
		try
		{
			status = Status.valueOf(parts[1]);
			capturedAt = Long.parseLong(parts[2]);
		}
		catch (IllegalArgumentException ex)
		{
			throw new IllegalArgumentException("invalid snapshot metadata", ex);
		}
		if (status == Status.NONE || status == Status.INCOMPATIBLE || capturedAt < 0
			|| !identityFingerprint.equals(parts[3]))
		{
			throw new IllegalArgumentException("snapshot identity set does not match this release");
		}
		byte[] encoded;
		try
		{
			encoded = Base64.getDecoder().decode(parts[4]);
		}
		catch (IllegalArgumentException ex)
		{
			throw new IllegalArgumentException("invalid snapshot ownership data", ex);
		}
		BitSet bits = BitSet.valueOf(encoded);
		if (bits.length() > identityNames.size())
		{
			throw new IllegalArgumentException("snapshot contains unknown beta identities");
		}
		Set<String> names = new LinkedHashSet<>();
		for (int index = bits.nextSetBit(0); index >= 0; index = bits.nextSetBit(index + 1))
		{
			names.add(identityNames.get(index));
		}
		return new Snapshot(status, names, capturedAt);
	}

	private Set<String> intersect(Set<String> ownedNames)
	{
		Set<String> result = new LinkedHashSet<>();
		if (ownedNames != null)
		{
			for (String name : ownedNames)
			{
				if (name != null)
				{
					String normalized = name.trim().toLowerCase(Locale.ROOT);
					if (identityNameSet.contains(normalized))
					{
						result.add(normalized);
					}
				}
			}
		}
		return Collections.unmodifiableSet(result);
	}

	public enum Status
	{
		NONE,
		PROVISIONAL,
		FROZEN_CAPTURED,
		FROZEN_INFERRED,
		INCOMPATIBLE;

		public boolean isFrozen()
		{
			return this == FROZEN_CAPTURED || this == FROZEN_INFERRED;
		}

		private boolean isTerminal()
		{
			return isFrozen() || this == INCOMPATIBLE;
		}
	}

	public enum SaveOutcome
	{
		SAVED,
		UNAVAILABLE,
		ALREADY_FROZEN,
		INCOMPATIBLE,
		PERSISTENCE_FAILED
	}

	public static final class SaveResult
	{
		private final SaveOutcome outcome;
		private final SnapshotView snapshot;

		private SaveResult(SaveOutcome outcome, SnapshotView snapshot)
		{
			this.outcome = outcome;
			this.snapshot = snapshot;
		}

		public SaveOutcome getOutcome()
		{
			return outcome;
		}

		public SnapshotView getSnapshot()
		{
			return snapshot;
		}
	}

	public static final class SnapshotView
	{
		private final Status status;
		private final int uniqueCardCount;
		private final long capturedAtEpochMillis;

		private SnapshotView(Status status, int uniqueCardCount, long capturedAtEpochMillis)
		{
			this.status = status;
			this.uniqueCardCount = uniqueCardCount;
			this.capturedAtEpochMillis = capturedAtEpochMillis;
		}

		public Status getStatus()
		{
			return status;
		}

		public int getUniqueCardCount()
		{
			return uniqueCardCount;
		}

		public long getCapturedAtEpochMillis()
		{
			return capturedAtEpochMillis;
		}
	}

	private static final class Snapshot
	{
		private final Status status;
		private final Set<String> ownedNames;
		private final long capturedAtEpochMillis;

		private Snapshot(Status status, Set<String> ownedNames, long capturedAtEpochMillis)
		{
			this.status = status;
			this.ownedNames = Collections.unmodifiableSet(new LinkedHashSet<>(ownedNames));
			this.capturedAtEpochMillis = capturedAtEpochMillis;
		}

		private static Snapshot none()
		{
			return new Snapshot(Status.NONE, Collections.emptySet(), 0L);
		}

		private static Snapshot incompatible()
		{
			return new Snapshot(Status.INCOMPATIBLE, Collections.emptySet(), 0L);
		}
	}

	interface Persistence
	{
		String load();

		void save(String raw);
	}

	private static final class ConfigPersistence implements Persistence
	{
		private final ConfigManager configManager;

		private ConfigPersistence(ConfigManager configManager)
		{
			this.configManager = configManager;
		}

		@Override
		public String load()
		{
			return configManager.getRSProfileConfiguration(BronzemanTcgConfig.GROUP, CONFIG_KEY);
		}

		@Override
		public void save(String raw)
		{
			configManager.setRSProfileConfiguration(BronzemanTcgConfig.GROUP, CONFIG_KEY, raw);
		}
	}
}
