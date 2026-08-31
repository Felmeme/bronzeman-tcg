package com.bronzemantcg;

import com.bronzemantcg.restriction.ItemInteractionService;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LockedItemMenuPolicyTest
{
	@Test
	public void explicitDropRemainsAllowed()
	{
		assertTrue(ItemInteractionService.isLockedItemDisposalOption("Drop"));
		assertTrue(ItemInteractionService.isLockedItemDisposalOption("drop"));
		assertTrue(ItemInteractionService.isLockedItemDisposalOption("Destroy"));
	}

	@Test
	public void promotedNonDisposalActionsRemainBlocked()
	{
		assertFalse(ItemInteractionService.isLockedItemDisposalOption("Use"));
		assertFalse(ItemInteractionService.isLockedItemDisposalOption("Wear"));
		assertFalse(ItemInteractionService.isLockedItemDisposalOption("Drink"));
		assertFalse(ItemInteractionService.isLockedItemDisposalOption(null));
	}

	@Test
	public void inventoryOperationsRemainVisibleForMenuEntrySwapper()
	{
		assertTrue(ItemInteractionService.isInventoryMenuVisibilityExempt(
			MenuAction.CC_OP, InterfaceID.INVENTORY));
		assertTrue(ItemInteractionService.isInventoryMenuVisibilityExempt(
			MenuAction.CC_OP_LOW_PRIORITY, InterfaceID.INVENTORY));
		assertTrue(ItemInteractionService.isInventoryMenuVisibilityExempt(
			MenuAction.WIDGET_TARGET, InterfaceID.INVENTORY));

		assertFalse(ItemInteractionService.isInventoryMenuVisibilityExempt(
			MenuAction.CC_OP, InterfaceID.SHOPMAIN));
		assertFalse(ItemInteractionService.isInventoryMenuVisibilityExempt(
			MenuAction.GROUND_ITEM_FIRST_OPTION, InterfaceID.INVENTORY));
	}

	@Test
	public void shopBuyAndSellSidesStayDistinct()
	{
		assertTrue(ItemInteractionService.isShopBuyOption(InterfaceID.SHOPMAIN, "Buy 1"));
		assertFalse(ItemInteractionService.isShopBuyOption(InterfaceID.SHOPSIDE, "Buy 1"));
		assertTrue(ItemInteractionService.isShopSellOption(InterfaceID.SHOPSIDE, "Sell 5"));
		assertFalse(ItemInteractionService.isShopSellOption(InterfaceID.SHOPMAIN, "Sell 5"));
		assertFalse(ItemInteractionService.isShopSellOption(InterfaceID.SHOPSIDE, null));
	}
}
