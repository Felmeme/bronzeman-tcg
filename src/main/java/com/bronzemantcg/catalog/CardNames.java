package com.bronzemantcg.catalog;

import java.util.regex.Pattern;

/**
 * Name-normalization helpers shared by every catalog lookup.
 */
public final class CardNames
{
	// "Attack potion(3)", "Saradomin brew(4)" -> card names carry no dose suffix.
	private static final Pattern DOSE_SUFFIX = Pattern.compile("\\s*\\([1-4]\\)$");

	private CardNames()
	{
	}

	public static String stripDoseSuffix(String name)
	{
		return DOSE_SUFFIX.matcher(name).replaceFirst("");
	}
}
