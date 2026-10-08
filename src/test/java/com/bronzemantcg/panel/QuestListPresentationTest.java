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
	public void npcAndEnemyFiltersAreIndependentWithoutMutatingSource()
	{
		List<QuestCatalog.QuestEntry> unchanged = QuestListPresentation.hideRequirements(
			source.getQuests(), false, false);
		List<QuestCatalog.QuestEntry> npcHidden = QuestListPresentation.hideRequirements(
			source.getQuests(), true, false);
		List<QuestCatalog.QuestEntry> enemiesHidden = QuestListPresentation.hideRequirements(
			source.getQuests(), false, true);

		assertTrue(hasRequirement(quest(unchanged, "Shield of Arrav"), "Charlie the Tramp"));
		assertTrue(hasRequirement(quest(unchanged, "Shield of Arrav"), "Weaponsmaster"));
		assertFalse(hasRequirement(quest(npcHidden, "Shield of Arrav"), "Charlie the Tramp"));
		assertTrue(hasRequirement(quest(npcHidden, "Shield of Arrav"), "Weaponsmaster"));
		assertTrue(hasRequirement(quest(enemiesHidden, "Shield of Arrav"), "Charlie the Tramp"));
		assertFalse(hasRequirement(quest(enemiesHidden, "Shield of Arrav"), "Weaponsmaster"));
		assertNotNull(requirement(quest(source.getQuests(), "Defender of Varrock"),
			"Armoured zombie"));
	}

	@Test
	public void routedNpcOnlyPathBecomesSatisfiedWhileItemPathRemainsRequired()
	{
		QuestCatalog.Requirement original = requirement(
			quest(source.getQuests(), "Shield of Arrav"),
			"Your Shield of Arrav gang route");
		QuestCatalog.Requirement filtered = requirement(quest(
			QuestListPresentation.hideRequirements(source.getQuests(), true, true),
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
	public void appliesIndependentFiltersToMiniquestsAndDropsEmptySections()
	{
		QuestCatalog.QuestEntry npcHidden = quest(QuestListPresentation.hideRequirements(
			source.getMiniquests(), true, false), "Mage Arena II");
		QuestCatalog.QuestEntry enemiesHidden = quest(QuestListPresentation.hideRequirements(
			source.getMiniquests(), false, true), "Mage Arena II");
		QuestCatalog.QuestEntry bothHidden = quest(QuestListPresentation.hideRequirements(
			source.getMiniquests(), true, true), "Mage Arena II");

		assertFalse(npcHidden.sections.stream().anyMatch(section ->
			"NPCs".equalsIgnoreCase(section.label)));
		assertTrue(npcHidden.sections.stream().anyMatch(section ->
			"Enemies".equalsIgnoreCase(section.label)));
		assertTrue(enemiesHidden.sections.stream().anyMatch(section ->
			"NPCs".equalsIgnoreCase(section.label)));
		assertFalse(enemiesHidden.sections.stream().anyMatch(section ->
			"Enemies".equalsIgnoreCase(section.label)));
		assertFalse(hasRequirement(bothHidden, "Kolodion"));
		assertFalse(bothHidden.sections.stream().anyMatch(section ->
			"NPCs".equalsIgnoreCase(section.label)
				|| "Enemies".equalsIgnoreCase(section.label)));
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
