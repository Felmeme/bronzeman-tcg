package com.bronzemantcg;

/** Config dropdown and enforcement policy for ordinary Herblore recipes. */
public enum HerbloreMode
{
	OFF("No Restrictions"),
	INPUT_ONLY("Input Only"),
	REQUIRE_UNFINISHED("Input + Pots"),
	REQUIRE_ALL("Require All");

	private final String label;

	HerbloreMode(String label)
	{
		this.label = label;
	}

	boolean enforcesInputs()
	{
		return this != OFF;
	}

	boolean enforcesOutput(HerbloreRecipeStage stage)
	{
		return this == REQUIRE_ALL
			|| (this == REQUIRE_UNFINISHED
				&& (stage == HerbloreRecipeStage.UNFINISHED || stage == null));
	}

	@Override
	public String toString()
	{
		return label;
	}
}
