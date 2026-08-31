package com.bronzemantcg.panel;

import com.bronzemantcg.catalog.QuestRequirementCatalog;
import com.google.gson.Gson;
import java.util.Collections;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QuestFilterModelTest
{
	private final QuestRequirementCatalog catalog =
		new QuestRequirementCatalog(new Gson());

	@Test
	public void filtersEachRequirementTypeIndependently()
	{
		int[] levels = new int[Skill.values().length];

		assertFalse(visible(catalog.get("Dragon Slayer I"), levels, 0,
			Collections.emptySet(),
			true, false, false, false));
		assertFalse(visible(catalog.get("Cold War"), levels, 0,
			Collections.emptySet(),
			false, true, false, false));
		assertFalse(visible(catalog.get("Enter the Abyss"), levels, 0,
			Collections.emptySet(),
			false, false, true, false));
		assertFalse(visible(null, levels, 0, Collections.emptySet(),
			false, false, false, true));
	}

	@Test
	public void uncheckedRequirementsDoNotHideAQuest()
	{
		QuestRequirementCatalog.Requirements requirements =
			catalog.get("Monkey Madness II");
		assertTrue(visible(requirements, new int[Skill.values().length], 0,
			Collections.emptySet(), false, false, false, false));
	}

	@Test
	public void loggedOutRequirementStateLeavesNonCardFiltersInert()
	{
		QuestRequirementCatalog.Requirements requirements =
			catalog.get("Monkey Madness II");
		assertTrue(visible(requirements, null, 0, Collections.emptySet(),
			true, true, true, false));
	}

	@Test
	public void completedAndCardFiltersRemainIndependent()
	{
		assertFalse(QuestFilterModel.isVisible(true, true, true, false,
			null, null, 0, Collections.emptySet(), false, false, false));
		assertFalse(QuestFilterModel.isVisible(false, false, false, true,
			null, null, 0, Collections.emptySet(), false, false, false));
	}

	private static boolean visible(QuestRequirementCatalog.Requirements requirements,
		int[] levels, int questPoints, java.util.Set<String> completedQuests,
		boolean questPointFilter, boolean skillFilter,
		boolean prerequisiteFilter, boolean cardFilter)
	{
		return QuestFilterModel.isVisible(false, false, !cardFilter, cardFilter,
			requirements, levels, questPoints, completedQuests,
			questPointFilter, skillFilter, prerequisiteFilter);
	}
}
