package com.bronzemantcg.ownership;

import com.google.gson.Gson;
import com.google.inject.ImplementedBy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ActiveCardIdentityCatalogTest
{
	private BundledCardIdentityCatalog bundled;
	private ActiveCardIdentityCatalog active;

	@Before
	public void setUp()
	{
		bundled = new BundledCardIdentityCatalog(new Gson());
		active = new ActiveCardIdentityCatalog(bundled);
	}

	@Test
	public void cardIdentityBoundaryUsesAtomicActiveCatalog()
	{
		ImplementedBy binding = CardIdentityCatalog.class.getAnnotation(ImplementedBy.class);
		assertSame(ActiveCardIdentityCatalog.class, binding.value());
	}

	@Test
	public void fallbackViewExposesReviewedBetaEntriesButNotV1Availability()
	{
		assertFalse(active.isV1CatalogAvailable());
		assertFalse(active.getView().isV1CatalogAvailable());
		assertEquals(bundled.size(), active.getView().getCardIdentities().size());
		assertTrue(active.getView().getEntityToCardNames(CardEntityKind.ITEM)
			.containsKey("water rune"));
	}

	@Test
	public void startsBundledActivatesWholeRemoteRevisionAndRollsBack()
	{
		ImmutableCardIdentityCatalog remote = remote("Remote water", 555, "Remote water entity");
		long bundledRevision = active.getRevision();

		long remoteRevision = active.activate(remote, remote.getEntries(), "version-1");

		assertTrue(active.isRemoteActive());
		assertTrue(active.isV1CatalogAvailable());
		assertTrue(remoteRevision > bundledRevision);
		assertEquals("Remote water", active.findById(CardEntityKind.ITEM, 555).get(0).getCardName());
		assertEquals(Set.of("remote water"), active.getView()
			.getEntityToCardNames(CardEntityKind.ITEM).get("remote water entity"));

		long restoredRevision = active.useBundled();
		assertFalse(active.isRemoteActive());
		assertFalse(active.isV1CatalogAvailable());
		assertTrue(restoredRevision > remoteRevision);
		assertEquals("Water rune", active.findById(CardEntityKind.ITEM, 555).get(0).getCardName());
	}

	@Test
	public void reapplyingSameImmutableRevisionDoesNotInvalidateCachesAgain()
	{
		ImmutableCardIdentityCatalog remote = remote("Remote", 1, "Remote");
		long first = active.activate(remote, remote.getEntries(), "version-1");

		assertEquals(first, active.activate(remote, remote.getEntries(), "version-1"));
		assertEquals(first, active.getRevision());
	}

	@Test
	public void concurrentReadersSeeOnlyCompleteCatalogues() throws Exception
	{
		ImmutableCardIdentityCatalog remote = remote("Remote water", 555, "Water rune");
		CompletableFuture<Void> writer = CompletableFuture.runAsync(() ->
		{
			for (int i = 0; i < 250; i++)
			{
				active.activate(remote, remote.getEntries(), "version-" + i);
				active.useBundled();
			}
		});
		List<CompletableFuture<Void>> readers = new ArrayList<>();
		for (int thread = 0; thread < 4; thread++)
		{
			readers.add(CompletableFuture.runAsync(() ->
			{
				for (int i = 0; i < 5000; i++)
				{
					List<CardIdentity> matches = active.findById(CardEntityKind.ITEM, 555);
					assertEquals(1, matches.size());
					String name = matches.get(0).getCardName();
					assertTrue("Water rune".equals(name) || "Remote water".equals(name));
				}
			}));
		}
		readers.add(writer);
		CompletableFuture.allOf(readers.toArray(new CompletableFuture[0]))
			.get(5, TimeUnit.SECONDS);
	}

	private static ImmutableCardIdentityCatalog remote(String parent, int id, String entityName)
	{
		CardIdentity identity = new CardIdentity(CardEntityKind.ITEM, parent, Set.of(id));
		return new ImmutableCardIdentityCatalog(List.of(
			new ImmutableCardIdentityCatalog.Entry(identity, List.of(entityName))));
	}
}
