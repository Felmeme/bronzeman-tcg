package com.bronzemantcg;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

/**
 * Bronzeman-style restriction driven by the OSRS TCG plugin's collection:
 * you may only attack NPCs, loot ground items, and gather from resource
 * nodes whose card(s) you have already pulled.
 *
 * Interop is read-only via osrs-tcg's persisted ConfigManager state
 * (see {@link TcgCollectionReader}); there is no compile-time dependency,
 * so both plugins can be installed independently from the Plugin Hub.
 */
@Slf4j
@PluginDescriptor(
	name = "Bronzeman TCG",
	description = "Restricts attacking NPCs, looting items and gathering resources until you've collected their card in the OSRS TCG plugin",
	tags = {"bronzeman", "tcg", "restriction", "ironman", "challenge"}
)
public class BronzemanTcgPlugin extends Plugin
{
	private static final String ATTACK_OPTION = "attack";
	private static final String TAKE_OPTION = "take";
	private static final String PICKPOCKET_OPTION = "pickpocket";
	private static final String MASTER_FARMER_NAME = "master farmer";
	private static final String USED_ON_SEPARATOR = " -> ";
	private static final long CHAT_THROTTLE_MS = 1_200L;
	private static final int MAX_LISTED_MISSING_CARDS = 4;

	@Inject
	private Client client;

	@Inject
	private ItemManager itemManager;

	@Inject
	private BronzemanTcgConfig config;

	@Inject
	private TcgCollectionReader collectionReader;

	@Inject
	private TrackedMonsterCatalog monsterCatalog;

	@Inject
	private TrackedItemCatalog itemCatalog;

	@Inject
	private ResourceNodeCatalog nodeCatalog;

	private long lastBlockMessageMs;

	@Override
	protected void startUp()
	{
		collectionReader.invalidate();
		log.info("Bronzeman TCG started. Tracking {} TCG-linked NPCs, {} items, {} resource node rules.",
			monsterCatalog.size(), itemCatalog.size(), nodeCatalog.size());
	}

	@Override
	protected void shutDown()
	{
		log.info("Bronzeman TCG stopped.");
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		// New account/profile: never let a previous profile's collection linger.
		collectionReader.invalidate();
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		NPC npc = event.getMenuEntry().getNpc();
		if (npc != null)
		{
			handleNpcInteraction(event, npc);
			return;
		}

		MenuAction action = event.getMenuAction();
		if (action == null)
		{
			return;
		}
		switch (action)
		{
			case GROUND_ITEM_FIRST_OPTION:
			case GROUND_ITEM_SECOND_OPTION:
			case GROUND_ITEM_THIRD_OPTION:
			case GROUND_ITEM_FOURTH_OPTION:
			case GROUND_ITEM_FIFTH_OPTION:
			case WIDGET_TARGET_ON_GROUND_ITEM:
				handleGroundItemInteraction(event, action);
				return;
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
				handleGameObjectInteraction(event);
				return;
			case WIDGET_TARGET_ON_GAME_OBJECT:
				handleItemOnGameObject(event);
				return;
			default:
		}
	}

	private void handleNpcInteraction(MenuOptionClicked event, NPC npc)
	{
		String npcName = resolveNpcName(npc);
		if (npcName == null || npcName.isEmpty())
		{
			return;
		}

		// Attack / spell-or-item-on-NPC restriction against the monster catalog.
		// Not part of the TCG catalog at all -> never restrict (it could never be unlocked).
		// Owning any variant card (normal or foil) -> allowed. Cards with wiki-style
		// disambiguation suffixes ("Soldier (Yanille)") all unlock the plain NPC name,
		// since that's the only name RuneLite exposes at attack time.
		if (isRestrictedNpcInteraction(event) && !isUnlocked(monsterCatalog, npcName))
		{
			event.consume();
			sendBlockedMessage(npcName);
			return;
		}

		// Resource-node rules on NPCs: pickpocketing, fishing spot options.
		String option = event.getMenuOption();
		if (option == null)
		{
			return;
		}
		String cleanOption = Text.removeTags(option).trim();

		// Master Farmer has his own difficulty dial (he gives seeds, not coin pouches),
		// independent of the generic pickpocketing toggle.
		if (MASTER_FARMER_NAME.equals(npcName.toLowerCase(Locale.ROOT))
			&& PICKPOCKET_OPTION.equals(cleanOption.toLowerCase(Locale.ROOT)))
		{
			checkMasterFarmer(event);
			return;
		}

		checkNodeRule(event, ResourceNodeCatalog.KIND_NPC, npcName, cleanOption);
	}

