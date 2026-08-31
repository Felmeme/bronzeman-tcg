package com.bronzemantcg.restriction;


/**
 * Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. Mirrors the
 * mining/woodcutting dials: the tool half requires each card-backed input group for the exact
 * spot and action (bait and feathers count as tools; uncarded inputs never lock). Bare-handed
 * harpooning is the explicit no-tool exception. The two catch modes require any or every carded
 * catch respectively.
 */
public enum FishingRestrictionMode
{
	OFF("No Restrictions"),
	TOOL_ONLY("Tools Only"),
	CARD_REQUIRED("Tools + Any of"),
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
