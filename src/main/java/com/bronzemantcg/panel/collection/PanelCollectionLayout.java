package com.bronzemantcg.panel.collection;

import com.bronzemantcg.ownership.CardEntityKind;
import com.bronzemantcg.ownership.CardIdentity;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
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
import lombok.extern.slf4j.Slf4j;

/**
 * Owner-curated placement model for the v1 Collection and Beta Collection tabs.
 * Active v1 identities are deliberately absent: they come only from the validated API catalogue.
 * This class has no Swing dependency, so layout validation and ownership remain
 * independently testable.
 */
@Slf4j
@Singleton
@SuppressWarnings("LombokGetterMayBeUsed")
public final class PanelCollectionLayout
{
	private static final String DEFAULT_RESOURCE = "/panel/collection_layout.json";

	private final List<Section> sections;
	private final List<CollectionPlacement> collectionPlacements;
	private final List<BetaCollectionCard> betaCollectionCards;
	private final Map<CardEntityKind, Map<Integer, Integer>> betaIdUseCounts;
	private final Map<String, Integer> betaNameUseCounts;
	private final String organiserFingerprint;
	private final String organiserProjectSha256;

	@Inject
	public PanelCollectionLayout(Gson gson)
	{
		this(gson, DEFAULT_RESOURCE);
	}

	PanelCollectionLayout(Gson gson, String resourcePath)
	{
		Loaded loaded = load(gson, resourcePath);
		sections = loaded.sections;
		collectionPlacements = loaded.collectionPlacements;
		betaCollectionCards = loaded.betaCollectionCards;
		betaIdUseCounts = loaded.betaIdUseCounts;
		betaNameUseCounts = loaded.betaNameUseCounts;
		organiserFingerprint = loaded.organiserFingerprint;
		organiserProjectSha256 = loaded.organiserProjectSha256;
	}

	public List<Section> getSections()
	{
		return sections;
	}

	public List<CollectionPlacement> getCollectionPlacements()
	{
		return collectionPlacements;
	}

	public List<BetaCollectionCard> getBetaCollectionCards()
	{
		return betaCollectionCards;
	}

	public String getOrganiserFingerprint()
	{
		return organiserFingerprint;
	}

	public String getOrganiserProjectSha256()
	{
		return organiserProjectSha256;
	}

	public boolean isBetaEntityIdUnique(CardEntityKind kind, int entityId)
	{
		Map<Integer, Integer> counts = betaIdUseCounts.get(kind);
		return counts != null && counts.getOrDefault(entityId, 0) == 1;
	}

	public boolean isBetaVariantNameUnique(String name)
	{
		return name != null && betaNameUseCounts.getOrDefault(normalize(name), 0) == 1;
	}

	private static Loaded load(Gson gson, String resourcePath)
	{
		if (gson == null || resourcePath == null)
		{
			return Loaded.empty();
		}
		try (InputStream stream = PanelCollectionLayout.class.getResourceAsStream(resourcePath))
		{
			if (stream == null)
			{
				log.warn("{} missing from classpath; future collection tabs will remain empty.",
					resourcePath);
				return Loaded.empty();
			}
			Snapshot snapshot = gson.fromJson(
				new InputStreamReader(stream, StandardCharsets.UTF_8), Snapshot.class);
			return validate(snapshot);
		}
		catch (IOException | RuntimeException ex)
		{
			log.warn("Failed to load {}; future collection tabs will remain empty.",
				resourcePath, ex);
			return Loaded.empty();
		}
	}

