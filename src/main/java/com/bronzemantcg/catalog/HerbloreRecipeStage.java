package com.bronzemantcg.catalog;


import java.util.Locale;

/** The point in a potion chain represented by a generated Herblore recipe. */
public enum HerbloreRecipeStage
{
	UNFINISHED,
	FINISHED,
	UPGRADE;

	public static HerbloreRecipeStage from(String value)
	{
		if (value == null)
		{
			return null;
		}
		try
		{
			return valueOf(value.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex)
		{
			return null;
		}
	}
}
