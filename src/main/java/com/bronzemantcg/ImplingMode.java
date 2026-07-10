package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum ImplingMode
{
	OFF("Off"),
	NET_ONLY("Butterfly net only"),
	BOTH("Net + jar");

	private final String label;

	ImplingMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
