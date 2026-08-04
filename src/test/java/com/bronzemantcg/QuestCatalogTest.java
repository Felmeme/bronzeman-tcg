package com.bronzemantcg;

import com.google.gson.Gson;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
		// 189, not 180: Recipe for Disaster is carried as its ten subquests, not one entry.
		Assert.assertEquals(189, catalog.getQuests().size());
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
		Assert.assertEquals("Fire Wave", spells.children.get(0).label);
		Assert.assertEquals("Fire Surge", spells.children.get(1).label);
		Assert.assertEquals(21, child(spells.children.get(0), "Fire rune").quantity);
		Assert.assertEquals(30, child(spells.children.get(1), "Fire rune").quantity);
		// The catalytic rune is what separates the two tiers, so pin it: sharing a Fire
		// rune count would not prove the branches are actually distinct spells.
		Assert.assertEquals(3, child(spells.children.get(0), "Blood rune").quantity);
		Assert.assertEquals(3, child(spells.children.get(1), "Wrath rune").quantity);
		Assert.assertEquals("Items", dragonSlayer.sections.get(0).label);
		Assert.assertNotNull(requirement(dragonSlayer, "Chisel"));
		assertNoQuestHelperSections(catalog.getQuests());
		assertNoQuestHelperSections(catalog.getMiniquests());

		// Recipe for Disaster is split into its ten subquests rather than carried as one
		// entry with ten sections. The display prefix is the owner's call, so accept
		// either the short or the full RuneLite form here.
		Assert.assertTrue(catalog.getQuests().stream()
			.noneMatch(entry -> "Recipe for Disaster".equals(entry.name)));
		Assert.assertEquals(10, catalog.getQuests().stream()
			.filter(entry -> entry.name.startsWith("RFD - ")
				|| entry.name.startsWith("Recipe for Disaster - "))
			.count());
		// A shortened display name must still resolve to RuneLite's own quest name, or
		// Hide completed and the quest-NPC index silently stop matching these entries.
		for (QuestCatalog.QuestEntry entry : catalog.getQuests())
		{
			if (entry.name.startsWith("RFD - "))
			{
				Assert.assertTrue(entry.name + " -> " + entry.questName,
					entry.questName.startsWith("Recipe for Disaster - "));
			}
		}
		QuestCatalog.QuestEntry awowogei = quest(catalog, "RFD - King Awowogei");
		Assert.assertEquals("Recipe for Disaster - King Awowogei", awowogei.questName);
		Assert.assertEquals("Items", awowogei.sections.get(0).label);
		Assert.assertEquals("Enemies", awowogei.sections.get(1).label);
		Assert.assertNotNull(requirement(awowogei, "Gorilla greegree"));
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
