package com.bronzemantcg;

import com.bronzemantcg.catalog.RecipeCatalog;
import com.bronzemantcg.catalog.HerbloreRecipeStage;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HerbloreRecipeDataTest
{
	@Test
	public void everyLogicalRecipeHasMatchingInteractionRules()
	{
		JsonArray recipes = loadRecipes();
		Map<String, Map<String, Integer>> signatures = new HashMap<>();
		Map<String, Integer> stageCounts = new HashMap<>();
		Set<String> triggerKeys = new HashSet<>();

		for (JsonElement element : recipes)
		{
			JsonObject recipe = element.getAsJsonObject();
			if (!"herblore".equals(recipe.get("category").getAsString()))
			{
				continue;
			}

			String stage = recipe.get("stage").getAsString();
			String kind = recipe.getAsJsonObject("trigger").get("kind").getAsString();
			stageCounts.merge(stage + "|" + kind, 1, Integer::sum);

			String output = recipe.get("output").isJsonNull()
				? "null" : recipe.get("output").getAsString();
			String signature = stage + "|" + output + "|" + recipe.get("inputs");
			signatures.computeIfAbsent(kind, ignored -> new HashMap<>())
				.merge(signature, 1, Integer::sum);

			JsonObject trigger = recipe.getAsJsonObject("trigger");
			for (JsonElement target : trigger.getAsJsonArray("targets"))
			{
				String key = kind + "|" + trigger.get("name").getAsString().toLowerCase()
					+ "|" + target.getAsString().toLowerCase();
				assertTrue("Duplicate Herblore trigger " + key, triggerKeys.add(key));
			}
		}

		assertEquals(Integer.valueOf(21), stageCounts.get("unfinished|item-on-item"));
		assertEquals(Integer.valueOf(21), stageCounts.get("unfinished|interface"));
		assertEquals(Integer.valueOf(41), stageCounts.get("finished|item-on-item"));
		assertEquals(Integer.valueOf(41), stageCounts.get("finished|interface"));
		assertEquals(Integer.valueOf(20), stageCounts.get("upgrade|item-on-item"));
		assertEquals(Integer.valueOf(20), stageCounts.get("upgrade|interface"));
		assertEquals(signatures.get("item-on-item"), signatures.get("interface"));
	}

	@Test
	public void catalogMatchesRealPotionItemForms()
	{
		RecipeCatalog catalog = new RecipeCatalog(new Gson());

		RecipeCatalog.Recipe unfinished = catalog.find(
			RecipeCatalog.KIND_INTERFACE, "Guam potion (unf)", "Vial of water");
		assertNotNull(unfinished);
		assertEquals(HerbloreRecipeStage.UNFINISHED, unfinished.herbloreStage);

		RecipeCatalog.Recipe finishedInventory = catalog.find(
			RecipeCatalog.KIND_ITEM_ON_ITEM, "Eye of newt", "Guam potion (unf)");
		assertNotNull(finishedInventory);
		assertEquals(HerbloreRecipeStage.FINISHED, finishedInventory.herbloreStage);

		RecipeCatalog.Recipe finishedInterface = catalog.find(
			RecipeCatalog.KIND_INTERFACE, "Attack potion(3)", "Eye of newt");
		assertNotNull(finishedInterface);
		assertEquals(HerbloreRecipeStage.FINISHED, finishedInterface.herbloreStage);

		assertNotNull(catalog.find(
			RecipeCatalog.KIND_INTERFACE, "Toadflax potion (unf)", "Vial of water"));
		assertNotNull(catalog.find(
			RecipeCatalog.KIND_INTERFACE, "Toadflax potion (unf)", "Coconut milk"));
	}

	private static JsonArray loadRecipes()
	{
		InputStream resource = HerbloreRecipeDataTest.class
			.getResourceAsStream("/nodes/recipe_nodes.json");
		assertNotNull(resource);
		return new JsonParser().parse(new InputStreamReader(resource,
			StandardCharsets.UTF_8))
			.getAsJsonObject()
			.getAsJsonArray("recipes");
	}
}
