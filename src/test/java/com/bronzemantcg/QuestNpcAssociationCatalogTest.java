package com.bronzemantcg;

import com.google.gson.Gson;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;
import net.runelite.api.Quest;

public class QuestNpcAssociationCatalogTest
{
	@Test
	public void loadsCompleteAuditedNonCombatAndStarterAssociations()
	{
		QuestNpcAssociationCatalog catalog = new QuestNpcAssociationCatalog(new Gson());
		List<QuestNpcAssociationCatalog.Association> associations = catalog.getAssociations();

		// Only audited runtime names already represented by the generated OSRS TCG
		// monster-card catalog are eligible for quest-state associations. Starter
		// metadata remains evidence only; it must not create a global exemption.
		Assert.assertEquals(48, associations.size());
		Assert.assertEquals(15, associations.stream().filter(a -> a.startsQuest).count());
		assertAssociation(associations, "A Porcine of Interest", "Spria", false);
		assertAssociation(associations, "Lost City", "Warrior", true);
		assertAssociation(associations, "Temple of Ikov", "Lucien", true);
		assertAssociation(associations, "The Heart of Darkness", "Prince Itzla Arkan", true);
	}

	@Test
	public void everyAssociationHasAnExistingMonsterCardMapping()
	{
		List<QuestNpcAssociationCatalog.Association> associations =
			new QuestNpcAssociationCatalog(new Gson()).getAssociations();
		TrackedMonsterCatalog monsterCatalog = new TrackedMonsterCatalog(new Gson());

		for (QuestNpcAssociationCatalog.Association association : associations)
		{
			Assert.assertTrue("Association has no existing NPC card mapping: "
				+ association.quest + " -> " + association.npc,
				monsterCatalog.isTracked(association.npc));
		}
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

	@Test
	public void starterMetadataDoesNotCreateAGlobalExemption()
	{
		Assert.assertFalse(QuestNpcIndex.shouldFailOpenAssociation(Quest.LOST_CITY));
		Assert.assertTrue(QuestNpcIndex.shouldFailOpenAssociation(null));
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
