package com.bronzemantcg;

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
}
