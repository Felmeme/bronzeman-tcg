package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum FletchingMode
{
	OFF("Off"),
	PRODUCT("Product"),
	PRODUCT_AND_MATERIALS("Product + Materials");

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