	private static Loaded validate(Snapshot snapshot)
	{
		if (snapshot == null || snapshot.schemaVersion != 1 || snapshot.sections == null
			|| snapshot.collectionPlacements == null || snapshot.betaCollectionCards == null)
		{
			throw new IllegalArgumentException("panel collection layout must use schema version 1");
		}

		Set<String> sectionIds = new HashSet<>();
		Set<String> categoryIds = new LinkedHashSet<>();
		List<String> categoryIdsByIndex = new ArrayList<>();
		List<Section> sections = new ArrayList<>();
		for (SectionDto section : snapshot.sections)
		{
			if (section == null)
			{
				throw new IllegalArgumentException("section is required");
			}
			String sectionId = required(section.id, "section id");
			String sectionName = required(section.name, "section name");
			if (!sectionIds.add(sectionId))
			{
				throw new IllegalArgumentException("duplicate section id: " + sectionId);
			}
			if (section.categories == null || section.categories.isEmpty())
			{
				throw new IllegalArgumentException("section has no categories: " + sectionId);
			}
			List<Category> categories = new ArrayList<>();
			for (CategoryDto category : section.categories)
			{
				if (category == null)
				{
					throw new IllegalArgumentException("category is required");
				}
				String categoryId = required(category.id, "category id");
				String categoryName = required(category.name, "category name");
				if (!categoryIds.add(categoryId))
				{
					throw new IllegalArgumentException("duplicate category id: " + categoryId);
				}
				categoryIdsByIndex.add(categoryId);
				categories.add(new Category(categoryId, categoryName, category.visible));
			}
			sections.add(new Section(sectionId, sectionName, section.visible, categories));
		}

		List<CollectionPlacement> collectionPlacements = new ArrayList<>();
		Set<String> collectionKeys = new HashSet<>();
		for (CollectionPlacementDto card : snapshot.collectionPlacements)
		{
			if (card == null)
			{
				throw new IllegalArgumentException("collection placement is required");
			}
			CardEntityKind kind = kind(card.kind);
			String cardName = required(card.cardName, "collection card name");
			String key = kind + "\0" + normalize(cardName);
			if (!collectionKeys.add(key))
			{
				throw new IllegalArgumentException("duplicate collection card: " + cardName);
			}
			Set<String> placementCategories = categories(
				card.categoryIndexes, categoryIdsByIndex, cardName);
			if (placementCategories.isEmpty())
			{
				throw new IllegalArgumentException("collection placement has no category: " + cardName);
			}
			collectionPlacements.add(new CollectionPlacement(kind, cardName,
				placementCategories));
		}

		List<BetaCollectionCard> betaCards = new ArrayList<>();
		Set<String> betaKeys = new HashSet<>();
		Map<CardEntityKind, Map<Integer, Integer>> idCounts = new EnumMap<>(CardEntityKind.class);
		Map<String, Integer> nameCounts = new HashMap<>();
		for (CardEntityKind kind : CardEntityKind.values())
		{
			idCounts.put(kind, new HashMap<>());
		}
		for (BetaCardDto card : snapshot.betaCollectionCards)
		{
			if (card == null)
			{
				throw new IllegalArgumentException("beta card is required");
			}
			String rowKey = required(card.key, "beta row key");
			if (!betaKeys.add(rowKey))
			{
				throw new IllegalArgumentException("duplicate beta row key: " + rowKey);
			}
			CardEntityKind kind = kind(card.kind);
			String parentName = required(card.parentName, "beta parent name");
			if (card.variants == null || card.variants.isEmpty())
			{
				throw new IllegalArgumentException("beta parent has no variants: " + parentName);
			}
			List<BetaVariant> variants = new ArrayList<>();
			Set<String> variantNames = new HashSet<>();
			for (BetaVariantDto variant : card.variants)
			{
				if (variant == null)
				{
					throw new IllegalArgumentException("beta variant is required");
				}
				String name = required(variant.name, "beta variant name");
				if (!variantNames.add(normalize(name)))
				{
					throw new IllegalArgumentException("duplicate beta variant: " + name);
				}
				nameCounts.merge(normalize(name), 1, Integer::sum);
				Set<Integer> entityIds = validIds(variant.entityIds);
				for (Integer entityId : entityIds)
				{
					idCounts.get(kind).merge(entityId, 1, Integer::sum);
				}
				variants.add(new BetaVariant(kind, name, entityIds));
			}
			Set<String> betaCategories = categories(
				card.categoryIndexes, categoryIdsByIndex, parentName);
			if (betaCategories.isEmpty())
			{
				throw new IllegalArgumentException("beta parent has no category: " + parentName);
			}
			betaCards.add(new BetaCollectionCard(rowKey, kind, parentName, card.betaOnly,
				card.visible, betaCategories, variants));
		}

		return new Loaded(sections, collectionPlacements, betaCards,
			freezeCounts(idCounts),
			Collections.unmodifiableMap(new LinkedHashMap<>(nameCounts)),
			snapshot.source == null ? null : snapshot.source.organiserFingerprint,
			snapshot.source == null ? null : snapshot.source.organiserProjectSha256);
	}

	private static Set<String> categories(List<Integer> values, List<String> known, String cardName)
	{
		if (values == null)
		{
			throw new IllegalArgumentException("card categories are missing: " + cardName);
		}
		LinkedHashSet<String> result = new LinkedHashSet<>();
		for (Integer value : values)
		{
			if (value == null || value < 0 || value >= known.size())
			{
				throw new IllegalArgumentException("unknown category index: " + value);
			}
			result.add(known.get(value));
		}
		return Collections.unmodifiableSet(result);
	}

