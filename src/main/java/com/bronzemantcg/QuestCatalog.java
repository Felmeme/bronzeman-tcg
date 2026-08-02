package com.bronzemantcg;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Informational quest data for the side panel: prerequisite quest state and which cards a
 * player must own to complete each quest (required items as any-of groups, plus resolvable
 * quest enemies). Loaded from resources/quest_cards.json and quest_prerequisites.json;
 * purely display - quests are never blocked by this plugin.
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

	public int size()
	{
		return quests.size();
	}

	private void load(Gson gson)
	{
		Map<String, List<Requirement>> prerequisitesByQuest = loadPrerequisites(gson);
		try (InputStream stream = getClass().getResourceAsStream("/quest_cards.json"))
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
				List<Requirement> prerequisites = prerequisitesByQuest.remove(
					normalizeQuestName(dto.name));
				if (prerequisites != null && !prerequisites.isEmpty())
				{
					sections.add(new Section("Quest prerequisites", prerequisites));
				}
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
					monsterMap.put(dto.name.trim(), Collections.unmodifiableList(monsters));
				}
				QuestEntry entry = new QuestEntry(dto.name.trim(), dto.miniquest,
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
			if (!prerequisitesByQuest.isEmpty())
			{
				log.warn("Ignored prerequisite data for {} unknown quest entries",
					prerequisitesByQuest.size());
			}
			log.info("Loaded {} quests and {} miniquests from card-requirement snapshot",
				quests.size(), miniquests.size());
		}
		catch (IOException ex)
		{
			log.warn("Failed to load quest_cards.json", ex);
		}
	}

	private Map<String, List<Requirement>> loadPrerequisites(Gson gson)
	{
		Map<String, List<Requirement>> result = new HashMap<>();
		try (InputStream stream = getClass().getResourceAsStream(
			"/quest_prerequisites.json"))
		{
			if (stream == null)
			{
				log.info("quest_prerequisites.json not present; quest readiness will be card-only.");
				return result;
			}
			PrerequisiteSnapshot snapshot = gson.fromJson(
				new InputStreamReader(stream, StandardCharsets.UTF_8),
				PrerequisiteSnapshot.class);
			if (snapshot == null || snapshot.quests == null)
			{
				return result;
			}
			for (QuestPrerequisitesDto quest : snapshot.quests)
			{
				if (quest == null || quest.name == null || quest.name.trim().isEmpty()
					|| quest.prerequisites == null)
				{
					continue;
				}
				List<Requirement> requirements = result.computeIfAbsent(
					normalizeQuestName(quest.name), ignored -> new ArrayList<>());
				for (PrerequisiteDto prerequisite : quest.prerequisites)
				{
					if (prerequisite == null || prerequisite.name == null
						|| prerequisite.name.trim().isEmpty())
					{
						continue;
					}
					QuestStateRequirement state = parseQuestState(prerequisite.state);
					String name = prerequisite.name.trim();
					requirements.add(new Requirement(
						(state == QuestStateRequirement.IN_PROGRESS ? "Start " : "Complete ") + name,
						Collections.emptyList(), null, Logic.ALL, "quest",
						Collections.emptyList(), null, null, false,
						Collections.singletonList(name), state));
				}
			}
		}
		catch (IOException ex)
		{
			log.warn("Failed to load quest_prerequisites.json", ex);
		}
		return result;
	}

	private static Requirement loadRequirement(RequirementDto dto)
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
				Requirement child = loadRequirement(childDto);
				if (child != null)
				{
					children.add(child);
				}
			}
		}
		return new Requirement(dto.label.trim(), dto.cards, dto.quantity,
			parseLogic(dto.logic), dto.type, children, dto.selector, dto.selectorValue,
			dto.displayCardsOnly, Collections.emptyList(), QuestStateRequirement.FINISHED);
	}

	private static Logic parseLogic(String value)
	{
		return "ANY".equalsIgnoreCase(value) ? Logic.ANY : Logic.ALL;
	}

	private static QuestStateRequirement parseQuestState(String value)
	{
		return "IN_PROGRESS".equalsIgnoreCase(value)
			? QuestStateRequirement.IN_PROGRESS : QuestStateRequirement.FINISHED;
	}

	static String normalizeQuestName(String name)
	{
		String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
		return normalized.endsWith(" (miniquest)")
			? normalized.substring(0, normalized.length() - " (miniquest)".length())
			: normalized;
	}

	private static String sortName(String name)
	{
		return name.regionMatches(true, 0, "The ", 0, 4) ? name.substring(4) : name;
	}

	private static void indexRequirement(Map<String, List<String>> questsByCard,
		String questName, Requirement requirement, List<String> monsters)
	{
		indexQuestCards(questsByCard, questName, requirement.displayCards);
		if ("enemy".equals(requirement.type))
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
		public final boolean miniquest;
		public final List<Section> sections;
		public final List<Requirement> requirements;
		public final String notes;

		QuestEntry(String name, boolean miniquest, List<Requirement> requirements)
		{
			this(name, miniquest, requirements, "");
		}

		QuestEntry(String name, boolean miniquest, List<Requirement> requirements, String notes)
		{
			this(name, miniquest,
				new Section[]{new Section("", requirements)}, notes);
		}

		private QuestEntry(String name, boolean miniquest, Section[] sections, String notes)
		{
			this.name = name;
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

		public int satisfiedCount(Set<String> ownedLowerCase)
		{
			return satisfiedCount(ownedLowerCase, RouteSelection.UNKNOWN);
		}

		public int satisfiedCount(Set<String> ownedLowerCase, RouteSelection route)
		{
			return satisfiedCount(ownedLowerCase, Collections.emptySet(),
				Collections.emptySet(), route);
		}

		public int satisfiedCount(Set<String> ownedLowerCase,
			Set<String> startedQuests, Set<String> completedQuests, RouteSelection route)
		{
			int count = 0;
			for (Requirement requirement : requirements)
			{
				if (requirement.isSatisfied(ownedLowerCase, startedQuests,
					completedQuests, route))
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

		public int satisfiedCount(Set<String> ownedLowerCase, RouteSelection route)
		{
			return satisfiedCount(ownedLowerCase, Collections.emptySet(),
				Collections.emptySet(), route);
		}

		public int satisfiedCount(Set<String> ownedLowerCase,
			Set<String> startedQuests, Set<String> completedQuests, RouteSelection route)
		{
			int count = 0;
			for (Requirement requirement : requirements)
			{
				if (requirement.isSatisfied(ownedLowerCase, startedQuests,
					completedQuests, route))
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

	public enum QuestStateRequirement
	{
		IN_PROGRESS,
		FINISHED
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
		public final List<String> displayQuests;
		public final List<String> lowerQuests;
		public final QuestStateRequirement questState;

		Requirement(String label, List<String> cards)
		{
			this(label, cards, null, Logic.ANY, "card",
				Collections.emptyList(), null, null, false,
				Collections.emptyList(), QuestStateRequirement.FINISHED);
		}

		Requirement(String label, List<String> cards, Integer quantity,
			Logic logic, String type, List<Requirement> children,
			String selector, String selectorValue, boolean displayCardsOnly,
			List<String> quests, QuestStateRequirement questState)
		{
			this.label = label;
			List<String> display = new ArrayList<>();
			List<String> lower = new ArrayList<>();
			for (String card : cards == null ? Collections.<String>emptyList() : cards)
			{
				if (card != null && !card.trim().isEmpty())
				{
					display.add(card.trim());
					lower.add(card.trim().toLowerCase(Locale.ROOT));
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
			List<String> displayedQuests = new ArrayList<>();
			List<String> normalizedQuests = new ArrayList<>();
			for (String quest : quests == null ? Collections.<String>emptyList() : quests)
			{
				if (quest != null && !quest.trim().isEmpty())
				{
					displayedQuests.add(quest.trim());
					normalizedQuests.add(normalizeQuestName(quest));
				}
			}
			this.displayQuests = Collections.unmodifiableList(displayedQuests);
			this.lowerQuests = Collections.unmodifiableList(normalizedQuests);
			this.questState = questState == null
				? QuestStateRequirement.FINISHED : questState;
		}

		public boolean isSatisfied(Set<String> ownedLowerCase)
		{
			return isSatisfied(ownedLowerCase, RouteSelection.UNKNOWN);
		}

		public boolean isSatisfied(Set<String> ownedLowerCase, RouteSelection route)
		{
			return isSatisfied(ownedLowerCase, Collections.emptySet(),
				Collections.emptySet(), route);
		}

		public boolean isSatisfied(Set<String> ownedLowerCase,
			Set<String> startedQuests, Set<String> completedQuests, RouteSelection route)
		{
			Requirement selected = selectedChild(route);
			if (selected != null)
			{
				return selected.isSatisfied(ownedLowerCase, startedQuests,
					completedQuests, route);
			}
			if (!children.isEmpty())
			{
				if (logic == Logic.ANY)
				{
					for (Requirement child : children)
					{
						if (child.isSatisfied(ownedLowerCase, startedQuests,
							completedQuests, route))
						{
							return true;
						}
					}
					return false;
				}
				for (Requirement child : children)
				{
					if (!child.isSatisfied(ownedLowerCase, startedQuests,
						completedQuests, route))
					{
						return false;
					}
				}
				return true;
			}
			if ("quest".equals(type))
			{
				for (String quest : lowerQuests)
				{
					if (completedQuests.contains(quest)
						|| (questState == QuestStateRequirement.IN_PROGRESS
							&& startedQuests.contains(quest)))
					{
						return true;
					}
				}
				return lowerQuests.isEmpty();
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

		public int displayTotal(RouteSelection route)
		{
			Requirement selected = selectedChild(route);
			if (selected != null)
			{
				return 1;
			}
			return children.isEmpty() || logic == Logic.ANY ? 1 : children.size();
		}

		public int displaySatisfied(Set<String> ownedLowerCase, RouteSelection route)
		{
			Requirement selected = selectedChild(route);
			if (selected != null || children.isEmpty() || logic == Logic.ANY)
			{
				return isSatisfied(ownedLowerCase, route) ? 1 : 0;
			}
			int count = 0;
			for (Requirement child : children)
			{
				if (child.isSatisfied(ownedLowerCase, route))
				{
					count++;
				}
			}
			return count;
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

	private static class PrerequisiteSnapshot
	{
		List<QuestPrerequisitesDto> quests;
	}

	private static class QuestPrerequisitesDto
	{
		String name;
		List<PrerequisiteDto> prerequisites;
	}

	private static class PrerequisiteDto
	{
		String name;
		String state;
	}

	private static class QuestDto
	{
		String name;
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
