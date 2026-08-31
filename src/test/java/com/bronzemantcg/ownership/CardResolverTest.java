package com.bronzemantcg.ownership;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CardResolverTest
{
	private CardIdentity item;
	private CardIdentity npc;
	private SyntheticCatalog catalog;
	private CardResolver resolver;

	@Before
	public void setUp()
	{
		item = identity(CardEntityKind.ITEM, "Test item", 100, 101, 102);
		npc = identity(CardEntityKind.NPC, "Test creature", 100, 103);
		catalog = new SyntheticCatalog()
			.add(item, "Test item", "Test item (charged)")
			.add(npc, "Test creature");
		resolver = new CardResolver(catalog);
	}

	@Test
	public void primaryAndVariantIdsResolveToOneParentIdentity()
	{
		assertTracked(CardEntityKind.ITEM, 100, "Test item", item);
		assertTracked(CardEntityKind.ITEM, 102, "Test item (2)", item);
	}

	@Test
	public void equalNumericIdsRemainSeparatedByKind()
	{
		assertTracked(CardEntityKind.ITEM, 100, "Test item", item);
		assertTracked(CardEntityKind.NPC, 100, "Test creature", npc);
	}

	@Test
	public void missingIdUsesReviewedNameAlias()
	{
		assertTracked(CardEntityKind.ITEM, -1, "Test item (charged)", item);
	}

	@Test
	public void unknownIdUnderKnownNameIsCatalogueMismatch()
	{
		CardResolver.Result result = resolver.resolve(CardEntityKind.ITEM, 999, "Test item");
		assertEquals(CardResolver.Status.CATALOG_MISMATCH, result.getStatus());
		assertFalse(result.isTracked());
		assertNull(result.getIdentity());
	}

	@Test
	public void unknownIdAndNameAreUntracked()
	{
		CardResolver.Result result = resolver.resolve(CardEntityKind.ITEM, 999, "Unknown item");
		assertEquals(CardResolver.Status.UNTRACKED, result.getStatus());
	}

	@Test
	public void duplicateIdFailsOpenAsAmbiguous()
	{
		catalog.add(identity(CardEntityKind.ITEM, "Conflicting item", 100), "Conflicting item");
		CardResolver.Result result = resolver.resolve(CardEntityKind.ITEM, 100, "Test item");
		assertEquals(CardResolver.Status.AMBIGUOUS, result.getStatus());
		assertFalse(result.isTracked());
	}

	@Test
	public void duplicateNameFailsOpenAsAmbiguousWithoutId()
	{
		catalog.add(identity(CardEntityKind.ITEM, "Other parent", 200), "Shared display name");
		catalog.alias(item, "Shared display name");
		CardResolver.Result result = resolver.resolve(CardEntityKind.ITEM, -1, "Shared display name");
		assertEquals(CardResolver.Status.AMBIGUOUS, result.getStatus());
	}

	@Test
	public void nullKindCannotResolveAcrossNamespaces()
	{
		assertEquals(CardResolver.Status.AMBIGUOUS,
			resolver.resolve(null, 100, "Test item").getStatus());
	}

	@Test
	public void parentCardNameHasDistinctLookup()
	{
		assertSame(item, resolver.resolveCardName(CardEntityKind.ITEM, "Test item").getIdentity());
		assertEquals(CardResolver.Status.UNTRACKED,
			resolver.resolveCardName(CardEntityKind.ITEM, "Test item (charged)").getStatus());
	}

	private void assertTracked(CardEntityKind kind, int id, String name, CardIdentity expected)
	{
		CardResolver.Result result = resolver.resolve(kind, id, name);
		assertEquals(CardResolver.Status.TRACKED, result.getStatus());
		assertTrue(result.isTracked());
		assertSame(expected, result.getIdentity());
	}

	private static CardIdentity identity(CardEntityKind kind, String name, Integer... ids)
	{
		return new CardIdentity(kind, name, new java.util.LinkedHashSet<>(Arrays.asList(ids)));
	}

	private static final class SyntheticCatalog implements CardIdentityCatalog
	{
		private final Map<String, List<CardIdentity>> byId = new HashMap<>();
		private final Map<String, List<CardIdentity>> byName = new HashMap<>();

		private SyntheticCatalog add(CardIdentity identity, String... names)
		{
			for (Integer id : identity.getEntityIds())
			{
				byId.computeIfAbsent(key(identity.getKind(), id), ignored -> new ArrayList<>())
					.add(identity);
			}
			for (String name : names)
			{
				alias(identity, name);
			}
			return this;
		}

		private void alias(CardIdentity identity, String name)
		{
			byName.computeIfAbsent(key(identity.getKind(), name), ignored -> new ArrayList<>())
				.add(identity);
		}

		@Override
		public List<CardIdentity> findById(CardEntityKind kind, int entityId)
		{
			return byId.getOrDefault(key(kind, entityId), Collections.emptyList());
		}

		@Override
		public List<CardIdentity> findByName(CardEntityKind kind, String entityName)
		{
			return byName.getOrDefault(key(kind, entityName), Collections.emptyList());
		}

		@Override
		public List<CardIdentity> findByCardName(CardEntityKind kind, String cardName)
		{
			return byName.getOrDefault(key(kind, cardName), Collections.emptyList()).stream()
				.filter(identity -> identity.getCardName().equalsIgnoreCase(cardName))
				.collect(java.util.stream.Collectors.toList());
		}

		private static String key(CardEntityKind kind, int id)
		{
			return kind + ":" + id;
		}

		private static String key(CardEntityKind kind, String name)
		{
			return kind + ":" + (name == null ? "" : name.trim().toLowerCase(Locale.ROOT));
		}
	}
}
