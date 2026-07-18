package com.bronzemantcg;

import java.util.regex.Pattern;

/**
 * Name-normalization helpers shared by every catalog lookup.
 */
final class CardNames
{
	// Total cards in the OSRS TCG catalog; matches the TCG plugin's own total.
	// Update by hand if osrs-tcg ever grows its Card.json.
	static final int TOTAL_CARDS = 6376;

	// "Attack potion(3)", "Saradomin brew(4)" -> card names carry no dose suffix.
	private static final Pattern DOSE_SUFFIX = Pattern.compile("\\s*\\([1-4]\\)$");

	private CardNames()
	{
	}

	static String stripDoseSuffix(String name)
	{
		return DOSE_SUFFIX.matcher(name).replaceFirst("");
	}
}
