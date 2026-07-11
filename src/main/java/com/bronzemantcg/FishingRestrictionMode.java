package com.bronzemantcg;

/**
 * Fishing spots share one NPC name everywhere, so a rule can only key on the menu option
 * (Net, Harpoon, ...) and lists every fish that option can yield anywhere. This mode picks
 * how much of that union the player must own; rendered as a config dropdown.
 */
public enum FishingRestrictionMode
{
	OFF("Off"),
	ANY_OF("Any of"),
	REQUIRE_ALL("Require ALL");

	private final String label;

	FishingRestrictionMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
