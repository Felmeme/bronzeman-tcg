package com.bronzemantcg;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(BronzemanTcgConfig.GROUP)
public interface BronzemanTcgConfig extends Config
{
	String GROUP = "bronzemantcg";

	@ConfigItem(
		keyName = "restrictAttacks",
		name = "Restrict attacks",
		description = "Block attacking any NPC whose card you have not yet collected in the OSRS TCG plugin. "
			+ "NPCs with no card in the TCG catalog are never restricted.",
		position = 0
	)
	default boolean restrictAttacks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "restrictSpellCasts",
		name = "Restrict spells/items on NPCs",
		description = "Also block casting spells on, or using items on, uncollected NPCs "
			+ "(prevents bypassing the restriction with magic or ranged via spell casts).",
		position = 1
	)
	default boolean restrictSpellCasts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "restrictLoot",
		name = "Restrict loot pickup",
		description = "Block picking up (or telegrabbing) ground items whose card you have not yet "
			+ "collected in the OSRS TCG plugin. Items with no card in the TCG catalog are never restricted.",
		position = 2
	)
	default boolean restrictLoot()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lootExemptNames",
		name = "Loot exempt list",
		description = "Comma-separated item names that are always lootable even without their card, "
			+ "e.g. universal drops that would make the early game unplayable. Case-insensitive.",
		position = 3
	)
	default String lootExemptNames()
	{
		return "Coins";
	}

	@ConfigItem(
		keyName = "chatFeedback",
		name = "Chat feedback",
		description = "Send a game chat message explaining why an action was blocked.",
		position = 4
	)
	default boolean chatFeedback()
	{
		return true;
	}
}
