package com.bronzemantcg.ownership;

import com.google.gson.Gson;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BundledCardIdentityCatalogTest
{
	@Test
	public void productionBetaFallbackHasReviewedCountsAndDiagnostics()
	{
		BundledCardIdentityCatalog catalog = new BundledCardIdentityCatalog(new Gson());
		assertEquals(5561, catalog.size());
		assertEquals(0, catalog.getAmbiguousIdCount(CardEntityKind.ITEM));
		assertEquals(1, catalog.getAmbiguousIdCount(CardEntityKind.NPC));
	}

	@Test
	public void variantIdsResolveToParentInSeparateNamespaces()
	{
		BundledCardIdentityCatalog catalog = new BundledCardIdentityCatalog(new Gson());
		assertSingleCard(catalog.findById(CardEntityKind.ITEM, 123), "Attack potion");
		assertTrue(catalog.findById(CardEntityKind.NPC, 123).isEmpty());
		assertSingleCard(catalog.findById(CardEntityKind.NPC, 2848), "Monkey");
		assertTrue(catalog.findById(CardEntityKind.ITEM, 2848).isEmpty());
	}

	@Test
	public void entityAliasesAndParentNamesHaveDistinctIndexes()
	{
		BundledCardIdentityCatalog catalog = new BundledCardIdentityCatalog(new Gson());
		assertSingleCard(catalog.findByName(CardEntityKind.NPC, "black chinchompa"),
			"Black chinchompa (Hunter)");
		assertTrue(catalog.findByCardName(
			CardEntityKind.NPC, "black chinchompa").isEmpty());
		assertSingleCard(catalog.findByCardName(
			CardEntityKind.NPC, "Black chinchompa (Hunter)"),
			"Black chinchompa (Hunter)");
	}

	@Test
	public void legacyParentNamesResolveToTheirCanonicalParent()
	{
		BundledCardIdentityCatalog catalog = new BundledCardIdentityCatalog(
			new Gson(), "/legacy-card-identity-catalog.json");
		assertEquals(1, catalog.size());
		assertSingleCard(catalog.findByCardName(CardEntityKind.ITEM, "Water rune"),
			"Water rune");
		assertSingleCard(catalog.findByCardName(CardEntityKind.ITEM, "water RUNE pack"),
			"Water rune");
		CardIdentity identity = catalog.findByCardName(
			CardEntityKind.ITEM, "Water rune pack").get(0);
		assertEquals(1, identity.getLegacyCardNames().size());
		assertTrue(identity.getLegacyCardNames().contains("Water rune pack"));
		assertEquals(1, identity.getOwnedNameRequiredEntityIds().size());
		assertTrue(identity.getOwnedNameRequiredEntityIds().contains(12730));
	}

	@Test
	public void reviewedBetaGroupingRemovesSameParentIdDuplication()
	{
		BundledCardIdentityCatalog catalog = new BundledCardIdentityCatalog(new Gson());
		assertSingleCard(catalog.findByName(CardEntityKind.NPC, "Soldier"), "Soldier");
		assertSingleCard(catalog.findById(CardEntityKind.NPC, 6438),
			"Animated steel armour");
	}

	@Test
	public void missingAndMalformedResourcesLoadAsEmpty()
	{
		BundledCardIdentityCatalog missing = new BundledCardIdentityCatalog(
			new Gson(), "/missing-card-identity-catalog.json");
		BundledCardIdentityCatalog malformed = new BundledCardIdentityCatalog(
			new Gson(), "/malformed-card-identity-catalog.json");
		assertEquals(0, missing.size());
		assertEquals(0, malformed.size());
		assertTrue(missing.findById(CardEntityKind.ITEM, 1).isEmpty());
	}

	@Test
	public void unreviewedV1ParentsRemainAbsent()
	{
		BundledCardIdentityCatalog catalog = new BundledCardIdentityCatalog(new Gson());
		assertTrue(catalog.findByCardName(CardEntityKind.ITEM, "Brewer's folly").isEmpty());
	}

	@Test
	public void productionFallbackIncludesReviewedBetaParentsAndAliases()
	{
		BundledCardIdentityCatalog catalog = new BundledCardIdentityCatalog(new Gson());
		assertSingleCard(catalog.findByCardName(CardEntityKind.ITEM, "Araxyte venom sac"),
			"Araxyte venom sac");
		assertSingleCard(catalog.findByCardName(CardEntityKind.ITEM, "Araxyte venom sack"),
			"Araxyte venom sac");
		assertTrue(catalog.findByCardName(CardEntityKind.NPC, "H.A.M. Member").isEmpty());
	}

	@Test
	public void crossParentBetaIdRequiresOwnedNameAndFailsOpenForEntityResolution()
	{
		BundledCardIdentityCatalog catalog = new BundledCardIdentityCatalog(new Gson());
		assertEquals(2, catalog.findById(CardEntityKind.NPC, 14706).size());
		for (CardIdentity identity : catalog.findById(CardEntityKind.NPC, 14706))
		{
			assertTrue(identity.getOwnedNameRequiredEntityIds().contains(14706));
		}
	}

	@Test
	public void retiredBurntRequirementsRemainRecognisedAsBetaVariants()
	{
		BundledCardIdentityCatalog catalog = new BundledCardIdentityCatalog(new Gson());
		assertSingleCard(catalog.findByCardName(CardEntityKind.ITEM, "Burnt lobster"),
			"Lobster");
	}

	@Test
	public void coinPouchResolvesToCoinsWithoutOwningIt()
	{
		BundledCardIdentityCatalog catalog = new BundledCardIdentityCatalog(new Gson());
		CardIdentity coins = catalog.findById(CardEntityKind.ITEM, 22521).get(0);

		assertEquals("Coins", coins.getCardName());
		assertTrue(catalog.findByCardName(CardEntityKind.ITEM, "Coin pouch").isEmpty());
		assertTrue(coins.getOwnedNameRequiredEntityIds().contains(22521));
		assertFalse(coins.isOwnedBy(TcgOwnershipSnapshot.namesOnly(
			java.util.Set.of("Coin pouch"))));
	}

	private static void assertSingleCard(List<CardIdentity> matches, String cardName)
	{
		assertEquals(1, matches.size());
		assertEquals(cardName, matches.get(0).getCardName());
	}

}
