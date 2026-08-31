package com.bronzemantcg.interop;

import com.bronzemantcg.ownership.CardEntityKind;
import com.bronzemantcg.ownership.CardOwnershipService;
import com.bronzemantcg.ownership.CardResolver;
import com.bronzemantcg.ownership.TcgOwnershipSnapshot;
import com.bronzemantcg.support.SimulatedV1CardIdentityCatalog;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Exercises the proposed v1 ownership payload through the real Bronzeman interop path. */
public class OsrsTcgV1SimulationTest
{
	private TcgCollectionReader collectionReader;
	private OsrsTcgInteropService interopService;
	private CardOwnershipService ownershipService;

	@Before
	public void setUp()
	{
		collectionReader = new TcgCollectionReader(null, null);
		interopService = new OsrsTcgInteropService(collectionReader, new EventBus());
		ownershipService = new CardOwnershipService(
			new CardResolver(new SimulatedV1CardIdentityCatalog()));
	}

	@Test
	public void betaWaterRunePackVariantUnlocksV1WaterRuneParent()
	{
		accept(payload(Collections.singletonList("Water rune pack"),
			Collections.singletonList(12730), Collections.emptyList(), "group-a"));

		TcgOwnershipSnapshot ownership = collectionReader.getOwnershipSnapshot();
		assertEquals("group-a", ownership.getGroupKey());
		assertStatus(CardOwnershipService.Status.OWNED,
			ownershipService.decideCard("Water rune", ownership, null, null));
		assertStatus(CardOwnershipService.Status.OWNED,
			ownershipService.decide(CardEntityKind.ITEM, 555, "Water rune",
				ownership, null, null));
		assertTrue(ownershipService.isCollectedCard("Water rune", ownership, null));
	}

	@Test
	public void displayRetainsExactBetaNamesWhenPresentIdsAreEmpty()
	{
		accept(payload(Collections.singletonList("Water rune pack"),
			Collections.emptyList(), Collections.emptyList(), null));
		TcgOwnershipSnapshot ownership = collectionReader.getOwnershipSnapshot();
		assertStatus(CardOwnershipService.Status.LOCKED,
			ownershipService.decideCard("Water rune", ownership, null, null));
		assertTrue(ownershipService.isCollectedCard("Water rune", ownership, null));
	}

	@Test
	public void equalItemAndNpcIdsRemainSeparateNamespaces()
	{
		accept(payload(Collections.singletonList("Water rune pack"),
			Collections.singletonList(12730), Collections.emptyList(), "group-a"));
		TcgOwnershipSnapshot itemOnly = collectionReader.getOwnershipSnapshot();
		assertStatus(CardOwnershipService.Status.OWNED,
			ownershipService.decide(CardEntityKind.ITEM, 12730, "Water rune pack",
				itemOnly, null, null));
		assertStatus(CardOwnershipService.Status.LOCKED,
			ownershipService.decide(CardEntityKind.NPC, 12730, "Armoured zombie",
				itemOnly, null, null));

		accept(payload(Collections.singletonList("Armoured zombie (Defender of Varrock)"),
			Collections.emptyList(), Collections.singletonList(12730), "group-a"));
		TcgOwnershipSnapshot npcOnly = collectionReader.getOwnershipSnapshot();
		assertStatus(CardOwnershipService.Status.LOCKED,
			ownershipService.decide(CardEntityKind.ITEM, 12730, "Water rune pack",
				npcOnly, null, null));
		assertStatus(CardOwnershipService.Status.OWNED,
			ownershipService.decide(CardEntityKind.NPC, 12731, "Armoured zombie",
				npcOnly, null, null));
	}

