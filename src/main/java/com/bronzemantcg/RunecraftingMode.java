package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum RunecraftingMode
{
	OFF("Off"),
	TALISMAN("Talisman"),
	TALISMAN_RUNES("Talisman and Runes");

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
