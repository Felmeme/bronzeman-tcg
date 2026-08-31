package com.bronzemantcg.catalog;

import java.util.Locale;
import java.util.Set;

/** Classifies carried item names that satisfy gathering-tool requirements. */
public final class ToolNameClassifier
{
	/*
	 * Woodcutting axes only. Generic " axe" suffix matching would also accept
	 * carded combat weapons such as the Zombie axe, Soulreaper axe and
	 * Morrigan's throwing axe. Felling axes are retained for future catalogue
	 * support; untracked names remain inert until OSRS TCG cards them.
	 */
	private static final Set<String> WOODCUTTING_AXES = Set.of(
		"bronze axe", "iron axe", "steel axe", "black axe", "mithril axe",
		"adamant axe", "rune axe", "dragon axe", "crystal axe", "infernal axe",
		"gilded axe", "3rd age axe", "corrupted axe",
		"bronze felling axe", "iron felling axe", "steel felling axe",
		"black felling axe", "mithril felling axe", "adamant felling axe",
		"rune felling axe", "dragon felling axe", "crystal felling axe");

	private ToolNameClassifier()
	{
	}

	public static boolean isMiningPickaxe(String itemName)
	{
		return itemName != null
			&& itemName.toLowerCase(Locale.ROOT).endsWith(" pickaxe");
	}

	public static boolean isWoodcuttingAxe(String itemName)
	{
		return itemName != null
			&& WOODCUTTING_AXES.contains(itemName.toLowerCase(Locale.ROOT));
	}
}
