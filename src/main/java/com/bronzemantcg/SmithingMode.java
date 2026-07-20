package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum SmithingMode
{
	OFF("No Card Needed"),
	BARS("Input Only"),
	ITEMS("Output Required"),
	BOTH("Require Card");

	private final String label;

	SmithingMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
