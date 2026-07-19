package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum FoodSettingsMode
{
	LOCKED("Require Card"),
	POTS_ONLY("Pots Only"),
	FOOD_ONLY("Food Only"),
	UNLOCKED("No Card Needed");

	private final String label;

	FoodSettingsMode(String label)
	{
		this.label = label;
	}

	boolean potionsUsable()
	{
		return this == POTS_ONLY || this == UNLOCKED;
	}

	boolean foodUsable()
	{
		return this == FOOD_ONLY || this == UNLOCKED;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
