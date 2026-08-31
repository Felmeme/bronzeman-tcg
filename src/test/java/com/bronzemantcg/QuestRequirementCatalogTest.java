package com.bronzemantcg;

import com.bronzemantcg.catalog.QuestCatalog;
import com.bronzemantcg.catalog.QuestRequirementCatalog;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class QuestRequirementCatalogTest
{
	private final QuestRequirementCatalog catalog = new QuestRequirementCatalog(new Gson());
	private final QuestCatalog questCatalog = new QuestCatalog(new Gson());

	private static int[] levels(Skill skill, int level)
	{
		int[] values = new int[Skill.values().length];
		values[skill.ordinal()] = level;
		return values;
	}

	@Test
	public void everyEntryNamesAKnownQuest()
	{
		Set<String> known = new HashSet<>();
		for (QuestCatalog.QuestEntry entry : questCatalog.getQuests())
		{
			known.add(entry.name.toLowerCase(Locale.ROOT));
		}
		for (QuestCatalog.QuestEntry entry : questCatalog.getMiniquests())
		{
			known.add(entry.name.toLowerCase(Locale.ROOT));
		}
		int matched = 0;
		for (String name : known)
		{
			if (catalog.get(name) != null)
			{
				matched++;
			}
		}
		// Every requirement entry must correspond to a quest we actually display,
		// otherwise the filter silently gates nothing.
		assertEquals(catalog.size(), matched);
	}

	@Test
	public void skillPrerequisiteAndQuestPointGatesAreLoaded()
	{
		QuestRequirementCatalog.Requirements mm2 = catalog.get("Monkey Madness II");
		assertNotNull(mm2);
		assertTrue(mm2.skills.stream()
			.anyMatch(s -> s.skill == Skill.SLAYER && s.level == 69));
		assertTrue(mm2.quests.stream()
			.anyMatch(q -> q.quest == Quest.RECIPE_FOR_DISASTER__KING_AWOWOGEI));

		QuestRequirementCatalog.Requirements dragonSlayer = catalog.get("Dragon Slayer I");
		assertNotNull(dragonSlayer);
		assertEquals(32, dragonSlayer.questPoints);
	}

	@Test
	public void lookupIsCaseInsensitiveAndAbsentQuestsAreUnrestricted()
	{
		assertNotNull(catalog.get("dragon slayer i"));
		assertNull(catalog.get("Cook's Assistant"));
		assertNull(catalog.get("no such quest"));
	}

	@Test
	public void unknownLevelsLeaveTheFilterInert()
	{
		QuestRequirementCatalog.Requirements mm2 = catalog.get("Monkey Madness II");
		// Logged out: levels are null, so nothing may be hidden.
		assertTrue(mm2.isMet(null, 0, Collections.emptySet()));
	}

	@Test
	public void strictLevelsAndPrerequisitesAreEnforced()
	{
		QuestRequirementCatalog.Requirements enterTheAbyss = catalog.get("Enter the Abyss");
		assertNotNull(enterTheAbyss);
		int[] noLevels = new int[Skill.values().length];
		assertFalse(enterTheAbyss.isMet(noLevels, 0, Collections.emptySet()));
		assertTrue(enterTheAbyss.isMet(noLevels, 0,
			Collections.singleton(Quest.RUNE_MYSTERIES.getName().toLowerCase(Locale.ROOT))));

		QuestRequirementCatalog.Requirements dragonSlayer = catalog.get("Dragon Slayer I");
		assertFalse(dragonSlayer.isMet(noLevels, 31, Collections.emptySet()));
		assertTrue(dragonSlayer.isMet(noLevels, 32, Collections.emptySet()));
	}

	@Test
	public void boostsAreNotHonoured()
	{
		QuestRequirementCatalog.Requirements coldWar = catalog.get("Cold War");
		assertNotNull(coldWar);
		QuestRequirementCatalog.SkillRequirement first = coldWar.skills.get(0);
		assertFalse(coldWar.isMet(levels(first.skill, first.level - 1), 0,
			Collections.emptySet()));
	}

	@Test
	public void displayOnlyNotesNeverAffectTheFilter()
	{
		QuestRequirementCatalog.Requirements boneVoyage = catalog.get("Bone Voyage");
		assertNotNull(boneVoyage);
		assertFalse(boneVoyage.other.isEmpty());
		// The Kudos gate is display-only: owning the prerequisite quest is enough.
		assertTrue(boneVoyage.isMet(new int[Skill.values().length], 0,
			Collections.singleton(Quest.THE_DIG_SITE.getName().toLowerCase(Locale.ROOT))));
	}
}
