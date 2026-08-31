package com.bronzemantcg.panel.collection;

import com.bronzemantcg.ownership.CardEntityKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Prepared hierarchy and exact variant ownership for the Beta Collection tab. */
@Singleton
public final class PanelBetaCollectionViewModel
{
	private final PanelCollectionOwnership ownership;
	private final List<Section> sections;
	private final List<PanelCollectionLayout.BetaCollectionCard> parents;
	private final Map<PanelCollectionLayout.BetaCollectionCard, String> displayNames;
	private final int variantTotal;

	@Inject
	public PanelBetaCollectionViewModel(PanelCollectionLayout catalog,
		PanelCollectionOwnership ownership)
	{
		this.ownership = ownership;
		Map<String, CategoryBuilder> categories = new LinkedHashMap<>();
		List<SectionBuilder> sectionBuilders = new ArrayList<>();
		for (PanelCollectionLayout.Section sourceSection : catalog.getSections())
		{
			if (!sourceSection.isVisible())
			{
				continue;
			}
			SectionBuilder section = new SectionBuilder(
				sourceSection.getId(), sourceSection.getName());
			for (PanelCollectionLayout.Category sourceCategory : sourceSection.getCategories())
			{
				if (sourceCategory.isVisible())
				{
					CategoryBuilder category = new CategoryBuilder(
						sourceCategory.getId(), sourceCategory.getName());
					section.categories.add(category);
					categories.put(category.id, category);
				}
			}
			sectionBuilders.add(section);
		}

		List<PanelCollectionLayout.BetaCollectionCard> visibleParents = new ArrayList<>();
		int variants = 0;
		for (PanelCollectionLayout.BetaCollectionCard card : catalog.getBetaCollectionCards())
		{
			if (!card.isVisible())
			{
				continue;
			}
			visibleParents.add(card);
			variants += card.getVariants().size();
			for (String categoryId : card.getCategoryIds())
			{
				CategoryBuilder category = categories.get(categoryId);
				if (category != null)
				{
					category.cards.add(card);
				}
			}
		}

		List<Section> builtSections = new ArrayList<>();
		for (SectionBuilder section : sectionBuilders)
		{
			Section built = section.build();
			if (!built.getCategories().isEmpty())
			{
				builtSections.add(built);
			}
		}
		sections = List.copyOf(builtSections);
		parents = List.copyOf(visibleParents);
		variantTotal = variants;
		displayNames = buildDisplayNames(visibleParents);
	}

	public List<Section> getSections()
	{
		return sections;
	}

	public List<PanelCollectionLayout.BetaCollectionCard> getParents()
	{
		return parents;
	}

	public int getParentTotal()
	{
		return parents.size();
	}

	public int getVariantTotal()
	{
		return variantTotal;
	}

	public String getDisplayName(PanelCollectionLayout.BetaCollectionCard card)
	{
		return displayNames.getOrDefault(card, card.getParentName());
	}

	public boolean parentNameMatches(PanelCollectionLayout.BetaCollectionCard card, String query)
	{
		return normalize(card.getParentName()).contains(normalize(query));
	}

	/** True when expanding the parent explains more than repeating the same card name. */
	public boolean hasVariantBreakdown(PanelCollectionLayout.BetaCollectionCard card)
	{
		List<PanelCollectionLayout.BetaVariant> variants = card.getVariants();
		if (variants.size() != 1)
		{
			return variants.size() > 1;
		}
		return !normalize(variants.get(0).getName())
			.equals(normalize(card.getParentName()));
	}

	public List<PanelCollectionLayout.BetaVariant> matchingVariants(
		PanelCollectionLayout.BetaCollectionCard card, String query)
	{
		if (!hasVariantBreakdown(card))
		{
			return Collections.emptyList();
		}
		String normalized = normalize(query);
		List<PanelCollectionLayout.BetaVariant> matches = new ArrayList<>();
		for (PanelCollectionLayout.BetaVariant variant : card.getVariants())
		{
			if (normalize(variant.getName()).contains(normalized))
			{
				matches.add(variant);
			}
		}
		return matches;
	}

	public State prepare(Set<String> betaSnapshotNames,
		BetaCollectionSnapshotService.Status snapshotStatus)
	{
		Map<PanelCollectionLayout.BetaCollectionCard, PanelCollectionViewModel.Status>
			parentStates = new LinkedHashMap<>();
		Map<PanelCollectionLayout.BetaVariant, PanelCollectionViewModel.Status>
			variantStates = new LinkedHashMap<>();
		int ownedParents = 0;
		for (PanelCollectionLayout.BetaCollectionCard parent : parents)
		{
			boolean parentOwned = false;
			for (PanelCollectionLayout.BetaVariant variant : parent.getVariants())
			{
				boolean variantOwned = ownership.isBetaVariantInSnapshot(
					variant, betaSnapshotNames);
				PanelCollectionViewModel.Status status = variantOwned
					? PanelCollectionViewModel.Status.OWNED
					: PanelCollectionViewModel.Status.LOCKED;
				parentOwned |= variantOwned;
				variantStates.put(variant, status);
			}
			PanelCollectionViewModel.Status parentStatus = parentOwned
				? PanelCollectionViewModel.Status.OWNED
				: PanelCollectionViewModel.Status.LOCKED;
			if (parentOwned)
			{
				ownedParents++;
			}
			parentStates.put(parent, parentStatus);
		}
		return new State(parentStates, variantStates, ownedParents, snapshotStatus);
	}

