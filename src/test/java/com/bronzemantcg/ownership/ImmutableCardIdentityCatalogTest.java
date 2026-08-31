package com.bronzemantcg.ownership;

import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ImmutableCardIdentityCatalogTest
{
	@Test
	public void indexesIdsEntityNamesAndCurrentAndLegacyCardNames()
	{
		CardIdentity identity = new CardIdentity(CardEntityKind.ITEM, "Water rune",
			Set.of("Water rune pack"), Set.of(555, 12730));
		ImmutableCardIdentityCatalog catalog = new ImmutableCardIdentityCatalog(List.of(
			new ImmutableCardIdentityCatalog.Entry(identity,
				List.of("Water rune", "Water rune pack"))));

		assertSame(identity, catalog.findById(CardEntityKind.ITEM, 12730).get(0));
		assertSame(identity, catalog.findByName(CardEntityKind.ITEM, "water RUNE pack").get(0));
		assertSame(identity, catalog.findByCardName(CardEntityKind.ITEM, "Water rune").get(0));
		assertSame(identity, catalog.findByCardName(CardEntityKind.ITEM, "water rune PACK").get(0));
	}

	@Test
	public void separatesKindsAndReportsAmbiguousIds()
	{
		CardIdentity first = new CardIdentity(CardEntityKind.ITEM, "First", Set.of(1));
		CardIdentity second = new CardIdentity(CardEntityKind.ITEM, "Second", Set.of(1));
		CardIdentity npc = new CardIdentity(CardEntityKind.NPC, "First", Set.of(1));
		ImmutableCardIdentityCatalog catalog = new ImmutableCardIdentityCatalog(List.of(
			new ImmutableCardIdentityCatalog.Entry(first, Set.of("Shared")),
			new ImmutableCardIdentityCatalog.Entry(second, Set.of("Shared")),
			new ImmutableCardIdentityCatalog.Entry(npc, Set.of("Shared"))));

		assertEquals(2, catalog.findById(CardEntityKind.ITEM, 1).size());
		assertEquals(1, catalog.findById(CardEntityKind.NPC, 1).size());
		assertEquals(1, catalog.getAmbiguousIdCount(CardEntityKind.ITEM));
		assertEquals(0, catalog.getAmbiguousIdCount(CardEntityKind.NPC));
	}

	@Test
	public void returnedCollectionsAreImmutable()
	{
		CardIdentity identity = new CardIdentity(CardEntityKind.ITEM, "Card", Set.of(1));
		ImmutableCardIdentityCatalog catalog = new ImmutableCardIdentityCatalog(List.of(
			new ImmutableCardIdentityCatalog.Entry(identity, Set.of("Entity"))));

		assertThrows(UnsupportedOperationException.class,
			() -> catalog.getEntries().add(catalog.getEntries().get(0)));
		assertThrows(UnsupportedOperationException.class,
			() -> catalog.findById(CardEntityKind.ITEM, 1).clear());
		assertTrue(catalog.findById(CardEntityKind.NPC, 1).isEmpty());
	}
}
