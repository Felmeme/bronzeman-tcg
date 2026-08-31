package com.bronzemantcg.ownership;

import com.bronzemantcg.support.SimulatedV1CardIdentityCatalog;
import com.google.gson.Gson;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CardOwnershipServiceTest
{
	private CardOwnershipService service;

	@Before
	public void setUp()
	{
		service = new CardOwnershipService(
			new CardResolver(new BundledCardIdentityCatalog(new Gson())));
	}

	@Test
	public void personalIdsAreAuthoritativeWhenNamespaceIsPresent()
	{
		TcgOwnershipSnapshot owned = TcgOwnershipSnapshot.fromApi(
			Collections.singletonList("Dragon axe"), Collections.singletonList(6739),
			Collections.emptyList(), null);
		assertStatus(CardOwnershipService.Status.OWNED,
			service.decide(CardEntityKind.ITEM, 6739, "Dragon axe", owned, null, null));

		TcgOwnershipSnapshot idSaysNotOwned = TcgOwnershipSnapshot.fromApi(
			Collections.singletonList("Dragon axe"), Collections.emptyList(),
			Collections.emptyList(), null);
		assertStatus(CardOwnershipService.Status.LOCKED,
			service.decide(CardEntityKind.ITEM, 6739, "Dragon axe", idSaysNotOwned, null, null));
	}

	@Test
	public void freshOfflineV1FailsOpenOnlyForIdentityMissingFromBetaFallback()
	{
		TcgOwnershipSnapshot emptyV1 = TcgOwnershipSnapshot.fromApi(
			Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null);
		CardOwnershipService.Decision v1Only = service.decideCard(
			CardEntityKind.NPC, "H.A.M. Member", emptyV1,
			Collections.emptySet(), Collections.emptySet());
		CardOwnershipService.Decision betaKnown = service.decideCard(
			CardEntityKind.NPC, "Monkey", emptyV1,
			Collections.emptySet(), Collections.emptySet());

		assertEquals(CardOwnershipService.Status.UNTRACKED, v1Only.getStatus());
		assertTrue(v1Only.isAllowed());
		assertEquals(CardOwnershipService.Status.LOCKED, betaKnown.getStatus());
		assertFalse(betaKnown.isAllowed());
	}

	@Test
	public void crossParentBetaIdCannotCollectEitherParentWithoutItsOwnedName()
	{
		TcgOwnershipSnapshot idOnly = TcgOwnershipSnapshot.fromApi(
			Collections.emptyList(), Collections.emptyList(),
			Collections.singletonList(14706), null);
		assertStatus(CardOwnershipService.Status.LOCKED, service.decideCard(
			CardEntityKind.NPC, "Juvenile custodian stalker", idOnly, null, null));
		assertStatus(CardOwnershipService.Status.LOCKED, service.decideCard(
			CardEntityKind.NPC, "Phantom Muspah", idOnly, null, null));

		TcgOwnershipSnapshot named = TcgOwnershipSnapshot.fromApi(
			Collections.singletonList("Strange creature (Shadows of Custodia)"),
			Collections.emptyList(), Collections.singletonList(14706), null);
		assertStatus(CardOwnershipService.Status.OWNED, service.decideCard(
			CardEntityKind.NPC, "Juvenile custodian stalker", named, null, null));
		assertStatus(CardOwnershipService.Status.LOCKED, service.decideCard(
			CardEntityKind.NPC, "Phantom Muspah", named, null, null));
	}

	@Test
	public void itemDoseIdsShareOneParentCard()
	{
		TcgOwnershipSnapshot ownedDose = TcgOwnershipSnapshot.fromApi(
			Collections.singletonList("Attack potion"), Collections.singletonList(123),
			Collections.emptyList(), null);
		assertStatus(CardOwnershipService.Status.OWNED,
			service.decide(CardEntityKind.ITEM, 121, "Attack potion(3)", ownedDose,
				null, null));

		TcgOwnershipSnapshot noOwnedDose = TcgOwnershipSnapshot.fromApi(
			Collections.singletonList("Attack potion"), Collections.emptyList(),
			Collections.emptyList(), null);
		assertStatus(CardOwnershipService.Status.LOCKED,
			service.decide(CardEntityKind.ITEM, 121, "Attack potion(3)", noOwnedDose,
				null, null));
	}

	@Test
	public void legacyNamesOnlyOwnershipStillWorks()
	{
		TcgOwnershipSnapshot legacy = TcgOwnershipSnapshot.namesOnly(
			Collections.singleton("dragon axe"));
		assertStatus(CardOwnershipService.Status.OWNED,
			service.decide(CardEntityKind.ITEM, 6739, "Dragon axe", legacy, null, null));

		TcgOwnershipSnapshot legacyPotion = TcgOwnershipSnapshot.namesOnly(
			Collections.singleton("attack potion"));
		assertStatus(CardOwnershipService.Status.OWNED,
			service.decide(CardEntityKind.ITEM, -1, "Attack potion(3)", legacyPotion,
				null, null));
	}

	@Test
	public void npcIdsAreAuthoritativeAndAnyVariantOwnsTheParentCard()
	{
		TcgOwnershipSnapshot ownedVariant = TcgOwnershipSnapshot.fromApi(
			Collections.singletonList("Monkey Archer"), Collections.emptyList(),
			Collections.singletonList(5272), null);
		assertStatus(CardOwnershipService.Status.OWNED,
			service.decide(CardEntityKind.NPC, 5274, "Monkey archer", ownedVariant,
				null, null));

		TcgOwnershipSnapshot idSaysNotOwned = TcgOwnershipSnapshot.fromApi(
			Collections.singletonList("Monkey Archer"), Collections.emptyList(),
			Collections.emptyList(), null);
		assertStatus(CardOwnershipService.Status.LOCKED,
			service.decide(CardEntityKind.NPC, 5274, "Monkey archer", idSaysNotOwned,
				null, null));
	}

	@Test
	public void npcNamesRemainTheCompatibilityFallback()
	{
		TcgOwnershipSnapshot legacy = TcgOwnershipSnapshot.namesOnly(
			Collections.singleton("monkey (monster)"));
		assertStatus(CardOwnershipService.Status.OWNED,
			service.decide(CardEntityKind.NPC, 2848, "Monkey", legacy, null, null));
	}

	@Test
	public void sharedAndExemptNpcParentNamesRemainAdditive()
	{
		TcgOwnershipSnapshot empty = TcgOwnershipSnapshot.fromApi(
			Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null);
		assertStatus(CardOwnershipService.Status.SHARED,
			service.decide(CardEntityKind.NPC, 2848, "Monkey", empty,
				Collections.singleton("Monkey (monster)"), null));
		assertStatus(CardOwnershipService.Status.EXEMPT,
			service.decide(CardEntityKind.NPC, 2848, "Monkey", empty,
				null, Collections.singleton("Monkey (monster)")));
	}

	@Test
	public void parentCardRequirementsUseAuthoritativeVariantIdsAcrossNamespaces()
	{
		TcgOwnershipSnapshot ownedVariants = TcgOwnershipSnapshot.fromApi(
			Collections.emptyList(), Collections.singletonList(123),
			Collections.singletonList(5272), null);
		assertStatus(CardOwnershipService.Status.OWNED,
			service.decideCard("Attack potion", ownedVariants, null, null));
		assertStatus(CardOwnershipService.Status.OWNED,
			service.decideCard("Monkey Archer", ownedVariants, null, null));

		TcgOwnershipSnapshot namesCannotOverridePresentIds = TcgOwnershipSnapshot.fromApi(
			Collections.singletonList("Attack potion"), Collections.emptyList(),
			Collections.emptyList(), null);
		assertStatus(CardOwnershipService.Status.LOCKED,
			service.decideCard("Attack potion", namesCannotOverridePresentIds, null, null));
	}

	@Test
	public void parentCardRequirementsPreserveFallbackAndAdditiveSources()
	{
		TcgOwnershipSnapshot legacy = TcgOwnershipSnapshot.namesOnly(
			Collections.singleton("monkey (monster)"));
		assertStatus(CardOwnershipService.Status.OWNED,
			service.decideCard("Monkey (monster)", legacy, null, null));

		TcgOwnershipSnapshot empty = TcgOwnershipSnapshot.fromApi(
			Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null);
		assertStatus(CardOwnershipService.Status.SHARED,
			service.decideCard("Attack potion", empty,
				Collections.singleton("Attack potion"), null));
		assertStatus(CardOwnershipService.Status.EXEMPT,
			service.decideCard("Attack potion", empty,
				null, Collections.singleton("Attack potion")));
		assertAllowedStatus(CardOwnershipService.Status.UNTRACKED,
			service.decideCard("Brewer's folly", empty, null, null));
	}

	@Test
	public void legacyParentAliasesPreserveIdAuthorityForEnforcement()
	{
		CardOwnershipService v1Service = new CardOwnershipService(
			new CardResolver(new SimulatedV1CardIdentityCatalog()));
		TcgOwnershipSnapshot legacy = TcgOwnershipSnapshot.namesOnly(
			Collections.singleton("water rune pack"));
		assertStatus(CardOwnershipService.Status.OWNED,
			v1Service.decideCard("Water rune", legacy, null, null));
		assertStatus(CardOwnershipService.Status.OWNED,
			v1Service.decideCard("Water rune pack", legacy, null, null));

		TcgOwnershipSnapshot idsSayNotOwned = TcgOwnershipSnapshot.fromApi(
			Collections.singletonList("Water rune pack"), Collections.emptyList(),
			Collections.emptyList(), null);
		assertStatus(CardOwnershipService.Status.LOCKED,
			v1Service.decideCard("Water rune", idsSayNotOwned, null, null));
		assertStatus(CardOwnershipService.Status.SHARED,
			v1Service.decideCard("Water rune", idsSayNotOwned,
				Collections.singleton("Water rune pack"), null));
		assertStatus(CardOwnershipService.Status.EXEMPT,
			v1Service.decideCard("Water rune", idsSayNotOwned,
				null, Collections.singleton("Water rune pack")));
	}

	@Test
	public void displayOwnershipIsAdditiveButNeverFailsOpen()
	{
		CardOwnershipService v1Service = new CardOwnershipService(
			new CardResolver(new SimulatedV1CardIdentityCatalog()));
		TcgOwnershipSnapshot legacyNameWithEmptyIds = TcgOwnershipSnapshot.fromApi(
			Collections.singletonList("Water rune pack"), Collections.emptyList(),
			Collections.emptyList(), null);
		assertTrue(v1Service.isCollectedCard("Water rune", legacyNameWithEmptyIds, null));

		TcgOwnershipSnapshot variantIdOnly = TcgOwnershipSnapshot.fromApi(
			Collections.emptyList(), Collections.singletonList(12730),
			Collections.emptyList(), null);
		assertTrue(v1Service.isCollectedCard("Water rune", variantIdOnly, null));
		assertFalse(v1Service.isCollectedCard(
			"Armoured zombie (Defender of Varrock)", variantIdOnly, null));

		TcgOwnershipSnapshot empty = TcgOwnershipSnapshot.fromApi(
			Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null);
		assertTrue(v1Service.isCollectedCard("Water rune", empty,
			Collections.singleton("Water rune pack")));
		assertFalse(v1Service.isCollectedCard("Unknown card", empty, null));

		TcgOwnershipSnapshot exactUnknown = TcgOwnershipSnapshot.fromApi(
			Collections.singletonList("Unknown card"), Collections.emptyList(),
			Collections.emptyList(), null);
		assertTrue(v1Service.isCollectedCard("Unknown card", exactUnknown, null));
	}

	@Test
	public void npcLegacyParentAliasResolvesWithinTheNpcNamespace()
	{
		CardOwnershipService v1Service = new CardOwnershipService(
			new CardResolver(new SimulatedV1CardIdentityCatalog()));
		TcgOwnershipSnapshot legacy = TcgOwnershipSnapshot.namesOnly(
			Collections.singleton("armoured zombie (quest)"));
		assertStatus(CardOwnershipService.Status.OWNED,
			v1Service.decideCard("Armoured zombie (Defender of Varrock)",
				legacy, null, null));
		assertTrue(v1Service.isCollectedCard(
			"Armoured zombie (Defender of Varrock)", legacy, null));
		assertFalse(v1Service.isCollectedCard("Water rune", legacy, null));
	}

	@Test
	public void sameNameParentsAreSeparatedByExplicitNamespace()
	{
		CardOwnershipService v1Service = new CardOwnershipService(
			new CardResolver(new SimulatedV1CardIdentityCatalog()));
		TcgOwnershipSnapshot itemOnly = TcgOwnershipSnapshot.fromApi(
			Collections.emptyList(), Collections.singletonList(7975),
			Collections.emptyList(), null);
		assertStatus(CardOwnershipService.Status.OWNED,
			v1Service.decideCard(CardEntityKind.ITEM, "Crawling Hand",
				itemOnly, null, null));
		assertStatus(CardOwnershipService.Status.LOCKED,
			v1Service.decideCard(CardEntityKind.NPC, "Crawling Hand",
				itemOnly, null, null));
		assertStatus(CardOwnershipService.Status.LOCKED,
			v1Service.decideCard(CardEntityKind.NPC, "Crawling Hand",
				itemOnly, Collections.singleton("Crawling Hand"), null));
		assertAllowedStatus(CardOwnershipService.Status.AMBIGUOUS,
			v1Service.decideCard("Crawling Hand", itemOnly, null, null));

		TcgOwnershipSnapshot npcOnly = TcgOwnershipSnapshot.fromApi(
			Collections.emptyList(), Collections.emptyList(),
			Collections.singletonList(448), null);
		assertStatus(CardOwnershipService.Status.LOCKED,
			v1Service.decideCard(CardEntityKind.ITEM, "Crawling Hand",
				npcOnly, null, null));
		assertStatus(CardOwnershipService.Status.OWNED,
			v1Service.decideCard(CardEntityKind.NPC, "Crawling Hand",
				npcOnly, null, null));
	}

	@Test
	public void sameNameCollectionRowsUseTheirExplicitNamespace()
	{
		CardOwnershipService v1Service = new CardOwnershipService(
			new CardResolver(new SimulatedV1CardIdentityCatalog()));
		TcgOwnershipSnapshot owned = TcgOwnershipSnapshot.fromApi(
			Collections.singletonList("Manta ray"), Collections.singletonList(391),
			Collections.singletonList(2182), null);

		assertTrue(v1Service.isCollectedCard(CardEntityKind.ITEM,
			"Manta ray", owned, null));
		assertFalse(v1Service.isCollectedCard(CardEntityKind.NPC,
			"Manta ray", owned, null));
		assertFalse(v1Service.isCollectedCard(CardEntityKind.NPC,
			"Manta ray", owned, Collections.singleton("Manta ray")));
		assertFalse(v1Service.isCollectedCard(CardEntityKind.ITEM,
			"Rock golem", owned, null));
		assertTrue(v1Service.isCollectedCard(CardEntityKind.NPC,
			"Rock golem", owned, null));
	}

	@Test
	public void sharedAndExemptParentNamesAreAdditive()
	{
		TcgOwnershipSnapshot empty = TcgOwnershipSnapshot.fromApi(
			Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null);
		assertStatus(CardOwnershipService.Status.SHARED,
			service.decide(CardEntityKind.ITEM, 6739, "Dragon axe", empty,
				Collections.singleton("Dragon Axe"), null));
		assertStatus(CardOwnershipService.Status.EXEMPT,
			service.decide(CardEntityKind.ITEM, 6739, "Dragon axe", empty,
				null, Collections.singleton("Dragon Axe")));
	}

	@Test
	public void lockedReviewedIdentityIsTheOnlyDenyingResult()
	{
		TcgOwnershipSnapshot empty = TcgOwnershipSnapshot.fromApi(
			Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null);
		CardOwnershipService.Decision decision = service.decide(
			CardEntityKind.ITEM, 6739, "Dragon axe", empty, null, null);
		assertStatus(CardOwnershipService.Status.LOCKED, decision);
		assertFalse(decision.isAllowed());
		assertEquals("Dragon axe", decision.getIdentity().getCardName());
	}

	@Test
	public void unresolvedStatesFailOpenWhileCanonicalRuntimeIdsStillLock()
	{
		TcgOwnershipSnapshot empty = TcgOwnershipSnapshot.fromApi(
			Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null);
		assertAllowedStatus(CardOwnershipService.Status.UNTRACKED,
			service.decide(CardEntityKind.ITEM, 999999, "Unknown", empty, null, null));
		assertAllowedStatus(CardOwnershipService.Status.CATALOG_MISMATCH,
			service.decide(CardEntityKind.ITEM, 999999, "Dragon axe", empty, null, null));
		assertStatus(CardOwnershipService.Status.LOCKED,
			service.decide(CardEntityKind.NPC, 6438, "Animated steel armour", empty, null, null));
	}

	private static void assertAllowedStatus(CardOwnershipService.Status status,
		CardOwnershipService.Decision decision)
	{
		assertStatus(status, decision);
		assertTrue(decision.isAllowed());
	}

	private static void assertStatus(CardOwnershipService.Status status,
		CardOwnershipService.Decision decision)
	{
		assertEquals(status, decision.getStatus());
	}
}
