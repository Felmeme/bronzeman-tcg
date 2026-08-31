package com.bronzemantcg.restriction;

import com.bronzemantcg.catalog.CardRequirement;
import com.bronzemantcg.catalog.RecipeCatalog;
import com.google.gson.Gson;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RecipeRestrictionServiceTest
{
	@Test
	public void firemakingOnlyRequiresTinderbox()
	{
		Harness harness = new Harness();
		harness.settings.tinderboxMode = CardRequirement.CARD_REQUIRED;

		assertEquals(List.of("Tinderbox"), harness.service.evaluate(
			RecipeCatalog.KIND_ITEM_ON_ITEM, "Tinderbox", "logs"));
		harness.owned.add("tinderbox");
		assertNull(harness.service.evaluate(
			RecipeCatalog.KIND_ITEM_ON_ITEM, "Tinderbox", "logs"));
	}

	@Test
	public void smeltingModesRetainTheirInputAndOutputSplit()
	{
		Harness harness = new Harness();
		harness.settings.smeltingMode = SmeltingMode.ORE;
		List<String> inputMissing = harness.service.evaluate(
			RecipeCatalog.KIND_ITEM_ON_OBJECT, "Copper ore", "furnace");
		assertTrue(inputMissing.contains("Copper ore"));
		assertTrue(inputMissing.contains("Tin ore"));

		harness.settings.smeltingMode = SmeltingMode.BOTH;
		assertTrue(harness.service.evaluate(RecipeCatalog.KIND_ITEM_ON_OBJECT,
			"Copper ore", "furnace").contains("Bronze bar"));
	}

	@Test
	public void crushedGemLayersOntoCraftingRequirement()
	{
		Harness harness = new Harness();
		harness.settings.craftingMode = CraftingMode.INPUT_ONLY;
		harness.settings.requireCrushedGem = true;
		harness.owned.addAll(Set.of("chisel", "uncut opal"));

		assertEquals(List.of("Crushed gem"), harness.service.evaluate(
			RecipeCatalog.KIND_ITEM_ON_ITEM, "Chisel", "uncut opal"));
	}

	@Test
	public void disabledAndUnknownRecipesRemainAllowed()
	{
		Harness harness = new Harness();
		assertNull(harness.service.evaluate(
			RecipeCatalog.KIND_ITEM_ON_OBJECT, "Copper ore", "furnace"));
		assertNull(harness.service.evaluate(
			RecipeCatalog.KIND_ITEM_ON_ITEM, "No such item", "nothing"));
	}

	@Test
	public void remainingProcessingCategoriesUseTheirIndependentSettings()
	{
		Harness harness = new Harness();

		harness.settings.cookingMode = CookingMode.INPUT_ONLY;
		assertTrue(harness.service.evaluate(RecipeCatalog.KIND_ITEM_ON_ITEM,
			"Knife", "banana").contains("Knife"));

		harness.settings.smithingMode = SmithingMode.BARS;
		assertTrue(harness.service.evaluate(RecipeCatalog.KIND_INTERFACE,
			"Bronze dagger", "anvil").contains("Bronze bar"));

		harness.settings.restrictEnchanting = true;
		assertTrue(harness.service.evaluate(RecipeCatalog.KIND_SPELL_ON_ITEM,
			"Lvl-1 Enchant", "sapphire ring") != null);

		harness.settings.fletchingMode = FletchingMode.INPUT_ONLY;
		assertTrue(harness.service.evaluate(RecipeCatalog.KIND_ITEM_ON_ITEM,
			"Bow string", "Shortbow (u)") != null);

		harness.settings.herbloreMode = HerbloreMode.INPUT_ONLY;
		assertTrue(harness.service.evaluate(RecipeCatalog.KIND_ITEM_ON_ITEM,
			"Guam leaf", "vial of water") != null);
	}

	private static final class Harness
	{
		private final Set<String> owned = new HashSet<>();
		private final FakeSettings settings = new FakeSettings();
		private final RecipeRestrictionService service = new RecipeRestrictionService(
			new RecipeCatalog(new Gson()), settings,
			() -> card -> owned.contains(card.toLowerCase()));
	}

	private static final class FakeSettings implements RecipeRestrictionService.Settings
	{
		private CardRequirement tinderboxMode = CardRequirement.NO_CARD;
		private SmeltingMode smeltingMode = SmeltingMode.OFF;
		private SmithingMode smithingMode = SmithingMode.OFF;
		private CookingMode cookingMode = CookingMode.OFF;
		private CraftingMode craftingMode = CraftingMode.OFF;
		private FletchingMode fletchingMode = FletchingMode.OFF;
		private HerbloreMode herbloreMode = HerbloreMode.OFF;
		private boolean restrictEnchanting;
		private boolean requireCrushedGem;

		public CardRequirement tinderboxMode() { return tinderboxMode; }
		public SmeltingMode smeltingMode() { return smeltingMode; }
		public SmithingMode smithingMode() { return smithingMode; }
		public CookingMode cookingMode() { return cookingMode; }
		public CraftingMode craftingMode() { return craftingMode; }
		public FletchingMode fletchingMode() { return fletchingMode; }
		public HerbloreMode herbloreMode() { return herbloreMode; }
		public boolean restrictEnchanting() { return restrictEnchanting; }
		public boolean requireCrushedGem() { return requireCrushedGem; }
	}
}
