package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum ForcedDropMode
{
	OFF("Off"),
	DROP("Drop only"),
	ALLOW_BANKING("Allow banking");

	private final String label;

	ForcedDropMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
