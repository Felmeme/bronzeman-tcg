package com.bronzemantcg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Exact card requirements for the materials consumed by Sailing hull and keel recipes.
 * The keys are the stable icon item IDs exposed by the Sailing/customisation widgets.
 */
final class SailingUpgradeRecipeCatalog
{
	private static final Map<Integer, Recipe> RECIPES;

	static
	{
		Map<Integer, Recipe> recipes = new HashMap<>();
		addHullTier(recipes, 32118, 32041, 32062,
			"Logs", "Plank", "Wooden hull parts", "Large wooden hull parts",
			"Bronze nails", null);
		addHullTier(recipes, 32121, 32044, 32065,
			"Oak logs", "Oak plank", "Oak hull parts", "Large oak hull parts",
			"Iron nails", null);
		addHullTier(recipes, 32124, 32047, 32068,
			"Teak logs", "Teak plank", "Teak hull parts", "Large teak hull parts",
			"Steel nails", "Lead bar");
		addHullTier(recipes, 32127, 32050, 32071,
			"Mahogany logs", "Mahogany plank", "Mahogany hull parts",
			"Large mahogany hull parts", "Mithril nails", "Lead bar");
		addHullTier(recipes, 32130, 32053, 32074,
			"Camphor logs", "Camphor plank", "Camphor hull parts",
			"Large camphor hull parts", "Adamantite nails", "Lead bar");
		addHullTier(recipes, 32133, 32056, 32077,
			"Ironwood logs", "Ironwood plank", "Ironwood hull parts",
			"Large ironwood hull parts", "Rune nails", "Cupronickel bar");
		addHullTier(recipes, 32136, 32059, 32080,
			"Rosewood logs", "Rosewood plank", "Rosewood hull parts",
			"Large rosewood hull parts", "Dragon nails", "Cupronickel bar");

		addKeelTier(recipes, 32181, 31999, 32020,
			"Bronze keel parts", "Large bronze keel parts", "Bronze bar", null);
		addKeelTier(recipes, 32183, 32002, 32023,
			"Iron keel parts", "Large iron keel parts", "Iron bar", null);
		addKeelTier(recipes, 32185, 32005, 32026,
			"Steel keel parts", "Large steel keel parts", "Steel bar", "Lead bar");
		addKeelTier(recipes, 32187, 32008, 32029,
			"Mithril keel parts", "Large mithril keel parts", "Mithril bar", "Lead bar");
		addKeelTier(recipes, 32189, 32011, 32032,
			"Adamant keel parts", "Large adamant keel parts", "Adamantite bar", "Lead bar");
		addKeelTier(recipes, 32191, 32014, 32035,
			"Rune keel parts", "Large rune keel parts", "Runite bar", "Cupronickel bar");
		addKeelTier(recipes, 32193, 32017, 32038,
			"Dragon keel parts", "Large dragon keel parts", "Dragon metal sheet",
			"Cupronickel bar");
		RECIPES = Collections.unmodifiableMap(recipes);
	}

	private SailingUpgradeRecipeCatalog()
	{
	}

	static Recipe find(int itemId)
	{
		return RECIPES.get(itemId);
	}

	static int size()
	{
		return RECIPES.size();
	}

	private static void addHullTier(Map<Integer, Recipe> recipes, int installedFirstId,
		int partsId, int largePartsId, String logs, String plank, String parts,
		String largeParts, String nails, String reinforcingBar)
	{
		// Installed hull icon IDs are consecutive in raft, skiff, sloop order.
		put(recipes, installedFirstId, logs, materials(logs, "Rope", "Swamp tar",
			reinforcingBar));
		put(recipes, installedFirstId + 1, parts, materials(parts, nails, "Swamp tar",
			reinforcingBar));
		put(recipes, installedFirstId + 2, largeParts,
			materials(largeParts, nails, "Swamp tar", reinforcingBar));
		// Workbench recipes create standard parts from planks and large parts from
		// five standard parts. Quantities do not alter card ownership requirements.
		put(recipes, partsId, plank, materials(plank));
		put(recipes, largePartsId, parts, materials(parts));
	}

	private static void addKeelTier(Map<Integer, Recipe> recipes, int installedFirstId,
		int partsId, int largePartsId, String parts, String largeParts, String bar,
		String reinforcingBar)
	{
		// Installed keel icon IDs are consecutive in skiff, sloop order.
		put(recipes, installedFirstId, parts, materials(parts, reinforcingBar));
		put(recipes, installedFirstId + 1, largeParts,
			materials(largeParts, reinforcingBar));
		put(recipes, partsId, bar, materials(bar));
		put(recipes, largePartsId, parts, materials(parts));
	}

	private static List<String> materials(String... names)
	{
		List<String> materials = new ArrayList<>();
		for (String name : names)
		{
			if (name != null)
			{
				materials.add(name);
			}
		}
		return materials;
	}

	private static void put(Map<Integer, Recipe> recipes, int itemId, String primary,
		List<String> materials)
	{
		recipes.put(itemId, new Recipe(primary, materials));
	}

	static final class Recipe
	{
		private final String primary;
		private final List<String> materials;

		private Recipe(String primary, List<String> materials)
		{
			this.primary = primary;
			this.materials = Collections.unmodifiableList(materials);
		}

		List<String> getMaterials()
		{
			return materials;
		}

		List<String> missingRequirements(Set<String> owned, SailingUpgradeMode mode)
		{
			if (mode == SailingUpgradeMode.OFF)
			{
				return Collections.emptyList();
			}
			List<String> required = mode == SailingUpgradeMode.PARTS
				? Collections.singletonList(primary) : materials;
			List<String> missing = new ArrayList<>();
			for (String card : required)
			{
				if (!owned.contains(card.toLowerCase(Locale.ROOT)))
				{
					missing.add(card);
				}
			}
			return missing;
		}
	}
}