	private static Map<PanelCollectionLayout.BetaCollectionCard, String> buildDisplayNames(
		List<PanelCollectionLayout.BetaCollectionCard> cards)
	{
		Map<String, Integer> nameCounts = new HashMap<>();
		for (PanelCollectionLayout.BetaCollectionCard card : cards)
		{
			nameCounts.merge(normalize(card.getParentName()), 1, Integer::sum);
		}
		Map<PanelCollectionLayout.BetaCollectionCard, String> result = new LinkedHashMap<>();
		for (PanelCollectionLayout.BetaCollectionCard card : cards)
		{
			String displayName = card.getParentName();
			if (nameCounts.getOrDefault(normalize(displayName), 0) > 1)
			{
				displayName += card.getKind() == CardEntityKind.ITEM ? " (item)" : " (npc)";
			}
			result.put(card, displayName);
		}
		return Collections.unmodifiableMap(result);
	}

	private static String normalize(String value)
	{
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	public static final class State
	{
		private final Map<PanelCollectionLayout.BetaCollectionCard,
			PanelCollectionViewModel.Status> parentStates;
		private final Map<PanelCollectionLayout.BetaVariant,
			PanelCollectionViewModel.Status> variantStates;
		private final int ownedParents;
		private final BetaCollectionSnapshotService.Status snapshotStatus;

		private State(Map<PanelCollectionLayout.BetaCollectionCard,
			PanelCollectionViewModel.Status> parentStates,
			Map<PanelCollectionLayout.BetaVariant,
				PanelCollectionViewModel.Status> variantStates,
			int ownedParents, BetaCollectionSnapshotService.Status snapshotStatus)
		{
			this.parentStates = Collections.unmodifiableMap(new LinkedHashMap<>(parentStates));
			this.variantStates = Collections.unmodifiableMap(new LinkedHashMap<>(variantStates));
			this.ownedParents = ownedParents;
			this.snapshotStatus = snapshotStatus;
		}

		public PanelCollectionViewModel.Status getParentStatus(
			PanelCollectionLayout.BetaCollectionCard card)
		{
			return parentStates.getOrDefault(card, PanelCollectionViewModel.Status.LOCKED);
		}

		public PanelCollectionViewModel.Status getVariantStatus(
			PanelCollectionLayout.BetaVariant variant)
		{
			return variantStates.getOrDefault(variant, PanelCollectionViewModel.Status.LOCKED);
		}

		public int getOwnedParents()
		{
			return ownedParents;
		}

		public BetaCollectionSnapshotService.Status getSnapshotStatus()
		{
			return snapshotStatus;
		}

		@Override
		public boolean equals(Object other)
		{
			if (this == other)
			{
				return true;
			}
			if (!(other instanceof State))
			{
				return false;
			}
			State state = (State) other;
			return ownedParents == state.ownedParents
				&& snapshotStatus == state.snapshotStatus
				&& parentStates.equals(state.parentStates)
				&& variantStates.equals(state.variantStates);
		}

		@Override
		public int hashCode()
		{
			int result = parentStates.hashCode();
			result = 31 * result + variantStates.hashCode();
			result = 31 * result + ownedParents;
			return 31 * result + snapshotStatus.hashCode();
		}
	}

	public static final class Section
	{
		private final String id;
		private final String name;
		private final List<Category> categories;
		private final List<PanelCollectionLayout.BetaCollectionCard> cards;

		private Section(String id, String name, List<Category> categories)
		{
			this.id = id;
			this.name = name;
			this.categories = List.copyOf(categories);
			LinkedHashSet<PanelCollectionLayout.BetaCollectionCard> values =
				new LinkedHashSet<>();
			for (Category category : categories)
			{
				values.addAll(category.getCards());
			}
			cards = List.copyOf(values);
		}

		public String getId()
		{
			return id;
		}

		public String getName()
		{
			return name;
		}

		public List<Category> getCategories()
		{
			return categories;
		}

		public List<PanelCollectionLayout.BetaCollectionCard> getCards()
		{
			return cards;
		}
	}

	public static final class Category
	{
		private final String id;
		private final String name;
		private final List<PanelCollectionLayout.BetaCollectionCard> cards;

		private Category(String id, String name,
			List<PanelCollectionLayout.BetaCollectionCard> cards)
		{
			this.id = id;
			this.name = name;
			this.cards = List.copyOf(cards);
		}

		public String getId()
		{
			return id;
		}

		public String getName()
		{
			return name;
		}

		public List<PanelCollectionLayout.BetaCollectionCard> getCards()
		{
			return cards;
		}
	}

	private static final class SectionBuilder
	{
		private final String id;
		private final String name;
		private final List<CategoryBuilder> categories = new ArrayList<>();

		private SectionBuilder(String id, String name)
		{
			this.id = id;
			this.name = name;
		}

		private Section build()
		{
			List<Category> built = new ArrayList<>();
			for (CategoryBuilder category : categories)
			{
				if (!category.cards.isEmpty())
				{
					built.add(category.build());
				}
			}
			return new Section(id, name, built);
		}
	}

	private static final class CategoryBuilder
	{
		private final String id;
		private final String name;
		private final List<PanelCollectionLayout.BetaCollectionCard> cards = new ArrayList<>();

		private CategoryBuilder(String id, String name)
		{
			this.id = id;
			this.name = name;
		}

		private Category build()
		{
			return new Category(id, name, cards);
		}
	}
}
