package com.bronzemantcg;

import com.google.gson.Gson;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;

public class QuestNpcAssociationCatalogTest
{
	@Test
	public void loadsCompleteAuditedNonCombatAndStarterAssociations()
	{
		QuestNpcAssociationCatalog catalog = new QuestNpcAssociationCatalog(new Gson());
		List<QuestNpcAssociationCatalog.Association> associations = catalog.getAssociations();

		// 48 confirmed omissions plus two already-indexed combat NPCs whose first-step
		// interactions still need the pre-start exception.
		Assert.assertEquals(50, associations.size());
		Assert.assertEquals(15, associations.stream().filter(a -> a.startsQuest).count());
		assertAssociation(associations, "A Porcine of Interest", "Spria", false);
		assertAssociation(associations, "Lost City", "Warrior", true);
		assertAssociation(associations, "Temple of Ikov", "Lucien", true);
		assertAssociation(associations, "The Heart of Darkness", "Prince Itzla Arkan", true);
	}

	@Test
	public void containsNoDuplicateQuestNpcPairs()
	{
		List<QuestNpcAssociationCatalog.Association> associations =
			new QuestNpcAssociationCatalog(new Gson()).getAssociations();
		Assert.assertEquals(associations.size(), associations.stream()
			.map(a -> a.quest + "\n" + a.npc)
			.collect(Collectors.toSet()).size());
	}

	private static void assertAssociation(List<QuestNpcAssociationCatalog.Association> entries,
		String quest, String npc, boolean startsQuest)
	{
		for (QuestNpcAssociationCatalog.Association entry : entries)
		{
			if (quest.equals(entry.quest) && npc.equals(entry.npc))
			{
				Assert.assertEquals(startsQuest, entry.startsQuest);
				return;
			}
		}
		Assert.fail("Missing association " + quest + " -> " + npc);
	}
}
