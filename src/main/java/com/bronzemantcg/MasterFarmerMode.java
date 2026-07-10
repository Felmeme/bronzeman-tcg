package com.bronzemantcg;

/**
 * Master Farmer gets his own difficulty dial: he gives seeds, not coin pouches, so the
 * generic pickpocket rule doesn't fit. Rendered as a config dropdown.
 */
public enum MasterFarmerMode
{
	OFF,
	COINS_POUCH,
	INSANITY;

	@Override
	public String toString()
	{
		switch (this)
		{
			case COINS_POUCH:
				return "Coins+Pouch";
			case INSANITY:
				return "Insanity";
			default:
				return "Off";
		}
	}
}
