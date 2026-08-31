package com.bronzemantcg.catalog.remote;

import com.bronzemantcg.ownership.CardEntityKind;
import com.bronzemantcg.ownership.CardIdentity;
import com.bronzemantcg.ownership.CardIdentityCatalog;
import com.bronzemantcg.ownership.ImmutableCardIdentityCatalog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** One fully validated, immutable OSRS TCG live-catalogue response. */
public final class OsrsTcgCatalogSnapshot implements CardIdentityCatalog
{
	private final ImmutableCardIdentityCatalog catalog;
	private final Map<CardEntityKind, Map<String, Set<String>>> cardNamesByEntityName;

	OsrsTcgCatalogSnapshot(List<ImmutableCardIdentityCatalog.Entry> entries)
	{
		catalog = new ImmutableCardIdentityCatalog(entries);
		Map<CardEntityKind, Map<String, Set<String>>> tracked = new EnumMap<>(CardEntityKind.class);
		for (CardEntityKind kind : CardEntityKind.values())
		{
			tracked.put(kind, new LinkedHashMap<>());
		}
		for (ImmutableCardIdentityCatalog.Entry entry : catalog.getEntries())
		{
			CardIdentity identity = entry.getIdentity();
			Map<String, Set<String>> names = tracked.get(identity.getKind());
			for (String entityName : entry.getEntityNames())
			{
				names.computeIfAbsent(normalize(entityName), ignored -> new LinkedHashSet<>())
					.add(identity.getCardName());
			}
		}
		cardNamesByEntityName = freeze(tracked);
	}

	@Override
	public List<CardIdentity> findById(CardEntityKind kind, int entityId)
	{
		return catalog.findById(kind, entityId);
	}

	@Override
	public List<CardIdentity> findByName(CardEntityKind kind, String entityName)
	{
		return catalog.findByName(kind, entityName);
	}

	@Override
	public List<CardIdentity> findByCardName(CardEntityKind kind, String cardName)
	{
		return catalog.findByCardName(kind, cardName);
	}

	public List<ImmutableCardIdentityCatalog.Entry> getEntries()
	{
		return catalog.getEntries();
	}

	public int size()
	{
		return catalog.size();
	}

	public int getAmbiguousIdCount(CardEntityKind kind)
	{
		return catalog.getAmbiguousIdCount(kind);
	}

	public Map<String, Set<String>> getCardNamesByEntityName(CardEntityKind kind)
	{
		return cardNamesByEntityName.getOrDefault(kind, Collections.emptyMap());
	}

	/**
	 * Adds reviewed Beta card-name aliases to matching live parents without importing any
	 * fallback entity ID or entity-name authority into the live snapshot.
	 */
	public OsrsTcgCatalogSnapshot withLegacyAliases(
		List<ImmutableCardIdentityCatalog.Entry> fallbackEntries)
	{
		Map<CardEntityKind, Map<String, CardIdentity>> fallbackByParent =
			new EnumMap<>(CardEntityKind.class);
		for (CardEntityKind kind : CardEntityKind.values())
		{
			fallbackByParent.put(kind, new LinkedHashMap<>());
		}
		if (fallbackEntries != null)
		{
			for (ImmutableCardIdentityCatalog.Entry entry : fallbackEntries)
			{
				if (entry != null)
				{
					CardIdentity identity = entry.getIdentity();
					fallbackByParent.get(identity.getKind()).put(
						normalize(identity.getCardName()), identity);
				}
			}
		}

		List<ImmutableCardIdentityCatalog.Entry> enriched = new ArrayList<>();
		for (ImmutableCardIdentityCatalog.Entry entry : catalog.getEntries())
		{
			CardIdentity live = entry.getIdentity();
			LinkedHashSet<String> legacyNames = new LinkedHashSet<>(live.getLegacyCardNames());
			CardIdentity fallback = fallbackByParent.get(live.getKind())
				.get(normalize(live.getCardName()));
			if (fallback != null)
			{
				legacyNames.addAll(fallback.getLegacyCardNames());
			}
			CardIdentity identity = new CardIdentity(live.getKind(), live.getCardName(),
				legacyNames, live.getEntityIds(), live.getOwnedNameRequiredEntityIds());
			enriched.add(new ImmutableCardIdentityCatalog.Entry(identity, entry.getEntityNames()));
		}
		return new OsrsTcgCatalogSnapshot(enriched);
	}

	private static Map<CardEntityKind, Map<String, Set<String>>> freeze(
		Map<CardEntityKind, Map<String, Set<String>>> source)
	{
		Map<CardEntityKind, Map<String, Set<String>>> result =
			new EnumMap<>(CardEntityKind.class);
		for (Map.Entry<CardEntityKind, Map<String, Set<String>>> kindEntry
			: source.entrySet())
		{
			Map<String, Set<String>> names = new LinkedHashMap<>();
			for (Map.Entry<String, Set<String>> entry : kindEntry.getValue().entrySet())
			{
				names.put(entry.getKey(), Collections.unmodifiableSet(
					new LinkedHashSet<>(entry.getValue())));
			}
			result.put(kindEntry.getKey(), Collections.unmodifiableMap(names));
		}
		return Collections.unmodifiableMap(result);
	}

	private static String normalize(String value)
	{
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}
}
