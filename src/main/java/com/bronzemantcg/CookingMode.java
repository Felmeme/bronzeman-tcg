package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum CookingMode
{
	OFF("No restrictions"),
	INPUT_ONLY("Input Only"),
	INPUT_OUTPUT("Input + Output");

	private final String label;

	CookingMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
