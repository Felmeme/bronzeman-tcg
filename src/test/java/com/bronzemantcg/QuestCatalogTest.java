package com.bronzemantcg;

import com.google.gson.Gson;
import java.util.List;
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
}