	private void handleGroundItemInteraction(MenuOptionClicked event, MenuAction action)
	{
		if (!config.restrictLoot())
		{
			return;
		}
		if (action != MenuAction.WIDGET_TARGET_ON_GROUND_ITEM)
		{
			// Telegrab (widget-on-ground-item) is always loot; plain clicks only for "Take".
			String option = event.getMenuOption();
			if (option == null
				|| !TAKE_OPTION.equals(Text.removeTags(option).trim().toLowerCase(Locale.ROOT)))
			{
				return;
			}
		}

		ItemComposition composition = itemManager.getItemComposition(event.getId());
		String itemName = composition.getName();
		if (itemName == null || itemName.isEmpty() || isLootExempt(itemName))
		{
			return;
		}

		if (isUnlocked(itemCatalog, itemName))
		{
			return;
		}

		event.consume();
		sendBlockedMessage(itemName);
	}

	private void handleGameObjectInteraction(MenuOptionClicked event)
	{
		String option = event.getMenuOption();
		if (option == null)
		{
			return;
		}
		String objectName = Text.removeTags(event.getMenuTarget()).trim();
		checkNodeRule(event, ResourceNodeCatalog.KIND_OBJECT, objectName, Text.removeTags(option));
	}

	private void handleItemOnGameObject(MenuOptionClicked event)
	{
		// Menu target reads "<used item> -> <target object>", e.g. "Raw shrimps -> Fire".
		String target = Text.removeTags(event.getMenuTarget());
		int separator = target.lastIndexOf(USED_ON_SEPARATOR);
		if (separator < 0)
		{
			return;
		}
		String usedItemName = target.substring(0, separator).trim();
		String objectName = target.substring(separator + USED_ON_SEPARATOR.length()).trim();
		checkNodeRule(event, ResourceNodeCatalog.KIND_ITEM_ON_OBJECT, usedItemName, objectName);
	}

	private void checkMasterFarmer(MenuOptionClicked event)
	{
		MasterFarmerMode mode = config.masterFarmerMode();
		List<String> required;
		switch (mode)
		{
			case COINS_POUCH:
				required = List.of("Coins", "Coin pouch");
				break;
			case INSANITY:
				required = nodeCatalog.getMasterFarmerSeedCards();
				break;
			default:
				return;
		}
		if (required.isEmpty())
		{
			// Insanity selected but the seed list is missing from the data file: leaving him
			// pickpocketable would silently disable the mode, so fall back to Coins+Pouch.
			required = List.of("Coins", "Coin pouch");
		}

		List<String> missing = missingCards(required);
		if (missing.isEmpty())
		{
			return;
		}
		event.consume();
		sendBlockedCardsMessage(missing);
	}

	private void checkNodeRule(MenuOptionClicked event, String kind, String name, String option)
	{
		ResourceNodeCatalog.Rule rule = nodeCatalog.find(kind, name, option);
		if (rule == null)
		{
			return;
		}

		boolean forceAllInGroups = false;
		if ("fishing".equals(rule.category))
		{
			// Fishing has a three-way mode instead of a toggle; it overrides the rule's
			// any-of group since the any/all choice is the player's difficulty dial.
			FishingRestrictionMode mode = config.fishingMode();
			if (mode == FishingRestrictionMode.OFF)
			{
				return;
			}
			forceAllInGroups = mode == FishingRestrictionMode.REQUIRE_ALL;
		}
		else if (!isCategoryRestricted(rule.category))
		{
			return;
		}

		List<String> missing = rule.missingRequirements(
			collectionReader.getOwnedCardNamesLowerCase(), Collections.emptySet(), forceAllInGroups);
		if (missing.isEmpty())
		{
			return;
		}

		event.consume();
		sendBlockedCardsMessage(missing);
	}

