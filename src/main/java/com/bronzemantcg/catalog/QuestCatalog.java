package com.bronzemantcg.catalog;


import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Quest;

/**
 * Informational quest data for the side panel: which cards a player must own to complete
 * each quest (required items as any-of groups, plus resolvable quest enemies). Loaded from
 * resources/quest/quest_cards.json; purely display - quests are never blocked by this plugin,
 * but under forced-drop a locked quest item genuinely can't be used, so "own the cards"
 * and "can physically complete it" coincide.
 */
@Slf4j
@Singleton
public class QuestCatalog
{
	private List<QuestEntry> quests = Collections.emptyList();
	private List<QuestEntry> miniquests = Collections.emptyList();
	// Quest name -> kill-required monster CARD names, including miniquests. Feeds
	// QuestNpcIndex's quest-state override.
	private Map<String, List<String>> questMonsterCards = Collections.emptyMap();
	private Map<String, List<String>> cardQuests = Collections.emptyMap();

	@Inject
	public QuestCatalog(Gson gson)
	{
		load(gson);
	}

	public List<QuestEntry> getQuests()
	{
		return quests;
	}

	public List<QuestEntry> getMiniquests()
	{
		return miniquests;
	}

	public Map<String, List<String>> getQuestMonsterCards()
	{
		return questMonsterCards;
	}

	public List<String> getQuestsForCard(String cardName)
	{
		if (cardName == null)
		{
			return Collections.emptyList();
		}
		return cardQuests.getOrDefault(
			cardName.toLowerCase(Locale.ROOT), Collections.emptyList());
	}

	private void load(Gson gson)
	{
		try (InputStream stream = getClass().getResourceAsStream("/quest/quest_cards.json"))
		{
			if (stream == null)
			{
				log.info("quest_cards.json not present; quest panel section will be empty.");
				return;
			}
			Snapshot snapshot = gson.fromJson(
				new InputStreamReader(stream, StandardCharsets.UTF_8), Snapshot.class);
			if (snapshot == null || snapshot.quests == null)
			{
				return;
			}
			List<QuestEntry> loaded = new ArrayList<>();
			List<QuestEntry> loadedMiniquests = new ArrayList<>();
			Map<String, List<String>> monsterMap = new HashMap<>();
			Map<String, List<String>> questsByCard = new HashMap<>();
			for (QuestDto dto : snapshot.quests)
			{
				if (dto == null || dto.name == null || dto.name.trim().isEmpty())
				{
					continue;
				}
				List<Section> sections = new ArrayList<>();
				List<String> monsters = new ArrayList<>();
				if (dto.sections != null)
				{
					for (SectionDto sectionDto : dto.sections)
					{
						if (sectionDto == null)
						{
							continue;
						}
						List<Requirement> requirements = new ArrayList<>();
						if (sectionDto.requirements != null)
						{
							for (RequirementDto requirementDto : sectionDto.requirements)
							{
								Requirement requirement = loadRequirement(requirementDto);
								if (requirement != null)
								{
									requirements.add(requirement);
									indexRequirement(questsByCard, dto.name.trim(),
										requirement, monsters);
								}
							}
						}
						sections.add(new Section(sectionDto.label, requirements));
					}
				}
				if (!monsters.isEmpty())
				{
					// Keyed by the game name: QuestNpcIndex matches these against Quest.getName().
					monsterMap.put(gameName(dto), Collections.unmodifiableList(monsters));
				}
				QuestEntry entry = new QuestEntry(dto.name.trim(), gameName(dto), dto.miniquest,
					sections.toArray(new Section[0]),
					dto.notes == null ? "" : dto.notes.trim());
				(dto.miniquest ? loadedMiniquests : loaded).add(entry);
			}
			loaded.sort((a, b) -> sortName(a.name).compareToIgnoreCase(sortName(b.name)));
			loadedMiniquests.sort((a, b) -> sortName(a.name).compareToIgnoreCase(sortName(b.name)));
			quests = Collections.unmodifiableList(loaded);
			miniquests = Collections.unmodifiableList(loadedMiniquests);
			questMonsterCards = Collections.unmodifiableMap(monsterMap);
			Map<String, List<String>> immutableCardQuests = new HashMap<>();
			for (Map.Entry<String, List<String>> entry : questsByCard.entrySet())
			{
				entry.getValue().sort(String.CASE_INSENSITIVE_ORDER);
				immutableCardQuests.put(entry.getKey(),
					Collections.unmodifiableList(entry.getValue()));
			}
			cardQuests = Collections.unmodifiableMap(immutableCardQuests);
			log.info("Loaded {} quests and {} miniquests from card-requirement snapshot",
				quests.size(), miniquests.size());
		}
		catch (IOException ex)
		{
			log.warn("Failed to load quest_cards.json", ex);
		}
	}

	/** Resolve the optional RuneLite constant to the game's own quest name. */
	private static String gameName(QuestDto dto)
	{
		if (dto.runeliteQuest == null || dto.runeliteQuest.trim().isEmpty())
		{
			return dto.name.trim();
		}
		for (Quest quest : Quest.values())
		{
			if (quest.name().equals(dto.runeliteQuest.trim()))
			{
				return quest.getName();
			}
		}
		log.warn("Unknown quest constant '{}' on '{}'", dto.runeliteQuest, dto.name);
		return dto.name.trim();
	}

