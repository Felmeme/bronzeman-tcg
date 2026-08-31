package com.bronzemantcg.restriction;


/** One shared difficulty setting for ordinary Hunter methods. */
public enum HunterMode
{
	OFF("No Restrictions"),
	TOOLS_ONLY("Tools Only"),
	ALL_CARDS("All Cards");

	private final String label;

	HunterMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
