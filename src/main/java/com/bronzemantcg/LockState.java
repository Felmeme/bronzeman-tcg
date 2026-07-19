package com.bronzemantcg;

/** Standard two-value dropdown shared by the item settings; Locked = restricted. */
public enum LockState
{
	LOCKED("Locked"),
	UNLOCKED("Unlocked");

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
