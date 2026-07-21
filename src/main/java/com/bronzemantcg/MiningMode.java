package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum MiningMode
{
	OFF("No Restrictions"),
	TOOL_ONLY("Tool Only"),
	CARD_REQUIRED("Tool + Ore");

	private final String label;

	MiningMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
