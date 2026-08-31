package com.bronzemantcg;

import com.bronzemantcg.restriction.RestrictionDecisionService;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TutorialIslandBypassTest
{
	@Test
	public void activeTutorialProgressBypassesRestrictions()
	{
		assertTrue(RestrictionDecisionService.isTutorialIslandProgress(1));
		assertTrue(RestrictionDecisionService.isTutorialIslandProgress(670));
	}

	@Test
	public void completedOrUnsetTutorialDoesNotBypassRestrictions()
	{
		assertFalse(RestrictionDecisionService.isTutorialIslandProgress(0));
		assertFalse(RestrictionDecisionService.isTutorialIslandProgress(1000));
	}
}
