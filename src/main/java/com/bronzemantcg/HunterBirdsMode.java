package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum HunterBirdsMode
{
	OFF("Off"),
	NET_ONLY("Gear only"),
	ALL_DROPS("All bird drops");

	private final String label;

	HunterBirdsMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
