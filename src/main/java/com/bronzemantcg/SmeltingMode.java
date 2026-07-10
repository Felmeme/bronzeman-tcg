package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum SmeltingMode
{
	OFF("Off"),
	ORE("Ore"),
	BARS("Bars"),
	BOTH("Both");

	private final String label;

	SmeltingMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
