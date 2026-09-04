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
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.api.Client;
import net.runelite.api.GameState;

/**
 * Preserves the personal beta collection at the boundary between the legacy name-only API and
 * the v1 ID-aware API. A readable legacy collection remains provisional until the first complete
 * v1 payload arrives, then becomes immutable. If Bronzeman first sees the player after that
 * boundary, the first v1 name set is retained but labelled inferred because the flattened API
 * cannot distinguish migrated beta cards from later v1 pulls. Exact beta provenance retained in
 * osrs-tcg's persisted state may repair an empty/inferred snapshot without changing the storage
 * schema or replacing a non-empty captured snapshot. Explicit imports and clears use a separate,
 * profile-scoped name-based record with a one-step undo copy; the legacy bitset is never rewritten
 * by these operations. Imported names outside the reviewed catalogue remain historical evidence.
 */
@Slf4j
@Singleton
public final class BetaCollectionSnapshotService
{
	private static final String CONFIG_KEY = "betaCollectionSnapshotV1";
	private static final int SCHEMA_VERSION = 1;
	private static final String MANUAL_CONFIG_KEY = "betaCollectionManualV1";

	private final Persistence persistence;
	private final LongSupplier currentTimeMillis;
	private final List<String> identityNames;
	private final Set<String> identityNameSet;
	private final Set<String> legacyIdentityNameSet;
	private final String identityFingerprint;

	private Snapshot snapshot = Snapshot.none();
	private boolean exactRecoveryRejectionLogged;
	private String manualCurrent;
	private String manualPrevious;
	private String loadedProfile;
	private long revision;

	@Inject
	public BetaCollectionSnapshotService(ConfigManager configManager, Client client,
		PanelCollectionLayout catalog)
	{
		this(new ConfigPersistence(configManager, client), catalog, System::currentTimeMillis);
	}

	BetaCollectionSnapshotService(Persistence persistence, PanelCollectionLayout catalog,
		LongSupplier currentTimeMillis)
	{
		this.persistence = persistence;
		this.currentTimeMillis = currentTimeMillis;
		identityNames = betaIdentityNames(catalog.getLegacyBetaCollectionCards());
		legacyIdentityNameSet = Collections.unmodifiableSet(new LinkedHashSet<>(identityNames));
		identityNameSet = Collections.unmodifiableSet(new LinkedHashSet<>(
			betaIdentityNames(catalog.getBetaCollectionCards())));
		identityFingerprint = fingerprint(identityNames);
		loadedProfile = persistence.profileKey();
	}

