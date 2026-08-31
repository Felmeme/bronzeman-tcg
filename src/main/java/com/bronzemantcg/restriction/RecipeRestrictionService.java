package com.bronzemantcg.restriction;

import com.bronzemantcg.BronzemanTcgConfig;
import com.bronzemantcg.catalog.CardRequirement;
import com.bronzemantcg.catalog.RecipeCatalog;
import java.util.function.Predicate;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Owns processing-recipe restriction decisions; event effects remain in the plugin. */
@Singleton
public final class RecipeRestrictionService
{
	private static final String CRUSHED_GEM_CARD = "Crushed gem";

	private final RecipeCatalog recipeCatalog;
	private final Settings settings;
	private final Ownership ownership;

	@Inject
	public RecipeRestrictionService(RecipeCatalog recipeCatalog, BronzemanTcgConfig config,
		RestrictionDecisionService restrictionDecisionService)
	{
		this(recipeCatalog, new ConfigSettings(config),
			restrictionDecisionService::requirementOwnership);
	}

	RecipeRestrictionService(RecipeCatalog recipeCatalog, Settings settings,
		Ownership ownership)
	{
		this.recipeCatalog = recipeCatalog;
		this.settings = settings;
		this.ownership = ownership;
	}

	/** @return missing cards, or null when the interaction is allowed. */
	public List<String> evaluate(String kind, String name, String target)
	{
		RecipeCatalog.Recipe recipe = recipeCatalog.find(kind, name, target);
		if (recipe == null)
		{
			return null;
		}

		boolean enforceInputs;
		boolean enforceOutput;
		boolean firemakingTinderboxOnly = false;
		switch (recipe.category)
		{
			case "firemaking":
				if (settings.tinderboxMode() != CardRequirement.CARD_REQUIRED) return null;
				enforceInputs = true;
				enforceOutput = false;
				firemakingTinderboxOnly = true;
				break;
			case "smithing-smelt":
			{
				SmeltingMode mode = settings.smeltingMode();
				if (mode == SmeltingMode.OFF) return null;
				enforceInputs = mode == SmeltingMode.ORE || mode == SmeltingMode.BOTH;
				enforceOutput = mode == SmeltingMode.BOTH;
				break;
			}
			case "smithing-forge":
			{
				SmithingMode mode = settings.smithingMode();
				if (mode == SmithingMode.OFF) return null;
				enforceInputs = mode == SmithingMode.BARS || mode == SmithingMode.BOTH;
				enforceOutput = mode == SmithingMode.BOTH;
				break;
			}
			case "cooking":
			{
				CookingMode mode = settings.cookingMode();
				if (mode == CookingMode.OFF) return null;
				enforceInputs = mode == CookingMode.INPUT_ONLY || mode == CookingMode.INPUT_OUTPUT;
				enforceOutput = mode == CookingMode.INPUT_OUTPUT;
				break;
			}
			case "crafting":
			{
				CraftingMode mode = settings.craftingMode();
				if (mode == CraftingMode.OFF) return null;
				enforceInputs = mode == CraftingMode.INPUT_ONLY || mode == CraftingMode.BOTH;
				enforceOutput = mode == CraftingMode.BOTH;
				break;
			}
			case "enchanting":
				if (!settings.restrictEnchanting()) return null;
				enforceInputs = true;
				enforceOutput = true;
				break;
			case "fletching":
			{
				FletchingMode mode = settings.fletchingMode();
				if (mode == FletchingMode.OFF) return null;
				enforceInputs = mode == FletchingMode.INPUT_ONLY
					|| mode == FletchingMode.PRODUCT_AND_MATERIALS;
				enforceOutput = mode == FletchingMode.PRODUCT_AND_MATERIALS;
				break;
			}
			case "herblore":
			{
				HerbloreMode mode = settings.herbloreMode();
				if (mode == HerbloreMode.OFF) return null;
				enforceInputs = mode.enforcesInputs();
				enforceOutput = mode.enforcesOutput(recipe.herbloreStage);
				break;
			}
			default:
				enforceInputs = true;
				enforceOutput = true;
		}

		Predicate<String> ownsCard = ownership.current();
		List<String> missing = recipe.missingRequirements(ownsCard, enforceInputs, enforceOutput);
		if (firemakingTinderboxOnly)
		{
			missing.removeIf(card -> !"Tinderbox".equalsIgnoreCase(card));
		}
		if (recipe.crushable && settings.requireCrushedGem()
			&& !ownsCard.test(CRUSHED_GEM_CARD))
		{
			missing.add(CRUSHED_GEM_CARD);
		}
		return missing.isEmpty() ? null : missing;
	}

	interface Ownership
	{
		Predicate<String> current();
	}

	interface Settings
	{
		CardRequirement tinderboxMode();
		SmeltingMode smeltingMode();
		SmithingMode smithingMode();
		CookingMode cookingMode();
		CraftingMode craftingMode();
		FletchingMode fletchingMode();
		HerbloreMode herbloreMode();
		boolean restrictEnchanting();
		boolean requireCrushedGem();
	}

	private static final class ConfigSettings implements Settings
	{
		private final BronzemanTcgConfig config;

		private ConfigSettings(BronzemanTcgConfig config)
		{
			this.config = config;
		}

		public CardRequirement tinderboxMode() { return config.tinderboxMode(); }
		public SmeltingMode smeltingMode() { return config.smeltingMode(); }
		public SmithingMode smithingMode() { return config.smithingMode(); }
		public CookingMode cookingMode() { return config.cookingMode(); }
		public CraftingMode craftingMode() { return config.craftingMode(); }
		public FletchingMode fletchingMode() { return config.fletchingMode(); }
		public HerbloreMode herbloreMode() { return config.herbloreMode(); }
		public boolean restrictEnchanting() { return config.restrictEnchanting(); }
		public boolean requireCrushedGem() { return config.requireCrushedGem(); }
	}
}
