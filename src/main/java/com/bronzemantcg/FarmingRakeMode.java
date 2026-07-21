package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum FarmingRakeMode
{
	TOOLS("Tools only"),
	BOTH("Tools + Weeds");

	private final String label;

	FarmingRakeMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
