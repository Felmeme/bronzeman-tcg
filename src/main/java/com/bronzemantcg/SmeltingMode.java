package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum SmeltingMode
{
	OFF("No Card Needed"),
	ORE("Input Only"),
	BOTH("Input + Output");

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
