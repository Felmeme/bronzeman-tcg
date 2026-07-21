package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum WoodcuttingMode
{
	OFF("No Restrictions"),
	TOOL_ONLY("Tool Only"),
	LOGS_ONLY("Logs + Axe");


	private final String label;

	WoodcuttingMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
