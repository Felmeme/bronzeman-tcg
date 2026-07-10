package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum FarmingPlantMode
{
	OFF("Off"),
	TOOLS("Tools only"),
	TOOLS_SEEDS("Tools + Seeds"),
	ALL("All");

	private final String label;

	FarmingPlantMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
