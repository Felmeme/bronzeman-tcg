package com.bronzemantcg.restriction;

import java.util.Locale;
import java.util.Map;

/** Hunter thresholds at which a flying creature can be caught without a net or jar. */
final class BarehandedHunterEligibilityPolicy
{
	private static final Map<String, Integer> REQUIRED_LEVELS = Map.ofEntries(
		Map.entry("ruby harvest", 25),
		Map.entry("sapphire glacialis", 35),
		Map.entry("snowy knight", 45),
		Map.entry("black warlock", 55),
		Map.entry("sunlight moth", 75),
		Map.entry("moonlight moth", 85),
		Map.entry("baby impling", 27),
		Map.entry("young impling", 32),
		Map.entry("gourmet impling", 38),
		Map.entry("earth impling", 46),
		Map.entry("essence impling", 52),
		Map.entry("eclectic impling", 60),
		Map.entry("nature impling", 68),
		Map.entry("magpie impling", 75),
		Map.entry("ninja impling", 84),
		Map.entry("crystal impling", 90),
		Map.entry("dragon impling", 93),
		Map.entry("lucky impling", 99));

	private BarehandedHunterEligibilityPolicy()
	{
	}

	static boolean canCatch(String creatureName, int boostedHunterLevel)
	{
		if (creatureName == null)
		{
			return false;
		}
		Integer required = REQUIRED_LEVELS.get(creatureName.trim().toLowerCase(Locale.ROOT));
		return required != null && boostedHunterLevel >= required;
	}
}
