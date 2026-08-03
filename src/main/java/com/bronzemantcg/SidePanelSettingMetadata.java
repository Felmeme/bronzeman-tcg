package com.bronzemantcg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit presentation metadata for the compact side-panel settings view.
 *
 * RuneLite's normal settings panel continues to use the annotations on
 * {@link BronzemanTcgConfig}. This separate table deliberately avoids asking
 * ConfigManager to reflect over that interface at runtime.
 */
final class SidePanelSettingMetadata
{
	enum Category
	{
		SETTINGS("Settings"),
		GATHERING("Gathering"),
		PRODUCTION("Production"),
		OTHER("Other");

		final String label;

		Category(String label)
		{
			this.label = label;
		}
	}

	enum Section
	{
		GENERAL(Category.SETTINGS, "General"),
		VISUALS(Category.SETTINGS, "Visuals"),
		EXTERNAL_PLUGINS(Category.SETTINGS, "External Plugins"),
		FARMING(Category.GATHERING, "Farming"),
		FISHING(Category.GATHERING, "Fishing"),
		HUNTER(Category.GATHERING, "Hunter"),
		MINING(Category.GATHERING, "Mining"),
		THIEVING(Category.GATHERING, "Thieving"),
		WOODCUTTING(Category.GATHERING, "Woodcutting"),
		COOKING(Category.PRODUCTION, "Cooking"),
		CRAFTING(Category.PRODUCTION, "Crafting"),
		FIREMAKING(Category.PRODUCTION, "Firemaking"),
		FLETCHING(Category.PRODUCTION, "Fletching"),
		HERBLORE(Category.PRODUCTION, "Herblore"),
		RUNECRAFTING(Category.PRODUCTION, "Runecrafting"),
		SMITHING(Category.PRODUCTION, "Smithing"),
		SAILING(Category.OTHER, "Sailing"),
		SLAYER(Category.OTHER, "Slayer");

		final Category category;
		final String label;

		Section(Category category, String label)
		{
			this.category = category;
			this.label = label;
		}
	}

	static final class Entry
	{
		final Section section;
		final String key;
		final String name;
		final String description;
		final int min;
		final int max;

		private Entry(Section section, String key, String name, String description,
			int min, int max)
		{
			this.section = section;
			this.key = key;
			this.name = name;
			this.description = description;
			this.min = min;
			this.max = max;
		}
	}

	private static final List<Entry> ALL;
	private static final Map<String, Entry> BY_KEY;

