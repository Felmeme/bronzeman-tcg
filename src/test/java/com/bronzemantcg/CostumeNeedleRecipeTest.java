package com.bronzemantcg;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CostumeNeedleRecipeTest
{
	private final RecipeCatalog catalog = new RecipeCatalog(new Gson());

	@Test
	public void costumeNeedleUsesExistingLeatherAndDragonhideTriggers()
	{
		assertNotNull(catalog.find(RecipeCatalog.KIND_ITEM_ON_ITEM,
			"Costume needle", "Leather"));
		assertNotNull(catalog.find(RecipeCatalog.KIND_ITEM_ON_ITEM,
			"Costume needle", "Green dragon leather"));
		assertNotNull(catalog.find(RecipeCatalog.KIND_ITEM_ON_ITEM,
			"Costume needle", "Blue dragon leather"));
		assertNotNull(catalog.find(RecipeCatalog.KIND_ITEM_ON_ITEM,
			"Costume needle", "Red dragon leather"));
		assertNotNull(catalog.find(RecipeCatalog.KIND_ITEM_ON_ITEM,
			"Costume needle", "Black dragon leather"));
	}

	@Test
	public void costumeNeedleCardSatisfiesNeedleAndThreadRequirements()
	{
		RecipeCatalog.Recipe recipe = catalog.find(
			RecipeCatalog.KIND_INTERFACE, "Leather vambraces", null);
		assertNotNull(recipe);

		Set<String> owned = lowerSet("Costume needle", "Leather", "Leather vambraces");
		assertTrue(recipe.missingRequirements(owned, true, true).isEmpty());
	}

	@Test
	public void ordinaryNeedleStillRequiresThread()
	{
		RecipeCatalog.Recipe recipe = catalog.find(
			RecipeCatalog.KIND_INTERFACE, "Leather vambraces", null);
		assertNotNull(recipe);

		Set<String> owned = lowerSet("Needle", "Leather", "Leather vambraces");
		List<String> missing = recipe.missingRequirements(owned, true, true);
		assertEquals(Collections.singletonList("Thread"), missing);
	}

	@Test
	public void costumeNeedleDoesNotBypassTheOutputCard()
	{
		RecipeCatalog.Recipe recipe = catalog.find(
			RecipeCatalog.KIND_INTERFACE, "Leather vambraces", null);
		assertNotNull(recipe);

		Set<String> owned = lowerSet("Costume needle", "Leather");
		List<String> missing = recipe.missingRequirements(owned, true, true);
		assertEquals(Collections.singletonList("Leather vambraces"), missing);
	}

	private static Set<String> lowerSet(String... cards)
	{
		Set<String> result = new HashSet<>();
		Arrays.stream(cards).map(card -> card.toLowerCase(Locale.ROOT)).forEach(result::add);
		return result;
	}
}
