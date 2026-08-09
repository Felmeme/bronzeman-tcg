package com.bronzemantcg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class FremennikTrialsBattlestaffRecipeTest
{
	@Test
	public void optionalCelastrusRouteChecksInputAndOutputCards()
	{
		RecipeCatalog.Recipe recipe = new RecipeCatalog(new Gson()).find(
			RecipeCatalog.KIND_INTERFACE, "Battlestaff", null);
		assertNotNull(recipe);
		assertEquals("fletching", recipe.category);
		assertEquals(List.of("Celastrus bark", "Battlestaff"),
			recipe.missingRequirements(Collections.emptySet(), true, true));
		assertEquals(Collections.emptyList(), recipe.missingRequirements(
			Set.of("celastrus bark", "battlestaff"), true, true));
	}
}
