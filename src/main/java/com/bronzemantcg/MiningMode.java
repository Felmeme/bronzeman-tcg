package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum MiningMode
{
	CARD_REQUIRED("Card Required"),
	ORE_ONLY("Ore Only"),
	TOOL_ONLY("Tool Only"),
	OFF("No Card Needed");

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
