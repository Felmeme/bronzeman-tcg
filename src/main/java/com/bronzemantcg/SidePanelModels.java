package com.bronzemantcg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Immutable view models passed from background preparation to Swing rendering. */
final class SidePanelModels
{
	private SidePanelModels() {}

	static int satisfiedRequirements(List<QuestCatalog.Requirement> requirements,
		Set<String> owned)
	{
		int count = 0;
		for (QuestCatalog.Requirement requirement : requirements)
		{
			if (requirement.isSatisfied(owned))
			{
				count++;
			}
		}
		return count;
	}

	static void mergeSlayerRequirement(
		Map<String, QuestCatalog.Requirement> requirements,
		QuestCatalog.Requirement incoming)
	{
		QuestCatalog.Requirement existing = requirements.get(incoming.label);
		if (existing == null)
		{
			requirements.put(incoming.label, incoming);
			return;
		}
		Map<String, String> cards = new LinkedHashMap<>();
		for (String card : existing.displayCards)
		{
			cards.put(card.toLowerCase(Locale.ROOT), card);
		}
		for (String card : incoming.displayCards)
		{
			cards.putIfAbsent(card.toLowerCase(Locale.ROOT), card);
		}
		requirements.put(existing.label,
			new QuestCatalog.Requirement(existing.label, new ArrayList<>(cards.values())));
	}

	static final class PreparedData
	{
		final List<QuestCatalog.QuestEntry> quests;
		final List<QuestCatalog.QuestEntry> contents;
		final List<QuestCatalog.QuestEntry> areas;
		final List<SlayerMasterEntry> slayer;
		final List<QuestCatalog.Requirement> allSuperiors;
		final List<QuestCatalog.QuestEntry> rumours;
		final List<SearchEntry> searchEntries;

		PreparedData(List<QuestCatalog.QuestEntry> quests,
			List<QuestCatalog.QuestEntry> contents, List<QuestCatalog.QuestEntry> areas,
			List<SlayerMasterEntry> slayer,
			List<QuestCatalog.Requirement> allSuperiors,
			List<QuestCatalog.QuestEntry> rumours, List<SearchEntry> searchEntries)
		{
			this.quests = quests;
			this.contents = contents;
			this.areas = areas;
			this.slayer = slayer;
			this.allSuperiors = allSuperiors;
			this.rumours = rumours;
			this.searchEntries = Collections.unmodifiableList(searchEntries);
		}
	}

	static final class SlayerMasterEntry
	{
		final String name;
		final List<SlayerTaskEntry> tasks;
		final List<QuestCatalog.Requirement> superiors;

		SlayerMasterEntry(String name, List<SlayerTaskEntry> tasks,
			List<QuestCatalog.Requirement> superiors)
		{
			this.name = name;
			this.tasks = Collections.unmodifiableList(new ArrayList<>(tasks));
			this.superiors = Collections.unmodifiableList(new ArrayList<>(superiors));
		}

		int satisfiedCount(Set<String> owned, boolean includeSuperiors)
		{
			int count = 0;
			for (SlayerTaskEntry task : tasks)
			{
				count += satisfiedRequirements(task.requirements, owned);
			}
			return count + (includeSuperiors
				? satisfiedRequirements(superiors, owned) : 0);
		}

		int requirementCount(boolean includeSuperiors)
		{
			int count = 0;
			for (SlayerTaskEntry task : tasks)
			{
				count += task.requirements.size();
			}
			return count + (includeSuperiors ? superiors.size() : 0);
		}
	}

	static final class SlayerTaskEntry
	{
		final String label;
		final boolean locationSpecific;
		final List<QuestCatalog.Requirement> requirements;

		SlayerTaskEntry(String label, boolean locationSpecific,
			List<QuestCatalog.Requirement> requirements)
		{
			this.label = label;
			this.locationSpecific = locationSpecific;
			this.requirements = Collections.unmodifiableList(new ArrayList<>(requirements));
		}
	}

	static final class SlayerTaskBuilder
	{
		final String label;
		final boolean locationSpecific;
		final Map<String, QuestCatalog.Requirement> requirements =
			new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

		SlayerTaskBuilder(String label, boolean locationSpecific)
		{
			this.label = label;
			this.locationSpecific = locationSpecific;
		}

		void add(QuestCatalog.Requirement requirement)
		{
			mergeSlayerRequirement(requirements, requirement);
		}

		SlayerTaskEntry build()
		{
			return new SlayerTaskEntry(label, locationSpecific,
				new ArrayList<>(requirements.values()));
		}
	}

	static final class PanelSnapshot
	{
		final PreparedData data;
		final Set<String> owned;
		final Set<String> shared;
		final List<RecentUnlocksTracker.Unlock> recentUnlocks;
		final List<RecentUnlocksTracker.Unlock> sharedRecentUnlocks;
		final boolean includeSlayerSuperiors;
		final Set<String> completedQuests;
		final int unlockedMonsters;
		final int unlockedItems;

		PanelSnapshot(PreparedData data, Set<String> owned, Set<String> shared,
			List<RecentUnlocksTracker.Unlock> recentUnlocks,
			List<RecentUnlocksTracker.Unlock> sharedRecentUnlocks,
			boolean includeSlayerSuperiors, Set<String> completedQuests,
			int unlockedMonsters, int unlockedItems)
		{
			this.data = data;
			this.owned = owned;
			this.shared = shared;
			this.recentUnlocks = recentUnlocks;
			this.sharedRecentUnlocks = sharedRecentUnlocks;
			this.includeSlayerSuperiors = includeSlayerSuperiors;
			this.completedQuests = completedQuests;
			this.unlockedMonsters = unlockedMonsters;
			this.unlockedItems = unlockedItems;
		}
	}

	static final class SharedCategory
	{
		final String name;
		final List<String> items;
		final Map<String, List<String>> subcategories;

		SharedCategory(String name, List<String> items,
			Map<String, List<String>> subcategories)
		{
			this.name = name;
			this.items = items;
			this.subcategories = subcategories;
		}

		int size()
		{
			int count = items.size();
			for (List<String> values : subcategories.values())
			{
				count += values.size();
			}
			return count;
		}
	}

	static final class SearchEntry
	{
		final String searchName;
		final String displayName;
		final Set<String> cards;

		SearchEntry(String searchName, String displayName, Set<String> cards)
		{
			this.searchName = searchName;
			this.displayName = displayName;
			this.cards = cards;
		}
	}
}
