package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum SailingUpgradeMode
{
	OFF("Off"),
	PARTS("Parts"),
	PARTS_MATERIALS("Parts + Materials"),
	EVERYTHING("Everything");

	private final String label;

	SailingUpgradeMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