	static
	{
		List<Entry> entries = new ArrayList<>();
		entries.add(setting(Section.GENERAL, "npcVisibilityMode", "NPC Locks",
			"NPCs with no card in the TCG catalog are never restricted."
				+ "<br>'Prevent Combat': the Attack option is hidden and offensive spells are blocked; "
				+ "talking and using items on the NPC still work."
				+ "<br>'Prevent Interaction': every menu option except Examine is removed, and items "
				+ "can't be used on the NPC."
				+ "<br>'Hide NPCs': locked NPCs are invisible."
				+ "<br>NPCs of quests you have started are always shown and talkable (Attack still "
				+ "needs the card)"));
		entries.add(setting(Section.GENERAL, "groundItemsMode", "Ground Items",
			"'Require Card': hides options for picking up Ground Items"
				+ "<br>'No Card Needed': Ground items do not require their card to unlock."));
		entries.add(setting(Section.GENERAL, "itemUsageMode", "Item Usage",
			"'Require Card': blocked item actions require their card; Drop and Destroy remain allowed."
				+ "<br>'No Card Needed': You can freely use items without their card."));
		entries.add(setting(Section.GENERAL, "foodSettingsMode", "Food Settings",
			"'Require Card': Consumables are not usable without their card."
				+ "<br>'Pots Only': potions are usable without Cards."
				+ "<br>'Food Only': food usable without Cards."
				+ "<br>'No Card Needed': both are usable without cards."));
		entries.add(setting(Section.GENERAL, "bankingMode", "Banking",
			"How locked inventory items interact with the bank while Item Usage is Locked."
				+ "<br>'Off': they can't be banked at all."
				+ "<br>'Deposit Only': Deposits work, withdrawals stay blocked until the card unlocks."
				+ "<br>'Full Banking': Deposit and Withrdrawls allowed without needing card."));
		entries.add(setting(Section.GENERAL, "grandExchangeMode", "Grand Exchange",
			"'Require Card': items whose card you have not collected can't be selected in the "
				+ "Grand Exchange search.<br>Items with no card can always be bought."));
		entries.add(setting(Section.GENERAL, "coinMode", "Coin Settings",
			"'Require Card': the Coins item is restricted wherever Bronzeman can identify it."
				+ "<br>'No Card Needed': Coins are never restricted."));
		entries.add(setting(Section.GENERAL, "lootExemptNames", "Exempt List",
			"Comma-separated and case-insensitive."
				+ "<br>Use * to match any number of characters, for example Rune* or *potion*."
				+ "<br>Items and NPC names added to the list are never restricted even without their card."
				+ "<br>For universal items that would otherwise make the game unplayable."
				+ "<br>Use this list to add exceptions based on things like Foil Card Rules."));
		entries.add(setting(Section.GENERAL, "showLockedMenuOptions", "Show Mouseover Options",
			"Also keep blocked tree and activity-NPC options visible."
				+ "<br>Other resource and ground-item options are already kept visible."
				+ "<br>Ordinary NPC options remain controlled by NPC Locks."
				+ "<br>The click is still blocked and chat explains which cards are missing."));

		entries.add(setting(Section.VISUALS, "lockedItemMarkMode", "Locked Item Indicator",
			"Fade items in your inventory and bank while their card is uncollected."
				+ "<br>'Fade + Icon' adds a small bank-filler badge on top of the faded sprite. "));
		entries.add(setting(Section.VISUALS, "tintLockedNpcs", "Tint locked NPCs grey",
			"NPCs whose card you have not collected are greyed out in the world."));
		entries.add(setting(Section.VISUALS, "lockedOutlineColor", "Outline colour",
			"Colour (and opacity) of the locked-NPC outline."));
		entries.add(range(Section.VISUALS, "lockedOutlineWidth", "Outline width",
			"Thickness of the locked-NPC outline in pixels.", 1, 10));
		entries.add(range(Section.VISUALS, "lockedOutlineFeather", "Outline feather",
			"How softly the outline fades at its edge (0 = hard line).", 0, 6));
		entries.add(setting(Section.VISUALS, "duelistCityMode", "Duelist City Mode",
			"IT'S TIME, TO..."));

		entries.add(setting(Section.EXTERNAL_PLUGINS, "acceptSharedUnlocks",
			"TCG Locked Party Sharing", "Toggle on for group play while using TCG Locked."
				+ "<br>Use the `Party` plugin to share the cards you pull with your group members."
				+ "<br>Use the TCG Locked side panel to sync your group."));

		entries.add(setting(Section.FARMING, "farmingRakeMode", "Raking",
			"Raking a patch requires the Rake card."
				+ "<br>'Tools + Weeds' additionally requires the Weeds card."));
		entries.add(setting(Section.FARMING, "compostMode", "Compost bins",
			"'Card Required': collecting compost from a bin requires any compost card."));
		entries.add(setting(Section.FISHING, "fishingMode", "Fishing Options",
			"Restrict fishing using the exact fishing spot and selected method."
				+ "<br>'Tools Only': every carried card-backed tool, bait or consumable applicable "
				+ "to that method must be unlocked."
				+ "<br>'Tools + Any Fish': also requires any one carded catch from that method."
				+ "<br>'Tools + Fish': requires every carded catch from that method."
				+ "<br>'No Restrictions': no fishing restriction."));
		entries.add(setting(Section.HUNTER, "hunterMode", "Hunter Options",
			"'Tools Only': every supported method requires its normal tool cards."
				+ "<br>'All Cards': additionally requires the relevant creature, caught-item and "
				+ "guaranteed-loot cards."
				+ "<br>Hunter-rumour items are only used by the separate rumour-master setting."));
		entries.add(setting(Section.HUNTER, "restrictHunterRumours", "Extreme: rumour masters",
			"Block each Hunters' Guild rumour master until you own the card of every creature "
				+ "they can assign.<br>Creatures with no card are excluded from the requirement."));
		entries.add(setting(Section.MINING, "miningMode", "Mining Options",
			"Block mining rocks until the respective ore card is collected."
				+ "<br>e.g. Copper rocks need Copper ore."
				+ "<br>'Tool Only' instead requires the pickaxe you are carrying."
				+ "<br>'Card Required' needs both."));
		entries.add(setting(Section.THIEVING, "thievingMode", "Pickpocketing Options",
			"'NPC Only': requires the target's NPC card when one exists."
				+ "<br>'Coins + Pouch': requires Coins and Coin pouch."
				+ "<br>'Coins + Pouch + NPC': also requires the target's NPC card when one exists."
				+ "<br>'Require All': requires all reviewed loot and the NPC card."
				+ "<br>H.A.M. Members and Master Farmers use their separate Full Loot options."));
		entries.add(setting(Section.THIEVING, "stallThievingMode", "Stalls & Chests",
			"'Off': no stall or thievable-chest restriction."
				+ "<br>'Any Of': owning any one card-backed loot item unlocks it."
				+ "<br>'All': every card-backed item on its loot table is required."
				+ "<br>Uncarded loot is ignored; unrelated storage and reward chests are unaffected."));
		entries.add(setting(Section.THIEVING, "hamFullLoot", "H.A.M. Full Loot",
			"With All mode, require all 37 pickpocket items instead of the Ham Outfit pieces."));
		entries.add(setting(Section.THIEVING, "masterFarmerInsanity",
			"Master Farmer Full Loot", "With All mode, require all 45 Master Farmer seeds."));
		entries.add(setting(Section.WOODCUTTING, "woodcuttingMode", "Woodcutting Options",
			"Block chopping trees until the respective logs card is collected."
				+ "<br>e.g. Oak tree needs Oak logs."
				+ "<br>'Tool Only' instead requires the axe you are carrying."
				+ "<br>'Card Required' needs both."));

		entries.add(setting(Section.COOKING, "cookingMode", "Cooking",
			"'No Restrictions': Cooking will not have extra restrictions."
				+ "<br>'Input Only': the raw food's card, e.g. Raw shrimps."
				+ "<br>'Input + Output': the raw AND cooked food cards, e.g. Raw shrimps and Shrimps."));
		entries.add(setting(Section.COOKING, "burntFoodMode", "Burnt Food Cards",
			"'Require Card': also need the burnt version's card to cook something."
				+ "<br>Fish that burns into the generic 'Burnt fish' has no card, so they are never affected."
				+ "<br>No effect while Cooking is set to 'No restrictions'."));
		entries.add(setting(Section.CRAFTING, "craftingMode", "Crafting Options",
			"'No Restrictions': no crafting cards required."
				+ "<br>'Input Only': the material cards, e.g. Needle, Thread and Leather."
				+ "<br>'Input + Output': the materials AND the finished item's card."));
		entries.add(setting(Section.CRAFTING, "restrictEnchanting", "Restrict enchanting",
			"Enchanting jewellery requires the unenchanted item's card AND the enchanted product's card."));
		entries.add(setting(Section.CRAFTING, "requireCrushedGem", "Require crushed gem",
			"Also require the Crushed gem card to cut gems that can shatter."
				+ "<br>Only applies to the gems that can actually be crushed; the rest are unaffected."
				+ "<br>No effect while 'Restrict crafting' is off."));
		entries.add(setting(Section.FIREMAKING, "tinderboxMode", "Tinderbox Use",
			"'Card Required': lighting a fire requires the Tinderbox card."
				+ "<br>The logs themselves are gated when you obtain them (Woodcutting / loot), not here."));
		entries.add(setting(Section.FLETCHING, "fletchingMode", "Fletching",
			"'No restrictions': fletching is never blocked."
				+ "<br>'Input Only': recipes only require the input item cards."
				+ "<br>'Input + Output': require all cards in fletching recipes."));
		entries.add(setting(Section.HERBLORE, "herbloreMode", "Herblore Options",
			"'Off': Herblore recipes are unrestricted."
				+ "<br>'Input Only': require every physical ingredient card."
				+ "<br>'Require Unfinished': additionally require the unfinished potion card when creating it."
				+ "<br>'Require All': additionally require every recipe's output card."
				+ "<br>Card names are dose-less, so any dose matches the one card."));
		entries.add(setting(Section.RUNECRAFTING, "runecraftingMode", "Runecrafting",
			"Crafting at an altar requires essence + talisman (tiara counts) cards."
				+ "<br>'Talisman and Runes' additionally requires the crafted rune's card."
				+ "<br>Altars with no talisman (Astral/Blood/Soul) skip that part."
				+ "<br>Guardians of the Rift uses its supplied essence/portals, so only the "
				+ "crafted rune card applies there in the stricter mode."));
		entries.add(setting(Section.SMITHING, "smeltingMode", "Smelting",
			"Smelting a bar at a furnace requires:"
				+ "<br>'Ore' = the ore cards, 'Bars' = the bar card, 'Both' = all of them."));
		entries.add(setting(Section.SMITHING, "smithingMode", "Smithing",
			"Smithing an item at an anvil requires:"
				+ "<br>'Bars' = the bar card, 'Items' = the product's card, 'Both' = both."
				+ "<br>The Hammer card is always required while enabled."));

		entries.add(setting(Section.SAILING, "sailingUpgradeMode", "Boat upgrades",
			"Installing a hull or keel tier requires cards."
				+ "<br>'Parts': the tier's part card (e.g. Oak hull parts)."
				+ "<br>'Parts + Materials': also the underlying material card (Oak plank, Bronze bar)."
				+ "<br>'Everything': additionally the log card and the Large part variant's card."
				+ "<br>Masts, helms, cannons and cargo holds have no part cards and are never restricted."));
		entries.add(setting(Section.SAILING, "restrictSalvaging", "Salvaging",
			"Salvaging a shipwreck requires the card of the salvage type that wreck tier yields."
				+ "<br>e.g. Barracuda shipwrecks need the Barracuda salvage card."));
		entries.add(setting(Section.SLAYER, "slayerMode", "Slayer Options",
			"Using a slayer master (Talk-to/Assignment/Trade/Rewards)."
				+ "<br>'Require Slayer Master': needs that master's own NPC card."
				+ "<br>'Full Task List': also needs the card of every monster that master can assign."
				+ "<br>Boss and revenant assignment categories are excluded; reviewed bosses can "
				+ "still substitute for a normal higher-master task."));
		entries.add(setting(Section.SLAYER, "restrictSlayerSuperiors", "Include superiors",
			"'Full Task List' additionally demands each master's superior variant cards "
				+ "(Abhorrent spectre, King kurask...)."
				+ "<br>Superiors only spawn after unlocking Bigger and Badder with slayer points, so "
				+ "anyone this affects has already opted into the grind."
				+ "<br>No effect unless Slayer Options is 'Full Task List'. Superiors are always "
				+ "separately combat-locked by their own cards regardless of this setting."));

		Map<String, Entry> byKey = new LinkedHashMap<>();
		for (Entry entry : entries)
		{
			if (byKey.put(entry.key, entry) != null)
			{
				throw new IllegalStateException("Duplicate side-panel setting: " + entry.key);
			}
		}
		ALL = Collections.unmodifiableList(entries);
		BY_KEY = Collections.unmodifiableMap(byKey);
	}

	private SidePanelSettingMetadata()
	{
	}

	static List<Entry> all()
	{
		return ALL;
	}

	static Entry require(String key)
	{
		Entry entry = BY_KEY.get(key);
		if (entry == null)
		{
			throw new IllegalArgumentException("Missing side-panel metadata: " + key);
		}
		return entry;
	}

	private static Entry setting(Section section, String key, String name, String description)
	{
		return new Entry(section, key, name, description, Integer.MIN_VALUE, Integer.MAX_VALUE);
	}

	private static Entry range(Section section, String key, String name, String description,
		int min, int max)
	{
		return new Entry(section, key, name, description, min, max);
	}
}
