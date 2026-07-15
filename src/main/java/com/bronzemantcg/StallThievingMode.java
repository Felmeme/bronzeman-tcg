package com.bronzemantcg;

/**
 * Market stalls yield one of several items from a loot table; a rule lists every card-backed
 * loot item as one any-of group. This mode picks how much of that table the player must own,
 * rendered as a config dropdown (mirrors {@link FishingRestrictionMode}).
 */
public enum StallThievingMode
{
	OFF("Off"),
	ANY_OF("Any of"),
	REQUIRE_ALL("All items");

	private final String label;

	StallThievingMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