	@Test
	public void presentEmptyIdsOverrideNamesWhileMissingIdsRetainCompatibility()
	{
		accept(payload(Collections.singletonList("Dragon axe"),
			Collections.emptyList(), Collections.emptyList(), null));
		assertStatus(CardOwnershipService.Status.LOCKED,
			ownershipService.decideCard("Dragon axe", collectionReader.getOwnershipSnapshot(),
				null, null));

		Map<String, Object> legacy = new HashMap<>();
		legacy.put("ownedNames", Collections.singletonList("Dragon axe"));
		accept(legacy);
		TcgOwnershipSnapshot legacyOwnership = collectionReader.getOwnershipSnapshot();
		assertFalse(legacyOwnership.hasEntityIds(CardEntityKind.ITEM));
		assertStatus(CardOwnershipService.Status.OWNED,
			ownershipService.decideCard("Dragon axe", legacyOwnership, null, null));
	}

	@Test
	public void variantIdsOwnTheirParentAcrossItemAndNpcFamilies()
	{
		accept(payload(Collections.emptyList(), Collections.singletonList(123),
			Collections.singletonList(5272), null));
		TcgOwnershipSnapshot ownership = collectionReader.getOwnershipSnapshot();
		assertStatus(CardOwnershipService.Status.OWNED,
			ownershipService.decide(CardEntityKind.ITEM, 121, "Attack potion(3)",
				ownership, null, null));
		assertStatus(CardOwnershipService.Status.OWNED,
			ownershipService.decide(CardEntityKind.NPC, 5274, "Monkey archer",
				ownership, null, null));
	}

	@Test
	public void malformedElementsAreIgnoredWithoutDiscardingValidIds()
	{
		accept(payload(Collections.singletonList("Water rune pack"),
			Arrays.asList(12730L, "555", -1, 2.5, null),
			Arrays.asList(12731.0, "12730", -5), " group-b "));

		TcgOwnershipSnapshot ownership = collectionReader.getOwnershipSnapshot();
		assertEquals(Collections.singleton(12730), ownership.getOwnedItemIds());
		assertEquals(Collections.singleton(12731), ownership.getOwnedNpcIds());
		assertEquals("group-b", ownership.getGroupKey());
		assertStatus(CardOwnershipService.Status.OWNED,
			ownershipService.decideCard("Water rune", ownership, null, null));
	}

	@Test
	public void unknownMismatchAndAmbiguityFailOpen()
	{
		accept(payload(Collections.emptyList(), Collections.emptyList(),
			Collections.emptyList(), null));
		TcgOwnershipSnapshot ownership = collectionReader.getOwnershipSnapshot();
		assertAllowedStatus(CardOwnershipService.Status.UNTRACKED,
			ownershipService.decide(CardEntityKind.ITEM, 999999, "Unknown item",
				ownership, null, null));
		assertAllowedStatus(CardOwnershipService.Status.CATALOG_MISMATCH,
			ownershipService.decide(CardEntityKind.ITEM, 999999, "Dragon axe",
				ownership, null, null));
		assertAllowedStatus(CardOwnershipService.Status.AMBIGUOUS,
			ownershipService.decide(CardEntityKind.NPC, 9000, "Ambiguous NPC",
				ownership, null, null));
	}

	@Test
	public void profileChangeDropsTheSimulatedV1Snapshot()
	{
		accept(payload(Collections.singletonList("Water rune pack"),
			Collections.singletonList(12730), Collections.emptyList(), "group-a"));
		assertTrue(collectionReader.hasApiData());

		interopService.onProfileChanged();

		assertFalse(collectionReader.hasApiData());
	}

	private void accept(Map<String, Object> data)
	{
		OsrsTcgInteropService.UpdateResult result = interopService.onPluginMessage(
			new PluginMessage("osrstcg", "owned-names-changed", data));
		assertTrue(result.isAccepted());
	}

	private static Map<String, Object> payload(Object names, Object itemIds,
		Object npcIds, Object groupKey)
	{
		Map<String, Object> data = new HashMap<>();
		data.put("ownedNames", names);
		data.put("ownedItemIds", itemIds);
		data.put("ownedNpcIds", npcIds);
		data.put("groupKey", groupKey);
		return data;
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
