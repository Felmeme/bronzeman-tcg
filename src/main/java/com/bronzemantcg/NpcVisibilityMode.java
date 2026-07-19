package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum NpcVisibilityMode
{
	OFF("Off"),
	PREVENT_COMBAT("Prevent Combat"),
	PREVENT_INTERACTION("Prevent Interaction"),
	HIDE("Hide NPCs");

	private final String label;

	NpcVisibilityMode(String label)
	{
		this.label = label;
	}

	/** True for the tiers that strip every menu option, not just combat. */
	boolean strictOptions()
	{
		return this == PREVENT_INTERACTION || this == HIDE;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
