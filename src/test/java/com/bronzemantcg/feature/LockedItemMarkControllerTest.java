package com.bronzemantcg.feature;

import com.bronzemantcg.BronzemanTcgConfig;
import com.bronzemantcg.restriction.LockState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.ScriptID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LockedItemMarkControllerTest
{
	@Test
	public void chosenModeReturnsWhenItemUsageIsRestrictedAgain()
	{
		assertFalse(LockedItemMarkController.isMarkingActive(
			LockState.UNLOCKED, LockedItemMarkMode.TRANSPARENT_ICON, false));
		assertTrue(LockedItemMarkController.isMarkingActive(
			LockState.LOCKED, LockedItemMarkMode.TRANSPARENT, false));
		assertTrue(LockedItemMarkController.isMarkingActive(
			LockState.LOCKED, LockedItemMarkMode.TRANSPARENT_ICON, false));
	}

	@Test
	public void offAndBypassDisableMarking()
	{
		assertFalse(LockedItemMarkController.isMarkingActive(
			LockState.LOCKED, LockedItemMarkMode.OFF, false));
		assertFalse(LockedItemMarkController.isMarkingActive(
			LockState.LOCKED, LockedItemMarkMode.TRANSPARENT, true));
	}

	@Test
	public void lockedItemReceivesOwnedOpacitySignature()
	{
		assertEquals(LockedItemMarkController.LOCKED_ITEM_OPACITY,
			LockedItemMarkController.resolveOpacity(true, 0));
	}

	@Test
	public void onlyOwnedOpacitySignatureIsRestored()
	{
		assertEquals(0, LockedItemMarkController.resolveOpacity(
			false, LockedItemMarkController.LOCKED_ITEM_OPACITY));
		assertEquals(75, LockedItemMarkController.resolveOpacity(false, 75));
	}

	@Test
	public void inventoryAndBankRedrawScriptsTriggerRefresh()
	{
		assertTrue(LockedItemMarkController.isMarkRedrawScript(ScriptID.INVENTORY_DRAWITEM));
		assertTrue(LockedItemMarkController.isMarkRedrawScript(ScriptID.BANKMAIN_BUILD));
		assertFalse(LockedItemMarkController.isMarkRedrawScript(-1));
	}

	@Test
	public void shopGroupsTriggerRefresh()
	{
		assertTrue(LockedItemMarkController.isMarkContainerGroup(InterfaceID.SHOPMAIN));
		assertTrue(LockedItemMarkController.isMarkContainerGroup(InterfaceID.SHOPSIDE));
		assertFalse(LockedItemMarkController.isMarkContainerGroup(InterfaceID.BANKMAIN));
	}

	@Test
	public void onlyMarkingConfigChangesTriggerRefresh()
	{
		assertTrue(LockedItemMarkController.shouldRefreshForConfig(
			BronzemanTcgConfig.GROUP, "itemUsageMode"));
		assertTrue(LockedItemMarkController.shouldRefreshForConfig(
			BronzemanTcgConfig.GROUP, "lockedItemMarkMode"));
		assertFalse(LockedItemMarkController.shouldRefreshForConfig(
			BronzemanTcgConfig.GROUP, "tintLockedNpcs"));
		assertFalse(LockedItemMarkController.shouldRefreshForConfig(
			"other", "itemUsageMode"));
	}

	@Test
	public void periodicSweepRunsEveryFiveTicks()
	{
		for (int tick = 1; tick < 15; tick++)
		{
			assertEquals(tick % 5 == 0, LockedItemMarkController.isPeriodicRefreshTick(tick));
		}
		assertFalse(LockedItemMarkController.isPeriodicRefreshTick(0));
	}

	@Test
	public void catalogueRevisionChangeTriggersImmediateRefresh()
	{
		assertTrue(LockedItemMarkController.isCatalogRevisionChanged(4L, 5L));
		assertFalse(LockedItemMarkController.isCatalogRevisionChanged(5L, 5L));
	}
}
