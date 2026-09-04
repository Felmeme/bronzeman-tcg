package com.bronzemantcg.panel.collection;

import com.bronzemantcg.ownership.TcgOwnershipSnapshot;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BetaCollectionSnapshotServiceTest
{
	private static final long NOW = 1_729_000_000_000L;

	@Test
	public void correctionPreservesLegacyFingerprintAndBitPositionsExactly()
	{
		MemoryPersistence memory = new MemoryPersistence();
		PanelCollectionLayout protectedLayout = new PanelCollectionLayout(new Gson(),
			"/panel/collection_layout.json", false);
		BetaCollectionSnapshotService original = new BetaCollectionSnapshotService(memory,
			protectedLayout, () -> NOW);
		original.observe(v1("Air rune", "Water rune"), true);
		String oldRaw = memory.raw;
		assertTrue(oldRaw.contains("sha256:9479946ff2180c7c6c78001121622ed0d67c19c0e3833dab930afc5ad305c5af"));
		BetaCollectionSnapshotService corrected = productionService(memory);
		corrected.reload();
		assertEquals(Set.of("air rune", "water rune"), corrected.getOwnedBetaNamesLowerCase());
		assertEquals(oldRaw, memory.raw);
		assertEquals(null, memory.manual);
	}

	@Test
	public void correctedHistoricalNamesSurviveAutomaticRecoveryAndRestart()
	{
		MemoryPersistence memory = new MemoryPersistence();
		BetaCollectionSnapshotService service = productionService(memory);
		assertTrue(service.observe(v1("Fish chunks", "Water rune"), true));
		assertEquals(BetaCollectionSnapshotService.Status.FROZEN_INFERRED, service.getStatus());
		assertTrue(service.canRecoverExact());
		assertTrue(service.recoverExact(Set.of("Fish chunks"), true));
		service = productionService(memory);
		service.reload();
		assertEquals(BetaCollectionSnapshotService.Status.FROZEN_CAPTURED, service.getStatus());
		assertEquals(Set.of("fish chunks"), service.getOwnedBetaNamesLowerCase());
		assertTrue(service.unmatchedNames(service.getOwnedBetaNamesLowerCase()).isEmpty());
		assertEquals(null, memory.raw);
	}

	@Test
	public void existingImportedFishChunksIsRecognisedWithoutReimporting()
	{
		MemoryPersistence memory = new MemoryPersistence();
		BetaCollectionSnapshotService before = new BetaCollectionSnapshotService(memory,
			new PanelCollectionLayout(new Gson(), "/panel/collection_layout.json", false), () -> NOW);
		before.importNames(before.beginEdit(), Set.of("Fish chunks"));
		String saved = memory.manual;
		BetaCollectionSnapshotService corrected = productionService(memory);
		corrected.reload();
		assertEquals(BetaCollectionSnapshotService.Status.IMPORTED, corrected.getStatus());
		assertTrue(corrected.unmatchedNames(corrected.getOwnedBetaNamesLowerCase()).isEmpty());
		assertEquals(saved, memory.manual);
	}

	private static BetaCollectionSnapshotService productionService(MemoryPersistence memory)
	{
		return new BetaCollectionSnapshotService(memory, new PanelCollectionLayout(new Gson()), () -> NOW);
	}

	@Test
	public void importPreservesUnknownNamesAndLegacyBytesAcrossRestart()
	{
		MemoryPersistence persistence = new MemoryPersistence();
		BetaCollectionSnapshotService service = service(persistence);
		service.observe(v1("Water rune", "Beta-only item"), true);
		String legacy = persistence.raw;
		service.importNames(service.beginEdit(), Set.of("Water rune pack", "Fish chunks"));
		assertEquals(legacy, persistence.raw);
		assertEquals(Set.of("fish chunks"), service.unmatchedNames(service.getOwnedBetaNamesLowerCase()));
		BetaCollectionSnapshotService reloaded = service(persistence);
		reloaded.reload();
		assertEquals(BetaCollectionSnapshotService.Status.IMPORTED, reloaded.getStatus());
		assertEquals(Set.of("water rune pack", "fish chunks"), reloaded.getOwnedBetaNamesLowerCase());
		assertFalse(reloaded.observe(v1("Beta-only item"), true));
		assertFalse(reloaded.recoverExact(Set.of("Beta-only item"), true));
		assertFalse(reloaded.canRecoverExact());
		reloaded.restore(reloaded.beginEdit());
		assertEquals(BetaCollectionSnapshotService.Status.FROZEN_INFERRED, reloaded.getStatus());
		assertEquals(Set.of("water rune", "beta-only item"), reloaded.getOwnedBetaNamesLowerCase());
	}

	@Test
	public void wipeStaysClearedAcrossRestartAndCanBeUndone()
	{
		MemoryPersistence persistence = new MemoryPersistence();
		BetaCollectionSnapshotService service = service(persistence);
		service.importNames(service.beginEdit(), Set.of("Fish chunks"));
		service.wipe(service.beginEdit());
		service = service(persistence);
		service.reload();
		assertEquals(BetaCollectionSnapshotService.Status.CLEARED, service.getStatus());
		assertTrue(service.getOwnedBetaNamesLowerCase().isEmpty());
		assertFalse(service.observe(v1("Water rune"), true));
		assertFalse(service.observe(namesOnly("Water rune"), true));
		assertFalse(service.recoverExact(Set.of("Water rune"), true));
		assertFalse(service.canRecoverExact());
		String wiped = persistence.manual;
		service.wipe(service.beginEdit());
		assertEquals(wiped, persistence.manual);
		assertEquals(BetaCollectionSnapshotService.SaveOutcome.ALREADY_FROZEN,
			service.saveCurrent(namesOnly("Water rune"), true).getOutcome());
		service.restore(service.beginEdit());
		assertEquals(Set.of("fish chunks"), service.getOwnedBetaNamesLowerCase());
	}

	@Test
	public void repeatingImportKeepsThePreviousSnapshotAndProfileReloadIsolatesManualData()
	{
		MemoryPersistence persistence = new MemoryPersistence();
		BetaCollectionSnapshotService service = service(persistence);
		service.importNames(service.beginEdit(), Set.of("Water rune"));
		service.importNames(service.beginEdit(), Set.of("Fish chunks"));
		String manual = persistence.manual;
		service.importNames(service.beginEdit(), Set.of("Fish chunks"));
		assertEquals(manual, persistence.manual);
		service.restore(service.beginEdit());
		assertEquals(Set.of("water rune"), service.getOwnedBetaNamesLowerCase());
		persistence.profile = "profile-b";
		persistence.manual = null;
		service.reload();
		assertEquals(BetaCollectionSnapshotService.Status.NONE, service.getStatus());
		assertTrue(service.getOwnedBetaNamesLowerCase().isEmpty());
		assertFalse(service.canRestore());
	}

	@Test
	public void rejectsStaleAccountRevisionAndLoggedOutEdits()
	{
		MemoryPersistence persistence = new MemoryPersistence();
		BetaCollectionSnapshotService service = service(persistence);
		BetaCollectionSnapshotService.EditToken token = service.beginEdit();
		persistence.profile = "profile-b";
		org.junit.Assert.assertThrows(IllegalStateException.class, () -> service.wipe(token));
		assertEquals(null, persistence.manual);
		persistence.profile = "profile-a";
		service.reload();
		org.junit.Assert.assertThrows(IllegalStateException.class, () -> service.wipe(token));
		BetaCollectionSnapshotService.EditToken beforeObserve = service.beginEdit();
		service.observe(v1("Water rune"), true);
		org.junit.Assert.assertThrows(IllegalStateException.class, () -> service.wipe(beforeObserve));
		BetaCollectionSnapshotService.EditToken beforeLogout = service.beginEdit();
		persistence.loggedIn = false;
		assertEquals(null, service.beginEdit());
		org.junit.Assert.assertThrows(IllegalStateException.class, () -> service.wipe(beforeLogout));
	}

	@Test
	public void accountSwitchWhileReadingUndoCopyCancelsBeforeWriting()
	{
		MemoryPersistence persistence = new MemoryPersistence();
		BetaCollectionSnapshotService service = service(persistence);
		persistence.switchProfileDuringLoad = true;
		org.junit.Assert.assertThrows(IllegalStateException.class,
			() -> service.importNames(service.beginEdit(), Set.of("Fish chunks")));
		assertEquals(null, persistence.manual);
		assertEquals(BetaCollectionSnapshotService.Status.NONE, service.getStatus());
	}

	@Test
	public void failedImportAndWipeLeaveCurrentAndUndoUntouched()
	{
		MemoryPersistence persistence = new MemoryPersistence();
		BetaCollectionSnapshotService service = service(persistence);
		service.importNames(service.beginEdit(), Set.of("Fish chunks"));
		String original = persistence.manual;
		persistence.failSave = true;
		org.junit.Assert.assertThrows(IllegalStateException.class, () -> service.wipe(service.beginEdit()));
		assertEquals(original, persistence.manual);
		assertEquals(Set.of("fish chunks"), service.getOwnedBetaNamesLowerCase());
		org.junit.Assert.assertThrows(IllegalStateException.class,
			() -> service.importNames(service.beginEdit(), Set.of("Water rune")));
		assertEquals(original, persistence.manual);
	}

	@Test
	public void importedNamesRemainReadableWithADifferentCatalogue()
	{
		MemoryPersistence persistence = new MemoryPersistence();
		BetaCollectionSnapshotService original = service(persistence);
		original.importNames(original.beginEdit(), Set.of("Fish chunks"));
		BetaCollectionSnapshotService reloaded = new BetaCollectionSnapshotService(persistence,
			new PanelCollectionLayout(new Gson()), () -> NOW);
		reloaded.reload();
		assertEquals(BetaCollectionSnapshotService.Status.IMPORTED, reloaded.getStatus());
		assertEquals(Set.of("fish chunks"), reloaded.getOwnedBetaNamesLowerCase());
	}

	@Test
	public void incompatibleLegacyIsPreservedAndManualDataNeverFallsBackSilently()
	{
		MemoryPersistence persistence = new MemoryPersistence();
		persistence.raw = "unknown legacy schema";
		BetaCollectionSnapshotService service = service(persistence);
		service.reload();
		service.importNames(service.beginEdit(), Set.of("Fish chunks"));
		String saved = persistence.manual;
		org.junit.Assert.assertThrows(IllegalArgumentException.class,
			() -> service.restore(service.beginEdit()));
		assertEquals(saved, persistence.manual);
		assertEquals("unknown legacy schema", persistence.raw);
		persistence.manual = "invalid manual record";
		service.reload();
		assertEquals(BetaCollectionSnapshotService.Status.INCOMPATIBLE, service.getStatus());
		assertFalse(service.observe(v1("Water rune"), true));
		assertEquals("invalid manual record", persistence.manual);
	}

	@Test
	public void rejectsInvalidOrEmptyImportedNames()
	{
		BetaCollectionSnapshotService service = service(new MemoryPersistence());
		org.junit.Assert.assertThrows(IllegalArgumentException.class,
			() -> service.importNames(service.beginEdit(), Set.of()));
		org.junit.Assert.assertThrows(IllegalArgumentException.class,
			() -> service.importNames(service.beginEdit(), Set.of("bad\nname")));
		org.junit.Assert.assertThrows(IllegalArgumentException.class,
			() -> service.importNames(service.beginEdit(), Set.of("x".repeat(201))));
	}

	@Test
	public void capturesAndFreezesReadableLegacySnapshot()
	{
		MemoryPersistence persistence = new MemoryPersistence();
		BetaCollectionSnapshotService service = service(persistence);

		assertTrue(service.observe(namesOnly("Water rune pack", "Not a beta card"), true));
		assertEquals(BetaCollectionSnapshotService.Status.PROVISIONAL, service.getStatus());
		assertEquals(Collections.singleton("water rune pack"),
			service.getOwnedBetaNamesLowerCase());

		assertTrue(service.observe(v1("Water rune pack", "Beta-only item"), true));
		assertEquals(BetaCollectionSnapshotService.Status.FROZEN_CAPTURED, service.getStatus());
		assertEquals(Collections.singleton("water rune pack"),
			service.getOwnedBetaNamesLowerCase());

		assertFalse(service.observe(v1("Beta-only item"), true));
		assertEquals(Collections.singleton("water rune pack"),
			service.getOwnedBetaNamesLowerCase());
	}

	@Test
	public void preservesAnObservedEmptyLegacyCollection()
	{
		MemoryPersistence persistence = new MemoryPersistence();
		BetaCollectionSnapshotService service = service(persistence);

		assertTrue(service.observe(namesOnly(), true));
		assertEquals(BetaCollectionSnapshotService.Status.PROVISIONAL, service.getStatus());
		assertTrue(service.getOwnedBetaNamesLowerCase().isEmpty());
		assertFalse(persistence.raw.isEmpty());

		assertTrue(service.observe(v1("Water rune"), true));
		assertEquals(BetaCollectionSnapshotService.Status.FROZEN_CAPTURED, service.getStatus());
		assertTrue(service.getOwnedBetaNamesLowerCase().isEmpty());
	}

	@Test
	public void completeEmptyV1PayloadPreservesNonEmptyProvisionalCollection()
	{
		BetaCollectionSnapshotService service = service(new MemoryPersistence());

		assertTrue(service.observe(namesOnly("Water rune pack"), true));
		assertTrue(service.observe(v1(), true));

		assertEquals(BetaCollectionSnapshotService.Status.FROZEN_CAPTURED,
			service.getStatus());
		assertEquals(Collections.singleton("water rune pack"),
			service.getOwnedBetaNamesLowerCase());
	}

	@Test
	public void waitsForCloudSyncInsteadOfFreezingFirstEmptyV1Payload()
	{
		BetaCollectionSnapshotService service = service(new MemoryPersistence());

		assertFalse(service.observe(v1(), true));
		assertEquals(BetaCollectionSnapshotService.Status.NONE, service.getStatus());

		assertTrue(service.observe(v1("Water rune", "Beta-only item"), true));
		assertEquals(BetaCollectionSnapshotService.Status.FROZEN_INFERRED,
			service.getStatus());
		assertEquals(Set.of("water rune", "beta-only item"),
			service.getOwnedBetaNamesLowerCase());
	}

	@Test
	public void manualSaveReplacesProvisionalExactlyWithoutFreezing()
	{
		MemoryPersistence persistence = new MemoryPersistence();
		BetaCollectionSnapshotService service = service(persistence);
		service.observe(namesOnly("Water rune pack"), true);

		BetaCollectionSnapshotService.SaveResult result = service.saveCurrent(
			namesOnly(), true);

		assertEquals(BetaCollectionSnapshotService.SaveOutcome.SAVED,
			result.getOutcome());
		assertEquals(BetaCollectionSnapshotService.Status.PROVISIONAL,
			service.getStatus());
		assertTrue(service.getOwnedBetaNamesLowerCase().isEmpty());
		assertFalse(persistence.raw.isEmpty());
	}

	@Test
	public void manualSaveDoesNotChangeFrozenOrUnavailableSnapshots()
	{
		MemoryPersistence frozenPersistence = new MemoryPersistence();
		BetaCollectionSnapshotService frozen = service(frozenPersistence);
		frozen.observe(namesOnly("Water rune pack"), true);
		frozen.observe(v1("Water rune pack"), true);
		String frozenRaw = frozenPersistence.raw;
		int frozenSaves = frozenPersistence.saveCount;

		BetaCollectionSnapshotService.SaveResult frozenResult = frozen.saveCurrent(
			namesOnly("Water rune"), true);

		assertEquals(BetaCollectionSnapshotService.SaveOutcome.ALREADY_FROZEN,
			frozenResult.getOutcome());
		assertEquals(frozenRaw, frozenPersistence.raw);
		assertEquals(frozenSaves, frozenPersistence.saveCount);

		MemoryPersistence unavailablePersistence = new MemoryPersistence();
		BetaCollectionSnapshotService unavailable = service(unavailablePersistence);
		BetaCollectionSnapshotService.SaveResult unavailableResult = unavailable.saveCurrent(
			namesOnly("Water rune"), false);

		assertEquals(BetaCollectionSnapshotService.SaveOutcome.UNAVAILABLE,
			unavailableResult.getOutcome());
		assertEquals(BetaCollectionSnapshotService.Status.NONE, unavailable.getStatus());
		assertEquals(0, unavailablePersistence.saveCount);
	}

	@Test
	public void persistenceFailureKeepsPreviousSnapshotUntouched()
	{
		MemoryPersistence persistence = new MemoryPersistence();
		BetaCollectionSnapshotService service = service(persistence);
		service.observe(namesOnly("Water rune pack"), true);
		String previousRaw = persistence.raw;
		persistence.failSave = true;

		BetaCollectionSnapshotService.SaveResult result = service.saveCurrent(
			namesOnly("Water rune"), true);

		assertEquals(BetaCollectionSnapshotService.SaveOutcome.PERSISTENCE_FAILED,
			result.getOutcome());
		assertEquals(Collections.singleton("water rune pack"),
			service.getOwnedBetaNamesLowerCase());
		assertEquals(previousRaw, persistence.raw);

		assertFalse(service.observe(namesOnly("Water rune"), true));
		assertEquals(Collections.singleton("water rune pack"),
			service.getOwnedBetaNamesLowerCase());
		assertEquals(previousRaw, persistence.raw);
	}

	@Test
	public void infersFirstSeenV1CollectionWhenNoLegacySnapshotExists()
	{
		BetaCollectionSnapshotService service = service(new MemoryPersistence());

		assertTrue(service.observe(v1("Water rune", "Beta-only item"), true));
		assertEquals(BetaCollectionSnapshotService.Status.FROZEN_INFERRED, service.getStatus());
		assertEquals(Set.of("water rune", "beta-only item"),
			service.getOwnedBetaNamesLowerCase());
	}

	@Test
	public void requiresBothV1IdListsBeforeFreezing()
	{
		BetaCollectionSnapshotService service = service(new MemoryPersistence());
		TcgOwnershipSnapshot partial = TcgOwnershipSnapshot.fromApi(
			Collections.singletonList("Water rune"), Collections.emptyList(), null, null);

		assertTrue(service.observe(partial, true));
		assertEquals(BetaCollectionSnapshotService.Status.PROVISIONAL, service.getStatus());
		assertTrue(service.observe(v1("Beta-only item"), true));
		assertEquals(BetaCollectionSnapshotService.Status.FROZEN_CAPTURED, service.getStatus());
		assertEquals(Collections.singleton("water rune"),
			service.getOwnedBetaNamesLowerCase());
	}

	@Test
	public void reloadsPersistedSnapshotForCurrentProfile()
	{
		MemoryPersistence persistence = new MemoryPersistence();
		BetaCollectionSnapshotService original = service(persistence);
		original.observe(namesOnly("Water rune pack"), true);
		original.observe(v1("Water rune pack"), true);

		BetaCollectionSnapshotService reloaded = service(persistence);
		reloaded.reload();
		assertEquals(BetaCollectionSnapshotService.Status.FROZEN_CAPTURED,
			reloaded.getStatus());
		assertEquals(Collections.singleton("water rune pack"),
			reloaded.getOwnedBetaNamesLowerCase());
	}

	@Test
	public void reloadClearsThePreviousProfilesInMemorySnapshot()
	{
		MemoryPersistence persistence = new MemoryPersistence();
		BetaCollectionSnapshotService service = service(persistence);
		service.observe(namesOnly("Water rune"), true);
		assertEquals(Collections.singleton("water rune"),
			service.getOwnedBetaNamesLowerCase());

		persistence.raw = null;
		service.reload();
		assertEquals(BetaCollectionSnapshotService.Status.NONE, service.getStatus());
		assertTrue(service.getOwnedBetaNamesLowerCase().isEmpty());
	}

	@Test
	public void leavesCorruptOrMismatchedStorageUntouched()
	{
		MemoryPersistence corrupt = new MemoryPersistence();
		corrupt.raw = "not-a-snapshot";
		BetaCollectionSnapshotService corruptService = service(corrupt);
		corruptService.reload();
		assertEquals(BetaCollectionSnapshotService.Status.INCOMPATIBLE,
			corruptService.getStatus());
		assertFalse(corruptService.observe(v1("Water rune"), true));
		assertFalse(corruptService.recoverExact(
			Collections.singleton("Water rune"), true));
		assertEquals(BetaCollectionSnapshotService.SaveOutcome.INCOMPATIBLE,
			corruptService.saveCurrent(namesOnly("Water rune"), true).getOutcome());
		assertEquals("not-a-snapshot", corrupt.raw);
		assertEquals(0, corrupt.saveCount);

		MemoryPersistence mismatched = new MemoryPersistence();
		BetaCollectionSnapshotService writer = service(mismatched);
		writer.observe(namesOnly("Water rune"), true);
		String[] parts = mismatched.raw.split("\\|", -1);
		parts[3] = "sha256:different";
		mismatched.raw = String.join("|", parts);
		mismatched.saveCount = 0;

		BetaCollectionSnapshotService reader = service(mismatched);
		reader.reload();
		assertEquals(BetaCollectionSnapshotService.Status.INCOMPATIBLE, reader.getStatus());
		assertEquals(0, mismatched.saveCount);
		assertTrue(mismatched.raw.contains("sha256:different"));
	}

	@Test
	public void ignoresUnavailableFallbackState()
	{
		MemoryPersistence persistence = new MemoryPersistence();
		BetaCollectionSnapshotService service = service(persistence);

		assertFalse(service.observe(namesOnly("Water rune"), false));
		assertEquals(BetaCollectionSnapshotService.Status.NONE, service.getStatus());
		assertEquals(0, persistence.saveCount);
	}

	@Test
	public void exactRecoveryRepairsEmptyCapturedSnapshot()
	{
		BetaCollectionSnapshotService service = service(new MemoryPersistence());
		service.observe(namesOnly(), true);
		service.observe(v1(), true);

		assertTrue(service.recoverExact(
			Set.of("Water rune pack", "Beta-only item"), true));
		assertEquals(BetaCollectionSnapshotService.Status.FROZEN_CAPTURED,
			service.getStatus());
		assertEquals(Set.of("water rune pack", "beta-only item"),
			service.getOwnedBetaNamesLowerCase());
	}

	@Test
	public void exactRecoveryRefinesInferredSnapshot()
	{
		BetaCollectionSnapshotService service = service(new MemoryPersistence());
		service.observe(v1("Water rune", "Beta-only item"), true);

		assertTrue(service.recoverExact(Collections.singleton("Water rune"), true));
		assertEquals(BetaCollectionSnapshotService.Status.FROZEN_CAPTURED,
			service.getStatus());
		assertEquals(Collections.singleton("water rune"),
			service.getOwnedBetaNamesLowerCase());
	}

	@Test
	public void exactRecoveryNeverReplacesNonEmptyCapturedSnapshot()
	{
		BetaCollectionSnapshotService service = service(new MemoryPersistence());
		service.observe(namesOnly("Water rune pack"), true);
		service.observe(v1(), true);

		assertFalse(service.canRecoverExact());
		assertFalse(service.recoverExact(Collections.singleton("Beta-only item"), true));
		assertEquals(Collections.singleton("water rune pack"),
			service.getOwnedBetaNamesLowerCase());
	}

	@Test
	public void exactRecoveryRejectsUnavailablePartialOrFailedPersistence()
	{
		MemoryPersistence persistence = new MemoryPersistence();
		BetaCollectionSnapshotService service = service(persistence);

		assertFalse(service.recoverExact(Collections.singleton("Water rune"), false));
		assertFalse(service.recoverExact(
			Set.of("Water rune", "Unknown beta identity"), true));
		assertEquals(BetaCollectionSnapshotService.Status.NONE, service.getStatus());
		assertEquals(0, persistence.saveCount);

		persistence.failSave = true;
		assertFalse(service.recoverExact(Collections.singleton("Water rune"), true));
		assertEquals(BetaCollectionSnapshotService.Status.NONE, service.getStatus());
	}

	private static BetaCollectionSnapshotService service(MemoryPersistence persistence)
	{
		PanelCollectionLayout catalog = new PanelCollectionLayout(
			new Gson(), "/panel/test_collection_layout.json");
		return new BetaCollectionSnapshotService(persistence, catalog, () -> NOW);
	}

	private static TcgOwnershipSnapshot namesOnly(String... names)
	{
		return TcgOwnershipSnapshot.fromApi(Arrays.asList(names), null, null, null);
	}

	private static TcgOwnershipSnapshot v1(String... names)
	{
		return TcgOwnershipSnapshot.fromApi(Arrays.asList(names),
			Collections.emptyList(), Collections.emptyList(), null);
	}

	private static final class MemoryPersistence
		implements BetaCollectionSnapshotService.Persistence
	{
		private String raw;
		private int saveCount;
		private boolean failSave;
		private String manual;
		private String profile = "profile-a";
		private boolean loggedIn = true;
		private boolean switchProfileDuringLoad;

		@Override
		public boolean canEdit()
		{
			return loggedIn;
		}

		@Override
		public String profileKey()
		{
			return profile;
		}

		@Override
		public String loadManual(String expectedProfile)
		{
			assertEquals(profile, expectedProfile);
			return manual;
		}

		@Override
		public void saveManual(String expectedProfile, String value)
		{
			assertEquals(profile, expectedProfile);
			if (failSave)
			{
				throw new IllegalStateException("simulated persistence failure");
			}
			manual = value;
		}

		@Override
		public String load(String expectedProfile)
		{
			assertEquals(profile, expectedProfile);
			if (switchProfileDuringLoad)
			{
				profile = "profile-b";
			}
			return raw;
		}

		@Override
		public void save(String expectedProfile, String value)
		{
			assertEquals(profile, expectedProfile);
			if (failSave)
			{
				throw new IllegalStateException("simulated persistence failure");
			}
			raw = value;
			saveCount++;
		}
	}
}
