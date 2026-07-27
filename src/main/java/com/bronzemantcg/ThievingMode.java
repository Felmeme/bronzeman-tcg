package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum ThievingMode
{
	OFF("No Restrictions"),
	COINS_POUCH("Coins+Pouch"),
	NPC_ONLY("Coins+Pouch+NPC"),
	NPC_AND_LOOT("Require All");

	private final String label;

	ThievingMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
