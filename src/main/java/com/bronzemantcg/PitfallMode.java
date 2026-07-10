package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum PitfallMode
{
	OFF("Off"),
	TOOLS("Just tools"),
	ALL("All");

	private final String label;

	PitfallMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
