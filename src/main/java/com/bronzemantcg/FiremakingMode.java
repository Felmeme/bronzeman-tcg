package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum FiremakingMode
{
	OFF("Off"),
	JUST_LOGS("Just logs"),
	BOTH("Logs + Tinderbox");

	private final String label;

	FiremakingMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
