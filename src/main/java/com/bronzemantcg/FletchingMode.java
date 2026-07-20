package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum FletchingMode
{
	//OFF("No restrictions"),
	INPUT_ONLY("Input Only"),
	//PRODUCT("Output Only"),
	PRODUCT_AND_MATERIALS("Input + Output");


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
