package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum LockedItemMarkMode
{
	OFF("Off"),
	TRANSPARENT("Fade"),
	TRANSPARENT_ICON("Fade + Icon");

	private final String label;

	LockedItemMarkMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
