package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum SmithingMode
{
	OFF("Off"),
	BARS("Bars"),
	ITEMS("Items"),
	BOTH("Both");

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
