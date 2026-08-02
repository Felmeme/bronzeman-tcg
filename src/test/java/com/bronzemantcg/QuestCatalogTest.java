package com.bronzemantcg;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.Quest;
import org.junit.Assert;
import org.junit.Test;

public class QuestCatalogTest
{
	@Test
	public void indexesQuestRelationshipsByCard()
	{
		QuestCatalog catalog = new QuestCatalog(new Gson());

		List<String> potionQuests = catalog.getQuestsForCard("Defence potion");
		Assert.assertTrue(potionQuests.contains("A Kingdom Divided"));
		Assert.assertEquals(potionQuests, catalog.getQuestsForCard("defence potion"));
		Assert.assertTrue(catalog.getQuestsForCard("Definitely not a card").isEmpty());
	}

	@Test
	public void loadsFullQuestsAndMiniquestsWithoutCountingLeadingTheForSorting()
	{
		QuestCatalog catalog = new QuestCatalog(new Gson());
		Assert.assertEquals(180, catalog.getQuests().size());
		Assert.assertEquals(23, catalog.getMiniquests().size());
		assertSortedIgnoringThe(catalog.getQuests());
		assertSortedIgnoringThe(catalog.getMiniquests());
	}

	@Test
	public void preservesNestedSpellQuantitiesAndRecipeForDisasterSections()
	{
		QuestCatalog catalog = new QuestCatalog(new Gson());
		QuestCatalog.QuestEntry dragonSlayer = quest(catalog, "Dragon Slayer II");
		QuestCatalog.Requirement spells = requirement(dragonSlayer,
			"Fire Wave/Surge Runes");
		Assert.assertEquals(QuestCatalog.Logic.ANY, spells.logic);
		Assert.assertTrue(spells.displayCardsOnly);
		Assert.assertEquals(2, spells.children.size());
		Assert.assertEquals(21, child(spells.children.get(0), "Fire rune").quantity);
		Assert.assertEquals(30, child(spells.children.get(1), "Fire rune").quantity);
		Assert.assertEquals("Items", section(dragonSlayer, "Items").label);
		Assert.assertNotNull(requirement(dragonSlayer, "Chisel"));
		assertNoQuestHelperSections(catalog.getQuests());
		assertNoQuestHelperSections(catalog.getMiniquests());

		Assert.assertEquals(11, quest(catalog, "Recipe for Disaster").sections.size());
	}

	@Test
	public void requiresQuestPrerequisitesInAdditionToCards()
	{
		QuestCatalog catalog = new QuestCatalog(new Gson());
		QuestCatalog.QuestEntry natureSpirit = quest(catalog, "Nature Spirit");
		Set<String> owned = allCards(natureSpirit);

		Assert.assertEquals(natureSpirit.requirements.size() - 2,
			natureSpirit.satisfiedCount(owned, Set.of(), Set.of(),
				QuestCatalog.RouteSelection.UNKNOWN));
		Assert.assertEquals(natureSpirit.requirements.size() - 1,
			natureSpirit.satisfiedCount(owned, Set.of("the restless ghost"),
				Set.of("the restless ghost"), QuestCatalog.RouteSelection.UNKNOWN));
		Assert.assertEquals(natureSpirit.requirements.size(),
			natureSpirit.satisfiedCount(owned,
				Set.of("the restless ghost", "priest in peril"),
				Set.of("the restless ghost", "priest in peril"),
				QuestCatalog.RouteSelection.UNKNOWN));

		owned.add("the restless ghost");
		owned.add("priest in peril");
		Assert.assertEquals(natureSpirit.requirements.size() - 2,
			natureSpirit.satisfiedCount(owned, Set.of(), Set.of(),
				QuestCatalog.RouteSelection.UNKNOWN));
	}

