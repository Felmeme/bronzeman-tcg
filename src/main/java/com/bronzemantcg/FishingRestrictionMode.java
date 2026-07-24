package com.bronzemantcg;

/**
 * Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. Mirrors the
 * mining/woodcutting dials: the tool half blocks while any carried, card-backed input for
 * the exact spot and action is locked (bait and feathers count as tools; uncarded inputs
 * never lock). The two catch modes require any or every carded catch respectively.
 */
public enum FishingRestrictionMode
{
	OFF("No Restrictions"),
	TOOL_ONLY("Tools Only"),
	CARD_REQUIRED("Tools + Any Fish"),
	ALL_CATCHES("Tools + Fish");

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
