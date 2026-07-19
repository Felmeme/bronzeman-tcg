package com.bronzemantcg;

/** Standard two-value dropdown shared by the item settings; Locked = restricted. */
public enum LockState
{
	LOCKED("Require Card"),
	UNLOCKED("No Card Needed");

	private final String label;

	LockState(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