	private static Set<Integer> validIds(List<Integer> values)
	{
		LinkedHashSet<Integer> result = new LinkedHashSet<>();
		if (values != null)
		{
			for (Integer value : values)
			{
				if (value == null || value < 0)
				{
					throw new IllegalArgumentException("invalid beta entity ID");
				}
				result.add(value);
			}
		}
		return Collections.unmodifiableSet(result);
	}

	private static CardEntityKind kind(String value)
	{
		try
		{
			return CardEntityKind.valueOf(required(value, "card kind").toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex)
		{
			throw new IllegalArgumentException("invalid card kind: " + value, ex);
		}
	}

	private static String required(String value, String label)
	{
		if (value == null || value.trim().isEmpty())
		{
			throw new IllegalArgumentException(label + " is required");
		}
		return value.trim();
	}

	private static String normalize(String value)
	{
		return value.trim().toLowerCase(Locale.ROOT);
	}

	private static Map<CardEntityKind, Map<Integer, Integer>> freezeCounts(
		Map<CardEntityKind, Map<Integer, Integer>> source)
	{
		Map<CardEntityKind, Map<Integer, Integer>> result = new EnumMap<>(CardEntityKind.class);
		for (Map.Entry<CardEntityKind, Map<Integer, Integer>> entry : source.entrySet())
		{
			result.put(entry.getKey(), Collections.unmodifiableMap(
				new LinkedHashMap<>(entry.getValue())));
		}
		return Collections.unmodifiableMap(result);
	}

	public static final class Section
	{
		private final String id;
		private final String name;
		private final boolean visible;
		private final List<Category> categories;

		private Section(String id, String name, boolean visible, List<Category> categories)
		{
			this.id = id;
			this.name = name;
			this.visible = visible;
			this.categories = List.copyOf(categories);
		}

		public String getId()
		{
			return id;
		}

		public String getName()
		{
			return name;
		}

		public boolean isVisible()
		{
			return visible;
		}

		public List<Category> getCategories()
		{
			return categories;
		}
	}

	public static final class Category
	{
		private final String id;
		private final String name;
		private final boolean visible;

		private Category(String id, String name, boolean visible)
		{
			this.id = id;
			this.name = name;
			this.visible = visible;
		}

		public String getId()
		{
			return id;
		}

		public String getName()
		{
			return name;
		}

		public boolean isVisible()
		{
			return visible;
		}
	}

	/** A stable owner-curated category key, without any bundled v1 identity data. */
	public static final class CollectionPlacement
	{
		private final CardEntityKind kind;
		private final String cardName;
		private final Set<String> categoryIds;

		private CollectionPlacement(CardEntityKind kind, String cardName,
			Set<String> categoryIds)
		{
			this.kind = kind;
			this.cardName = cardName;
			this.categoryIds = categoryIds;
		}

		public CardEntityKind getKind()
		{
			return kind;
		}

		public String getCardName()
		{
			return cardName;
		}

		public Set<String> getCategoryIds()
		{
			return categoryIds;
		}
	}

	/** Runtime-only Collection identity projected from the active v1 API catalogue. */
	public static final class CollectionCard
	{
		private final CardEntityKind kind;
		private final String cardName;
		private final Set<String> categoryIds;
		private final Set<String> acceptedNamesLowerCase;
		private final Set<Integer> entityIds;

		private CollectionCard(CardEntityKind kind, String cardName, Set<String> categoryIds,
			Set<String> acceptedNamesLowerCase, Set<Integer> entityIds)
		{
			this.kind = kind;
			this.cardName = cardName;
			this.categoryIds = categoryIds;
			this.acceptedNamesLowerCase = acceptedNamesLowerCase;
			this.entityIds = entityIds;
		}

		static CollectionCard fromIdentity(CardIdentity identity, Set<String> categoryIds)
		{
			LinkedHashSet<String> acceptedNames = new LinkedHashSet<>();
			acceptedNames.add(normalize(identity.getCardName()));
			for (String legacyName : identity.getLegacyCardNames())
			{
				acceptedNames.add(normalize(legacyName));
			}
			return new CollectionCard(identity.getKind(), identity.getCardName(),
				Collections.unmodifiableSet(new LinkedHashSet<>(categoryIds)),
				Collections.unmodifiableSet(acceptedNames), identity.getEntityIds());
		}

		public CardEntityKind getKind()
		{
			return kind;
		}

		public String getCardName()
		{
			return cardName;
		}

		public Set<String> getCategoryIds()
		{
			return categoryIds;
		}

		public Set<String> getAcceptedNamesLowerCase()
		{
			return acceptedNamesLowerCase;
		}

		public Set<Integer> getEntityIds()
		{
			return entityIds;
		}
	}

	public static final class BetaCollectionCard
	{
		private final String key;
		private final CardEntityKind kind;
		private final String parentName;
		private final boolean betaOnly;
		private final boolean visible;
		private final Set<String> categoryIds;
		private final List<BetaVariant> variants;

		private BetaCollectionCard(String key, CardEntityKind kind, String parentName,
			boolean betaOnly, boolean visible, Set<String> categoryIds,
			List<BetaVariant> variants)
		{
			this.key = key;
			this.kind = kind;
			this.parentName = parentName;
			this.betaOnly = betaOnly;
			this.visible = visible;
			this.categoryIds = categoryIds;
			this.variants = List.copyOf(variants);
		}

		public String getKey()
		{
			return key;
		}

		public CardEntityKind getKind()
		{
			return kind;
		}

		public String getParentName()
		{
			return parentName;
		}

		public boolean isBetaOnly()
		{
			return betaOnly;
		}

		public boolean isVisible()
		{
			return visible;
		}

		public Set<String> getCategoryIds()
		{
			return categoryIds;
		}

		public List<BetaVariant> getVariants()
		{
			return variants;
		}
	}

	public static final class BetaVariant
	{
		private final CardEntityKind kind;
		private final String name;
		private final Set<Integer> entityIds;

		private BetaVariant(CardEntityKind kind, String name, Set<Integer> entityIds)
		{
			this.kind = kind;
			this.name = name;
			this.entityIds = entityIds;
		}

		public CardEntityKind getKind()
		{
			return kind;
		}

		public String getName()
		{
			return name;
		}

		public Set<Integer> getEntityIds()
		{
			return entityIds;
		}
	}

	private static final class Loaded
	{
		private final List<Section> sections;
		private final List<CollectionPlacement> collectionPlacements;
		private final List<BetaCollectionCard> betaCollectionCards;
		private final Map<CardEntityKind, Map<Integer, Integer>> betaIdUseCounts;
		private final Map<String, Integer> betaNameUseCounts;
		private final String organiserFingerprint;
		private final String organiserProjectSha256;

		private Loaded(List<Section> sections, List<CollectionPlacement> collectionPlacements,
			List<BetaCollectionCard> betaCollectionCards,
			Map<CardEntityKind, Map<Integer, Integer>> betaIdUseCounts,
			Map<String, Integer> betaNameUseCounts,
			String organiserFingerprint, String organiserProjectSha256)
		{
			this.sections = List.copyOf(sections);
			this.collectionPlacements = List.copyOf(collectionPlacements);
			this.betaCollectionCards = List.copyOf(betaCollectionCards);
			this.betaIdUseCounts = betaIdUseCounts;
			this.betaNameUseCounts = betaNameUseCounts;
			this.organiserFingerprint = organiserFingerprint;
			this.organiserProjectSha256 = organiserProjectSha256;
		}

		private static Loaded empty()
		{
			return new Loaded(Collections.emptyList(), Collections.emptyList(),
				Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(),
				null, null);
		}
	}

	// These private input DTOs are populated by RuneLite's injected Gson. They intentionally have
	// no Java-side writers; the narrow suppressions document that boundary for IntelliJ.
	@SuppressWarnings({"unused", "MismatchedQueryAndUpdateOfCollection"})
	private static final class Snapshot
	{
		private int schemaVersion;
		private SourceDto source;
		private List<SectionDto> sections;
		private List<CollectionPlacementDto> collectionPlacements;
		private List<BetaCardDto> betaCollectionCards;
	}

	@SuppressWarnings("unused")
	private static final class SourceDto
	{
		private String organiserFingerprint;
		private String organiserProjectSha256;
	}

	@SuppressWarnings({"unused", "MismatchedQueryAndUpdateOfCollection"})
	private static final class SectionDto
	{
		private String id;
		private String name;
		private boolean visible;
		private List<CategoryDto> categories;
	}

	@SuppressWarnings("unused")
	private static final class CategoryDto
	{
		private String id;
		private String name;
		private boolean visible;
	}

	@SuppressWarnings("unused")
	private static final class CollectionPlacementDto
	{
		private String kind;
		private String cardName;
		private List<Integer> categoryIndexes;
	}

	@SuppressWarnings({"unused", "MismatchedQueryAndUpdateOfCollection"})
	private static final class BetaCardDto
	{
		private String key;
		private String kind;
		private String parentName;
		private boolean betaOnly;
		private boolean visible;
		private List<Integer> categoryIndexes;
		private List<BetaVariantDto> variants;
	}

	@SuppressWarnings("unused")
	private static final class BetaVariantDto
	{
		private String name;
		private List<Integer> entityIds;
	}
}
