package com.bronzemantcg.panel.collection;

import com.bronzemantcg.ownership.ActiveCardIdentityCatalog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Projects TCG Locked names through the current Collection or reviewed Beta layout. */
@Singleton
public final class PanelSharedCardsViewModel
{
	private static final String OTHER_CARDS = "Other Cards";

	private final PanelCollectionLayout catalog;
	private final PanelCollectionOwnership ownership;
	private final ActiveCardIdentityCatalog activeCatalog;
	private final Map<String, List<PanelCollectionLayout.BetaCollectionCard>> betaCardsByCategory;

	@Inject
	public PanelSharedCardsViewModel(PanelCollectionLayout catalog,
		PanelCollectionOwnership ownership, ActiveCardIdentityCatalog activeCatalog)
	{
		this.catalog = catalog;
		this.ownership = ownership;
		this.activeCatalog = activeCatalog;
		Map<String, List<PanelCollectionLayout.BetaCollectionCard>> byCategory = new HashMap<>();
		for (PanelCollectionLayout.BetaCollectionCard card : catalog.getBetaCollectionCards())
		{
			if (!card.isVisible())
			{
				continue;
			}
			for (String categoryId : card.getCategoryIds())
			{
				byCategory.computeIfAbsent(categoryId, ignored -> new ArrayList<>()).add(card);
			}
		}
		Map<String, List<PanelCollectionLayout.BetaCollectionCard>> frozen =
			new LinkedHashMap<>();
		for (Map.Entry<String, List<PanelCollectionLayout.BetaCollectionCard>> entry
			: byCategory.entrySet())
		{
			frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
		}
		betaCardsByCategory = Collections.unmodifiableMap(frozen);
	}

	public State prepare(Set<String> sharedCardNames,
		PanelCollectionViewModel.State collectionState)
	{
		Set<String> remaining = normalizeNames(sharedCardNames);
		List<Category> categories = collectionState != null
			&& collectionState.isCatalogAvailable()
			? currentCategories(collectionState, remaining)
			: betaCategories(remaining);
		if (!remaining.isEmpty())
		{
			List<String> other = new ArrayList<>();
			for (String cardName : remaining)
			{
				other.add(displayCardName(cardName));
			}
			other.sort(String.CASE_INSENSITIVE_ORDER);
			categories.add(new Category(OTHER_CARDS, other, Collections.emptyMap()));
		}
		return new State(categories);
	}

	public String displayCardName(String cardName)
	{
		String displayName = activeCatalog.findDisplayCardName(cardName);
		if (displayName != null)
		{
			return displayName;
		}
		String normalized = normalize(cardName);
		return normalized.isEmpty() ? normalized
			: Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
	}

	private List<Category> currentCategories(PanelCollectionViewModel.State state,
		Set<String> remaining)
	{
		List<Category> result = new ArrayList<>();
		Set<PanelCollectionLayout.CollectionCard> placed = new HashSet<>();
		for (PanelCollectionViewModel.Section section : state.getSections())
		{
			Map<String, List<String>> subcategories = new LinkedHashMap<>();
			for (PanelCollectionViewModel.Category category : section.getCategories())
			{
				List<String> cards = new ArrayList<>();
				for (PanelCollectionLayout.CollectionCard card : category.getCards())
				{
					Set<String> matches = state.matchingSharedNames(card, remaining);
					if (state.getStatus(card) == PanelCollectionViewModel.Status.SHARED
						&& !matches.isEmpty() && placed.add(card))
					{
						cards.add(card.getCardName());
						remaining.removeAll(matches);
					}
				}
				if (!cards.isEmpty())
				{
					subcategories.put(category.getName(), cards);
				}
			}
			if (!subcategories.isEmpty())
			{
				result.add(new Category(section.getName(), Collections.emptyList(),
					subcategories));
			}
		}
		return result;
	}

	private List<Category> betaCategories(Set<String> remaining)
	{
		List<Category> result = new ArrayList<>();
		Set<String> placed = new HashSet<>();
		for (PanelCollectionLayout.Section section : catalog.getSections())
		{
			if (!section.isVisible())
			{
				continue;
			}
			Map<String, List<String>> subcategories = new LinkedHashMap<>();
			for (PanelCollectionLayout.Category category : section.getCategories())
			{
				if (!category.isVisible())
				{
					continue;
				}
				List<String> cards = new ArrayList<>();
				for (PanelCollectionLayout.BetaCollectionCard card
					: betaCardsByCategory.getOrDefault(category.getId(), Collections.emptyList()))
				{
					for (PanelCollectionLayout.BetaVariant variant : card.getVariants())
					{
						String normalized = normalize(variant.getName());
						if (remaining.contains(normalized) && placed.add(normalized)
							&& ownership.isBetaVariantInSnapshot(variant, remaining))
						{
							cards.add(variant.getName());
							remaining.remove(normalized);
						}
					}
				}
				if (!cards.isEmpty())
				{
					subcategories.put(category.getName(), cards);
				}
			}
			if (!subcategories.isEmpty())
			{
				result.add(new Category(section.getName(), Collections.emptyList(),
					subcategories));
			}
		}
		return result;
	}

	private static Set<String> normalizeNames(Set<String> names)
	{
		Set<String> result = new LinkedHashSet<>();
		if (names != null)
		{
			for (String name : names)
			{
				String normalized = normalize(name);
				if (!normalized.isEmpty())
				{
					result.add(normalized);
				}
			}
		}
		return result;
	}

	private static String normalize(String value)
	{
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	public static final class State
	{
		private final List<Category> categories;

		private State(List<Category> categories)
		{
			this.categories = List.copyOf(categories);
		}

		public List<Category> getCategories()
		{
			return categories;
		}
	}

	public static final class Category
	{
		private final String name;
		private final List<String> items;
		private final Map<String, List<String>> subcategories;

		public Category(String name, List<String> items,
			Map<String, List<String>> subcategories)
		{
			this.name = name;
			this.items = List.copyOf(items);
			Map<String, List<String>> frozen = new LinkedHashMap<>();
			for (Map.Entry<String, List<String>> entry : subcategories.entrySet())
			{
				frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
			}
			this.subcategories = Collections.unmodifiableMap(frozen);
		}

		public String getName()
		{
			return name;
		}

		public List<String> getItems()
		{
			return items;
		}

		public Map<String, List<String>> getSubcategories()
		{
			return subcategories;
		}

		public int size()
		{
			int count = items.size();
			for (List<String> values : subcategories.values())
			{
				count += values.size();
			}
			return count;
		}
	}
}
