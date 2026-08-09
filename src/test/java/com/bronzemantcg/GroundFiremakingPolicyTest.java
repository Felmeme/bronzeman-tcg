package com.bronzemantcg;

import com.google.gson.Gson;
import org.junit.Assert;
import org.junit.Test;

public class GroundFiremakingPolicyTest
{
	@Test
	public void recognizesOnlyTheGroundLightOperation()
	{
		Assert.assertTrue(BronzemanTcgPlugin.isGroundFiremakingOption("Light"));
		Assert.assertTrue(BronzemanTcgPlugin.isGroundFiremakingOption(
			"<col=ff9040>Light</col>"));
		Assert.assertFalse(BronzemanTcgPlugin.isGroundFiremakingOption("Take"));
		Assert.assertFalse(BronzemanTcgPlugin.isGroundFiremakingOption("Lay"));
	}

	@Test
	public void groundLightingIsLimitedToCoveredTinderboxRecipes()
	{
		RecipeCatalog catalog = new RecipeCatalog(new Gson());
		RecipeCatalog.Recipe oak = catalog.find(RecipeCatalog.KIND_ITEM_ON_ITEM,
			"Tinderbox", "Oak logs");
		Assert.assertNotNull(oak);
		Assert.assertEquals("firemaking", oak.category);
		Assert.assertNull(catalog.find(RecipeCatalog.KIND_ITEM_ON_ITEM,
			"Tinderbox", "Bird snare"));
	}
}