	@Test
	public void startedPrerequisiteAcceptsInProgressOrFinishedQuest()
	{
		QuestCatalog.QuestEntry makingHistory = quest(
			new QuestCatalog(new Gson()), "Making History");
		Set<String> owned = allCards(makingHistory);

		QuestCatalog.Requirement restlessGhost = requirement(
			makingHistory, "Start The Restless Ghost");
		Assert.assertEquals(QuestCatalog.QuestStateRequirement.IN_PROGRESS,
			restlessGhost.questState);
		Assert.assertTrue(restlessGhost.isSatisfied(owned, Set.of("the restless ghost"),
			Set.of(), QuestCatalog.RouteSelection.UNKNOWN));
		Assert.assertTrue(restlessGhost.isSatisfied(owned, Set.of(),
			Set.of("the restless ghost"),
			QuestCatalog.RouteSelection.UNKNOWN));
		Assert.assertEquals(makingHistory.requirements.size(),
			makingHistory.satisfiedCount(owned, Set.of("the restless ghost"),
				Set.of("priest in peril"), QuestCatalog.RouteSelection.UNKNOWN));
		Assert.assertEquals(makingHistory.requirements.size(),
			makingHistory.satisfiedCount(owned, Set.of(),
				Set.of("priest in peril", "the restless ghost"),
				QuestCatalog.RouteSelection.UNKNOWN));
	}

	@Test
	public void bundledPrerequisitesResolveWithoutDuplicatesOrSelfReferences()
	{
		QuestCatalog catalog = new QuestCatalog(new Gson());
		List<QuestCatalog.QuestEntry> entries = new ArrayList<>();
		entries.addAll(catalog.getQuests());
		entries.addAll(catalog.getMiniquests());
		Set<String> questNames = new HashSet<>();
		for (QuestCatalog.QuestEntry entry : entries)
		{
			questNames.add(QuestCatalog.normalizeQuestName(entry.name));
		}
		Set<String> runeLiteQuestNames = new HashSet<>();
		for (Quest quest : Quest.values())
		{
			runeLiteQuestNames.add(QuestCatalog.normalizeQuestName(quest.getName()));
		}

		int prerequisiteEntries = 0;
		int prerequisiteCount = 0;
		for (QuestCatalog.QuestEntry entry : entries)
		{
			Set<String> seen = new HashSet<>();
			boolean hasPrerequisite = false;
			for (QuestCatalog.Requirement requirement : entry.requirements)
			{
				if (!"quest".equals(requirement.type))
				{
					continue;
				}
				hasPrerequisite = true;
				prerequisiteCount++;
				Assert.assertEquals(1, requirement.lowerQuests.size());
				String prerequisite = requirement.lowerQuests.get(0);
				Assert.assertTrue("Unknown prerequisite " + prerequisite,
					questNames.contains(prerequisite));
				Assert.assertTrue("Prerequisite has no RuneLite quest state: " + prerequisite,
					runeLiteQuestNames.contains(prerequisite));
				Assert.assertNotEquals(QuestCatalog.normalizeQuestName(entry.name), prerequisite);
				Assert.assertTrue("Duplicate prerequisite in " + entry.name,
					seen.add(prerequisite + "\0" + requirement.questState));
			}
			if (hasPrerequisite)
			{
				prerequisiteEntries++;
			}
		}
		Assert.assertEquals(116, prerequisiteEntries);
		Assert.assertEquals(258, prerequisiteCount);
	}

	@Test
	public void appliesTheRecordedShieldGangToSplitRoutes()
	{
		QuestCatalog catalog = new QuestCatalog(new Gson());
		QuestCatalog.Requirement shieldRoute = requirement(
			quest(catalog, "Shield of Arrav"), "Your Shield of Arrav gang route");
		Set<String> phoenixCards = Set.of("coins", "jonny the beard");
		Assert.assertTrue(shieldRoute.isSatisfied(phoenixCards,
			QuestCatalog.RouteSelection.UNKNOWN));
		Assert.assertTrue(shieldRoute.isSatisfied(phoenixCards,
			QuestCatalog.RouteSelection.PHOENIX));
		Assert.assertFalse(shieldRoute.isSatisfied(phoenixCards,
			QuestCatalog.RouteSelection.BLACK_ARM));

		QuestCatalog.Requirement heroesRoute = requirement(
			quest(catalog, "Heroes' Quest"), "Your Shield of Arrav gang route");
		Assert.assertTrue(heroesRoute.isSatisfied(Set.of("grip"),
			QuestCatalog.RouteSelection.PHOENIX));
		Assert.assertFalse(heroesRoute.isSatisfied(Set.of("grip"),
			QuestCatalog.RouteSelection.BLACK_ARM));
	}

