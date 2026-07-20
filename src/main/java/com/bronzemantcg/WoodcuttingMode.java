package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum WoodcuttingMode
{
	CARD_REQUIRED("Card Required"),
	LOGS_ONLY("Logs Only"),
	TOOL_ONLY("Tool Only"),
	OFF("No Card Needed");

	private final String label;

	WoodcuttingMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
