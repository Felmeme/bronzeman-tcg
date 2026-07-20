package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum CraftingMode
{
	BOTH("Require Card"),
	INPUT_ONLY("Input Only"),
	OUTPUT_ONLY("Output Required"),
	OFF("No Card Needed");

	private final String label;

	CraftingMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