	@Test
	public void retainsLocalQuestDataAndDoesNotIndexOldNotesAsCards()
	{
		QuestCatalog catalog = new QuestCatalog(new Gson());
		QuestCatalog.QuestEntry bloodMoon = quest(catalog, "The Blood Moon Rises");
		Assert.assertNotNull(requirement(bloodMoon, "Tinderbox"));
		Assert.assertTrue(catalog.getQuestsForCard(
			"Don't have to attack to complete quest").isEmpty());
		Assert.assertTrue(catalog.getQuestsForCard("Lyre Monsters - Check wiki").isEmpty());
	}

	private static QuestCatalog.QuestEntry quest(QuestCatalog catalog, String name)
	{
		for (QuestCatalog.QuestEntry entry : catalog.getQuests())
		{
			if (entry.name.equals(name))
			{
				return entry;
			}
		}
		throw new AssertionError("Missing quest " + name);
	}

	private static QuestCatalog.Requirement requirement(
		QuestCatalog.QuestEntry entry, String label)
	{
		for (QuestCatalog.Requirement requirement : entry.requirements)
		{
			QuestCatalog.Requirement match = find(requirement, label);
			if (match != null)
			{
				return match;
			}
		}
		throw new AssertionError("Missing requirement " + label + " in " + entry.name);
	}

	private static QuestCatalog.Requirement child(
		QuestCatalog.Requirement parent, String label)
	{
		for (QuestCatalog.Requirement child : parent.children)
		{
			if (child.label.equals(label))
			{
				return child;
			}
		}
		throw new AssertionError("Missing child " + label);
	}

	private static QuestCatalog.Section section(
		QuestCatalog.QuestEntry entry, String label)
	{
		for (QuestCatalog.Section section : entry.sections)
		{
			if (section.label.equals(label))
			{
				return section;
			}
		}
		throw new AssertionError("Missing section " + label + " in " + entry.name);
	}

	private static Set<String> allCards(QuestCatalog.QuestEntry entry)
	{
		Set<String> cards = new HashSet<>();
		for (QuestCatalog.Requirement requirement : entry.requirements)
		{
			collectCards(requirement, cards);
		}
		return cards;
	}

	private static void collectCards(
		QuestCatalog.Requirement requirement, Set<String> cards)
	{
		cards.addAll(requirement.lowerCards);
		for (QuestCatalog.Requirement child : requirement.children)
		{
			collectCards(child, cards);
		}
	}

	private static QuestCatalog.Requirement find(
		QuestCatalog.Requirement requirement, String label)
	{
		if (requirement.label.equals(label))
		{
			return requirement;
		}
		for (QuestCatalog.Requirement child : requirement.children)
		{
			QuestCatalog.Requirement match = find(child, label);
			if (match != null)
			{
				return match;
			}
		}
		return null;
	}

	private static void assertSortedIgnoringThe(List<QuestCatalog.QuestEntry> entries)
	{
		String previous = "";
		for (QuestCatalog.QuestEntry entry : entries)
		{
			String current = entry.name.regionMatches(true, 0, "The ", 0, 4)
				? entry.name.substring(4) : entry.name;
			current = current.toLowerCase(Locale.ROOT);
			Assert.assertTrue(previous.compareTo(current) <= 0);
			previous = current;
		}
	}

	private static void assertNoQuestHelperSections(
		List<QuestCatalog.QuestEntry> entries)
	{
		for (QuestCatalog.QuestEntry entry : entries)
		{
			for (QuestCatalog.Section section : entry.sections)
			{
				Assert.assertNotEquals("Quest Helper details", section.label);
			}
		}
	}
}
