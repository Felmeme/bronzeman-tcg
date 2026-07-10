package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum SalamanderMode
{
	OFF("Off"),
	ROPE_NET("Rope + Net"),
	ITEMS_SALLY("Items + Salamander");

	private final String label;

	SalamanderMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
