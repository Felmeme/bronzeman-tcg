package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum RunecraftingMode
{
	OFF("No Restrictions"),
	TALISMAN("Input Only"),
	TALISMAN_RUNES("Input + Output");

	private final String label;

	RunecraftingMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
