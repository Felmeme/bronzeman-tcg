package com.bronzemantcg.collection;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Before;
import org.junit.Test;

import static com.bronzemantcg.collection.BetaCollectionSnapshotService.SaveOutcome.ALREADY_FROZEN;
import static com.bronzemantcg.collection.BetaCollectionSnapshotService.SaveOutcome.PERSISTENCE_FAILED;
import static com.bronzemantcg.collection.BetaCollectionSnapshotService.SaveOutcome.SAVED;
import static com.bronzemantcg.collection.BetaCollectionSnapshotService.SaveOutcome.UNAVAILABLE;
import static com.bronzemantcg.collection.BetaCollectionSnapshotService.Status.FROZEN_CAPTURED;
import static com.bronzemantcg.collection.BetaCollectionSnapshotService.Status.FROZEN_INFERRED;
import static com.bronzemantcg.collection.BetaCollectionSnapshotService.Status.INCOMPATIBLE;
import static com.bronzemantcg.collection.BetaCollectionSnapshotService.Status.NONE;
import static com.bronzemantcg.collection.BetaCollectionSnapshotService.Status.PROVISIONAL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BetaCollectionSnapshotServiceTest
{
	private MemoryPersistence persistence;
	private AtomicLong clock;
	private BetaCollectionSnapshotService service;

	@Before
	public void setUp()
	{
		persistence = new MemoryPersistence();
		clock = new AtomicLong(100L);
		service = new BetaCollectionSnapshotService(persistence,
			new BetaCollectionIdentityCatalog(Arrays.asList("alpha", "beta", "gamma")),
			clock::get);
	}

	@Test
	public void legacyUpdatesReplaceTheProvisionalSnapshotExactly()
	{
		assertTrue(service.observeLegacy(names("Alpha", "not a beta card"), true));
		assertView(PROVISIONAL, 1, 100L);
		assertEquals(names("alpha"), service.getOwnedBetaNamesLowerCase());

		clock.set(200L);
		assertTrue(service.observeLegacy(Collections.emptySet(), true));
		assertView(PROVISIONAL, 0, 200L);
		assertTrue(service.getOwnedBetaNamesLowerCase().isEmpty());
		assertFalse(persistence.raw.isEmpty());
	}

	@Test
	public void unreadableLegacyStateDoesNotReplaceOrWriteTheSnapshot()
	{
		service.observeLegacy(names("alpha"), true);
		String saved = persistence.raw;

		assertFalse(service.observeLegacy(Collections.emptySet(), false));
		assertEquals(saved, persistence.raw);
		assertEquals(names("alpha"), service.getOwnedBetaNamesLowerCase());
	}

	@Test
	public void manualSaveRefreshesProvisionalStateButNeverFreezesIt()
	{
		assertEquals(SAVED, service.saveCurrent(names("alpha"), true).getOutcome());
		clock.set(300L);
		assertEquals(SAVED, service.saveCurrent(names("beta"), true).getOutcome());

		assertView(PROVISIONAL, 1, 300L);
		assertEquals(names("beta"), service.getOwnedBetaNamesLowerCase());
		assertEquals(UNAVAILABLE,
			service.saveCurrent(names("alpha"), false).getOutcome());
	}

	@Test
	public void persistenceFailureLeavesThePreviousSnapshotUnchanged()
	{
		service.observeLegacy(names("alpha"), true);
		String saved = persistence.raw;
		persistence.failSaves = true;

		assertEquals(PERSISTENCE_FAILED,
			service.saveCurrent(names("beta"), true).getOutcome());
		assertEquals(saved, persistence.raw);
		assertEquals(names("alpha"), service.getOwnedBetaNamesLowerCase());
	}

	@Test
	public void firstCompleteV1PayloadFreezesTheProvisionalBetaSnapshot()
	{
		service.observeLegacy(names("alpha", "beta"), true);
		clock.set(400L);

		assertTrue(service.observeApi(names("gamma"), true));
		assertView(FROZEN_CAPTURED, 2, 400L);
		assertEquals(names("alpha", "beta"), service.getOwnedBetaNamesLowerCase());
		assertFalse(service.observeLegacy(names("gamma"), true));
		assertFalse(service.observeApi(names("gamma"), true));
		assertEquals(ALREADY_FROZEN,
			service.saveCurrent(names("gamma"), true).getOutcome());
	}

	@Test
	public void completeEmptyV1PayloadOverridesAStaleProvisionalSnapshot()
	{
		service.observeLegacy(names("alpha", "beta"), true);

		assertTrue(service.observeApi(Collections.emptySet(), true));
		assertView(FROZEN_CAPTURED, 0, 100L);
		assertTrue(service.getOwnedBetaNamesLowerCase().isEmpty());
	}

	@Test
	public void firstCompleteV1PayloadWithoutLegacyStateCreatesAnInferredSnapshot()
	{
		assertTrue(service.observeApi(names("beta", "unknown"), true));

		assertView(FROZEN_INFERRED, 1, 100L);
		assertEquals(names("beta"), service.getOwnedBetaNamesLowerCase());
	}

	@Test
	public void incompleteApiPayloadRemainsProvisional()
	{
		assertTrue(service.observeApi(names("gamma"), false));

		assertView(PROVISIONAL, 1, 100L);
		assertEquals(names("gamma"), service.getOwnedBetaNamesLowerCase());
	}

	@Test
	public void persistedSnapshotReloadsAndMissingProfileStateClearsMemory()
	{
		service.observeLegacy(names("alpha"), true);
		BetaCollectionSnapshotService reloaded = new BetaCollectionSnapshotService(persistence,
			new BetaCollectionIdentityCatalog(Arrays.asList("alpha", "beta", "gamma")),
			clock::get);

		reloaded.reload();
		assertEquals(names("alpha"), reloaded.getOwnedBetaNamesLowerCase());
		persistence.raw = null;
		reloaded.reload();
		assertEquals(NONE, reloaded.getView().getStatus());
		assertTrue(reloaded.getOwnedBetaNamesLowerCase().isEmpty());
	}

	@Test
	public void incompatibleStoredDataIsLeftUntouched()
	{
		persistence.raw = "1|PROVISIONAL|100|sha256:wrong|AQ==";

		service.reload();

		assertEquals(INCOMPATIBLE, service.getView().getStatus());
		assertEquals("1|PROVISIONAL|100|sha256:wrong|AQ==", persistence.raw);
		assertFalse(service.observeLegacy(names("alpha"), true));
	}

	private void assertView(BetaCollectionSnapshotService.Status status, int count, long timestamp)
	{
		BetaCollectionSnapshotService.SnapshotView view = service.getView();
		assertEquals(status, view.getStatus());
		assertEquals(count, view.getUniqueCardCount());
		assertEquals(timestamp, view.getCapturedAtEpochMillis());
	}

	private static Set<String> names(String... values)
	{
		return new LinkedHashSet<>(Arrays.asList(values));
	}

	private static final class MemoryPersistence
		implements BetaCollectionSnapshotService.Persistence
	{
		private String raw;
		private boolean failSaves;

		@Override
		public String load()
		{
			return raw;
		}

		@Override
		public void save(String value)
		{
			if (failSaves)
			{
				throw new IllegalStateException("test failure");
			}
			raw = value;
		}
	}
}
