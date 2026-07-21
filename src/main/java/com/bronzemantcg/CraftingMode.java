package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum CraftingMode
{
	OFF("No Restrictions"),
	INPUT_ONLY("Input Only"),
	BOTH("Input + Output");

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
