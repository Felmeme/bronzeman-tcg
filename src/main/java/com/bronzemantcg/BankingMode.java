package com.bronzemantcg;

/** Config dropdown; see BronzemanTcgConfig for the behaviour of each mode. */
public enum BankingMode
{
	OFF("Off"),
	DEPOSIT_ONLY("Deposit Only"),
	FULL("Full Banking");

	private final String label;

	BankingMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
