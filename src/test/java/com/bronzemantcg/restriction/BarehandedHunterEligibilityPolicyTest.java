package com.bronzemantcg.restriction;

import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BarehandedHunterEligibilityPolicyTest
{
	@Test
	public void everySupportedCreatureChangesAtItsExactHunterThreshold()
	{
		Map<String, Integer> thresholds = Map.ofEntries(
			Map.entry("Ruby harvest", 25),
			Map.entry("Sapphire glacialis", 35),
			Map.entry("Snowy knight", 45),
			Map.entry("Black warlock", 55),
			Map.entry("Sunlight moth", 75),
			Map.entry("Moonlight moth", 85),
			Map.entry("Baby impling", 27),
			Map.entry("Young impling", 32),
			Map.entry("Gourmet impling", 38),
			Map.entry("Earth impling", 46),
			Map.entry("Essence impling", 52),
			Map.entry("Eclectic impling", 60),
			Map.entry("Nature impling", 68),
			Map.entry("Magpie impling", 75),
			Map.entry("Ninja impling", 84),
			Map.entry("Crystal impling", 90),
			Map.entry("Dragon impling", 93),
			Map.entry("Lucky impling", 99));

		for (Map.Entry<String, Integer> threshold : thresholds.entrySet())
		{
			assertFalse(threshold.getKey(), BarehandedHunterEligibilityPolicy.canCatch(
				threshold.getKey(), threshold.getValue() - 1));
			assertTrue(threshold.getKey(), BarehandedHunterEligibilityPolicy.canCatch(
				threshold.getKey(), threshold.getValue()));
		}
		assertFalse(BarehandedHunterEligibilityPolicy.canCatch("Unknown creature", 99));
	}
}
