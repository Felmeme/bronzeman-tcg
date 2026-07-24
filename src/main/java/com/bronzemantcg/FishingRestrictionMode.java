package com.bronzemantcg;

/**
 * Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. Mirrors the
 * mining/woodcutting dials: the tool half blocks while any carried, card-backed tool for
 * the clicked spot type is locked (uncarded tools never lock, per the standing rule), and
 * "Tools + Fish" additionally needs any card from the spot type's yield union.
 */
public enum FishingRestrictionMode
{
	OFF("No Restrictions"),
	TOOL_ONLY("Tools Only"),
	CARD_REQUIRED("Tools + Fish");

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
