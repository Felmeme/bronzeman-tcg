package com.bronzemantcg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class ZogreOgreArrowShaftKeyboardTest
{
	@Test
	public void singularAndPluralProductsUseExistingOutputGate()
	{
		assertOgreShaftProduct("Ogre arrow shaft");
		assertOgreShaftProduct("Ogre arrow shafts");
	}

	private void assertOgreShaftProduct(String product)
	{
		RecipeCatalog.Recipe recipe = new RecipeCatalog(new Gson()).find(
			RecipeCatalog.KIND_INTERFACE, product, null);
		assertNotNull(recipe);
		assertEquals("fletching", recipe.category);
		assertEquals(List.of("Ogre arrow shaft"), recipe.missingRequirements(
			Collections.emptySet(), false, true));
	}
}