	/** Reloads the snapshot stored for the current RuneScape profile. */
	public synchronized void reload()
	{
		revision++;
		loadedProfile = persistence.profileKey();
		manualCurrent = null;
		manualPrevious = null;
		exactRecoveryRejectionLogged = false;
		String raw;
		try
		{
			String manual = persistence.loadManual(loadedProfile);
			if (manual != null && !manual.isEmpty())
			{
				if (manual.length() > 16 * 1024 * 1024)
				{
					throw new IllegalArgumentException("manual snapshot exceeds size limit");
				}
				String[] parts = manual.split("\\|", -1);
				if (parts.length != 3 || !"1".equals(parts[0]))
				{
					throw new IllegalArgumentException("invalid manual snapshot envelope");
				}
				manualCurrent = unbase64(parts[1]);
				manualPrevious = unbase64(parts[2]);
				snapshot = decodeRecord(manualCurrent);
				return;
			}
			raw = persistence.load(loadedProfile);
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
			log.warn("Stored beta collection snapshot is incompatible.", ex);
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
			if (snapshot.status == Status.NONE && observed.isEmpty())
			{
				// OSRS TCG can answer its initial query before cloud collection sync. Do not
				// turn that transient empty reply into an irreversible empty snapshot.
				return false;
			}
			Status status = snapshot.status == Status.PROVISIONAL
				? Status.FROZEN_CAPTURED : Status.FROZEN_INFERRED;
			// A provisional picture is the pre-v1 collection. Preserve it even when the
			// first structurally complete v1 reply is transiently empty during cloud sync.
			Set<String> frozen = snapshot.status == Status.PROVISIONAL
				? snapshot.ownedNames : observed;
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

	/**
	 * Repairs historical state from persisted beta provenance. The source must be complete:
	 * one unknown name rejects the entire recovery rather than writing a partial snapshot.
	 */
	public synchronized boolean recoverExact(Set<String> ownedNames, boolean available)
	{
		if (!available || ownedNames == null || snapshot.status == Status.IMPORTED
			|| snapshot.status == Status.CLEARED || snapshot.status == Status.INCOMPATIBLE
			|| (snapshot.status == Status.FROZEN_CAPTURED && !snapshot.ownedNames.isEmpty()))
		{
			return false;
		}

		Set<String> recovered = new LinkedHashSet<>();
		int unknown = 0;
		for (String name : ownedNames)
		{
			String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
			if (normalized.isEmpty() || !identityNameSet.contains(normalized))
			{
				unknown++;
			}
			else
			{
				recovered.add(normalized);
			}
		}
		if (unknown > 0)
		{
			if (!exactRecoveryRejectionLogged)
			{
				log.warn("Beta snapshoCt recovery rejected because {} persisted beta identities "
					+ "are not in the protected catalogue", unknown);
				exactRecoveryRejectionLogged = true;
			}
			return false;
		}

		Set<String> exact = Collections.unmodifiableSet(recovered);
		if (snapshot.status == Status.FROZEN_CAPTURED && snapshot.ownedNames.equals(exact))
		{
			return false;
		}
		Snapshot next = new Snapshot(Status.FROZEN_CAPTURED, exact,
			currentTimeMillis.getAsLong());
		if (!install(next))
		{
			return false;
		}
		log.info("Recovered exact beta collection snapshot ({} identities)", exact.size());
		return true;
	}

	/** Whether exact persisted provenance could still improve the current snapshot. */
	public synchronized boolean canRecoverExact()
	{
		return snapshot.status != Status.IMPORTED && snapshot.status != Status.CLEARED
			&& snapshot.status != Status.INCOMPATIBLE
			&& (snapshot.status != Status.FROZEN_CAPTURED || snapshot.ownedNames.isEmpty());
	}

	/** Explicit user save. It refreshes the provisional snapshot but never freezes early. */
	public synchronized SaveResult saveCurrent(TcgOwnershipSnapshot ownership,
		boolean stateAvailable)
	{
		if (snapshot.status == Status.INCOMPATIBLE)
		{
			return new SaveResult(SaveOutcome.INCOMPATIBLE, getView());
		}
		if (snapshot.status.isFrozen() || snapshot.status == Status.CLEARED)
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
		if (loadedProfile == null || !Objects.equals(loadedProfile, persistence.profileKey()))
		{
			return false;
		}
		try
		{
			boolean needsNamedRecord = !legacyIdentityNameSet.containsAll(value.ownedNames);
			if (manualCurrent != null || needsNamedRecord)
			{
				String encoded = needsNamedRecord ? encodeNamed(value) : encode(value);
				String previous = manualCurrent == null ? persistence.load(loadedProfile) : manualPrevious;
				persistence.saveManual(loadedProfile, envelope(encoded, previous));
				manualCurrent = encoded;
				manualPrevious = previous;
			}
			else
			{
				persistence.save(loadedProfile, encode(value));
			}
			snapshot = value;
			revision++;
			return true;
		}
		catch (RuntimeException ex)
		{
			log.warn("Could not persist the beta collection snapshot", ex);
			return false;
		}
	}

	/** Captures the profile and snapshot revision before opening any modal UI or background work. */
	public synchronized EditToken beginEdit()
	{
		return !persistence.canEdit() || loadedProfile == null
			|| !Objects.equals(loadedProfile, persistence.profileKey())
			? null : new EditToken(loadedProfile, revision);
	}

	public synchronized boolean isCurrent(EditToken token)
	{
		return persistence.canEdit() && token != null && token.revision == revision
			&& Objects.equals(token.profile, loadedProfile)
			&& Objects.equals(token.profile, persistence.profileKey());
	}

	/** Explicit imports replace rather than union with an inferred collection. All names survive. */
	public synchronized void importNames(EditToken token, Set<String> names)
	{
		Set<String> normalized = validateNames(names);
		if (normalized.isEmpty())
		{
			throw new IllegalArgumentException("No Beta names to import. Use Wipe instead.");
		}
		manualChange(token, encodeNamed(Status.IMPORTED, normalized));
	}

	/** A persistent tombstone prevents the normal observation/recovery path undoing a user wipe. */
	public synchronized void wipe(EditToken token)
	{
		manualChange(token, encodeNamed(Status.CLEARED, Collections.emptySet()));
	}

	public synchronized boolean canRestore()
	{
		return manualPrevious != null;
	}

	public synchronized void restore(EditToken token)
	{
		if (manualPrevious == null)
		{
			throw new IllegalStateException("There is no previous snapshot to restore.");
		}
		manualChange(token, manualPrevious);
	}

	public Set<String> unmatchedNames(Set<String> names)
	{
		Set<String> unmatched = new java.util.TreeSet<>(names);
		unmatched.removeAll(identityNameSet);
		return Collections.unmodifiableSet(unmatched);
	}

	private void manualChange(EditToken token, String nextRaw)
	{
		if (!isCurrent(token))
		{
			throw new IllegalStateException("The account or snapshot changed. Please try again.");
		}
		Snapshot next = decodeRecord(nextRaw);
		if (snapshot.status == Status.INCOMPATIBLE)
		{
			String existingManual = persistence.loadManual(token.profile);
			if (existingManual != null && !existingManual.isEmpty())
			{
				throw new IllegalStateException("The existing manual snapshot is unreadable. "
					+ "It has been left untouched for recovery.");
			}
		}
		if ((next.status == Status.CLEARED || next.status == Status.IMPORTED)
			&& snapshot.status == next.status && snapshot.ownedNames.equals(next.ownedNames))
		{
			// Repeating an import/wipe must not replace the useful undo copy with itself.
			return;
		}
		String previous = manualCurrent == null ? persistence.load(token.profile) : manualCurrent;
		previous = previous == null ? "" : previous;
		// One write stores both the replacement and its undo copy. Never modify the legacy key.
		String replacement = envelope(nextRaw, previous);
		if (!isCurrent(token))
		{
			throw new IllegalStateException("The account changed. Please try again.");
		}
		persistence.saveManual(token.profile, replacement);
		manualCurrent = nextRaw;
		manualPrevious = previous;
		snapshot = next;
		revision++;
	}

	private String encodeNamed(Status status, Set<String> names)
	{
		return encodeNamed(new Snapshot(status, names, currentTimeMillis.getAsLong()));
	}

	private String encodeNamed(Snapshot value)
	{
		return "2|" + value.status.name() + "|" + value.capturedAtEpochMillis + "|"
			+ base64(String.join("\n", new java.util.TreeSet<>(value.ownedNames)));
	}

	private Snapshot decodeRecord(String raw)
	{
		if (raw.isEmpty())
		{
			return Snapshot.none();
		}
		if (!raw.startsWith("2|"))
		{
			return decode(raw);
		}
		String[] parts = raw.split("\\|", -1);
		if (parts.length != 4)
		{
			throw new IllegalArgumentException("invalid named snapshot");
		}
		Status status = Status.valueOf(parts[1]);
		long time = Long.parseLong(parts[2]);
		String decoded = unbase64(parts[3]);
		Set<String> names = validateNames(decoded.isEmpty() ? Collections.emptySet()
			: new LinkedHashSet<>(java.util.Arrays.asList(decoded.split("\n", -1))));
		if (time < 0 || status == Status.NONE || status == Status.INCOMPATIBLE
			|| (status == Status.CLEARED && !names.isEmpty())
			|| (status == Status.IMPORTED && names.isEmpty()))
		{
			throw new IllegalArgumentException("invalid named snapshot metadata");
		}
		return new Snapshot(status, names, time);
	}

	private static Set<String> validateNames(Set<String> names)
	{
		if (names == null || names.size() > 20_000)
		{
			throw new IllegalArgumentException("Invalid number of Beta names.");
		}
		Set<String> result = new LinkedHashSet<>();
		for (String value : names)
		{
			String name = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
			if (name.isEmpty() || name.length() > 200 || name.chars().anyMatch(Character::isISOControl))
			{
				throw new IllegalArgumentException("Invalid Beta card name.");
			}
			result.add(name);
		}
		return result;
	}

	private static String envelope(String current, String previous)
	{
		String encoded = "1|" + base64(current) + "|" + base64(previous == null ? "" : previous);
		if (encoded.length() > 16 * 1024 * 1024)
		{
			throw new IllegalArgumentException("Snapshot and undo copy exceed the storage limit.");
		}
		return encoded;
	}

	private static String base64(String value)
	{
		return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private static String unbase64(String value)
	{
		return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
	}

	public static final class EditToken
	{
		private final String profile;
		private final long revision;

		private EditToken(String profile, long revision)
		{
			this.profile = profile;
			this.revision = revision;
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
		if (status == Status.NONE || status == Status.INCOMPATIBLE
			|| status == Status.IMPORTED || status == Status.CLEARED || capturedAt < 0
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

	private static List<String> betaIdentityNames(List<PanelCollectionLayout.BetaCollectionCard> cards)
	{
		Set<String> names = new LinkedHashSet<>();
		for (PanelCollectionLayout.BetaCollectionCard card : cards)
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
		IMPORTED,
		CLEARED,
		INCOMPATIBLE;

		public boolean isFrozen()
		{
			return this == FROZEN_CAPTURED || this == FROZEN_INFERRED || this == IMPORTED;
		}

		private boolean isTerminal()
		{
			return isFrozen() || this == CLEARED || this == INCOMPATIBLE;
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
		String load(String profile);

		void save(String profile, String raw);

		String profileKey();

		boolean canEdit();

		String loadManual(String profile);

		void saveManual(String profile, String raw);
	}

	private static final class ConfigPersistence implements Persistence
	{
		private final ConfigManager configManager;
		private final Client client;

		private ConfigPersistence(ConfigManager configManager, Client client)
		{
			this.configManager = configManager;
			this.client = client;
		}

		@Override
		public String load(String profile)
		{
			return profile == null ? null
				: configManager.getConfiguration(BronzemanTcgConfig.GROUP, profile, CONFIG_KEY);
		}

		@Override
		public void save(String profile, String raw)
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, profile, CONFIG_KEY, raw);
		}

		@Override
		public String profileKey()
		{
			return configManager.getRSProfileKey();
		}

		@Override
		public boolean canEdit()
		{
			return client.getGameState() == GameState.LOGGED_IN;
		}

		@Override
		public String loadManual(String profile)
		{
			return profile == null ? null
				: configManager.getConfiguration(BronzemanTcgConfig.GROUP, profile, MANUAL_CONFIG_KEY);
		}

		@Override
		public void saveManual(String profile, String raw)
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, profile, MANUAL_CONFIG_KEY, raw);
		}
	}
}
