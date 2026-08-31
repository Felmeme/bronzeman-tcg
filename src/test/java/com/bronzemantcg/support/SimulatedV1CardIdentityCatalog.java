package com.bronzemantcg.support;

import com.bronzemantcg.ownership.CardEntityKind;
import com.bronzemantcg.ownership.CardIdentity;
import com.bronzemantcg.ownership.CardIdentityCatalog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Small test-only catalogue representing reviewed OSRS TCG v1 parent/variant relationships. */
public final class SimulatedV1CardIdentityCatalog implements CardIdentityCatalog
{
	private final Map<CardEntityKind, Map<Integer, List<CardIdentity>>> byId = new LinkedHashMap<>();
	private final Map<CardEntityKind, Map<String, List<CardIdentity>>> byName = new LinkedHashMap<>();
	private final Map<CardEntityKind, Map<String, List<CardIdentity>>> byCardName = new LinkedHashMap<>();

	public SimulatedV1CardIdentityCatalog()
	{
		for (CardEntityKind kind : CardEntityKind.values())
		{
			byId.put(kind, new LinkedHashMap<>());
			byName.put(kind, new LinkedHashMap<>());
			byCardName.put(kind, new LinkedHashMap<>());
		}

		add(CardEntityKind.ITEM, "Water rune", names("Water rune pack"),
			names("Water rune"), 555, 12730);
		add(CardEntityKind.ITEM, "Attack potion", Collections.emptyList(),
			names("Attack potion"), 121, 123);
		add(CardEntityKind.ITEM, "Dragon axe", Collections.emptyList(),
			names("Dragon axe"), 6739);
		add(CardEntityKind.NPC, "Armoured zombie (Defender of Varrock)",
			names("Armoured zombie (quest)"), names("Armoured zombie"), 12730, 12731);
		add(CardEntityKind.NPC, "Monkey Archer", Collections.emptyList(),
			names("Monkey archer"), 5272, 5274);
		add(CardEntityKind.ITEM, "Crawling hand", Collections.emptyList(),
			names("Crawling hand"), 7975, 7982);
		add(CardEntityKind.NPC, "Crawling Hand", Collections.emptyList(),
			names("Crawling hand"), 448, 449, 12249);
		add(CardEntityKind.ITEM, "Manta ray", Collections.emptyList(),
			names("Manta ray"), 391, 393, 24589);
		add(CardEntityKind.NPC, "Manta ray", Collections.emptyList(),
			names("Manta ray"), 15220, 15221);
		add(CardEntityKind.ITEM, "Rock golem", Collections.emptyList(),
			names("Rock golem"), 13321, 21187, 31278);
		add(CardEntityKind.NPC, "Rock Golem", Collections.emptyList(),
			names("Rock golem"), 2182, 6725, 14923);
		add(CardEntityKind.NPC, "First ambiguous parent", Collections.emptyList(),
			names("Ambiguous NPC"), 9000);
		add(CardEntityKind.NPC, "Second ambiguous parent", Collections.emptyList(),
			names("Ambiguous NPC"), 9000);
	}

	@Override
	public List<CardIdentity> findById(CardEntityKind kind, int entityId)
	{
		return find(byId, kind, entityId);
	}

	@Override
	public List<CardIdentity> findByName(CardEntityKind kind, String entityName)
	{
		return find(byName, kind, normalize(entityName));
	}

	@Override
	public List<CardIdentity> findByCardName(CardEntityKind kind, String cardName)
	{
		return find(byCardName, kind, normalize(cardName));
	}

	private void add(CardEntityKind kind, String cardName, List<String> legacyCardNames,
		List<String> entityNames, Integer... entityIds)
	{
		CardIdentity identity = new CardIdentity(kind, cardName,
			new LinkedHashSet<>(legacyCardNames),
			new LinkedHashSet<>(Arrays.asList(entityIds)));
		add(byCardName.get(kind), normalize(cardName), identity);
		for (String legacyCardName : legacyCardNames)
		{
			add(byCardName.get(kind), normalize(legacyCardName), identity);
		}
		for (String entityName : entityNames)
		{
			add(byName.get(kind), normalize(entityName), identity);
		}
		for (Integer entityId : entityIds)
		{
			add(byId.get(kind), entityId, identity);
		}
	}

	private static <K> List<CardIdentity> find(
		Map<CardEntityKind, Map<K, List<CardIdentity>>> index, CardEntityKind kind, K key)
	{
		Map<K, List<CardIdentity>> values = index.get(kind);
		if (values == null)
		{
			return Collections.emptyList();
		}
		return values.getOrDefault(key, Collections.emptyList());
	}

	private static <K> void add(Map<K, List<CardIdentity>> index, K key,
		CardIdentity identity)
	{
		index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(identity);
	}

	private static List<String> names(String... values)
	{
		return Arrays.asList(values);
	}

	private static String normalize(String value)
	{
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}
}
