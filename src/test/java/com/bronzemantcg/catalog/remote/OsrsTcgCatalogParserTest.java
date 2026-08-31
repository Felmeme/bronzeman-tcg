package com.bronzemantcg.catalog.remote;

import com.bronzemantcg.ownership.CardEntityKind;
import com.bronzemantcg.ownership.CardIdentity;
import com.bronzemantcg.ownership.ImmutableCardIdentityCatalog;
import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class OsrsTcgCatalogParserTest
{
	private OsrsTcgCatalogParser parser;

	@Before
	public void setUp()
	{
		parser = new OsrsTcgCatalogParser(new Gson());
	}

	@Test
	public void parsesParentAndVariantIdsAndNames() throws Exception
	{
		OsrsTcgCatalogSnapshot snapshot = fixture();
		assertEquals(7, snapshot.size());
		assertSingleParent(snapshot.findById(CardEntityKind.ITEM, 555), "Water rune");
		assertSingleParent(snapshot.findById(CardEntityKind.ITEM, 12730), "Water rune");
		assertSingleParent(snapshot.findByName(CardEntityKind.ITEM, "Water rune pack"),
			"Water rune");
		assertSingleParent(snapshot.findByCardName(CardEntityKind.ITEM, "Water rune pack"),
			"Water rune");
	}

	@Test
	public void reflectsCurrentUnfinishedPotionParentMerge() throws Exception
	{
		OsrsTcgCatalogSnapshot snapshot = fixture();
		assertSingleParent(snapshot.findById(CardEntityKind.ITEM, 91), "Guam leaf");
		assertSingleParent(snapshot.findByName(CardEntityKind.ITEM, "Grimy guam leaf"),
			"Guam leaf");
	}

	@Test
	public void sameNpcNameCanResolveByDistinctIds() throws Exception
	{
		OsrsTcgCatalogSnapshot snapshot = fixture();
		assertEquals(2,
			snapshot.findByName(CardEntityKind.NPC, "strange creature").size());
		assertSingleParent(snapshot.findById(CardEntityKind.NPC, 12063), "Phantom Muspah");
		assertSingleParent(snapshot.findById(CardEntityKind.NPC, 14706),
			"Juvenile custodian stalker");
	}

	@Test
	public void equalNamesAndIdsRemainSeparatedByNamespace() throws Exception
	{
		OsrsTcgCatalogSnapshot snapshot = fixture();
		assertSingleParent(snapshot.findByName(CardEntityKind.ITEM, "Crawling hand"),
			"Crawling hand");
		assertSingleParent(snapshot.findByName(CardEntityKind.NPC, "Crawling hand"),
			"Crawling Hand");
		assertSingleParent(snapshot.findById(CardEntityKind.NPC, 0), "Tool Leprechaun");
		assertEquals(0, snapshot.getAmbiguousIdCount(CardEntityKind.ITEM));
		assertEquals(0, snapshot.getAmbiguousIdCount(CardEntityKind.NPC));
	}

	@Test
	public void derivesImmutableTrackedEntityViews() throws Exception
	{
		OsrsTcgCatalogSnapshot snapshot = fixture();
		Map<String, Set<String>> npcs = snapshot.getCardNamesByEntityName(CardEntityKind.NPC);
		assertEquals(Set.of("Phantom Muspah", "Juvenile custodian stalker"),
			npcs.get("strange creature"));
		assertThrows(UnsupportedOperationException.class,
			() -> npcs.put("new", Set.of("New")));
		assertThrows(UnsupportedOperationException.class,
			() -> npcs.get("strange creature").clear());
	}

	@Test
	public void betaAliasOverlayAddsNamesWithoutFallbackEntityAuthority() throws Exception
	{
		OsrsTcgCatalogSnapshot live = fixture();
		CardIdentity fallback = new CardIdentity(CardEntityKind.ITEM, "Water rune",
			Set.of("Historical water card"), Set.of(999999));
		OsrsTcgCatalogSnapshot enriched = live.withLegacyAliases(List.of(
			new ImmutableCardIdentityCatalog.Entry(fallback, Set.of("fallback entity"))));

		assertSingleParent(enriched.findByCardName(
			CardEntityKind.ITEM, "Historical water card"), "Water rune");
		assertTrue(enriched.findById(CardEntityKind.ITEM, 999999).isEmpty());
		assertTrue(enriched.findByName(CardEntityKind.ITEM, "fallback entity").isEmpty());
		assertSingleParent(enriched.findById(CardEntityKind.ITEM, 555), "Water rune");
	}

	@Test
	public void rejectsMissingNamespacesAndMalformedJson()
	{
		assertInvalid("{}", "items");
		assertInvalid("{\"items\":[{\"id\":1,\"name\":\"Item\",\"tcg\":{}}]}", "NPCs");
		assertInvalid("{", "malformed");
	}

	@Test
	public void rejectsInvalidParentsAndVariants()
	{
		assertInvalid(catalogue("{\"id\":-1,\"name\":\"Item\",\"tcg\":{}}",
			validNpc()), "invalid ID");
		assertInvalid(catalogue("{\"id\":1,\"name\":\" \",\"tcg\":{}}",
			validNpc()), "missing name");
		assertInvalid(catalogue("{\"id\":1,\"name\":\"Item\"}", validNpc()),
			"no tcg data");
		assertInvalid(catalogue("{\"id\":1,\"name\":\"Item\",\"tcg\":{\"variants\":[null]}}",
			validNpc()), "variant");
	}

	@Test
	public void rejectsIdsAssignedToDifferentParents()
	{
		String items = "{\"id\":1,\"name\":\"First\",\"tcg\":{}},"
			+ "{\"id\":2,\"name\":\"Second\",\"tcg\":{\"variants\":["
			+ "{\"id\":1,\"name\":\"Second variant\"}]}}";
		assertInvalid("{\"items\":[" + items + "],\"npcs\":[" + validNpc() + "]}",
			"reuses entity ID 1");
	}

	@Test
	public void harmlessDuplicateIdsWithinOneParentAreDeduplicated() throws Exception
	{
		String item = "{\"id\":1,\"name\":\"Item\",\"tcg\":{\"variants\":["
			+ "{\"id\":1,\"name\":\"Item\"},{\"id\":2,\"name\":\"Variant\"},"
			+ "{\"id\":2,\"name\":\"Variant\"}]}}";
		OsrsTcgCatalogSnapshot snapshot = parser.parse(new StringReader(
			catalogue(item, validNpc())));
		assertSingleParent(snapshot.findById(CardEntityKind.ITEM, 1), "Item");
		assertSingleParent(snapshot.findById(CardEntityKind.ITEM, 2), "Item");
	}

	private OsrsTcgCatalogSnapshot fixture() throws Exception
	{
		InputStream stream = getClass().getResourceAsStream("/osrs-tcg-live-catalog-fixture.json");
		if (stream == null)
		{
			throw new AssertionError("fixture missing");
		}
		try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
		{
			return parser.parse(reader);
		}
	}

	private void assertInvalid(String json, String expectedMessage)
	{
		CatalogValidationException exception = assertThrows(CatalogValidationException.class,
			() -> parser.parse(new StringReader(json)));
		assertTrue(exception.getMessage(), exception.getMessage().contains(expectedMessage));
	}

	private static String catalogue(String items, String npcs)
	{
		return "{\"items\":[" + items + "],\"npcs\":[" + npcs + "]}";
	}

	private static String validNpc()
	{
		return "{\"id\":1,\"name\":\"NPC\",\"tcg\":{}}";
	}

	private static void assertSingleParent(List<CardIdentity> matches, String parentName)
	{
		assertEquals(1, matches.size());
		assertEquals(parentName, matches.get(0).getCardName());
	}
}