	private boolean isCategoryRestricted(String category)
	{
		switch (category)
		{
			case "woodcutting":
				return config.restrictWoodcutting();
			case "mining":
				return config.restrictMining();
			case "pickpocketing":
				return config.restrictPickpocketing();
			case "cooking":
				return config.restrictCooking();
			default:
				// Data shipped a category this build has no toggle for: restrict, so new
				// data stays challenge-mode-loud rather than silently inert.
				return true;
		}
	}

	private List<String> missingCards(List<String> requiredCards)
	{
		Set<String> owned = collectionReader.getOwnedCardNamesLowerCase();
		List<String> missing = new ArrayList<>();
		for (String card : requiredCards)
		{
			if (!owned.contains(card.trim().toLowerCase(Locale.ROOT)))
			{
				missing.add(card);
			}
		}
		return missing;
	}

	private boolean isUnlocked(CardNameCatalog catalog, String entityName)
	{
		Set<String> variantCards = catalog.getCardVariantsLowerCase(entityName);
		if (variantCards.isEmpty())
		{
			// Untracked -> never restricted (it could never be unlocked).
			return true;
		}
		Set<String> owned = collectionReader.getOwnedCardNamesLowerCase();
		for (String card : variantCards)
		{
			if (owned.contains(card))
			{
				return true;
			}
		}
		return false;
	}

	private boolean isRestrictedNpcInteraction(MenuOptionClicked event)
	{
		MenuAction action = event.getMenuAction();
		if (action == null)
		{
			return false;
		}

		switch (action)
		{
			case NPC_FIRST_OPTION:
			case NPC_SECOND_OPTION:
			case NPC_THIRD_OPTION:
			case NPC_FOURTH_OPTION:
			case NPC_FIFTH_OPTION:
			{
				if (!config.restrictAttacks())
				{
					return false;
				}
				String option = event.getMenuOption();
				return option != null
					&& ATTACK_OPTION.equals(Text.removeTags(option).trim().toLowerCase(Locale.ROOT));
			}
			case WIDGET_TARGET_ON_NPC:
				// Spell cast on NPC, or item used on NPC.
				return config.restrictSpellCasts();
			default:
				return false;
		}
	}

	private boolean isLootExempt(String itemName)
	{
		String needle = itemName.trim().toLowerCase(Locale.ROOT);
		Set<String> exempt = new HashSet<>();
		for (String entry : config.lootExemptNames().split(","))
		{
			exempt.add(entry.trim().toLowerCase(Locale.ROOT));
		}
		return exempt.contains(needle);
	}

	private String resolveNpcName(NPC npc)
	{
		// Prefer the transformed composition so multi-form NPCs report their current form's name.
		NPCComposition composition = npc.getTransformedComposition();
		String name = composition != null ? composition.getName() : npc.getName();
		if (name == null)
		{
			return null;
		}
		return Text.removeTags(name).trim();
	}

	private void sendBlockedMessage(String entityName)
	{
		if (!config.chatFeedback())
		{
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastBlockMessageMs < CHAT_THROTTLE_MS)
		{
			return;
		}
		lastBlockMessageMs = now;
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			String.format(Locale.US,
				"[Bronzeman TCG] You haven't collected the %s card yet - open more packs!", entityName),
			null);
	}

	private void sendBlockedCardsMessage(List<String> missingCards)
	{
		if (missingCards.size() == 1)
		{
			sendBlockedMessage(missingCards.get(0));
			return;
		}
		if (!config.chatFeedback())
		{
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastBlockMessageMs < CHAT_THROTTLE_MS)
		{
			return;
		}
		lastBlockMessageMs = now;
		// Insanity mode can be missing dozens of seeds; keep the chat line readable.
		String listed = String.join(", ", missingCards.subList(0,
			Math.min(missingCards.size(), MAX_LISTED_MISSING_CARDS)));
		if (missingCards.size() > MAX_LISTED_MISSING_CARDS)
		{
			listed += String.format(Locale.US, " and %d more",
				missingCards.size() - MAX_LISTED_MISSING_CARDS);
		}
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			String.format(Locale.US,
				"[Bronzeman TCG] You haven't collected these cards yet: %s - open more packs!", listed),
			null);
	}

	@Provides
	BronzemanTcgConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BronzemanTcgConfig.class);
	}
}
