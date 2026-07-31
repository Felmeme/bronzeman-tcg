package com.bronzemantcg;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TutorialIslandBypassTest
{
	@Test
	public void activeTutorialProgressBypassesRestrictions()
	{
		assertTrue(BronzemanTcgPlugin.isTutorialIslandProgress(1));
		assertTrue(BronzemanTcgPlugin.isTutorialIslandProgress(670));
	}

	@Test
	public void completedOrUnsetTutorialDoesNotBypassRestrictions()
	{
		assertFalse(BronzemanTcgPlugin.isTutorialIslandProgress(0));
		assertFalse(BronzemanTcgPlugin.isTutorialIslandProgress(1000));
	}
}
