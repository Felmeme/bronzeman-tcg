package com.bronzemantcg;

import com.bronzemantcg.restriction.HerbloreMode;
import com.bronzemantcg.catalog.HerbloreRecipeStage;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HerbloreModeTest
{
	@Test
	public void modesApplyToTheApprovedRecipeStages()
	{
		assertFalse(HerbloreMode.OFF.enforcesInputs());
		assertFalse(HerbloreMode.OFF.enforcesOutput(HerbloreRecipeStage.UNFINISHED));

		assertTrue(HerbloreMode.INPUT_ONLY.enforcesInputs());
		assertFalse(HerbloreMode.INPUT_ONLY.enforcesOutput(HerbloreRecipeStage.UNFINISHED));
		assertFalse(HerbloreMode.INPUT_ONLY.enforcesOutput(HerbloreRecipeStage.FINISHED));

		assertTrue(HerbloreMode.REQUIRE_UNFINISHED.enforcesInputs());
		assertTrue(HerbloreMode.REQUIRE_UNFINISHED.enforcesOutput(
			HerbloreRecipeStage.UNFINISHED));
		assertFalse(HerbloreMode.REQUIRE_UNFINISHED.enforcesOutput(
			HerbloreRecipeStage.FINISHED));
		assertFalse(HerbloreMode.REQUIRE_UNFINISHED.enforcesOutput(
			HerbloreRecipeStage.UPGRADE));

		assertTrue(HerbloreMode.REQUIRE_ALL.enforcesInputs());
		assertTrue(HerbloreMode.REQUIRE_ALL.enforcesOutput(
			HerbloreRecipeStage.UNFINISHED));
		assertTrue(HerbloreMode.REQUIRE_ALL.enforcesOutput(
			HerbloreRecipeStage.FINISHED));
		assertTrue(HerbloreMode.REQUIRE_ALL.enforcesOutput(
			HerbloreRecipeStage.UPGRADE));
	}

	@Test
	public void missingStageFailsClosedForRequireUnfinished()
	{
		assertTrue(HerbloreMode.REQUIRE_UNFINISHED.enforcesOutput(null));
	}
}
