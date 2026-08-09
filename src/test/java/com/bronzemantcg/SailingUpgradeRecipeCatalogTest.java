package com.bronzemantcg;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

public class SailingUpgradeRecipeCatalogTest
{
	@Test
	public void coversEveryHullKeelAndPartIconVariant()
	{
		// 21 installed hulls + 14 installed keels + 28 standard/large part recipes.
		Assert.assertEquals(63, SailingUpgradeRecipeCatalog.size());
	}

	@Test
	public void oakRaftRequiresItsActualMaterialsInsteadOfOakHullParts()
	{
		SailingUpgradeRecipeCatalog.Recipe recipe =
			SailingUpgradeRecipeCatalog.find(32121);
		Assert.assertNotNull(recipe);
		Assert.assertEquals(List.of("Oak logs", "Rope", "Swamp tar"),
			recipe.getMaterials());
		Assert.assertEquals(List.of("Oak logs"),
			recipe.missingRequirements(Collections.emptySet(), SailingUpgradeMode.PARTS));
		Assert.assertEquals(List.of("Oak logs", "Rope", "Swamp tar"),
			recipe.missingRequirements(Collections.emptySet(),
				SailingUpgradeMode.PARTS_MATERIALS));
		Assert.assertTrue(recipe.missingRequirements(
			Set.of("oak logs", "rope", "swamp tar", "oak hull parts"),
			SailingUpgradeMode.PARTS_MATERIALS).isEmpty());
	}

	@Test
	public void oakSkiffAndSloopUseTheirOwnPartAndNailRecipes()
	{
		Assert.assertEquals(List.of("Oak hull parts", "Iron nails", "Swamp tar"),
			SailingUpgradeRecipeCatalog.find(32122).getMaterials());
		Assert.assertEquals(List.of("Large oak hull parts", "Iron nails", "Swamp tar"),
			SailingUpgradeRecipeCatalog.find(32123).getMaterials());
	}

	@Test
	public void workbenchPartRecipesRequireInputsNotOutputs()
	{
		Assert.assertEquals(List.of("Oak plank"),
			SailingUpgradeRecipeCatalog.find(32044).getMaterials());
		Assert.assertEquals(List.of("Oak hull parts"),
			SailingUpgradeRecipeCatalog.find(32065).getMaterials());
	}

	@Test
	public void higherTierReinforcementMaterialsAreIncluded()
	{
		Assert.assertEquals(
			List.of("Large rosewood hull parts", "Dragon nails", "Swamp tar",
				"Cupronickel bar"),
			SailingUpgradeRecipeCatalog.find(32138).getMaterials());
		Assert.assertEquals(List.of("Large dragon keel parts", "Cupronickel bar"),
			SailingUpgradeRecipeCatalog.find(32194).getMaterials());
	}
}
