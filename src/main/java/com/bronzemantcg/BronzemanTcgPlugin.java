package com.bronzemantcg;

import com.google.inject.Provides;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.Text;

/**
 * Bronzeman-style restriction driven by the OSRS TCG plugin's collection:
 * attacking NPCs, looting, equipping, buying, gathering and processing are
 * gated behind owning the matching card(s).
 *
 * Interop is read-only via osrs-tcg's persisted ConfigManager state
 * (see {@link TcgCollectionReader}); there is no compile-time dependency,
 * so both plugins can be installed independently from the Plugin Hub.
 *
 * Everything works by consuming MenuOptionClicked. Known limitation
 * (documented, owner-accepted): keyboard-driven interface defaults
 * (spacebar "make") bypass the menu pipeline and cannot be consumed.
 */
@Slf4j
@PluginDescriptor(
	name = "Bronzeman TCG",
	description = "Restricts attacking, looting, equipping, buying, gathering and crafting until you've collected the card in the OSRS TCG plugin",
	tags = {"bronzeman", "tcg", "restriction", "ironman", "challenge"}
)
public class BronzemanTcgPlugin extends Plugin implements RenderCallback
{
	private static final String ATTACK_OPTION = "attack";
	private static final String TAKE_OPTION = "take";
	private static final String PICKPOCKET_OPTION = "pickpocket";
	private static final String MASTER_FARMER_NAME = "master farmer";
	private static final String USED_ON_SEPARATOR = " -> ";
	private static final String CAST_PREFIX = "Cast ";
	private static final String CRUSHED_GEM_CARD = "Crushed gem";
	private static final String CRUSHED_GEM_CARD_LOWER = "crushed gem";
	private static final long CHAT_THROTTLE_MS = 1_200L;
	private static final int MAX_LISTED_MISSING_CARDS = 4;

	private static final Set<String> EQUIP_VERBS = new HashSet<>(List.of("wear", "wield", "equip"));
	private static final Set<String> FORCED_DROP_ALLOWED = new HashSet<>(List.of(
		"drop", "examine", "destroy", "release"));
	// Production-interface verbs: the furnace smelting screen (and possibly other stations)
	// uses a different widget group than the SKILLMULTI/SMITHING ones we match directly, so
	// any interface click with one of these options gets a recipe lookup by product name.
	private static final Set<String> MAKE_VERBS = new HashSet<>(List.of(
		"smelt", "make", "make-x", "make-all", "make sets", "craft", "smith",
		"string", "mix", "cook", "bake", "fletch", "spin", "fire"));
	// LMS island map regions, copied verbatim from RuneLite core's LootTrackerPlugin; a lenient
	// fallback so restrictions stay lifted even if a client update shifts the BR_INGAME timing.
	private static final Set<Integer> LMS_REGIONS = new HashSet<>(List.of(
		13658, 13659, 13660, 13914, 13915, 13916, 13918, 13919, 13920,
		14174, 14175, 14176, 14430, 14431, 14432));

	@Inject
	private Client client;

	@Inject
	private ChatMessageManager chatMessageManager;

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

	@Inject
	private RecipeCatalog recipeCatalog;

	@Inject
	private QuestCatalog questCatalog;

	@Inject
	private ContentCatalog contentCatalog;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BronzemanTcgOverlay overlay;

	@Inject
	private TcgStatsOverlay statsOverlay;

	@Inject
	private RenderCallbackManager renderCallbackManager;

	private long lastBlockMessageMs;
	private BronzemanTcgPanel panel;
	private NavigationButton navButton;
	private int tickCounter;
	private String lootExemptRaw;
	private Set<String> lootExemptSet = Collections.emptySet();

	@Override
	protected void startUp()
	{
		collectionReader.invalidate();

		panel = new BronzemanTcgPanel(monsterCatalog, itemCatalog, nodeCatalog, questCatalog,
			contentCatalog, collectionReader, config);
		navButton = NavigationButton.builder()
			.tooltip("Bronzeman TCG")
			.icon(drawPanelIcon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);
		overlayManager.add(statsOverlay);
		renderCallbackManager.register(this);

		log.info("Bronzeman TCG started. Tracking {} TCG-linked NPCs, {} items, {} node rules, {} recipe rules.",
			monsterCatalog.size(), itemCatalog.size(), nodeCatalog.size(), recipeCatalog.size());
	}

