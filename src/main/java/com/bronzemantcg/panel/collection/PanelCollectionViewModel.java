package com.bronzemantcg.panel.collection;

import com.bronzemantcg.ownership.ActiveCardIdentityCatalog;
import com.bronzemantcg.ownership.CardEntityKind;
import com.bronzemantcg.ownership.CardIdentity;
import com.bronzemantcg.ownership.TcgOwnershipSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Prepared structure and ownership state for the v1 Collection and Global Search views. */
@Singleton
public final class PanelCollectionViewModel
{
	static final String BETA_ONLY_SECTION_ID = "section-beta-only-items";
	static final String UNSORTED_SECTION_ID = "collection-unsorted-v1";
	static final String UNSORTED_ITEM_CATEGORY_ID = "collection-unsorted-v1-items";
	static final String UNSORTED_NPC_CATEGORY_ID = "collection-unsorted-v1-npcs";

	private final PanelCollectionOwnership ownership;
	private final PanelCollectionLayout layout;
	private final ActiveCardIdentityCatalog activeCatalog;
	private volatile Prepared prepared;

	@Inject
	public PanelCollectionViewModel(PanelCollectionLayout layout,
		PanelCollectionOwnership ownership, ActiveCardIdentityCatalog activeCatalog)
	{
		this.layout = layout;
		this.ownership = ownership;
		this.activeCatalog = activeCatalog;
		prepared = build(PanelCollectionProjection.unavailable(0L), layout);
	}

