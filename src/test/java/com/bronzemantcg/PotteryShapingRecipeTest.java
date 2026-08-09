package com.bronzemantcg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class PotteryShapingRecipeTest
{
	private final RecipeCatalog catalog = new RecipeCatalog(new Gson());

	@Test
	public void wheelRecipesGateTheUnfiredItemBeingMade()
	{
		assertWheelRecipe("Pot", "Unfired pot");
		assertWheelRecipe("Bowl", "Unfired bowl");
		assertWheelRecipe("Pie dish", "Unfired pie dish");
	}

	private void assertWheelRecipe(String interfaceProduct, String unfiredCard)
	{
		RecipeCatalog.Recipe recipe = catalog.find(
			RecipeCatalog.KIND_INTERFACE, interfaceProduct, "Potter's wheel");
		assertNotNull(interfaceProduct, recipe);
		assertEquals("crafting", recipe.category);
		assertEquals(unfiredCard, recipe.output);
		assertEquals(List.of("Soft clay", unfiredCard),
			recipe.missingRequirements(Collections.emptySet(), true, true));
		assertEquals(Collections.emptyList(), recipe.missingRequirements(
			Set.of("soft clay", unfiredCard.toLowerCase()), true, true));
	}
}
