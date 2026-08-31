package com.bronzemantcg.restriction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Cohesive policy for ordinary pickpocketing, H.A.M. Members, stalls/chests and Master Farmers.
 * Enum names and constants are persisted RuneLite config state; keep them migration-compatible.
 */
public final class ThievingPolicy
{
	private ThievingPolicy()
	{
	}

	public enum ThievingMode
	{
		OFF("No Restrictions", false, false, Collections.emptySet()),
		NPC_CARD_ONLY("NPC Only", true, true, Collections.emptySet()),
		COINS("Coins", true, false, Set.of("npc", "loot", "loot-ham", "loot-elf")),
		COINS_NPC("Coins + NPC", true, false, Set.of("loot", "loot-ham", "loot-elf")),
		REQUIRE_ALL("Require All", true, false, Collections.emptySet());

		private final String label;
		private final boolean enabled;
		private final boolean npcOnly;
		private final Set<String> excludedRoles;

		ThievingMode(String label, boolean enabled, boolean npcOnly, Set<String> excludedRoles)
		{
			this.label = label;
			this.enabled = enabled;
			this.npcOnly = npcOnly;
			this.excludedRoles = excludedRoles;
		}

		boolean isEnabled()
		{
			return enabled;
		}

		boolean isNpcOnly()
		{
			return npcOnly;
		}

		Set<String> excludedRoles()
		{
			return excludedRoles;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	public enum StallThievingMode
	{
		OFF("No Restrictions", false, false),
		ANY_OF("Any Of", true, false),
		REQUIRE_ALL("All", true, true);

		private final String label;
		private final boolean enabled;
		private final boolean requireAll;

		StallThievingMode(String label, boolean enabled, boolean requireAll)
		{
			this.label = label;
			this.enabled = enabled;
			this.requireAll = requireAll;
		}

		boolean isEnabled()
		{
			return enabled;
		}

		boolean requiresAll()
		{
			return requireAll;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	public enum HamPickpocketingMode
	{
		OFF("No Restrictions", false, Collections.emptySet()),
		MEMBER_CARD("Member Card", true, Set.of("loot", "loot-ham")),
		MEMBER_AND_OUTFIT("Member + Outfit", true, Set.of("loot-ham")),
		FULL_LOOT("Full Loot", true, Collections.emptySet());

		private final String label;
		private final boolean enabled;
		private final Set<String> excludedRoles;

		HamPickpocketingMode(String label, boolean enabled, Set<String> excludedRoles)
		{
			this.label = label;
			this.enabled = enabled;
			this.excludedRoles = excludedRoles;
		}

		boolean isEnabled()
		{
			return enabled;
		}

		Set<String> excludedRoles()
		{
			return excludedRoles;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	static List<String> missingMasterFarmerRequirements(boolean fullLoot,
		List<String> seedCards, Predicate<String> ownsCard)
	{
		if (!fullLoot || seedCards == null || seedCards.isEmpty())
		{
			return Collections.emptyList();
		}
		List<String> missing = new ArrayList<>();
		for (String seed : seedCards)
		{
			if (seed != null && !ownsCard.test(seed))
			{
				missing.add(seed);
			}
		}
		return missing;
	}
}
