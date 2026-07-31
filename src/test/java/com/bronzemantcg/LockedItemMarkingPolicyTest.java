package com.bronzemantcg;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LockedItemMarkingPolicyTest
{
	@Test
	public void chosenModeReturnsWhenItemUsageIsRestrictedAgain()
	{
		assertFalse(BronzemanTcgPlugin.isLockedItemMarkingActive(
			LockState.UNLOCKED, LockedItemMarkMode.TRANSPARENT_ICON, false));
		assertTrue(BronzemanTcgPlugin.isLockedItemMarkingActive(
			LockState.LOCKED, LockedItemMarkMode.TRANSPARENT_ICON, false));
	}

	@Test
	public void offAndBypassStillDisableMarking()
	{
		assertFalse(BronzemanTcgPlugin.isLockedItemMarkingActive(
			LockState.LOCKED, LockedItemMarkMode.OFF, false));
		assertFalse(BronzemanTcgPlugin.isLockedItemMarkingActive(
			LockState.LOCKED, LockedItemMarkMode.TRANSPARENT, true));
	}
}
