package com.bronzemantcg.panel.collection;

import com.bronzemantcg.ownership.ActiveCardIdentityCatalog;
import com.bronzemantcg.ownership.CardEntityKind;
import com.bronzemantcg.ownership.CardIdentity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** One immutable set of Collection parents projected onto the owner-curated panel layout. */
final class PanelCollectionProjection
{
	private final long revision;
	private final boolean remote;
	private final List<PanelCollectionLayout.CollectionCard> cards;
	private final Map<CardEntityKind, Map<Integer, Integer>> idUseCounts;
	private final Map<String, Integer> nameUseCounts;

	private PanelCollectionProjection(long revision, boolean remote,
		List<PanelCollectionLayout.CollectionCard> cards)
	{
		this.revision = revision;
		this.remote = remote;
		this.cards = List.copyOf(cards);
		Map<CardEntityKind, Map<Integer, Integer>> ids = new EnumMap<>(CardEntityKind.class);
		Map<String, Integer> names = new HashMap<>();
		for (CardEntityKind kind : CardEntityKind.values())
		{
			ids.put(kind, new HashMap<>());
		}
		for (PanelCollectionLayout.CollectionCard card : cards)
		{
			for (Integer id : card.getEntityIds())
			{
				ids.get(card.getKind()).merge(id, 1, Integer::sum);
			}
			for (String name : card.getAcceptedNamesLowerCase())
			{
				names.merge(normalize(name), 1, Integer::sum);
			}
		}
		idUseCounts = freezeCounts(ids);
		nameUseCounts = Collections.unmodifiableMap(new LinkedHashMap<>(names));
	}

	static PanelCollectionProjection unavailable(long revision)
	{
		return new PanelCollectionProjection(revision, false, Collections.emptyList());
	}

	static PanelCollectionProjection fromActive(PanelCollectionLayout layout,
		ActiveCardIdentityCatalog.View active)
	{
		if (!active.isV1CatalogAvailable())
		{
			return unavailable(active.getRevision());
		}

		Map<CardEntityKind, Map<String, PanelCollectionLayout.CollectionPlacement>> exact =
			new EnumMap<>(CardEntityKind.class);
		Map<CardEntityKind, Map<String, Set<PanelCollectionLayout.CollectionPlacement>>> aliases =
			new EnumMap<>(CardEntityKind.class);
		Map<PanelCollectionLayout.CollectionPlacement, Integer> layoutOrder = new HashMap<>();
		for (CardEntityKind kind : CardEntityKind.values())
		{
			exact.put(kind, new HashMap<>());
			aliases.put(kind, new HashMap<>());
		}
		int order = 0;
		for (PanelCollectionLayout.CollectionPlacement placement : layout.getCollectionPlacements())
		{
			layoutOrder.put(placement, order++);
			exact.get(placement.getKind()).put(
				normalize(placement.getCardName()), placement);
		}
		for (PanelCollectionLayout.BetaCollectionCard beta : layout.getBetaCollectionCards())
		{
			PanelCollectionLayout.CollectionPlacement placement = exact.get(beta.getKind())
				.get(normalize(beta.getParentName()));
			if (placement == null)
			{
				continue;
			}
			for (PanelCollectionLayout.BetaVariant variant : beta.getVariants())
			{
				aliases.get(beta.getKind())
					.computeIfAbsent(normalize(variant.getName()),
						ignored -> new LinkedHashSet<>())
					.add(placement);
			}
		}

		List<RankedCard> ranked = new ArrayList<>();
		for (CardIdentity identity : active.getCardIdentities())
		{
			PanelCollectionLayout.CollectionPlacement placement = exact.get(identity.getKind())
				.get(normalize(identity.getCardName()));
			if (placement == null)
			{
				LinkedHashSet<PanelCollectionLayout.CollectionPlacement> candidates =
					new LinkedHashSet<>();
				for (String legacyName : identity.getLegacyCardNames())
				{
					PanelCollectionLayout.CollectionPlacement match = exact.get(identity.getKind())
						.get(normalize(legacyName));
					if (match != null)
					{
						candidates.add(match);
					}
					candidates.addAll(aliases.get(identity.getKind()).getOrDefault(
						normalize(legacyName), Collections.emptySet()));
				}
				if (candidates.size() == 1)
				{
					placement = candidates.iterator().next();
				}
			}
			Set<String> categories = placement == null
				? Collections.emptySet() : placement.getCategoryIds();
			ranked.add(new RankedCard(PanelCollectionLayout.CollectionCard.fromIdentity(
				identity, categories), placement == null
					? Integer.MAX_VALUE : layoutOrder.get(placement)));
		}
		ranked.sort(Comparator.comparingInt(RankedCard::getOrder)
			.thenComparing(card -> card.card.getKind())
			.thenComparing(card -> card.card.getCardName(), String.CASE_INSENSITIVE_ORDER));
		List<PanelCollectionLayout.CollectionCard> projected = new ArrayList<>();
		for (RankedCard card : ranked)
		{
			projected.add(card.card);
		}
		return new PanelCollectionProjection(active.getRevision(), true, projected);
	}

	long getRevision()
	{
		return revision;
	}

	boolean isRemote()
	{
		return remote;
	}

	List<PanelCollectionLayout.CollectionCard> getCards()
	{
		return cards;
	}

	boolean isEntityIdUnique(CardEntityKind kind, int entityId)
	{
		return idUseCounts.getOrDefault(kind, Collections.emptyMap())
			.getOrDefault(entityId, 0) == 1;
	}

	boolean isAcceptedNameUnique(String name)
	{
		return name != null && nameUseCounts.getOrDefault(normalize(name), 0) == 1;
	}

	private static Map<CardEntityKind, Map<Integer, Integer>> freezeCounts(
		Map<CardEntityKind, Map<Integer, Integer>> source)
	{
		Map<CardEntityKind, Map<Integer, Integer>> result =
			new EnumMap<>(CardEntityKind.class);
		for (Map.Entry<CardEntityKind, Map<Integer, Integer>> entry : source.entrySet())
		{
			result.put(entry.getKey(), Collections.unmodifiableMap(
				new LinkedHashMap<>(entry.getValue())));
		}
		return Collections.unmodifiableMap(result);
	}

	private static String normalize(String value)
	{
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private static final class RankedCard
	{
		private final PanelCollectionLayout.CollectionCard card;
		private final int order;

		private RankedCard(PanelCollectionLayout.CollectionCard card, int order)
		{
			this.card = card;
			this.order = order;
		}

		private int getOrder()
		{
			return order;
		}
	}
}
