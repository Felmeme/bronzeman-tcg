package com.bronzemantcg;

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
		assertTrue(BronzemanTcgPlugin.isLockedItemDisposalOption("Drop"));
		assertTrue(BronzemanTcgPlugin.isLockedItemDisposalOption("drop"));
		assertTrue(BronzemanTcgPlugin.isLockedItemDisposalOption("Destroy"));
	}

	@Test
	public void promotedNonDisposalActionsRemainBlocked()
	{
		assertFalse(BronzemanTcgPlugin.isLockedItemDisposalOption("Use"));
		assertFalse(BronzemanTcgPlugin.isLockedItemDisposalOption("Wear"));
		assertFalse(BronzemanTcgPlugin.isLockedItemDisposalOption("Drink"));
		assertFalse(BronzemanTcgPlugin.isLockedItemDisposalOption(null));
	}

	@Test
	public void inventoryOperationsRemainVisibleForMenuEntrySwapper()
	{
		assertTrue(BronzemanTcgPlugin.isInventoryMenuVisibilityExempt(
			MenuAction.CC_OP, InterfaceID.INVENTORY));
		assertTrue(BronzemanTcgPlugin.isInventoryMenuVisibilityExempt(
			MenuAction.CC_OP_LOW_PRIORITY, InterfaceID.INVENTORY));
		assertTrue(BronzemanTcgPlugin.isInventoryMenuVisibilityExempt(
			MenuAction.WIDGET_TARGET, InterfaceID.INVENTORY));

		assertFalse(BronzemanTcgPlugin.isInventoryMenuVisibilityExempt(
			MenuAction.CC_OP, InterfaceID.SHOPMAIN));
		assertFalse(BronzemanTcgPlugin.isInventoryMenuVisibilityExempt(
			MenuAction.GROUND_ITEM_FIRST_OPTION, InterfaceID.INVENTORY));
	}

	@Test
	public void lateFilterKeepsDisposalAndHidesOtherLockedOperations()
	{
		assertFalse(BronzemanTcgPlugin.shouldHideLockedInventoryOption("Drop", true));
		assertFalse(BronzemanTcgPlugin.shouldHideLockedInventoryOption("Destroy", true));
		assertTrue(BronzemanTcgPlugin.shouldHideLockedInventoryOption("Wear", true));
		assertTrue(BronzemanTcgPlugin.shouldHideLockedInventoryOption("Drink", true));
		assertFalse(BronzemanTcgPlugin.shouldHideLockedInventoryOption("Wear", false));
	}
}
