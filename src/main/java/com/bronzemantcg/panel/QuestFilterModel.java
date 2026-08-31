package com.bronzemantcg.panel;

import com.bronzemantcg.catalog.QuestRequirementCatalog;
import java.util.Locale;
import java.util.Set;

/** Pure visibility policy for the independent quest-panel filters. */
final class QuestFilterModel
{
	private QuestFilterModel()
	{
	}

	static boolean isVisible(boolean completed, boolean hideCompleted,
		boolean cardsMet, boolean requireCards,
		QuestRequirementCatalog.Requirements requirements,
		int[] realSkillLevels, int questPoints, Set<String> completedQuests,
		boolean requireQuestPoints, boolean requireSkillLevels,
		boolean requirePrerequisiteQuests)
	{
		if (hideCompleted && completed)
		{
			return false;
		}
		if (requireCards && !cardsMet)
		{
			return false;
		}
		if (requirements == null || realSkillLevels == null)
		{
			return true;
		}
		if (requireQuestPoints && questPoints < requirements.questPoints)
		{
			return false;
		}
		if (requireSkillLevels)
		{
			for (QuestRequirementCatalog.SkillRequirement requirement : requirements.skills)
			{
				int ordinal = requirement.skill.ordinal();
				if (ordinal >= realSkillLevels.length
					|| realSkillLevels[ordinal] < requirement.level)
				{
					return false;
				}
			}
		}
		if (requirePrerequisiteQuests)
		{
			for (QuestRequirementCatalog.QuestRequirement requirement : requirements.quests)
			{
				if (!completedQuests.contains(
					requirement.quest.getName().toLowerCase(Locale.ROOT)))
				{
					return false;
				}
			}
		}
		return true;
	}
}
