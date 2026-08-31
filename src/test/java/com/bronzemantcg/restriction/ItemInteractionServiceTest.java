package com.bronzemantcg.restriction;

import com.bronzemantcg.catalog.RecipeCatalog;
import com.bronzemantcg.ownership.TcgOwnershipSnapshot;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import net.runelite.api.MenuAction;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ItemInteractionServiceTest
{
	@Test
	public void groundItemsOnlyRestrictTakeOrTelegrabWhileLocked()
	{
		assertTrue(ItemInteractionService.isGroundInteractionRestricted(
			LockState.LOCKED, MenuAction.GROUND_ITEM_FIRST_OPTION, "Take"));
		assertTrue(ItemInteractionService.isGroundInteractionRestricted(
			LockState.LOCKED, MenuAction.WIDGET_TARGET_ON_GROUND_ITEM, "Cast"));
		assertFalse(ItemInteractionService.isGroundInteractionRestricted(
			LockState.LOCKED, MenuAction.GROUND_ITEM_FIRST_OPTION, "Examine"));
		assertFalse(ItemInteractionService.isGroundInteractionRestricted(
			LockState.UNLOCKED, MenuAction.GROUND_ITEM_FIRST_OPTION, "Take"));
	}

	@Test
	public void bankModesRetainTheirDepositAndWithdrawMatrix()
	{
		assertTrue(ItemInteractionService.isBankInteractionRestricted(
			LockState.LOCKED, BankingMode.OFF, "Withdraw-1"));
		assertTrue(ItemInteractionService.isBankInteractionRestricted(
			LockState.LOCKED, BankingMode.OFF, "Deposit-1"));
		assertTrue(ItemInteractionService.isBankInteractionRestricted(
			LockState.LOCKED, BankingMode.DEPOSIT_ONLY, "Withdraw-All"));
		assertFalse(ItemInteractionService.isBankInteractionRestricted(
			LockState.LOCKED, BankingMode.DEPOSIT_ONLY, "Deposit-All"));
		assertFalse(ItemInteractionService.isBankInteractionRestricted(
			LockState.LOCKED, BankingMode.FULL, "Withdraw-X"));
		assertFalse(ItemInteractionService.isBankInteractionRestricted(
			LockState.UNLOCKED, BankingMode.OFF, "Deposit-1"));
	}

	@Test
	public void inventoryUsageKeepsOnlyExplicitDisposalActions()
	{
		assertFalse(ItemInteractionService.isInventoryUsageRestricted(
			LockState.LOCKED, "Drop"));
		assertFalse(ItemInteractionService.isInventoryUsageRestricted(
			LockState.LOCKED, "Destroy"));
		assertTrue(ItemInteractionService.isInventoryUsageRestricted(
			LockState.LOCKED, "Wear"));
		assertTrue(ItemInteractionService.isInventoryUsageRestricted(
			LockState.LOCKED, "Drink"));
		assertFalse(ItemInteractionService.isInventoryUsageRestricted(
			LockState.UNLOCKED, "Wear"));
	}

	@Test
	public void foodSettingsApplyOnlyToExactEatAndDrinkActions()
	{
		assertTrue(ItemInteractionService.requiresInventoryCard(
			LockState.LOCKED, FoodSettingsMode.LOCKED, "Eat"));
		assertTrue(ItemInteractionService.requiresInventoryCard(
			LockState.LOCKED, FoodSettingsMode.LOCKED, "Drink"));

		assertFalse(ItemInteractionService.requiresInventoryCard(
			LockState.LOCKED, FoodSettingsMode.FOOD_ONLY, "EAT"));
		assertTrue(ItemInteractionService.requiresInventoryCard(
			LockState.LOCKED, FoodSettingsMode.FOOD_ONLY, "Drink"));

		assertTrue(ItemInteractionService.requiresInventoryCard(
			LockState.LOCKED, FoodSettingsMode.POTS_ONLY, "Eat"));
		assertFalse(ItemInteractionService.requiresInventoryCard(
			LockState.LOCKED, FoodSettingsMode.POTS_ONLY, "drink"));

		assertFalse(ItemInteractionService.requiresInventoryCard(
			LockState.LOCKED, FoodSettingsMode.UNLOCKED, "Eat"));
		assertFalse(ItemInteractionService.requiresInventoryCard(
			LockState.LOCKED, FoodSettingsMode.UNLOCKED, "Drink"));

		assertTrue(ItemInteractionService.requiresInventoryCard(
			LockState.LOCKED, FoodSettingsMode.UNLOCKED, "Wear"));
		assertTrue(ItemInteractionService.requiresInventoryCard(
			LockState.LOCKED, FoodSettingsMode.UNLOCKED, "Guzzle"));
		assertFalse(ItemInteractionService.requiresInventoryCard(
			LockState.LOCKED, FoodSettingsMode.LOCKED, "Drop"));
		assertFalse(ItemInteractionService.requiresInventoryCard(
			LockState.UNLOCKED, FoodSettingsMode.LOCKED, "Eat"));
	}

	@Test
	public void usedOnTargetsAreSplitAtTheLastArrow()
	{
		assertArrayEquals(new String[]{"Raw shrimps", "Fire"},
			ItemInteractionService.splitUsedOn("Raw shrimps -> Fire"));
		assertArrayEquals(new String[]{"A -> B", "C"},
			ItemInteractionService.splitUsedOn("A -> B -> C"));
		assertNull(ItemInteractionService.splitUsedOn("No target pair"));
	}

	@Test
	public void onlyEnchantSpellsEnterJewelleryRecipeRouting()
	{
		assertTrue(ItemInteractionService.isEnchantSpellLabel("Cast Lvl-3 Enchant"));
		assertTrue(ItemInteractionService.isEnchantSpellLabel("Cast",
			"<col=00ff00>Lvl-4 Enchant</col>", ""));
		assertFalse(ItemInteractionService.isEnchantSpellLabel("Cast Low Level Alchemy"));
		assertFalse(ItemInteractionService.isEnchantSpellLabel("Cast",
			"High Level Alchemy", ""));
		assertFalse(ItemInteractionService.isEnchantSpellLabel("Cast Telekinetic Grab"));
	}

	@Test
	public void productQuantityNormalisationPreservesRealNumericNames()
	{
		assertEquals("arrow shafts",
			ItemInteractionService.stripProductQuantity("45 arrow shafts"));
		assertEquals("Arrow shafts",
			ItemInteractionService.stripProductQuantity("45 x Arrow shafts"));
		assertEquals("Arrow shafts",
			ItemInteractionService.stripProductQuantity("Arrow shafts x45"));
		assertEquals("3rd age pickaxe",
			ItemInteractionService.stripProductQuantity("3rd age pickaxe"));
		assertEquals("", ItemInteractionService.stripProductQuantity(null));
	}

	@Test
	public void productionVerbPrefixesCoverQuantityVariants()
	{
		assertTrue(ItemInteractionService.isMakeVerb("smelt-1"));
		assertTrue(ItemInteractionService.isMakeVerb("make-all"));
		assertTrue(ItemInteractionService.isMakeVerb("fletch-10"));
		assertFalse(ItemInteractionService.isMakeVerb("withdraw-1"));
		assertFalse(ItemInteractionService.isMakeVerb(null));
	}

	@Test
	public void rememberedMaterialUsesExactRecipeAndExpiresAfterOneHundredTicks()
	{
		ItemInteractionService service = serviceForMaterialMemory();
		service.rememberMaterialPair("Knife", "Oak logs", 100);

		assertEquals("Oak logs", service.resolveInterfaceMaterial("Crossbow stock", 200));
		assertNull(service.resolveInterfaceMaterial("Crossbow stock", 201));
		assertNull(service.resolveInterfaceMaterial("Unrelated product", 100));
	}

	@Test
	public void onlyWoodcuttingWorldObjectsHideTheirBlockedOption()
	{
		assertTrue(ItemInteractionService.shouldHideWorldObjectCategory("woodcutting"));
		assertFalse(ItemInteractionService.shouldHideWorldObjectCategory("mining"));
		assertFalse(ItemInteractionService.shouldHideWorldObjectCategory(null));
	}

	@Test
	public void shopBuyingRequiresCoinsAndTheCanonicalItemParent()
	{
		RestrictionDecisionTestSupport.Harness harness =
			RestrictionDecisionTestSupport.harness().ownership(v1Items());
		ItemInteractionService service = serviceForShop(harness);

		assertEquals(List.of("Coins", "Dragon axe"),
			service.evaluateShopItem(6739, "Dragon axe", true).getMissingCards());

		harness.ownership(v1Items(617));
		assertEquals(List.of("Dragon axe"),
			service.evaluateShopItem(6739, "Dragon axe", true).getMissingCards());

		harness.ownership(v1Items(6739));
		assertEquals(List.of("Coins"),
			service.evaluateShopItem(6739, "Dragon axe", true).getMissingCards());

		harness.ownership(v1Items(617, 6739));
		assertFalse(service.evaluateShopItem(6739, "Dragon axe", true).isBlocked());
	}

	@Test
	public void shopSellingRequiresOnlyTheSoldItemAndCoinsExemptionAppliesToBuying()
	{
		RestrictionDecisionTestSupport.Harness harness =
			RestrictionDecisionTestSupport.harness().ownership(v1Items());
		ItemInteractionService service = serviceForShop(harness);

		assertEquals(List.of("Dragon axe"),
			service.evaluateShopItem(6739, "Dragon axe", false).getMissingCards());
		harness.ownership(v1Items(6739));
		assertFalse(service.evaluateShopItem(6739, "Dragon axe", false).isBlocked());
		assertEquals(List.of("Coins"),
			service.evaluateShopItem(6739, "Dragon axe", true).getMissingCards());
		harness.coinsExempt(true);
		assertFalse(service.evaluateShopItem(6739, "Dragon axe", true).isBlocked());
	}

	private static ItemInteractionService serviceForMaterialMemory()
	{
		return new ItemInteractionService(null, null, null, null, null, null, null,
			new RecipeCatalog(new Gson()));
	}

	private static ItemInteractionService serviceForShop(
		RestrictionDecisionTestSupport.Harness harness)
	{
		return new ItemInteractionService(null, null, null, harness.getService(),
			null, null, null, new RecipeCatalog(new Gson()));
	}

	private static TcgOwnershipSnapshot v1Items(Integer... itemIds)
	{
		return TcgOwnershipSnapshot.fromApi(Collections.emptyList(),
			List.of(itemIds), Collections.emptyList(), null);
	}
}
