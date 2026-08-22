package com.bronzemantcg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class SailingMetalSmeltingRecipeTest
{
	private final RecipeCatalog catalog = new RecipeCatalog(new Gson());

	@Test
	public void leadBarUsesLeadOre()
	{
		assertRecipe("Lead bar", List.of("Lead ore", "Lead bar"),
			Set.of("lead ore", "lead bar"));
	}

	@Test
	public void cupronickelBarUsesCopperAndNickelOre()
	{
		assertRecipe("Cupronickel bar",
			List.of("Copper ore", "Nickel ore", "Cupronickel bar"),
			Set.of("copper ore", "nickel ore", "cupronickel bar"));
	}

	private void assertRecipe(String product, List<String> missing, Set<String> owned)
	{
		RecipeCatalog.Recipe recipe = catalog.find(
			RecipeCatalog.KIND_INTERFACE, product, "Furnace");
		assertNotNull(product, recipe);
		assertEquals("smithing-smelt", recipe.category);
		assertEquals(missing, recipe.missingRequirements(
			Collections.emptySet(), true, true));
		assertEquals(Collections.emptyList(),
			recipe.missingRequirements(owned, true, true));
	}
}