	@Override
	protected void shutDown()
	{
		renderCallbackManager.unregister(this);
		overlayManager.remove(overlay);
		overlayManager.remove(statsOverlay);
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		log.info("Bronzeman TCG stopped.");
	}

	/**
	 * Render callback: returning false keeps the entity out of the scene (and removes its
	 * clickbox). NPCs only - ground items don't route through this callback (verified
	 * in-game on the predecessor hook), so they stay visible and rely on the
	 * Take-blocking instead. Called many times per frame on the client thread, so the
	 * check stays to map lookups.
	 */
	@Override
	public boolean addEntity(Renderable renderable, boolean ui)
	{
		if (!config.hideLockedEntities() || !(renderable instanceof NPC) || isLmsBypassed())
		{
			return true;
		}
		String name = resolveNpcName((NPC) renderable);
		if (name == null || name.isEmpty())
		{
			return true;
		}
		// CotS override: with guard marking allowed, Guards must stay visible even while
		// locked, or hide-locked-NPCs silently bricks the quest.
		if (config.allowCotsGuards() && "guard".equals(name.toLowerCase(Locale.ROOT)))
		{
			return true;
		}
		return isUnlocked(monsterCatalog, name);
	}

	/**
	 * Menu-entry hiding: blocked options are removed from menus as they assemble, so a
	 * locked tree simply has no Chop down and a locked ground item's left-click falls
	 * through to Walk here. Decisions come from the same evaluate* helpers as the
	 * click-consuming path (which stays as the final guard for keyboard flows and
	 * anything this pass misses), so the hidden set and the blocked set cannot drift.
	 * Interface/bank/shop/item-on-item menus stay consume-only by design. Fires many
	 * times per frame while menus assemble; every check must stay a map lookup.
	 */
	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.hideLockedOptions() || isLmsBypassed())
		{
			return;
		}
		MenuEntry entry = event.getMenuEntry();
		if (shouldHideEntry(entry))
		{
			client.getMenu().removeMenuEntry(entry);
		}
	}

	private boolean shouldHideEntry(MenuEntry entry)
	{
		MenuAction type = entry.getType();
		String option = Text.removeTags(entry.getOption()).trim();
		if (option.isEmpty())
		{
			return false;
		}
		String optionLower = option.toLowerCase(Locale.ROOT);

		switch (type)
		{
			case NPC_FIRST_OPTION:
			case NPC_SECOND_OPTION:
			case NPC_THIRD_OPTION:
			case NPC_FOURTH_OPTION:
			case NPC_FIFTH_OPTION:
			{
				NPC npc = entry.getNpc();
				if (npc == null)
				{
					return false;
				}
				String name = resolveNpcName(npc);
				if (name == null || name.isEmpty())
				{
					return false;
				}
				if (ATTACK_OPTION.equals(optionLower) && config.restrictAttacks()
					&& !isUnlocked(monsterCatalog, name))
				{
					return true;
				}
				if (MASTER_FARMER_NAME.equals(name.toLowerCase(Locale.ROOT))
					&& PICKPOCKET_OPTION.equals(optionLower))
				{
					List<String> missing = masterFarmerMissing();
					return missing != null && !missing.isEmpty();
				}
				return evaluateNodeRule(ResourceNodeCatalog.KIND_NPC, name, option) != null;
			}
			case GROUND_ITEM_FIRST_OPTION:
			case GROUND_ITEM_SECOND_OPTION:
			case GROUND_ITEM_THIRD_OPTION:
			case GROUND_ITEM_FOURTH_OPTION:
			case GROUND_ITEM_FIFTH_OPTION:
			{
				if (!config.restrictLoot() || !TAKE_OPTION.equals(optionLower))
				{
					return false;
				}
				String itemName = itemManager.getItemComposition(entry.getIdentifier()).getName();
				return itemName != null && !itemName.isEmpty() && !isLootExempt(itemName)
					&& !isUnlocked(itemCatalog, itemName);
			}
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
			{
				String objectName = Text.removeTags(entry.getTarget()).trim();
				return !objectName.isEmpty()
					&& evaluateNodeRule(ResourceNodeCatalog.KIND_OBJECT, objectName, option) != null;
			}
			case CC_OP:
			case CC_OP_LOW_PRIORITY:
			{
				// Inventory item ops only; bank/shop/interface menus stay consume-only.
				if (!entry.isItemOp() || entry.getItemId() <= 0
					|| WidgetUtil.componentToInterface(entry.getParam1()) != InterfaceID.INVENTORY)
				{
					return false;
				}
				String itemName = itemManager.getItemComposition(entry.getItemId()).getName();
				if (itemName == null || itemName.isEmpty())
				{
					return false;
				}
				// Activity gates (trap laying) apply even to exempt items, mirroring the click path.
				if (evaluateNodeRule(ResourceNodeCatalog.KIND_INVENTORY, itemName, option) != null)
				{
					return true;
				}
				if (isLootExempt(itemName) || isUnlocked(itemCatalog, itemName))
				{
					return false;
				}
				if (config.restrictEquipping() && EQUIP_VERBS.contains(optionLower))
				{
					return true;
				}
				if (config.restrictPotionDrinking() && "drink".equals(optionLower))
				{
					return true;
				}
				return config.forcedDropMode() != ForcedDropMode.OFF
					&& !FORCED_DROP_ALLOWED.contains(optionLower);
			}
			default:
				return false;
		}
	}

	/**
	 * Upside-down bronze med helm - the bronzeman emblem. The real item sprite is pulled
	 * from the game cache and rotated 180°; it loads asynchronously, so we hand the nav
	 * button a buffer now and paint the sprite into it whenever it arrives.
	 */
	private BufferedImage drawPanelIcon()
	{
		BufferedImage icon = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		AsyncBufferedImage helm = itemManager.getImage(ItemID.BRONZE_MED_HELM);
		Runnable paint = () ->
		{
			Graphics2D g = icon.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.rotate(Math.PI, 12, 12);
			// Item sprites are 36x32; scale preserving aspect, centred vertically.
			g.drawImage(helm, 0, 2, 24, 21, null);
			g.dispose();
		};
		helm.onLoaded(paint);
		paint.run();
		return icon;
	}

	/**
	 * Gathers the nearby tracked-NPC snapshot on the client thread every few ticks and
	 * hands it to the panel on the Swing EDT (game state must not be read from Swing).
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (panel == null || ++tickCounter % 5 != 0)
		{
			return;
		}
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}
		WorldPoint playerLocation = player.getWorldLocation();
		Map<String, Integer> nearest = new HashMap<>();
		for (NPC npc : client.getNpcs())
		{
			if (npc == null)
			{
				continue;
			}
			String name = resolveNpcName(npc);
			if (name == null || name.isEmpty() || !monsterCatalog.isTracked(name))
			{
				continue;
			}
			int distance = npc.getWorldLocation().distanceTo(playerLocation);
			nearest.merge(name, distance, Math::min);
		}
		List<BronzemanTcgPanel.NearbyEntry> entries = new ArrayList<>(nearest.size());
		for (Map.Entry<String, Integer> e : nearest.entrySet())
		{
			entries.add(new BronzemanTcgPanel.NearbyEntry(e.getKey(), e.getValue()));
		}
		entries.sort(Comparator.comparingInt(e -> e.distance));

		BronzemanTcgPanel target = panel;
		SwingUtilities.invokeLater(() ->
		{
			if (target.isShowing())
			{
				target.updateNearby(entries);
			}
		});
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
		if (isLmsBypassed())
		{
			return;
		}
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
			case CC_OP:
			case CC_OP_LOW_PRIORITY:
				handleWidgetOp(event);
				return;
			case WIDGET_TARGET:
				handleUseSelected(event);
				return;
			case WIDGET_TARGET_ON_WIDGET:
				handleWidgetOnWidget(event);
				return;
			default:
		}
	}

	// ------------------------------------------------------------------ NPC path

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

		// Resource-node rules on NPCs: pickpocketing, fishing spots, slayer masters,
		// rumour masters, pitfall beasts.
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

	private void checkMasterFarmer(MenuOptionClicked event)
	{
		List<String> missing = masterFarmerMissing();
		if (missing == null || missing.isEmpty())
		{
			return;
		}
		event.consume();
		sendBlockedCardsMessage(missing);
	}

	/** Missing cards for Master Farmer's own dial; null when the mode is Off. */
	private List<String> masterFarmerMissing()
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
				return null;
		}
		if (required.isEmpty())
		{
			// Insanity selected but the seed list is missing from the data file: leaving him
			// pickpocketable would silently disable the mode, so fall back to Coins+Pouch.
			required = List.of("Coins", "Coin pouch");
		}
		return missingCards(required);
	}

	// ------------------------------------------------------------------ ground items

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

	// ------------------------------------------------------------------ game objects

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
		if (checkNodeRule(event, ResourceNodeCatalog.KIND_ITEM_ON_OBJECT, usedItemName, objectName))
		{
			return;
		}
		// Processing recipes triggered by item-on-object (e.g. ore on furnace).
		checkRecipe(event, RecipeCatalog.KIND_ITEM_ON_OBJECT, usedItemName, objectName);
	}

	// ------------------------------------------------------------------ widget ops (CC_OP)

	private void handleWidgetOp(MenuOptionClicked event)
	{
		MenuEntry entry = event.getMenuEntry();
		String option = Text.removeTags(event.getMenuOption()).trim();
		String optionLower = option.toLowerCase(Locale.ROOT);
		int group = WidgetUtil.componentToInterface(entry.getParam1());

		// Bank: discriminate on option text (the bank-side panels are not the inventory group).
		if (optionLower.startsWith("withdraw"))
		{
			if (config.forcedDropMode() != ForcedDropMode.OFF)
			{
				blockIfLockedItem(event, itemOpName(event, entry));
			}
			return;
		}
		if (optionLower.startsWith("deposit"))
		{
			// Allow-banking mode: the bank is the holding pen, deposits always allowed.
			if (config.forcedDropMode() == ForcedDropMode.DROP)
			{
				blockIfLockedItem(event, itemOpName(event, entry));
			}
			return;
		}

		if (group == InterfaceID.SHOPMAIN)
		{
			if (config.restrictBuying() && optionLower.startsWith("buy"))
			{
				blockIfLockedItem(event, itemOpName(event, entry));
			}
			return;
		}

		if (group == InterfaceID.SKILLMULTI || group == InterfaceID.SMITHING)
		{
			// Make-X product click: only the product name is reliable. Mouse-only block;
			// keyboard defaults bypass the menu pipeline (owner-accepted limitation).
			// Node rules get first refusal, same as the make-verb fallback below - cooking
			// lives there, and this interface is how range-click "Cook" flows arrive.
			String product = Text.removeTags(event.getMenuTarget()).trim();
			if (!product.isEmpty()
				&& !checkNodeRule(event, ResourceNodeCatalog.KIND_INTERFACE, product,
					ResourceNodeCatalog.ANY_OPTION))
			{
				checkRecipe(event, RecipeCatalog.KIND_INTERFACE, product, null);
			}
			return;
		}

		if (group == InterfaceID.INVENTORY)
		{
			handleInventoryOp(event, entry, option, optionLower);
			return;
		}

		// Grand Exchange search results live in the chatbox; consuming the selection is
		// the best preventive hook available (best-effort - keyboard flows may bypass).
		if (config.restrictBuying() && group == InterfaceID.CHATBOX && isGrandExchangeOpen())
		{
			String targetName = Text.removeTags(event.getMenuTarget()).trim();
			if (!targetName.isEmpty() && !isLootExempt(targetName)
				&& !isUnlocked(itemCatalog, targetName))
			{
				event.consume();
				sendBlockedMessage(targetName);
				return;
			}
		}

		// Production interfaces outside the groups matched above (e.g. furnace smelting,
		// the shipwright's upgrade menu): a make-verb click still names its product in the
		// menu target. Prefix match so quantity variants ("Smelt-1", "Make-5") are covered.
		// Node rules get first refusal (they carry role-based modes, e.g. sailing upgrades).
		if (isMakeVerb(optionLower))
		{
			String product = Text.removeTags(event.getMenuTarget()).trim();
			if (!product.isEmpty()
				&& !checkNodeRule(event, ResourceNodeCatalog.KIND_INTERFACE, product,
					ResourceNodeCatalog.ANY_OPTION))
			{
				checkRecipe(event, RecipeCatalog.KIND_INTERFACE, product, null);
			}
		}
	}


	private void handleInventoryOp(MenuOptionClicked event, MenuEntry entry, String option,
		String optionLower)
	{
		String itemName = itemOpName(event, entry);
		if (itemName == null || itemName.isEmpty())
		{
			return;
		}

		// Node rules keyed on inventory items (laying bird snares / box traps).
		if (checkNodeRule(event, ResourceNodeCatalog.KIND_INVENTORY, itemName, option))
		{
			return;
		}

		// Firemaking: "Light" on logs is the tinderbox recipe from the item-op side.
		if ("light".equals(optionLower)
			&& checkRecipe(event, RecipeCatalog.KIND_ITEM_ON_ITEM, "Tinderbox", itemName))
		{
			return;
		}

		if (config.restrictEquipping() && EQUIP_VERBS.contains(optionLower)
			&& blockIfLockedItem(event, itemName))
		{
			return;
		}

		if (config.restrictPotionDrinking() && "drink".equals(optionLower)
			&& blockIfLockedItem(event, itemName))
		{
			return;
		}

		if (config.forcedDropMode() != ForcedDropMode.OFF && !FORCED_DROP_ALLOWED.contains(optionLower))
		{
			blockIfLockedItem(event, itemName);
		}
	}

	/** "Use" with an inventory item selected: in forced-drop mode a locked item can't be used. */
	private void handleUseSelected(MenuOptionClicked event)
	{
		if (config.forcedDropMode() == ForcedDropMode.OFF)
		{
			return;
		}
		if (WidgetUtil.componentToInterface(event.getMenuEntry().getParam1()) != InterfaceID.INVENTORY)
		{
			return;
		}
		blockIfLockedItem(event, Text.removeTags(event.getMenuTarget()).trim());
	}

	// ------------------------------------------------------------------ item/spell on item

	private void handleWidgetOnWidget(MenuOptionClicked event)
	{
		String target = Text.removeTags(event.getMenuTarget());
		int separator = target.lastIndexOf(USED_ON_SEPARATOR);
		if (separator < 0)
		{
			return;
		}
		String source = target.substring(0, separator).trim();
		String destination = target.substring(separator + USED_ON_SEPARATOR.length()).trim();

		boolean isSpell = source.startsWith(CAST_PREFIX) || isSelectedWidgetSpell();
		if (isSpell)
		{
			if (config.restrictEnchanting())
			{
				// Keyed by the target jewellery alone; each item has exactly one enchant.
				checkRecipe(event, RecipeCatalog.KIND_SPELL_ON_ITEM, destination, null);
			}
			return;
		}

		// Forced drop: a locked item can't be used on anything (or be used upon).
		if (config.forcedDropMode() != ForcedDropMode.OFF
			&& (blockIfLockedItem(event, source) || blockIfLockedItem(event, destination)))
		{
			return;
		}

		// Processing recipes; data keys tool->material, clicks can arrive either way round.
		if (!checkRecipe(event, RecipeCatalog.KIND_ITEM_ON_ITEM, source, destination))
		{
			checkRecipe(event, RecipeCatalog.KIND_ITEM_ON_ITEM, destination, source);
		}
	}

	private static boolean isMakeVerb(String optionLower)
	{
		for (String verb : MAKE_VERBS)
		{
			if (optionLower.startsWith(verb))
			{
				return true;
			}
		}
		return false;
	}

	private boolean isSelectedWidgetSpell()
	{
		if (!client.isWidgetSelected())
		{
			return false;
		}
		Widget selected = client.getSelectedWidget();
		return selected != null && selected.getItemId() <= 0;
	}

	private boolean isGrandExchangeOpen()
	{
		return client.getWidget(InterfaceID.GE_OFFERS, 0) != null;
	}

	// ------------------------------------------------------------------ rule evaluation

	/** @return true when the event was consumed (blocked). */
	private boolean checkNodeRule(MenuOptionClicked event, String kind, String name, String option)
	{
		// Data-string mismatches (an option or name that differs from the wiki-sourced value)
		// fail silently and look exactly like "the restriction is ignored", so log the lookup.
		// Click path only: the hide path calls evaluateNodeRule many times per frame.
		if (log.isDebugEnabled())
		{
			ResourceNodeCatalog.Rule rule = nodeCatalog.find(kind, name, option);
			log.debug("node lookup kind={} name='{}' option='{}' -> {}",
				kind, name, option, rule == null ? "NO RULE" : "rule[" + rule.category + "]");
		}
		List<String> missing = evaluateNodeRule(kind, name, option);
		if (missing == null)
		{
			return false;
		}
		event.consume();
		sendBlockedCardsMessage(missing);
		return true;
	}

	/**
	 * The shared restriction decision, used by both the click-consuming path and the
	 * menu-entry hiding path so the hidden set and the blocked set can never drift apart.
	 *
	 * @return the missing card display strings when this interaction is restricted,
	 *         or null when allowed (no rule, category off, or requirements met).
	 */
	private List<String> evaluateNodeRule(String kind, String name, String option)
	{
		ResourceNodeCatalog.Rule rule = nodeCatalog.find(kind, name, option);
		if (rule == null)
		{
			return null;
		}

		boolean forceAllInGroups = false;
		Set<String> excludedRoles;
		if ("fishing".equals(rule.category))
		{
			// Fishing has a three-way mode instead of a toggle; it overrides the rule's
			// any-of group since the any/all choice is the player's difficulty dial.
			FishingRestrictionMode mode = config.fishingMode();
			if (mode == FishingRestrictionMode.OFF)
			{
				return null;
			}
			forceAllInGroups = mode == FishingRestrictionMode.REQUIRE_ALL;
			excludedRoles = Collections.emptySet();
		}
		else if ("thieving-stalls".equals(rule.category))
		{
			// Stalls carry one any-of loot group; the mode dial forces all-of for "All items".
			StallThievingMode mode = config.stallThievingMode();
			if (mode == StallThievingMode.OFF)
			{
				return null;
			}
			forceAllInGroups = mode == StallThievingMode.REQUIRE_ALL;
			excludedRoles = Collections.emptySet();
		}
		else
		{
			excludedRoles = excludedRolesFor(rule.category);
			if (excludedRoles == null)
			{
				return null;
			}
		}

		List<String> missing = rule.missingRequirements(
			collectionReader.getOwnedCardNamesLowerCase(), excludedRoles, forceAllInGroups);
		return missing.isEmpty() ? null : missing;
	}

	/**
	 * Config gate per node category.
	 *
	 * @return roles to skip during evaluation, or null when the category is switched off.
	 *         Unknown categories restrict fully so new data stays loud rather than inert.
	 */
	private Set<String> excludedRolesFor(String category)
	{
		switch (category)
		{
			case "woodcutting":
				return config.restrictWoodcutting() ? Collections.emptySet() : null;
			case "mining":
				return config.restrictMining() ? Collections.emptySet() : null;
			case "pickpocketing":
				switch (config.thievingMode())
				{
					case COINS_POUCH:
						return Set.of("npc");
					case NPC_AND_LOOT:
						return Collections.emptySet();
					default:
						return null;
				}
			case "cooking":
				if (!config.restrictCooking())
				{
					return null;
				}
				// Burnt cards ride on top of the cooked requirement, like slayer's superiors.
				return config.restrictBurntFood() ? Collections.emptySet() : Set.of("burnt");
			case "farming-compost":
				return config.restrictCompost() ? Collections.emptySet() : null;
			case "hunter-chins":
				return config.restrictChins() ? Collections.emptySet() : null;
			case "hunter-rumours":
				return config.restrictHunterRumours() ? Collections.emptySet() : null;
			case "quest-cots":
				// CotS guard marking; the toggle makes the quest completable without the card.
				return config.allowCotsGuards() ? null : Collections.emptySet();
			case "runecrafting":
				switch (config.runecraftingMode())
				{
					case TALISMAN:
						return Set.of("rune");
					case TALISMAN_RUNES:
						return Collections.emptySet();
					default:
						return null;
				}
			case "farming-rake":
				switch (config.farmingRakeMode())
				{
					case TOOLS:
						return Set.of("weeds");
					case BOTH:
						return Collections.emptySet();
					default:
						return null;
				}
			case "farming-plant":
				switch (config.farmingPlantMode())
				{
					case TOOLS:
						return Set.of("seed", "produce");
					case TOOLS_SEEDS:
						return Set.of("produce");
					case ALL:
						return Collections.emptySet();
					default:
						return null;
				}
			case "sailing-upgrades":
				switch (config.sailingUpgradeMode())
				{
					case PARTS:
						return Set.of("material", "large");
					case PARTS_MATERIALS:
						return Set.of("large");
					case EVERYTHING:
						return Collections.emptySet();
					default:
						return null;
				}
			case "sailing-salvage":
				return config.restrictSalvaging() ? Collections.emptySet() : null;
			case "slayer":
			{
				Set<String> excluded = new HashSet<>();
				if (!config.restrictSlayerMasters())
				{
					excluded.add("master");
				}
				if (!config.restrictSlayerMonsters())
				{
					excluded.add("monsters");
				}
				// Superiors ride on top of Require-monsters; the checkbox alone does nothing.
				if (!config.restrictSlayerMonsters() || !config.restrictSlayerSuperiors())
				{
					excluded.add("superiors");
				}
				return excluded.contains("master") && excluded.contains("monsters") ? null : excluded;
			}
			case "hunter-birds":
			case "hunter-butterflies":
				switch (config.hunterBirdsMode())
				{
					case NET_ONLY:
						return Set.of("creature", "extra");
					case ALL_DROPS:
						return Collections.emptySet();
					default:
						return null;
				}
			case "hunter-implings":
				switch (config.implingMode())
				{
					case NET_ONLY:
						return Set.of("extra");
					case BOTH:
						return Collections.emptySet();
					default:
						return null;
				}
			case "hunter-salamanders":
				switch (config.salamanderMode())
				{
					case ROPE_NET:
						return Set.of("creature");
					case ITEMS_SALLY:
						return Collections.emptySet();
					default:
						return null;
				}
			case "hunter-pitfalls":
				switch (config.pitfallMode())
				{
					case TOOLS:
						return Set.of("creature");
					case ALL:
						return Collections.emptySet();
					default:
						return null;
				}
			default:
				// Data shipped a category this build has no toggle for: restrict, so new
				// data stays challenge-mode-loud rather than silently inert.
				return Collections.emptySet();
		}
	}

	/** @return true when the event was consumed (blocked). */
	private boolean checkRecipe(MenuOptionClicked event, String kind, String name, String target)
	{
		RecipeCatalog.Recipe recipe = recipeCatalog.find(kind, name, target);
		if (recipe == null)
		{
			return false;
		}

		boolean enforceInputs;
		boolean enforceOutput;
		boolean skipTinderbox = false;
		switch (recipe.category)
		{
			case "firemaking":
			{
				FiremakingMode mode = config.firemakingMode();
				if (mode == FiremakingMode.OFF
					|| (recipe.eventLog && !config.restrictEventLogs()))
				{
					return false;
				}
				enforceInputs = true;
				enforceOutput = false;
				skipTinderbox = mode == FiremakingMode.JUST_LOGS;
				break;
			}
			case "smithing-smelt":
			{
				SmeltingMode mode = config.smeltingMode();
				if (mode == SmeltingMode.OFF)
				{
					return false;
				}
				enforceInputs = mode == SmeltingMode.ORE || mode == SmeltingMode.BOTH;
				enforceOutput = mode == SmeltingMode.BARS || mode == SmeltingMode.BOTH;
				break;
			}
			case "smithing-forge":
			{
				SmithingMode mode = config.smithingMode();
				if (mode == SmithingMode.OFF)
				{
					return false;
				}
				enforceInputs = mode == SmithingMode.BARS || mode == SmithingMode.BOTH;
				enforceOutput = mode == SmithingMode.ITEMS || mode == SmithingMode.BOTH;
				break;
			}
			case "crafting":
				if (!config.restrictCrafting())
				{
					return false;
				}
				enforceInputs = true;
				enforceOutput = true;
				break;
			case "enchanting":
				if (!config.restrictEnchanting())
				{
					return false;
				}
				enforceInputs = true;
				enforceOutput = true;
				break;
			case "fletching":
				if (!config.restrictFletching())
				{
					return false;
				}
				enforceInputs = true;
				enforceOutput = true;
				break;
			case "herblore":
				if (!config.restrictHerblore())
				{
					return false;
				}
				enforceInputs = true;
				enforceOutput = true;
				break;
			default:
				enforceInputs = true;
				enforceOutput = true;
		}

		List<String> missing = recipe.missingRequirements(
			collectionReader.getOwnedCardNamesLowerCase(), enforceInputs, enforceOutput);
		if (skipTinderbox)
		{
			for (Iterator<String> it = missing.iterator(); it.hasNext(); )
			{
				if ("Tinderbox".equalsIgnoreCase(it.next()))
				{
					it.remove();
				}
			}
		}
		// Crushed gem rides on top of the crafting requirement, for gems that can shatter.
		if (recipe.crushable && config.requireCrushedGem()
			&& !collectionReader.getOwnedCardNamesLowerCase().contains(CRUSHED_GEM_CARD_LOWER))
		{
			missing.add(CRUSHED_GEM_CARD);
		}
		if (missing.isEmpty())
		{
			return false;
		}

		event.consume();
		sendBlockedCardsMessage(missing);
		return true;
	}

	// ------------------------------------------------------------------ helpers

	/** Item name for an item-op entry, falling back to the menu target for non-item-ops. */
	private String itemOpName(MenuOptionClicked event, MenuEntry entry)
	{
		if (entry.isItemOp() && event.getItemId() > 0)
		{
			return itemManager.getItemComposition(event.getItemId()).getName();
		}
		return Text.removeTags(event.getMenuTarget()).trim();
	}

	/** @return true when the item is tracked-but-unowned and the event was consumed. */
	private boolean blockIfLockedItem(MenuOptionClicked event, String itemName)
	{
		// The exempt list applies to EVERY item block (forced drop, withdraw, equip, buy...),
		// not just loot pickup - exempt items exist so universal items never brick gameplay.
		if (itemName == null || itemName.isEmpty() || isLootExempt(itemName)
			|| isUnlocked(itemCatalog, itemName))
		{
			return false;
		}
		event.consume();
		sendBlockedMessage(itemName);
		return true;
	}

	/**
	 * True while the local player is in a live Last Man Standing match, so every restriction
	 * lifts (LMS hands out temporary gear and supplies the player doesn't own). Primary signal
	 * is the client's own BR_INGAME varbit (RuneLite also names it Varbits.IN_LMS); OR'd with
	 * the LMS island map regions as a lenient fallback should a client update shift the varbit's
	 * timing. Both reads are client-thread only, which every caller here already is. The Ferox
	 * Enclave lobby reads 0 / a different region, so it is unaffected.
	 */
	private boolean isLmsBypassed()
	{
		if (!config.allowInLms())
		{
			return false;
		}
		if (client.getVarbitValue(VarbitID.BR_INGAME) == 1)
		{
			return true;
		}
		for (int region : client.getMapRegions())
		{
			if (LMS_REGIONS.contains(region))
			{
				return true;
			}
		}
		return false;
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
		String raw = config.lootExemptNames();
		if (!raw.equals(lootExemptRaw))
		{
			Set<String> exempt = new HashSet<>();
			for (String entry : raw.split(","))
			{
				exempt.add(entry.trim().toLowerCase(Locale.ROOT));
			}
			lootExemptSet = exempt;
			lootExemptRaw = raw;
		}
		return lootExemptSet.contains(itemName.trim().toLowerCase(Locale.ROOT));
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
		queueChat(String.format(Locale.US,
			"[Bronzeman TCG] You haven't collected the %s card yet - open more packs!", entityName));
	}

	/**
	 * CONSOLE type via the chat manager: raw GAMEMESSAGEs from addChatMessage get hidden
	 * when the player's Game chat tab is set to "Filter" (why feedback looked dead on a
	 * standard client but fine in dev testing).
	 */
	private void queueChat(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(message)
			.build());
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
		queueChat(String.format(Locale.US,
			"[Bronzeman TCG] You haven't collected these cards yet: %s - open more packs!", listed));
	}

	@Provides
	BronzemanTcgConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BronzemanTcgConfig.class);
	}
}
