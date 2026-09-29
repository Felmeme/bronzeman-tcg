package com.bronzemantcg.panel;

import com.bronzemantcg.catalog.QuestCatalog;
import com.google.gson.Gson;
import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class QuestListPresentationTest
{
	private final QuestCatalog source = new QuestCatalog(new Gson());

	@Test
	public void removesNpcAndEnemyRequirementsWithoutMutatingSource()
	{
		List<QuestCatalog.QuestEntry> filtered =
			QuestListPresentation.hideNpcRequirements(source.getQuests());

		assertNoNpcRequirements(filtered);
		QuestCatalog.QuestEntry original = quest(source.getQuests(), "Defender of Varrock");
		QuestCatalog.QuestEntry projected = quest(filtered, "Defender of Varrock");
		assertTrue(projected.requirements.size() < original.requirements.size());
		assertNotNull(requirement(original, "Armoured zombie"));
		assertFalse(hasRequirement(projected, "Armoured zombie"));
	}

	@Test
	public void routedNpcOnlyPathBecomesSatisfiedWhileItemPathRemainsRequired()
	{
		QuestCatalog.Requirement original = requirement(
			quest(source.getQuests(), "Shield of Arrav"),
			"Your Shield of Arrav gang route");
		QuestCatalog.Requirement filtered = requirement(quest(
			QuestListPresentation.hideNpcRequirements(source.getQuests()),
			"Shield of Arrav"), "Your Shield of Arrav gang route");

		assertFalse(original.isSatisfied(Set.of(), QuestCatalog.RouteSelection.BLACK_ARM));
		assertTrue(filtered.isSatisfied(Set.of(), QuestCatalog.RouteSelection.BLACK_ARM));
		assertFalse(filtered.isSatisfied(Set.of(), QuestCatalog.RouteSelection.PHOENIX));
		assertTrue(filtered.isSatisfied(Set.of("coins"), QuestCatalog.RouteSelection.PHOENIX));
		assertFalse(hasRequirement(filtered, "Weaponsmaster"));
		assertFalse(hasRequirement(filtered, "Charlie the Tramp"));
		assertFalse(hasRequirement(filtered, "Jonny the Beard"));
		assertTrue(hasRequirement(filtered, "Coins"));
	}

	@Test
	public void appliesToMiniquestsAndDropsEmptyNpcSections()
	{
		List<QuestCatalog.QuestEntry> filtered =
			QuestListPresentation.hideNpcRequirements(source.getMiniquests());

		assertNoNpcRequirements(filtered);
		QuestCatalog.QuestEntry mageArena = quest(filtered, "Mage Arena I");
		assertFalse(hasRequirement(mageArena, "Kolodion"));
		assertFalse(mageArena.sections.stream().anyMatch(section ->
			"NPCs".equalsIgnoreCase(section.label) || "Enemies".equalsIgnoreCase(section.label)));
	}

	private static void assertNoNpcRequirements(List<QuestCatalog.QuestEntry> entries)
	{
		for (QuestCatalog.QuestEntry entry : entries)
		{
			for (QuestCatalog.Requirement requirement : entry.requirements)
			{
				assertNoNpcRequirements(requirement);
			}
		}
	}

	private static void assertNoNpcRequirements(QuestCatalog.Requirement requirement)
	{
		assertFalse(requirement.type, QuestListPresentation.isNpcRequirement(requirement));
		for (QuestCatalog.Requirement child : requirement.children)
		{
			assertNoNpcRequirements(child);
		}
	}

	private static QuestCatalog.QuestEntry quest(
		List<QuestCatalog.QuestEntry> entries, String name)
	{
		return entries.stream().filter(entry -> name.equals(entry.name))
			.findFirst().orElseThrow(() -> new AssertionError("Missing quest " + name));
	}

	private static boolean hasRequirement(QuestCatalog.QuestEntry entry, String label)
	{
		for (QuestCatalog.Requirement requirement : entry.requirements)
		{
			if (hasRequirement(requirement, label))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean hasRequirement(QuestCatalog.Requirement requirement, String label)
	{
		if (label.equals(requirement.label))
		{
			return true;
		}
		for (QuestCatalog.Requirement child : requirement.children)
		{
			if (hasRequirement(child, label))
			{
				return true;
			}
		}
		return false;
	}

	private static QuestCatalog.Requirement requirement(
		QuestCatalog.QuestEntry entry, String label)
	{
		for (QuestCatalog.Requirement requirement : entry.requirements)
		{
			QuestCatalog.Requirement match = requirement(requirement, label);
			if (match != null)
			{
				return match;
			}
		}
		throw new AssertionError("Missing requirement " + label + " in " + entry.name);
	}

	private static QuestCatalog.Requirement requirement(
		QuestCatalog.Requirement requirement, String label)
	{
		if (label.equals(requirement.label))
		{
			return requirement;
		}
		for (QuestCatalog.Requirement child : requirement.children)
		{
			QuestCatalog.Requirement match = requirement(child, label);
			if (match != null)
			{
				return match;
			}
		}
		return null;
	}
}
