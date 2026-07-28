package com.bronzemantcg;

import com.google.gson.Gson;
import org.junit.Assert;
import org.junit.Test;

public class CardKnowledgeCatalogTest
{
	@Test
	public void loadsCompleteGeneratedSnapshot()
	{
		CardKnowledgeCatalog catalog = new CardKnowledgeCatalog(new Gson());
		CardKnowledgeCatalog.Card lobster = catalog.find("Lobster");

		Assert.assertNotNull(lobster);
		Assert.assertTrue(lobster.isResource());
		Assert.assertEquals(379, lobster.primaryId());
		Assert.assertNotNull(lobster.sources);
		Assert.assertEquals("Cooking", lobster.sources.production.skill);

		CardKnowledgeCatalog.Card vorkath = catalog.find("Vorkath");
		Assert.assertNotNull(vorkath);
		Assert.assertFalse(vorkath.isResource());
		Assert.assertEquals(76, vorkath.drops.size());
		Assert.assertNotNull(catalog.find(vorkath.drops.get(0).card));
	}
}
