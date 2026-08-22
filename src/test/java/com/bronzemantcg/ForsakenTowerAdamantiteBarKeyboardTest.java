package com.bronzemantcg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class ForsakenTowerAdamantiteBarKeyboardTest
{
	@Test
	public void optionalDiarySmeltUsesExistingExactKeyboardRecipe()
	{
		RecipeCatalog.Recipe recipe = new RecipeCatalog(new Gson()).find(
			RecipeCatalog.KIND_INTERFACE, "Adamantite bar", null);
		assertNotNull(recipe);
		assertEquals("smithing-smelt", recipe.category);
		assertEquals(List.of("Adamantite ore", "Coal", "Adamantite bar"),
			recipe.missingRequirements(Collections.emptySet(), true, true));
		assertEquals('1', MakeInterfaceKeyboardPolicy.shortcutForIndex(0));
	}
}