	private static Requirement loadRequirement(RequirementDto dto)
	{
		return loadRequirement(dto, null);
	}

	private static Requirement loadRequirement(RequirementDto dto, String inheritedType)
	{
		if (dto == null || dto.label == null || dto.label.trim().isEmpty())
		{
			return null;
		}
		List<Requirement> children = new ArrayList<>();
		if (dto.children != null)
		{
			for (RequirementDto childDto : dto.children)
			{
				Requirement child = loadRequirement(childDto,
					dto.type == null || dto.type.trim().isEmpty() ? inheritedType : dto.type);
				if (child != null)
				{
					children.add(child);
				}
			}
		}
		String type = dto.type == null || dto.type.trim().isEmpty()
			? inheritedType : dto.type;
		return new Requirement(dto.label.trim(), dto.cards, dto.cards, dto.quantity,
			parseLogic(dto.logic), type, children, dto.selector, dto.selectorValue,
			dto.displayCardsOnly);
	}

	private static Logic parseLogic(String value)
	{
		return "ANY".equalsIgnoreCase(value) ? Logic.ANY : Logic.ALL;
	}

	private static String sortName(String name)
	{
		return name.regionMatches(true, 0, "The ", 0, 4) ? name.substring(4) : name;
	}

	private static void indexRequirement(Map<String, List<String>> questsByCard,
		String questName, Requirement requirement, List<String> monsters)
	{
		indexQuestCards(questsByCard, questName, requirement.displayCards);
		if ("enemy".equals(requirement.type) || "npc".equals(requirement.type))
		{
			for (String card : requirement.displayCards)
			{
				if (!monsters.contains(card))
				{
					monsters.add(card);
				}
			}
		}
		for (Requirement child : requirement.children)
		{
			indexRequirement(questsByCard, questName, child, monsters);
		}
	}

	private static void indexQuestCards(Map<String, List<String>> questsByCard,
		String questName, List<String> cards)
	{
		for (String card : cards)
		{
			if (card == null || card.trim().isEmpty())
			{
				continue;
			}
			List<String> quests = questsByCard.computeIfAbsent(
				card.trim().toLowerCase(Locale.ROOT), ignored -> new ArrayList<>());
			if (!quests.contains(questName))
			{
				quests.add(questName);
			}
		}
	}

	public static class QuestEntry
	{
		public final String name;
		/**
		 * The name RuneLite's {@link Quest} enum uses. Usually identical to {@link #name},
		 * but a shortened display name ("RFD - King Awowogei") must still resolve to the
		 * game's own name or quest-completion lookups silently never match.
		 */
		public final String questName;
		public final boolean miniquest;
		public final List<Section> sections;
		public final List<Requirement> requirements;
		public final String notes;

		public QuestEntry(String name, boolean miniquest, List<Requirement> requirements)
		{
			this(name, miniquest, requirements, "");
		}

		public QuestEntry(String name, boolean miniquest, List<Requirement> requirements, String notes)
		{
			this(name, miniquest,
				new Section[]{new Section("", requirements)}, notes);
		}

		private QuestEntry(String name, boolean miniquest, Section[] sections, String notes)
		{
			this(name, name, miniquest, sections, notes);
		}

		private QuestEntry(String name, String questName, boolean miniquest,
			Section[] sections, String notes)
		{
			this.name = name;
			this.questName = questName == null || questName.trim().isEmpty() ? name : questName;
			this.miniquest = miniquest;
			List<Section> sectionList = new ArrayList<>();
			Collections.addAll(sectionList, sections);
			this.sections = Collections.unmodifiableList(sectionList);
			List<Requirement> flattened = new ArrayList<>();
			for (Section section : sectionList)
			{
				flattened.addAll(section.requirements);
			}
			this.requirements = Collections.unmodifiableList(flattened);
			this.notes = notes;
		}

		/** Copy this entry with presentation-only sections while preserving game identity. */
		public QuestEntry withSections(List<Section> projectedSections)
		{
			return new QuestEntry(name, questName, miniquest,
				projectedSections.toArray(new Section[0]), notes);
		}

		public int satisfiedCount(Set<String> ownedLowerCase)
		{
			return satisfiedCount(ownedLowerCase, RouteSelection.UNKNOWN);
		}

		public int satisfiedCount(Set<String> ownedLowerCase, RouteSelection route)
		{
			int count = 0;
			for (Requirement requirement : requirements)
			{
				if (requirement.isSatisfied(ownedLowerCase, route))
				{
					count++;
				}
			}
			return count;
		}
	}

	public static class Section
	{
		public final String label;
		public final List<Requirement> requirements;

		Section(String label, List<Requirement> requirements)
		{
			this.label = label == null ? "" : label.trim();
			this.requirements = Collections.unmodifiableList(new ArrayList<>(requirements));
		}

