package com.bronzemantcg;

/**
 * Config dropdown; see BronzemanTcgConfig for the behaviour of each mode.
 *
 * <p>Named for raking because that is what it originally covered and its config keyName
 * ("farmingRakeMode") is a public contract that must not change. It now governs farming
 * generally - raking and planting alike. Constant names are likewise stored values and
 * must not be renamed; only their labels are display text.
 */
public enum FarmingRakeMode
{
	OFF("No Restrictions"),
	TOOLS("Tools Only"),
	BOTH("Tools and Seeds");

	private final String label;

	FarmingRakeMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
