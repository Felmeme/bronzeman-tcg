package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum SlayerMode
{
	OFF("No Restrictions"),
	MASTER("Require Slayer Master"),
	FULL("Full Task List");

	private final String label;

	SlayerMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
