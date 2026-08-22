package com.bronzemantcg;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DirectItemActionPolicyTest
{
	@Test
	public void directTargetActionsFollowItemUsage()
	{
		assertNotNull(BronzemanTcgPlugin.directItemActionExcludedRoles(LockState.LOCKED));
		assertNull(BronzemanTcgPlugin.directItemActionExcludedRoles(LockState.UNLOCKED));
	}
}
