package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum FletchingMode
{
	OFF("No Card Needed"),
	PRODUCT("Output Required"),
	INPUT_ONLY("Input Only"),
	PRODUCT_AND_MATERIALS("Require Card");


	private final String label;

	FletchingMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
