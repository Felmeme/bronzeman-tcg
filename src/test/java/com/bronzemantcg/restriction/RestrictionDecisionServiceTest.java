package com.bronzemantcg.restriction;

import com.bronzemantcg.ownership.CardEntityKind;
import com.bronzemantcg.ownership.CardOwnershipService;
import com.bronzemantcg.ownership.CardResolver;
import com.bronzemantcg.ownership.TcgOwnershipSnapshot;
import com.bronzemantcg.support.SimulatedV1CardIdentityCatalog;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class RestrictionDecisionServiceTest
{
	@Test
	public void unavailableTutorialAndLmsStatesShareOneBypassDecision()
	{
		RestrictionDecisionTestSupport.Harness harness =
			RestrictionDecisionTestSupport.harness();
		RestrictionDecisionService service = harness.getService();

		assertFalse(service.isEnforcementBypassed());
		harness.stateAvailable(false);
		assertTrue(service.isEnforcementBypassed());
		harness.stateAvailable(true).tutorialProgress(670);
		assertTrue(service.isEnforcementBypassed());
		harness.tutorialProgress(0).lmsState(1);
		assertTrue(service.isEnforcementBypassed());
		harness.lmsState(0).mapRegions(13658);
		assertTrue(service.isEnforcementBypassed());
		harness.mapRegions(12850);
		assertFalse(service.isEnforcementBypassed());
	}

	@Test
	public void personalSharedAndExplicitItemOwnershipUseTheSameBoundary()
	{
		RestrictionDecisionTestSupport.Harness harness =
			RestrictionDecisionTestSupport.harness();
		RestrictionDecisionService service = harness.getService();

		assertTrue(service.isItemLocked(6739, "Dragon axe"));
		harness.ownership(TcgOwnershipSnapshot.namesOnly(Set.of("dragon axe")));
		assertFalse(service.isItemLocked(6739, "Dragon axe"));

		harness.ownership(emptyIds()).shared(Set.of("dragon axe"));
		assertFalse(service.isItemLocked(6739, "Dragon axe"));
		harness.acceptShared(false);
		assertTrue(service.isItemLocked(6739, "Dragon axe"));

		harness.configuredExempt(Set.of("dragon axe"));
		assertFalse(service.isItemLocked(6739, "Dragon axe"));
	}

	@Test
	public void onlyCoinsAndExplicitNamesEnterTheGlobalExemptionSet()
	{
		RestrictionDecisionTestSupport.Harness harness = RestrictionDecisionTestSupport.harness()
			.ownership(emptyIds())
			.coinsExempt(true)
			.configuredExempt(Set.of("lobster"));
		RestrictionDecisionService service = harness.getService();

		assertFalse(service.isItemLocked(995, "Coins"));
		assertFalse(service.isItemLocked(379, "Lobster"));
		assertTrue(service.isItemLocked(121, "Attack potion(3)"));
		assertTrue(service.requirementOwnership(CardEntityKind.ITEM).test("Coins"));
		assertTrue(service.requirementOwnership().test("Lobster"));
		assertFalse(service.requirementOwnership().test("Attack potion"));
	}

	@Test
	public void coinPouchVariantReportsOnlyTheCoinsParent()
	{
		RestrictionDecisionTestSupport.Harness harness = RestrictionDecisionTestSupport.harness()
			.ownership(TcgOwnershipSnapshot.fromApi(Collections.emptyList(),
				Collections.emptyList(), Collections.emptyList(), null));
		RestrictionDecisionService service = harness.getService();

		assertEquals("Coins", service.missingItemCardName(617, "Coin pouch"));
		harness.ownership(TcgOwnershipSnapshot.fromApi(Collections.emptyList(),
			List.of(617), Collections.emptyList(), null));
		assertNull(service.missingItemCardName(617, "Coin pouch"));
	}

	@Test
	public void betaCoinPouchDoesNotUnlockV1Coins()
	{
		RestrictionDecisionTestSupport.Harness harness = RestrictionDecisionTestSupport.harness()
			.ownership(TcgOwnershipSnapshot.namesOnly(Set.of("coin pouch")));
		RestrictionDecisionService service = harness.getService();

		assertEquals("Coins", service.missingItemCardName(22521, "Coin pouch"));
		harness.ownership(TcgOwnershipSnapshot.fromApi(List.of("Coin pouch"),
			List.of(22521), Collections.emptyList(), null));
		assertEquals("Coins", service.missingItemCardName(22521, "Coin pouch"));

		harness.ownership(TcgOwnershipSnapshot.fromApi(List.of("Coins"),
			List.of(22521), Collections.emptyList(), null));
		assertNull(service.missingItemCardName(22521, "Coin pouch"));
		harness.ownership(TcgOwnershipSnapshot.fromApi(Collections.emptyList(),
			List.of(995), Collections.emptyList(), null));
		assertNull(service.missingItemCardName(995, "Coins"));
	}

	@Test
	public void npcChecksUseOnlyExplicitExemptions()
	{
		RestrictionDecisionTestSupport.Harness harness = RestrictionDecisionTestSupport.harness()
			.ownership(emptyIds());
		RestrictionDecisionService service = harness.getService();

		assertTrue(service.isNpcLocked(2848, "Monkey"));
		harness.configuredExempt(Set.of("monkey (monster)"));
		assertFalse(service.isNpcLocked(2848, "Monkey"));
	}

	@Test
	public void permanentNpcExemptionsBypassCardOwnership()
	{
		RestrictionDecisionService service = RestrictionDecisionTestSupport.harness()
			.ownership(emptyIds())
			.getService();

		assertFalse(service.isNpcLocked(2850, "Veos"));
		assertFalse(service.isNpcLocked(311, "Ironman tutor"));
		assertTrue(service.isNpcLocked(2848, "Monkey"));
	}

	@Test
	public void unknownAndNamelessEntitiesStillFailOpen()
	{
		RestrictionDecisionService service =
			RestrictionDecisionTestSupport.harness().getService();

		assertFalse(service.isItemLocked(999999, "Unknown item"));
		assertFalse(service.isItemLocked(999999, "Dragon axe"));
		assertFalse(service.isItemLocked(6739, ""));
		assertFalse(service.isNpcLocked(999999, "Unknown NPC"));
		assertFalse(service.isNpcLocked(2848, null));
	}

	@Test
	public void ambiguousEntitiesAndParentRequirementsFailOpen()
	{
		CardOwnershipService ownershipService = new CardOwnershipService(
			new CardResolver(new SimulatedV1CardIdentityCatalog()));
		RestrictionDecisionService service = RestrictionDecisionTestSupport
			.harness(ownershipService).ownership(emptyIds()).getService();

		assertFalse(service.isNpcLocked(9000, "Ambiguous NPC"));
		assertTrue(service.requirementOwnership().test("Crawling Hand"));
	}

	@Test
	public void itemContextStaysStableUntilADecisionSourceChanges()
	{
		RestrictionDecisionTestSupport.Harness harness =
			RestrictionDecisionTestSupport.harness();
		RestrictionDecisionService service = harness.getService();
		RestrictionDecisionService.ItemContext first = service.getItemContext();

		assertSame(first, service.getItemContext());
		harness.shared(Set.of("dragon axe"));
		assertNotSame(first, service.getItemContext());
	}

	@Test
	public void itemIdCacheInvalidatesWithTheContext()
	{
		RestrictionDecisionTestSupport.Harness harness = RestrictionDecisionTestSupport.harness()
			.itemName(6739, "Dragon axe");
		RestrictionDecisionService service = harness.getService();

		assertTrue(service.isItemLocked(6739));
		assertTrue(service.isItemLocked(6739));
		assertEquals(1, harness.getItemNameCalls());

		harness.ownership(TcgOwnershipSnapshot.namesOnly(Set.of("dragon axe")));
		assertFalse(service.isItemLocked(6739));
		assertEquals(2, harness.getItemNameCalls());
	}

	@Test
	public void itemContextAndIdCacheInvalidateWithCatalogueRevision()
	{
		AtomicLong revision = new AtomicLong();
		CardOwnershipService ownershipService = new CardOwnershipService(
			new CardResolver(new SimulatedV1CardIdentityCatalog()));
		RestrictionDecisionTestSupport.Harness harness = RestrictionDecisionTestSupport
			.harness(ownershipService, revision::get)
			.itemName(6739, "Dragon axe");
		RestrictionDecisionService service = harness.getService();

		assertTrue(service.isItemLocked(6739));
		assertEquals(1, harness.getItemNameCalls());

		revision.incrementAndGet();
		assertTrue(service.isItemLocked(6739));
		assertEquals(2, harness.getItemNameCalls());
	}

	private static TcgOwnershipSnapshot emptyIds()
	{
		return TcgOwnershipSnapshot.fromApi(Collections.emptyList(),
			Collections.emptyList(), Collections.emptyList(), null);
	}
}
