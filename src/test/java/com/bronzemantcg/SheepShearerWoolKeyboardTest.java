package com.bronzemantcg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class SheepShearerWoolKeyboardTest
{
	@Test
	public void ballOfWoolUsesExistingMouseAndKeyboardRecipe()
	{
		RecipeCatalog.Recipe recipe = new RecipeCatalog(new Gson()).find(
			RecipeCatalog.KIND_INTERFACE, "Ball of wool", null);
		assertNotNull(recipe);
		assertEquals("crafting", recipe.category);
		assertEquals(List.of("Wool", "Ball of wool"), recipe.missingRequirements(
			Collections.emptySet(), true, true));
		assertEquals('1', MakeInterfaceKeyboardPolicy.shortcutForIndex(0));
	}
}
