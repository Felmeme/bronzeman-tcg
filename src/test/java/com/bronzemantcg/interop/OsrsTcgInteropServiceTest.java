package com.bronzemantcg.interop;

import com.bronzemantcg.ownership.CardEntityKind;
import com.bronzemantcg.ownership.TcgOwnershipSnapshot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OsrsTcgInteropServiceTest
{
	private TcgCollectionReader collectionReader;
	private EventBus eventBus;
	private OsrsTcgInteropService service;
	private List<PluginMessage> posted;

	@Before
	public void setUp()
	{
		// These tests exercise only the API path, so no persisted-state dependencies are read.
		collectionReader = new TcgCollectionReader(null, null);
		eventBus = new EventBus();
		service = new OsrsTcgInteropService(collectionReader, eventBus);
		posted = new ArrayList<>();
		eventBus.register(PluginMessage.class, posted::add, 0);
	}

	@Test
	public void ignoresNullUnrelatedAndMalformedMessages()
	{
		assertEquals(OsrsTcgInteropService.UpdateResult.IGNORED,
			service.onPluginMessage(null));
		assertEquals(OsrsTcgInteropService.UpdateResult.IGNORED,
			service.onPluginMessage(message("other", "owned-names", names("Dragon axe"))));
		assertEquals(OsrsTcgInteropService.UpdateResult.IGNORED,
			service.onPluginMessage(message("osrstcg", "other", names("Dragon axe"))));
		assertEquals(OsrsTcgInteropService.UpdateResult.IGNORED,
			service.onPluginMessage(new PluginMessage("osrstcg", "owned-names")));

		Map<String, Object> malformed = new HashMap<>();
		malformed.put("ownedNames", "Dragon axe");
		assertEquals(OsrsTcgInteropService.UpdateResult.IGNORED,
			service.onPluginMessage(message("osrstcg", "owned-names", malformed)));
		assertFalse(collectionReader.hasApiData());
	}

	@Test
	public void acceptsLegacyNamesOnlyPayload()
	{
		Map<String, Object> data = names(" Dragon axe ", 42, null);
		assertEquals(OsrsTcgInteropService.UpdateResult.FIRST_UPDATE,
			service.onPluginMessage(message("osrstcg", "owned-names", data)));

		TcgOwnershipSnapshot snapshot = collectionReader.getOwnershipSnapshot();
		assertEquals(Collections.singleton("dragon axe"), snapshot.getOwnedCardNamesLowerCase());
		assertFalse(snapshot.hasEntityIds(CardEntityKind.ITEM));
		assertFalse(snapshot.hasEntityIds(CardEntityKind.NPC));
		assertFalse(collectionReader.hasLiveV1Capability());
	}

	@Test
	public void preservesEmptyIdNamespaceAsAuthoritative()
	{
		Map<String, Object> data = names("Test card");
		data.put("ownedItemIds", Collections.emptyList());
		data.put("ownedNpcIds", Collections.singletonList(100));
		data.put("groupKey", " group-a ");
		service.onPluginMessage(message("osrstcg", "owned-names-changed", data));

		TcgOwnershipSnapshot snapshot = collectionReader.getOwnershipSnapshot();
		assertTrue(snapshot.hasEntityIds(CardEntityKind.ITEM));
		assertFalse(snapshot.ownsEntityId(CardEntityKind.ITEM, 100));
		assertTrue(snapshot.hasEntityIds(CardEntityKind.NPC));
		assertTrue(snapshot.ownsEntityId(CardEntityKind.NPC, 100));
		assertEquals("group-a", snapshot.getGroupKey());
		assertTrue(collectionReader.hasLiveV1Capability());
	}

	@Test
	public void distinguishesFirstPayloadFromLaterPushes()
	{
		assertEquals(OsrsTcgInteropService.UpdateResult.FIRST_UPDATE,
			service.onPluginMessage(message("osrstcg", "owned-names", names("Dragon axe"))));
		assertEquals(OsrsTcgInteropService.UpdateResult.UPDATED,
			service.onPluginMessage(message("osrstcg", "owned-names-changed", names("Rune axe"))));
		assertEquals(Collections.singleton("rune axe"),
			collectionReader.getOwnedCardNamesLowerCase());
	}

	@Test
	public void startupQueriesOnFirstTickAndRetriesAtExistingCadence()
	{
		service.startUp();
		service.onGameTick();
		assertEquals(1, posted.size());
		assertQuery(posted.get(0));

		for (int i = 0; i < 100; i++)
		{
			service.onGameTick();
		}
		assertEquals(1, posted.size());
		service.onGameTick();
		assertEquals(2, posted.size());
	}

	@Test
	public void synchronousReplyStopsQueryRetries()
	{
		eventBus.register(PluginMessage.class, query ->
		{
			if ("osrstcg".equals(query.getNamespace()) && "query-owned-names".equals(query.getName()))
			{
				service.onPluginMessage(message("osrstcg", "owned-names", names("Dragon axe")));
			}
		}, 1);

		service.startUp();
		service.onGameTick();
		for (int i = 0; i < 250; i++)
		{
			service.onGameTick();
		}
		assertEquals(1, posted.size());
		assertTrue(collectionReader.hasApiData());
	}

	@Test
	public void profileChangeDropsApiSnapshotAndRearmsQuery()
	{
		service.onPluginMessage(message("osrstcg", "owned-names", names("Dragon axe")));
		assertTrue(collectionReader.hasApiData());

		service.onProfileChanged();
		assertFalse(collectionReader.hasApiData());
		assertFalse(collectionReader.hasLiveV1Capability());
		service.onGameTick();
		assertEquals(1, posted.size());
		assertQuery(posted.get(0));
	}

	private static Map<String, Object> names(Object... values)
	{
		Map<String, Object> data = new HashMap<>();
		data.put("ownedNames", Arrays.asList(values));
		return data;
	}

	private static PluginMessage message(String namespace, String name, Map<String, Object> data)
	{
		return new PluginMessage(namespace, name, data);
	}

	private static void assertQuery(PluginMessage message)
	{
		assertEquals("osrstcg", message.getNamespace());
		assertEquals("query-owned-names", message.getName());
		assertTrue(message.getData().isEmpty());
	}
}
