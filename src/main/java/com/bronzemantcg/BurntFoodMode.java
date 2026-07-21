package com.bronzemantcg;

/** Config dropdown; layers the burnt food card on top of the Cooking requirement. */
public enum BurntFoodMode
{
	REQUIRE_CARD("Require Card"),
	OFF("No Card Needed");

	private final String label;

	BurntFoodMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
