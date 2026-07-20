package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum SmeltingMode
{
	OFF("No Card Needed"),
	ORE("Input Only"),
	BARS("Output Required"),
	BOTH("Require Card");

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
