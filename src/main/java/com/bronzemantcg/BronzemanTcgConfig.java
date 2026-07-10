package com.bronzemantcg;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

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

	@ConfigSection(
		name = "Resource nodes",
		description = "Block gathering from skill resource nodes until the card of the item they yield is collected.",
		position = 5
	)
	String resourceNodesSection = "resourceNodesSection";

	@ConfigItem(
		keyName = "restrictWoodcutting",
		name = "Restrict woodcutting",
		description = "Block chopping trees until the respective logs card is collected (e.g. Oak tree needs Oak logs).",
		section = resourceNodesSection,
		position = 0
	)
	default boolean restrictWoodcutting()
	{
		return true;
	}

	@ConfigItem(
		keyName = "restrictMining",
		name = "Restrict mining",
		description = "Block mining rocks until the respective ore card is collected (e.g. Copper rocks need Copper ore).",
		section = resourceNodesSection,
		position = 1
	)
	default boolean restrictMining()
	{
		return true;
	}

	@ConfigItem(
		keyName = "restrictFishing",
		name = "Restrict fishing",
		description = "Block fishing spots until a card of a fish they yield is collected "
			+ "(spot locations can't be told apart, so owning any fish that spot type can give unlocks it).",
		section = resourceNodesSection,
		position = 2
	)
	default boolean restrictFishing()
	{
		return true;
	}

	@ConfigItem(
		keyName = "restrictPickpocketing",
		name = "Restrict pickpocketing",
		description = "Block pickpocketing NPCs until the cards of their loot are collected (e.g. Coins and coin pouch).",
		section = resourceNodesSection,
		position = 3
	)
	default boolean restrictPickpocketing()
	{
		return true;
	}

	@ConfigItem(
		keyName = "restrictCooking",
		name = "Restrict cooking",
		description = "Block using raw food on fires/ranges until the cooked version's card is collected.",
		section = resourceNodesSection,
		position = 4
	)
	default boolean restrictCooking()
	{
		return true;
	}
}
