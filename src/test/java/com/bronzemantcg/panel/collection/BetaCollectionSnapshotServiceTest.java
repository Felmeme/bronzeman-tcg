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
	public void completeEmptyV1PayloadOverridesStaleProvisionalCollection()
	{
		BetaCollectionSnapshotService service = service(new MemoryPersistence());

		assertTrue(service.observe(namesOnly("Water rune pack"), true));
		assertTrue(service.observe(v1(), true));

		assertEquals(BetaCollectionSnapshotService.Status.FROZEN_CAPTURED,
			service.getStatus());
		assertTrue(service.getOwnedBetaNamesLowerCase().isEmpty());
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

		@Override
		public String load()
		{
			return raw;
		}

		@Override
		public void save(String value)
		{
			if (failSave)
			{
				throw new IllegalStateException("simulated persistence failure");
			}
			raw = value;
			saveCount++;
		}
	}
}
