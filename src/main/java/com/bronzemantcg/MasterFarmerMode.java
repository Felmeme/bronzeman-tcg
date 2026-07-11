package com.bronzemantcg;

/**
 * Master Farmer gets his own difficulty dial: he gives seeds, not coin pouches, so the
 * generic pickpocket rule doesn't fit. Rendered as a config dropdown.
 */
public enum MasterFarmerMode
{
	OFF("Off"),
	COINS_POUCH("Coins+Pouch"),
	INSANITY("Insanity");

	private final String label;

	MasterFarmerMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
