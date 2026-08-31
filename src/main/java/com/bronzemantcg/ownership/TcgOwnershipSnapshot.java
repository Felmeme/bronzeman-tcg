package com.bronzemantcg.ownership;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Immutable, locally cached view of the ownership payload published by OSRS TCG. */
public final class TcgOwnershipSnapshot
{
	private final Set<String> ownedCardNamesLowerCase;
	private final Set<Integer> ownedItemIds;
	private final Set<Integer> ownedNpcIds;
	private final boolean itemIdsAvailable;
	private final boolean npcIdsAvailable;
	private final String groupKey;

	private TcgOwnershipSnapshot(Set<String> ownedCardNamesLowerCase,
		Set<Integer> ownedItemIds, Set<Integer> ownedNpcIds,
		boolean itemIdsAvailable, boolean npcIdsAvailable, String groupKey)
	{
		this.ownedCardNamesLowerCase = ownedCardNamesLowerCase;
		this.ownedItemIds = ownedItemIds;
		this.ownedNpcIds = ownedNpcIds;
		this.itemIdsAvailable = itemIdsAvailable;
		this.npcIdsAvailable = npcIdsAvailable;
		this.groupKey = groupKey;
	}

	public static TcgOwnershipSnapshot fromApi(List<?> names, List<?> itemIds,
		List<?> npcIds, String groupKey)
	{
		return new TcgOwnershipSnapshot(normalizeNames(names), normalizeIds(itemIds),
			normalizeIds(npcIds), itemIds != null, npcIds != null, cleanGroupKey(groupKey));
	}

	public static TcgOwnershipSnapshot namesOnly(Set<String> lowerCaseNames)
	{
		Set<String> names = lowerCaseNames == null
			? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(lowerCaseNames));
		return new TcgOwnershipSnapshot(names, Collections.emptySet(), Collections.emptySet(),
			false, false, null);
	}

	public Set<String> getOwnedCardNamesLowerCase()
	{
		return ownedCardNamesLowerCase;
	}

	public Set<Integer> getOwnedItemIds()
	{
		return ownedItemIds;
	}

	public Set<Integer> getOwnedNpcIds()
	{
		return ownedNpcIds;
	}

	public String getGroupKey()
	{
		return groupKey;
	}

	public boolean hasEntityIds(CardEntityKind kind)
	{
		return kind == CardEntityKind.ITEM ? itemIdsAvailable
			: kind == CardEntityKind.NPC && npcIdsAvailable;
	}

	public boolean ownsEntityId(CardEntityKind kind, int id)
	{
		if (id < 0 || kind == null)
		{
			return false;
		}
		return kind == CardEntityKind.ITEM ? ownedItemIds.contains(id) : ownedNpcIds.contains(id);
	}

	private static Set<String> normalizeNames(List<?> values)
	{
		if (values == null)
		{
			return Collections.emptySet();
		}
		Set<String> normalized = new LinkedHashSet<>();
		for (Object value : values)
		{
			if (value instanceof String && !((String) value).trim().isEmpty())
			{
				normalized.add(((String) value).trim().toLowerCase(Locale.ROOT));
			}
		}
		return Collections.unmodifiableSet(normalized);
	}

	private static Set<Integer> normalizeIds(List<?> values)
	{
		if (values == null)
		{
			return Collections.emptySet();
		}
		Set<Integer> normalized = new LinkedHashSet<>();
		for (Object value : values)
		{
			if (!(value instanceof Number))
			{
				continue;
			}
			Number number = (Number) value;
			long integer = number.longValue();
			double decimal = number.doubleValue();
			if (integer >= 0 && integer <= Integer.MAX_VALUE && Double.isFinite(decimal)
				&& decimal == integer)
			{
				normalized.add((int) integer);
			}
		}
		return Collections.unmodifiableSet(normalized);
	}

	private static String cleanGroupKey(String value)
	{
		return value == null || value.trim().isEmpty() ? null : value.trim();
	}
}
