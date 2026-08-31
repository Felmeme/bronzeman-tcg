package com.bronzemantcg.ownership;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Shared immutable indexes used by bundled and remotely parsed card identities. */
public final class ImmutableCardIdentityCatalog implements CardIdentityCatalog
{
	private final List<Entry> entries;
	private final Map<CardEntityKind, Map<Integer, List<CardIdentity>>> byId;
	private final Map<CardEntityKind, Map<String, List<CardIdentity>>> byEntityName;
	private final Map<CardEntityKind, Map<String, List<CardIdentity>>> byCardName;
	private final Map<CardEntityKind, Integer> ambiguousIdCounts;

	public ImmutableCardIdentityCatalog(Iterable<Entry> sourceEntries)
	{
		Map<CardEntityKind, Map<Integer, List<CardIdentity>>> idIndex = enumIndex();
		Map<CardEntityKind, Map<String, List<CardIdentity>>> entityNameIndex = enumIndex();
		Map<CardEntityKind, Map<String, List<CardIdentity>>> cardNameIndex = enumIndex();
		List<Entry> loaded = new ArrayList<>();
		if (sourceEntries != null)
		{
			for (Entry entry : sourceEntries)
			{
				if (entry == null)
				{
					continue;
				}
				loaded.add(entry);
				CardIdentity identity = entry.identity;
				CardEntityKind kind = identity.getKind();
				addName(cardNameIndex.get(kind), normalize(identity.getCardName()), identity);
				for (String legacyCardName : identity.getLegacyCardNames())
				{
					addName(cardNameIndex.get(kind), normalize(legacyCardName), identity);
				}
				for (String entityName : entry.entityNames)
				{
					addName(entityNameIndex.get(kind), normalize(entityName), identity);
				}
				for (Integer entityId : identity.getEntityIds())
				{
					addId(idIndex.get(kind), entityId, identity);
				}
			}
		}
		entries = Collections.unmodifiableList(loaded);
		byId = freeze(idIndex);
		byEntityName = freeze(entityNameIndex);
		byCardName = freeze(cardNameIndex);
		Map<CardEntityKind, Integer> counts = new EnumMap<>(CardEntityKind.class);
		for (CardEntityKind kind : CardEntityKind.values())
		{
			counts.put(kind, countAmbiguous(byId.get(kind)));
		}
		ambiguousIdCounts = Collections.unmodifiableMap(counts);
	}

	@Override
	public List<CardIdentity> findById(CardEntityKind kind, int entityId)
	{
		Map<Integer, List<CardIdentity>> index = byId.get(kind);
		return index == null ? Collections.emptyList()
			: index.getOrDefault(entityId, Collections.emptyList());
	}

	@Override
	public List<CardIdentity> findByName(CardEntityKind kind, String entityName)
	{
		return find(byEntityName, kind, entityName);
	}

	@Override
	public List<CardIdentity> findByCardName(CardEntityKind kind, String cardName)
	{
		return find(byCardName, kind, cardName);
	}

	public List<Entry> getEntries()
	{
		return entries;
	}

	public int size()
	{
		return entries.size();
	}

	public int getAmbiguousIdCount(CardEntityKind kind)
	{
		return ambiguousIdCounts.getOrDefault(kind, 0);
	}

	private static List<CardIdentity> find(
		Map<CardEntityKind, Map<String, List<CardIdentity>>> indexes,
		CardEntityKind kind, String value)
	{
		Map<String, List<CardIdentity>> index = indexes.get(kind);
		if (index == null)
		{
			return Collections.emptyList();
		}
		return index.getOrDefault(normalize(value), Collections.emptyList());
	}

	private static void addName(Map<String, List<CardIdentity>> index, String key,
		CardIdentity identity)
	{
		if (!key.isEmpty())
		{
			index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(identity);
		}
	}

	private static void addId(Map<Integer, List<CardIdentity>> index, Integer key,
		CardIdentity identity)
	{
		if (key != null)
		{
			index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(identity);
		}
	}

	private static int countAmbiguous(Map<Integer, List<CardIdentity>> index)
	{
		if (index == null)
		{
			return 0;
		}
		int count = 0;
		for (List<CardIdentity> matches : index.values())
		{
			if (matches.size() > 1)
			{
				count++;
			}
		}
		return count;
	}

	private static <K> Map<CardEntityKind, Map<K, List<CardIdentity>>> freeze(
		Map<CardEntityKind, Map<K, List<CardIdentity>>> source)
	{
		Map<CardEntityKind, Map<K, List<CardIdentity>>> result =
			new EnumMap<>(CardEntityKind.class);
		for (Map.Entry<CardEntityKind, Map<K, List<CardIdentity>>> kindEntry
			: source.entrySet())
		{
			Map<K, List<CardIdentity>> values = new LinkedHashMap<>();
			for (Map.Entry<K, List<CardIdentity>> entry : kindEntry.getValue().entrySet())
			{
				values.put(entry.getKey(), List.copyOf(entry.getValue()));
			}
			result.put(kindEntry.getKey(), Collections.unmodifiableMap(values));
		}
		return Collections.unmodifiableMap(result);
	}

	private static <K> Map<CardEntityKind, Map<K, List<CardIdentity>>> enumIndex()
	{
		Map<CardEntityKind, Map<K, List<CardIdentity>>> index =
			new EnumMap<>(CardEntityKind.class);
		for (CardEntityKind kind : CardEntityKind.values())
		{
			index.put(kind, new LinkedHashMap<>());
		}
		return index;
	}

	private static String normalize(String value)
	{
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	/** One identity plus every in-game entity name which can resolve to it. */
	public static final class Entry
	{
		private final CardIdentity identity;
		private final Set<String> entityNames;

		public Entry(CardIdentity identity, Iterable<String> entityNames)
		{
			if (identity == null)
			{
				throw new IllegalArgumentException("identity is required");
			}
			this.identity = identity;
			Map<String, String> validNames = new LinkedHashMap<>();
			if (entityNames != null)
			{
				for (String entityName : entityNames)
				{
					String normalized = normalize(entityName);
					if (!normalized.isEmpty())
					{
						validNames.putIfAbsent(normalized, entityName.trim());
					}
				}
			}
			this.entityNames = Collections.unmodifiableSet(
				new LinkedHashSet<>(validNames.values()));
		}

		public CardIdentity getIdentity()
		{
			return identity;
		}

		public Set<String> getEntityNames()
		{
			return entityNames;
		}
	}
}
