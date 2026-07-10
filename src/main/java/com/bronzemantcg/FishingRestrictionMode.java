package com.bronzemantcg;

/**
 * Fishing spots share one NPC name everywhere, so a rule can only key on the menu option
 * (Net, Harpoon, ...) and lists every fish that option can yield anywhere. This mode picks
 * how much of that union the player must own; rendered as a config dropdown.
 */
public enum FishingRestrictionMode
{
	OFF,
	ANY_OF,
	REQUIRE_ALL;

	@Override
	public String toString()
	{
		switch (this)
		{
			case ANY_OF:
				return "Any of";
			case REQUIRE_ALL:
				return "Require ALL";
			default:
				return "Off";
		}
	}
}
