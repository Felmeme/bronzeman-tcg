package com.bronzemantcg.panel.collection;

import com.bronzemantcg.BronzemanTcgConfig;
import com.bronzemantcg.ownership.CardEntityKind;
import com.bronzemantcg.ownership.TcgOwnershipSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
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

/**
 * Preserves the personal beta collection at the boundary between the legacy name-only API and
 * the v1 ID-aware API. A readable legacy collection remains provisional until the first complete
 * v1 payload arrives, then becomes immutable. If Bronzeman first sees the player after that
 * boundary, the first v1 name set is retained but labelled inferred because the flattened API
 * cannot distinguish migrated beta cards from later v1 pulls.
 */
@Slf4j
@Singleton
public final class BetaCollectionSnapshotService
{
	private static final String CONFIG_KEY = "betaCollectionSnapshotV1";
	private static final int SCHEMA_VERSION = 1;

	private final Persistence persistence;
	private final LongSupplier currentTimeMillis;
	private final List<String> identityNames;
	private final Set<String> identityNameSet;
	private final String identityFingerprint;

	private Snapshot snapshot = Snapshot.none();

	@Inject
	public BetaCollectionSnapshotService(ConfigManager configManager,
		PanelCollectionLayout catalog)
	{
		this(new ConfigPersistence(configManager), catalog, System::currentTimeMillis);
	}

	BetaCollectionSnapshotService(Persistence persistence, PanelCollectionLayout catalog,
		LongSupplier currentTimeMillis)
	{
		this.persistence = persistence;
		this.currentTimeMillis = currentTimeMillis;
		identityNames = betaIdentityNames(catalog);
		identityNameSet = Collections.unmodifiableSet(new LinkedHashSet<>(identityNames));
		identityFingerprint = fingerprint(identityNames);
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
			// Keep the stored value untouched. Replacing it would discard the only recoverable copy.
			log.warn("Stored beta collection snapshot is incompatible; leaving it untouched", ex);
			snapshot = Snapshot.incompatible();
		}
	}

	/**
	 * Observes one personal ownership picture. Unavailable fallback state is ignored. A v1
	 * boundary is accepted only when both entity-ID lists are present, including valid empty lists.
	 *
	 * @return true when the in-memory snapshot status or owned identities changed
	 */
	public synchronized boolean observe(TcgOwnershipSnapshot ownership, boolean stateAvailable)
	{
		if (ownership == null || !stateAvailable || snapshot.status.isTerminal())
		{
			return false;
		}

		boolean v1Payload = ownership.hasEntityIds(CardEntityKind.ITEM)
			&& ownership.hasEntityIds(CardEntityKind.NPC);
		Set<String> observed = intersect(ownership.getOwnedCardNamesLowerCase());
		Snapshot next;
		if (v1Payload)
		{
			Status status = snapshot.status == Status.PROVISIONAL
				? Status.FROZEN_CAPTURED : Status.FROZEN_INFERRED;
			// A complete empty v1 payload means the player migrated/reset with no cards.
			// It must override a stale provisional snapshot rather than resurrect it.
			Set<String> frozen = observed.isEmpty()
				? Collections.emptySet()
				: snapshot.status == Status.PROVISIONAL ? snapshot.ownedNames : observed;
			next = new Snapshot(status, frozen, currentTimeMillis.getAsLong());
		}
		else
		{
			if (snapshot.status == Status.PROVISIONAL && snapshot.ownedNames.equals(observed))
			{
				return false;
			}
			next = new Snapshot(Status.PROVISIONAL, observed, currentTimeMillis.getAsLong());
		}

		return install(next);
	}

	/** Explicit user save. It refreshes the provisional snapshot but never freezes early. */
	public synchronized SaveResult saveCurrent(TcgOwnershipSnapshot ownership,
		boolean stateAvailable)
	{
		if (snapshot.status == Status.INCOMPATIBLE)
		{
			return new SaveResult(SaveOutcome.INCOMPATIBLE, getView());
		}
		if (snapshot.status.isFrozen())
		{
			return new SaveResult(SaveOutcome.ALREADY_FROZEN, getView());
		}
		if (ownership == null || !stateAvailable)
		{
			return new SaveResult(SaveOutcome.UNAVAILABLE, getView());
		}

		Snapshot next = new Snapshot(Status.PROVISIONAL,
			intersect(ownership.getOwnedCardNamesLowerCase()), currentTimeMillis.getAsLong());
		if (!install(next))
		{
			return new SaveResult(SaveOutcome.PERSISTENCE_FAILED, getView());
		}
		return new SaveResult(SaveOutcome.SAVED, getView());
	}

	public synchronized Status getStatus()
	{
		return snapshot.status;
	}

	public synchronized Set<String> getOwnedBetaNamesLowerCase()
	{
		return snapshot.ownedNames;
	}

	/** Coherent status-and-names view for background panel preparation. */
	public synchronized SnapshotView getView()
	{
		return new SnapshotView(snapshot.status, snapshot.ownedNames);
	}

	private boolean install(Snapshot value)
	{
		try
		{
			persistence.save(encode(value));
			snapshot = value;
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
		return SCHEMA_VERSION + "|" + value.status.name() + "|" + value.capturedAtEpochMillis
			+ "|" + identityFingerprint + "|"
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

	private static List<String> betaIdentityNames(PanelCollectionLayout catalog)
	{
		Set<String> names = new LinkedHashSet<>();
		for (PanelCollectionLayout.BetaCollectionCard card : catalog.getBetaCollectionCards())
		{
			for (PanelCollectionLayout.BetaVariant variant : card.getVariants())
			{
				names.add(variant.getName().trim().toLowerCase(Locale.ROOT));
			}
		}
		List<String> sorted = new ArrayList<>(names);
		Collections.sort(sorted);
		return Collections.unmodifiableList(sorted);
	}

	private static String fingerprint(List<String> names)
	{
		try
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (String name : names)
			{
				digest.update(name.getBytes(StandardCharsets.UTF_8));
				digest.update((byte) '\n');
			}
			StringBuilder result = new StringBuilder("sha256:");
			for (byte value : digest.digest())
			{
				result.append(String.format("%02x", value & 0xff));
			}
			return result.toString();
		}
		catch (NoSuchAlgorithmException ex)
		{
			throw new IllegalStateException("SHA-256 is unavailable", ex);
		}
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
		private final Set<String> ownedNames;

		private SnapshotView(Status status, Set<String> ownedNames)
		{
			this.status = status;
			this.ownedNames = ownedNames;
		}

		public Status getStatus()
		{
			return status;
		}

		public Set<String> getOwnedNamesLowerCase()
		{
			return ownedNames;
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
