package com.bronzemantcg.catalog.remote;

import com.bronzemantcg.ownership.CardEntityKind;
import com.bronzemantcg.ownership.ImmutableCardIdentityCatalog;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Rejects structurally valid responses which are too incomplete to activate safely. */
@Singleton
public final class RemoteCatalogValidator
{
	static final int MINIMUM_ITEM_PARENTS = 3000;
	static final int MINIMUM_NPC_PARENTS = 1000;

	private final int minimumItemParents;
	private final int minimumNpcParents;
	private final Map<CardEntityKind, Set<String>> sentinelParents;

	@Inject
	public RemoteCatalogValidator()
	{
		this(MINIMUM_ITEM_PARENTS, MINIMUM_NPC_PARENTS, productionSentinels());
	}

	RemoteCatalogValidator(int minimumItemParents, int minimumNpcParents,
		Map<CardEntityKind, Set<String>> sentinelParents)
	{
		this.minimumItemParents = minimumItemParents;
		this.minimumNpcParents = minimumNpcParents;
		Map<CardEntityKind, Set<String>> copy = new EnumMap<>(CardEntityKind.class);
		for (CardEntityKind kind : CardEntityKind.values())
		{
			copy.put(kind, Set.copyOf(sentinelParents.getOrDefault(kind, Set.of())));
		}
		this.sentinelParents = Map.copyOf(copy);
	}

	public void validate(OsrsTcgCatalogSnapshot snapshot) throws CatalogValidationException
	{
		if (snapshot == null)
		{
			throw new CatalogValidationException("catalogue snapshot is required");
		}
		Map<CardEntityKind, Integer> counts = new EnumMap<>(CardEntityKind.class);
		for (CardEntityKind kind : CardEntityKind.values())
		{
			counts.put(kind, 0);
		}
		for (ImmutableCardIdentityCatalog.Entry entry : snapshot.getEntries())
		{
			CardEntityKind kind = entry.getIdentity().getKind();
			counts.put(kind, counts.get(kind) + 1);
		}
		requireCoverage(CardEntityKind.ITEM, counts.get(CardEntityKind.ITEM),
			minimumItemParents);
		requireCoverage(CardEntityKind.NPC, counts.get(CardEntityKind.NPC),
			minimumNpcParents);
		for (Map.Entry<CardEntityKind, Set<String>> entry : sentinelParents.entrySet())
		{
			for (String parent : entry.getValue())
			{
				if (snapshot.findByCardName(entry.getKey(), parent).size() != 1)
				{
					throw new CatalogValidationException("catalogue is missing "
						+ entry.getKey() + " sentinel parent " + parent);
				}
			}
		}
	}

	private static void requireCoverage(CardEntityKind kind, int actual, int minimum)
		throws CatalogValidationException
	{
		if (actual < minimum)
		{
			throw new CatalogValidationException("catalogue has only " + actual + " "
				+ kind + " parents; expected at least " + minimum);
		}
	}

	private static Map<CardEntityKind, Set<String>> productionSentinels()
	{
		Map<CardEntityKind, Set<String>> sentinels = new EnumMap<>(CardEntityKind.class);
		sentinels.put(CardEntityKind.ITEM,
			new LinkedHashSet<>(Set.of("Coins", "Water rune")));
		sentinels.put(CardEntityKind.NPC,
			new LinkedHashSet<>(Set.of("Goblin", "Zulrah")));
		return sentinels;
	}
}