		/** Copy this section with presentation-only requirements. */
		public Section withRequirements(List<Requirement> projectedRequirements)
		{
			return new Section(label, projectedRequirements);
		}

		public int satisfiedCount(Set<String> ownedLowerCase, RouteSelection route)
		{
			int count = 0;
			for (Requirement requirement : requirements)
			{
				if (requirement.isSatisfied(ownedLowerCase, route))
				{
					count++;
				}
			}
			return count;
		}
	}

	public enum Logic
	{
		ALL,
		ANY
	}

	public enum RouteSelection
	{
		UNKNOWN,
		BLACK_ARM,
		PHOENIX
	}

	/** Recursive card requirement. A leaf owns any card; a branch combines child nodes. */
	public static class Requirement
	{
		public final String label;
		public final List<String> displayCards;
		public final List<String> lowerCards;
		public final int quantity;
		public final Logic logic;
		public final String type;
		public final List<Requirement> children;
		public final String selector;
		public final String selectorValue;
		public final boolean displayCardsOnly;

		public Requirement(String label, List<String> cards)
		{
			this(label, cards, cards, null, Logic.ANY, "card",
				Collections.emptyList(), null, null, false);
		}

		Requirement(String label, List<String> displayCards, List<String> acceptedCards,
			Integer quantity,
			Logic logic, String type, List<Requirement> children,
			String selector, String selectorValue, boolean displayCardsOnly)
		{
			this.label = label;
			List<String> display = new ArrayList<>();
			for (String card : displayCards == null
				? Collections.<String>emptyList() : displayCards)
			{
				if (card != null && !card.trim().isEmpty())
				{
					display.add(card.trim());
				}
			}
			List<String> lower = new ArrayList<>();
			for (String card : acceptedCards == null
				? Collections.<String>emptyList() : acceptedCards)
			{
				if (card != null && !card.trim().isEmpty())
				{
					String normalized = card.trim().toLowerCase(Locale.ROOT);
					if (!lower.contains(normalized))
					{
						lower.add(normalized);
					}
				}
			}
			this.displayCards = Collections.unmodifiableList(display);
			this.lowerCards = Collections.unmodifiableList(lower);
			this.quantity = quantity == null || quantity < 1 ? 1 : quantity;
			this.logic = logic == null ? Logic.ALL : logic;
			this.type = type == null ? "card" : type.trim().toLowerCase(Locale.ROOT);
			this.children = Collections.unmodifiableList(new ArrayList<>(children));
			this.selector = selector == null ? "" : selector.trim();
			this.selectorValue = selectorValue == null ? "" : selectorValue.trim();
			this.displayCardsOnly = displayCardsOnly;
		}

		/**
		 * Copy this requirement for another presentation catalogue. Display cards remain
		 * canonical and compact while accepted cards may include reviewed legacy aliases.
		 */
		public Requirement withProjection(List<String> projectedDisplayCards,
			List<String> acceptedCards, List<Requirement> projectedChildren)
		{
			return new Requirement(label, projectedDisplayCards, acceptedCards, quantity,
				logic, type, projectedChildren, selector, selectorValue, displayCardsOnly);
		}

		public boolean isSatisfied(Set<String> ownedLowerCase)
		{
			return isSatisfied(ownedLowerCase, RouteSelection.UNKNOWN);
		}

		public boolean isSatisfied(Set<String> ownedLowerCase, RouteSelection route)
		{
			Requirement selected = selectedChild(route);
			if (selected != null)
			{
				return selected.isSatisfied(ownedLowerCase, route);
			}
			if (!children.isEmpty())
			{
				if (logic == Logic.ANY)
				{
					for (Requirement child : children)
					{
						if (child.isSatisfied(ownedLowerCase, route))
						{
							return true;
						}
					}
					return false;
				}
				for (Requirement child : children)
				{
					if (!child.isSatisfied(ownedLowerCase, route))
					{
						return false;
					}
				}
				return true;
			}
			for (String card : lowerCards)
			{
				if (ownedLowerCase.contains(card))
				{
					return true;
				}
			}
			return lowerCards.isEmpty();
		}

		private Requirement selectedChild(RouteSelection route)
		{
			if (!"SHIELD_GANG".equals(selector) || route == RouteSelection.UNKNOWN)
			{
				return null;
			}
			for (Requirement child : children)
			{
				if (route.name().equals(child.selectorValue))
				{
					return child;
				}
			}
			return null;
		}
	}

	private static class Snapshot
	{
		int schema;
		List<QuestDto> quests;
	}

	private static class QuestDto
	{
		String name;
		/** Optional RuneLite Quest constant when the display name differs from the game's. */
		String runeliteQuest;
		boolean miniquest;
		List<SectionDto> sections;
		String notes;
	}

	private static class SectionDto
	{
		String label;
		List<RequirementDto> requirements;
	}

	private static class RequirementDto
	{
		String label;
		List<String> cards;
		Integer quantity;
		String logic;
		String type;
		List<RequirementDto> children;
		String selector;
		String selectorValue;
		boolean displayCardsOnly;
	}
}
