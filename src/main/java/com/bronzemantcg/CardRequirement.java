package com.bronzemantcg;

/** Simple two-state config dropdown for restrictions with a single on/off card gate. */
public enum CardRequirement
{
	CARD_REQUIRED("Card Required"),
	NO_CARD("No Card Needed");

	private final String label;

	CardRequirement(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