	private static Prepared build(PanelCollectionProjection projection,
		PanelCollectionLayout catalog)
	{
		Map<String, CategoryBuilder> categories = new LinkedHashMap<>();
		Set<String> betaOnlyCategoryIds = new HashSet<>();
		List<SectionBuilder> sectionBuilders = new ArrayList<>();
		for (PanelCollectionLayout.Section sourceSection : catalog.getSections())
		{
			if (BETA_ONLY_SECTION_ID.equals(sourceSection.getId()))
			{
				for (PanelCollectionLayout.Category category : sourceSection.getCategories())
				{
					betaOnlyCategoryIds.add(category.getId());
				}
				continue;
			}
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

		List<PanelCollectionLayout.CollectionCard> unsortedItems = new ArrayList<>();
		List<PanelCollectionLayout.CollectionCard> unsortedNpcs = new ArrayList<>();
		int items = 0;
		int npcs = 0;
		for (PanelCollectionLayout.CollectionCard card : projection.getCards())
		{
			if (card.getKind() == CardEntityKind.ITEM)
			{
				items++;
			}
			else
			{
				npcs++;
			}
			for (String categoryId : card.getCategoryIds())
			{
				CategoryBuilder category = categories.get(categoryId);
				if (category != null)
				{
					category.cards.add(card);
				}
			}
			boolean hasNormalAssignment = card.getCategoryIds().stream()
				.anyMatch(categoryId -> !betaOnlyCategoryIds.contains(categoryId));
			if (!hasNormalAssignment)
			{
				(card.getKind() == CardEntityKind.ITEM ? unsortedItems : unsortedNpcs).add(card);
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
		List<Category> unsortedCategories = new ArrayList<>();
		if (!unsortedItems.isEmpty())
		{
			unsortedCategories.add(new Category(UNSORTED_ITEM_CATEGORY_ID,
				"Items", unsortedItems));
		}
		if (!unsortedNpcs.isEmpty())
		{
			unsortedCategories.add(new Category(UNSORTED_NPC_CATEGORY_ID,
				"NPCs", unsortedNpcs));
		}
		if (!unsortedCategories.isEmpty())
		{
			builtSections.add(new Section(UNSORTED_SECTION_ID,
				"Unsorted v1", unsortedCategories));
		}
		List<Section> sections = List.copyOf(builtSections);
		List<SearchCard> searchCards = buildSearchCards(projection.getCards());
		Map<CardEntityKind, Map<String, PanelCollectionLayout.CollectionCard>>
			cardsByKindAndName = buildCardLookup(projection.getCards());
		return new Prepared(projection, sections, searchCards, cardsByKindAndName,
			items, npcs);
	}

	private Prepared currentPrepared()
	{
		ActiveCardIdentityCatalog.View active = activeCatalog.getView();
		Prepared current = prepared;
		if (current.projection.getRevision() == active.getRevision())
		{
			return current;
		}
		synchronized (this)
		{
			current = prepared;
			if (current.projection.getRevision() != active.getRevision())
			{
				current = build(PanelCollectionProjection.fromActive(layout, active), layout);
				prepared = current;
			}
			return current;
		}
	}

	public List<Section> getSections()
	{
		return currentPrepared().sections;
	}

	public List<SearchCard> getSearchCards()
	{
		return currentPrepared().searchCards;
	}

	public int getItemTotal()
	{
		return currentPrepared().itemTotal;
	}

	public int getNpcTotal()
	{
		return currentPrepared().npcTotal;
	}

	public Optional<PanelCollectionLayout.CollectionCard> findCard(
		CardEntityKind kind, String cardName)
	{
		if (kind == null || cardName == null)
		{
			return Optional.empty();
		}
		PanelCollectionLayout.CollectionCard projected = currentPrepared().cardsByKindAndName
			.getOrDefault(kind, Collections.emptyMap()).get(normalize(cardName));
		if (projected != null)
		{
			return Optional.of(projected);
		}
		List<CardIdentity> identities = activeCatalog
			.getView().findByCardName(kind, cardName);
		return identities.size() == 1
			? Optional.of(PanelCollectionLayout.CollectionCard.fromIdentity(
				identities.get(0), Collections.emptySet()))
			: Optional.empty();
	}

	public State prepare(TcgOwnershipSnapshot personalOwnership, Set<String> sharedCardNames)
	{
		return prepare(personalOwnership, sharedCardNames, Collections.emptySet(), false);
	}

	public State prepare(TcgOwnershipSnapshot personalOwnership, Set<String> sharedCardNames,
		Set<String> frozenBetaNames)
	{
		return prepare(personalOwnership, sharedCardNames, frozenBetaNames, false);
	}

	public State prepare(TcgOwnershipSnapshot personalOwnership, Set<String> sharedCardNames,
		Set<String> frozenBetaNames, boolean hideBetaProgress)
	{
		Prepared current = currentPrepared();
		Map<PanelCollectionLayout.CollectionCard, Status> states = new LinkedHashMap<>();
		int ownedItems = 0;
		int ownedNpcs = 0;
		for (SearchCard searchCard : current.searchCards)
		{
			PanelCollectionLayout.CollectionCard card = searchCard.getCard();
			Status status;
			boolean collectedThroughBeta = hideBetaProgress
				&& ownership.isPersonallyCollected(card, null, frozenBetaNames,
					current.projection);
			if (!collectedThroughBeta
				&& ownership.isPersonallyCollected(card, personalOwnership, frozenBetaNames,
					current.projection))
			{
				status = Status.OWNED;
				if (card.getKind() == CardEntityKind.ITEM)
				{
					ownedItems++;
				}
				else
				{
					ownedNpcs++;
				}
			}
			else if (ownership.isSharedCollectionCard(
				card, sharedCardNames, current.projection))
			{
				status = Status.SHARED;
			}
			else
			{
				status = Status.LOCKED;
			}
			states.put(card, status);
		}
		return new State(current, states, ownedItems, ownedNpcs);
	}

	private static List<SearchCard> buildSearchCards(
		List<PanelCollectionLayout.CollectionCard> cards)
	{
		Map<String, Integer> nameCounts = new HashMap<>();
		for (PanelCollectionLayout.CollectionCard card : cards)
		{
			nameCounts.merge(normalize(card.getCardName()), 1, Integer::sum);
		}
		List<SearchCard> result = new ArrayList<>();
		for (PanelCollectionLayout.CollectionCard card : cards)
		{
			String normalized = normalize(card.getCardName());
			String displayName = card.getCardName();
			if (nameCounts.getOrDefault(normalized, 0) > 1)
			{
				displayName += card.getKind() == CardEntityKind.ITEM ? " (item)" : " (npc)";
			}
			result.add(new SearchCard(normalized, displayName, card));
		}
		result.sort(Comparator.comparing(SearchCard::getDisplayName,
			String.CASE_INSENSITIVE_ORDER));
		return List.copyOf(result);
	}

	private static Map<CardEntityKind, Map<String, PanelCollectionLayout.CollectionCard>>
		buildCardLookup(List<PanelCollectionLayout.CollectionCard> cards)
	{
		Map<CardEntityKind, Map<String, PanelCollectionLayout.CollectionCard>> result =
			new EnumMap<>(CardEntityKind.class);
		for (PanelCollectionLayout.CollectionCard card : cards)
		{
			result.computeIfAbsent(card.getKind(), ignored -> new HashMap<>())
				.put(normalize(card.getCardName()), card);
		}
		for (Map.Entry<CardEntityKind,
			Map<String, PanelCollectionLayout.CollectionCard>> entry : result.entrySet())
		{
			entry.setValue(Collections.unmodifiableMap(entry.getValue()));
		}
		return Collections.unmodifiableMap(result);
	}

	private static String normalize(String value)
	{
		return value.trim().toLowerCase(Locale.ROOT);
	}

	public enum Status
	{
		OWNED,
		SHARED,
		LOCKED
	}

	public static final class State
	{
		private final Prepared prepared;
		private final Map<PanelCollectionLayout.CollectionCard, Status> states;
		private final int ownedItems;
		private final int ownedNpcs;

		private State(Prepared prepared,
			Map<PanelCollectionLayout.CollectionCard, Status> states,
			int ownedItems, int ownedNpcs)
		{
			this.prepared = prepared;
			this.states = Collections.unmodifiableMap(new LinkedHashMap<>(states));
			this.ownedItems = ownedItems;
			this.ownedNpcs = ownedNpcs;
		}

		public Status getStatus(PanelCollectionLayout.CollectionCard card)
		{
			return states.getOrDefault(card, Status.LOCKED);
		}

		public int getOwnedItems()
		{
			return ownedItems;
		}

		public int getOwnedNpcs()
		{
			return ownedNpcs;
		}

		public List<Section> getSections()
		{
			return prepared.sections;
		}

		public List<SearchCard> getSearchCards()
		{
			return prepared.searchCards;
		}

		public int getItemTotal()
		{
			return prepared.itemTotal;
		}

		public int getNpcTotal()
		{
			return prepared.npcTotal;
		}

		public long getCatalogRevision()
		{
			return prepared.projection.getRevision();
		}

		public boolean isRemoteCatalog()
		{
			return prepared.projection.isRemote();
		}

		public boolean isCatalogAvailable()
		{
			return prepared.projection.isRemote();
		}

		Set<String> matchingSharedNames(PanelCollectionLayout.CollectionCard card,
			Set<String> sharedCardNames)
		{
			if (card == null || sharedCardNames == null || sharedCardNames.isEmpty())
			{
				return Collections.emptySet();
			}
			Set<String> normalizedShared = new HashSet<>();
			for (String sharedName : sharedCardNames)
			{
				if (sharedName != null)
				{
					normalizedShared.add(normalize(sharedName));
				}
			}
			Set<String> matches = new LinkedHashSet<>();
			for (String acceptedName : card.getAcceptedNamesLowerCase())
			{
				String normalized = normalize(acceptedName);
				if (prepared.projection.isAcceptedNameUnique(normalized)
					&& normalizedShared.contains(normalized))
				{
					matches.add(normalized);
				}
			}
			return Collections.unmodifiableSet(matches);
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
			return prepared.projection.getRevision()
				== state.prepared.projection.getRevision()
				&& ownedItems == state.ownedItems && ownedNpcs == state.ownedNpcs
				&& states.equals(state.states);
		}

		@Override
		public int hashCode()
		{
			int result = Long.hashCode(prepared.projection.getRevision());
			result = 31 * result + states.hashCode();
			result = 31 * result + ownedItems;
			return 31 * result + ownedNpcs;
		}
	}

	private static final class Prepared
	{
		private final PanelCollectionProjection projection;
		private final List<Section> sections;
		private final List<SearchCard> searchCards;
		private final Map<CardEntityKind, Map<String, PanelCollectionLayout.CollectionCard>>
			cardsByKindAndName;
		private final int itemTotal;
		private final int npcTotal;

		private Prepared(PanelCollectionProjection projection, List<Section> sections,
			List<SearchCard> searchCards,
			Map<CardEntityKind, Map<String, PanelCollectionLayout.CollectionCard>>
				cardsByKindAndName,
			int itemTotal, int npcTotal)
		{
			this.projection = projection;
			this.sections = sections;
			this.searchCards = searchCards;
			this.cardsByKindAndName = cardsByKindAndName;
			this.itemTotal = itemTotal;
			this.npcTotal = npcTotal;
		}
	}

	public static final class Section
	{
		private final String id;
		private final String name;
		private final List<Category> categories;
		private final List<PanelCollectionLayout.CollectionCard> cards;

		private Section(String id, String name, List<Category> categories)
		{
			this.id = id;
			this.name = name;
			this.categories = List.copyOf(categories);
			LinkedHashSet<PanelCollectionLayout.CollectionCard> values = new LinkedHashSet<>();
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

		public List<PanelCollectionLayout.CollectionCard> getCards()
		{
			return cards;
		}
	}

	public static final class Category
	{
		private final String id;
		private final String name;
		private final List<PanelCollectionLayout.CollectionCard> cards;

		private Category(String id, String name,
			List<PanelCollectionLayout.CollectionCard> cards)
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

		public List<PanelCollectionLayout.CollectionCard> getCards()
		{
			return cards;
		}
	}

	public static final class SearchCard
	{
		private final String searchName;
		private final String displayName;
		private final PanelCollectionLayout.CollectionCard card;

		private SearchCard(String searchName, String displayName,
			PanelCollectionLayout.CollectionCard card)
		{
			this.searchName = searchName;
			this.displayName = displayName;
			this.card = card;
		}

		public String getSearchName()
		{
			return searchName;
		}

		public String getDisplayName()
		{
			return displayName;
		}

		public PanelCollectionLayout.CollectionCard getCard()
		{
			return card;
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
		private final List<PanelCollectionLayout.CollectionCard> cards = new ArrayList<>();

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
