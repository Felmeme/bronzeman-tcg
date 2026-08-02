package com.questhelper;

import com.questhelper.questinfo.QuestHelperQuest;
import com.questhelper.requirements.quest.QuestRequirement;
import java.util.Collections;
import java.util.List;
import net.runelite.api.QuestState;
import org.junit.jupiter.api.Test;

/**
 * Copy into Quest Helper's src/test/java/com/questhelper directory at the pinned
 * commit, then run this test to reproduce quest_helper_prerequisites_source.json.
 */
public class PrerequisiteDumpTest extends MockedTest
{
	@Test
	void dumpTopLevelQuestRequirements()
	{
		for (QuestHelperQuest quest : QuestHelperQuest.values())
		{
			com.questhelper.questhelpers.QuestHelper helper = quest.getQuestHelper();
			helper.setQuest(quest);
			injector.injectMembers(helper);
			helper.setQuestHelperPlugin(questHelperPlugin);
			helper.setConfig(questHelperConfig);
			helper.initializeRequirements();

			List<com.questhelper.requirements.Requirement> requirements =
				helper.getGeneralRequirements();
			for (com.questhelper.requirements.Requirement requirement :
				requirements == null
					? Collections.<com.questhelper.requirements.Requirement>emptyList()
					: requirements)
			{
				if (!(requirement instanceof QuestRequirement))
				{
					continue;
				}
				QuestRequirement prerequisite = (QuestRequirement) requirement;
				QuestState state = prerequisite.getRequiredState();
				System.out.println("@@PREREQ\t" + quest.name() + "\t" + quest.getName()
					+ "\t" + prerequisite.getQuest().name() + "\t"
					+ prerequisite.getQuest().getName() + "\t"
					+ (state == null
						? "VAR:" + prerequisite.getMinimumVarValue() : state.name()));
			}
		}
	}
}
