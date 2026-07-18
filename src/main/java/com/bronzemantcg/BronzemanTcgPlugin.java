package com.bronzemantcg;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
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
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Renderable;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
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
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
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
	description = "Account restriction settings to work alongside the OSRS TCG Plugin.",
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
	// Forced drop leaves a locked item only its two disposal options; everything else
	// (including Examine and Release) is blocked and, with option hiding on, removed.
	private static final Set<String> FORCED_DROP_ALLOWED = new HashSet<>(List.of(
		"drop", "destroy"));
	// Other Hub plugins that also gate actions on the OSRS TCG collection. Running one
	// alongside us means two independent rule sets fighting over the same menu entries,
	// so the welcome message points it out rather than trying to reconcile them. Matched
	// on PluginDescriptor.name(); add display names here as they appear.
	private static final Set<String> CONFLICTING_PLUGINS = new HashSet<>(List.of(
		"TCG Locked"));
	// osrs-tcg's PluginDescriptor name - the collection this plugin is built around.
	// Checked via PluginManager rather than through its stored data, so this stays
	// valid whichever way we end up reading the collection.
	private static final String REQUIRED_PLUGIN = "OSRS TCG";
	// Game ticks are 600ms. ~9s clears the login chat burst; ~30min between reminders
	// is often enough to notice, rare enough not to nag.
	private static final int WELCOME_DELAY_TICKS = 15;
	private static final int REMINDER_TICKS = 3000;
	// ~60s. Checked far more often than the other notices: switching OSRS TCG off is a
	// quieter way to dodge restrictions than switching this plugin off, so it should
	// surface quickly rather than sit unnoticed for half an hour.
	private static final int REQUIRED_PLUGIN_TICKS = 100;
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
	private ConfigManager configManager;

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

	@Inject
	private PluginManager pluginManager;

	private long lastBlockMessageMs;
	private boolean welcomeShown;
	// Countdowns in game ticks; negative means idle.
	private int welcomeDelayTicks = -1;
	private int reminderTicks = -1;
	private int requiredPluginTicks = -1;
	private BronzemanTcgPanel panel;
	private NavigationButton navButton;
	private int tickCounter;
	private String lootExemptRaw;
	private Set<String> lootExemptSet = Collections.emptySet();
	private Set<String> effectiveOwned = Collections.emptySet();
	private Set<String> effectiveOwnedBase;
	private Set<String> effectiveOwnedExempt;
	private boolean effectiveOwnedCoins;

	@Override
	protected void startUp()
	{
		collectionReader.invalidate();
		migrateExemptList();
		welcomeShown = false;
		welcomeDelayTicks = -1;
		reminderTicks = -1;
		requiredPluginTicks = -1;

		panel = new BronzemanTcgPanel(monsterCatalog, itemCatalog, nodeCatalog, questCatalog,
			contentCatalog, collectionReader, config);
		navButton = NavigationButton.builder()
			.tooltip("Bronzeman TCG")
			.icon(loadPanelIcon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);
		overlayManager.add(statsOverlay);
		renderCallbackManager.register(this);

		log.info("Bronzeman TCG started. Tracking {} TCG-linked NPCs, {} items, {} node rules, {} recipe rules.",
			monsterCatalog.size(), itemCatalog.size(), nodeCatalog.size(), recipeCatalog.size());

		// Enabling the plugin mid-session won't fire another LOGGED_IN, so greet here too.
		scheduleWelcome();
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

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGGED_IN:
				scheduleWelcome();
				break;
			case LOGIN_SCREEN:
				// Re-arm for the next login. World hops run HOPPING -> LOADING -> LOGGED_IN
				// without passing through the login screen, so hopping never re-greets.
				welcomeShown = false;
				welcomeDelayTicks = -1;
				break;
			default:
				break;
		}
	}

	/**
	 * Arm the greeting countdown. Firing on the login event itself buries the message
	 * under the client's own login spam - clan broadcasts, welcome text and other
	 * plugins all post in the first few seconds - so it waits them out instead.
	 */
	private void scheduleWelcome()
	{
		if (welcomeShown || welcomeDelayTicks >= 0 || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		welcomeDelayTicks = WELCOME_DELAY_TICKS;
	}

	/**
	 * Drives both the delayed greeting and the recurring conflict reminder. Ticks only
	 * arrive while logged in, so a logged-out session never counts down.
	 */
	private void tickWelcomeTimers()
	{
		if (welcomeDelayTicks >= 0 && --welcomeDelayTicks < 0)
		{
			// The greeting posts every notice itself, so nothing else runs this tick.
			showWelcomeMessage();
			return;
		}
		if (reminderTicks >= 0 && --reminderTicks < 0)
		{
			postPeriodicNotices();
		}
		if (requiredPluginTicks >= 0 && --requiredPluginTicks < 0)
		{
			requiredPluginTicks = REQUIRED_PLUGIN_TICKS;
			warnRequiredPluginDisabled();
		}
	}

	/** One greeting per login, once the login chat has settled. Not optional - it's a
	 *  single line that confirms the plugin loaded and shows collection progress. */
	private void showWelcomeMessage()
	{
		welcomeShown = true;
		// Scheduled unconditionally: a conflicting plugin enabled mid-session should
		// still get picked up at the next reminder.
		reminderTicks = REMINDER_TICKS;
		requiredPluginTicks = REQUIRED_PLUGIN_TICKS;

		if (collectionReader.isStateAvailable())
		{
			queueChat(String.format(Locale.UK,
				"[Bronzeman TCG] Active - %,d/%,d cards collected. Good luck on the pulls!",
				collectionReader.getOwnedCardCount(), CardNames.TOTAL_CARDS));
		}
		else
		{
			// Greeting the player with "0/6,376 collected" would misreport an unread
			// collection as an empty one.
			warnCollectionUnreadable();
		}
		warnRequiredPluginDisabled();
		warnConflictingPlugins();
	}

	/** Re-checks periodically, so both notices keep surfacing while they still apply. */
	private void postPeriodicNotices()
	{
		reminderTicks = REMINDER_TICKS;
		warnCollectionUnreadable();
		// warnRequiredPluginDisabled() deliberately absent - it runs on its own faster timer.
		warnConflictingPlugins();
	}

	/**
	 * Enforcement stands down when the collection can't be read, so the player has to be
	 * told - quietly playing unrestricted defeats the point of the account. Never optional,
	 * and repeated, since the usual cause (a missing or newly-updated OSRS TCG plugin)
	 * needs the player to act.
	 */
	private void warnCollectionUnreadable()
	{
		if (collectionReader.isStateAvailable())
		{
			return;
		}
		queueChat("[Bronzeman TCG] - Can't read your OSRS TCG collection, so restrictions are "
			+ "OFF. Check that the OSRS TCG plugin is installed and up to date, and that you've "
			+ "opened at least one pack.");
	}

	/**
	 * Disabling OSRS TCG leaves its stored collection intact, so restrictions deliberately
	 * keep enforcing against it - standing down would make switching that plugin off a
	 * one-click bypass of this one. The player still needs telling, since they've stopped
	 * earning cards. Never optional: OSRS TCG is a hard requirement here.
	 */
	private void warnRequiredPluginDisabled()
	{
		// An unreadable collection already gets its own, more urgent, warning - no point
		// stacking two messages describing the same broken setup.
		if (isPluginEnabled(REQUIRED_PLUGIN) || !collectionReader.isStateAvailable())
		{
			return;
		}
		queueChat("[Bronzeman TCG] - The OSRS TCG plugin is turned off. Restrictions are still "
			+ "active using your last known collection, but you won't earn any new cards until "
			+ "you turn it back on.");
	}

	/** True when a plugin with this display name is installed and currently enabled. */
	private boolean isPluginEnabled(String displayName)
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
			if (descriptor != null && displayName.equals(descriptor.name()))
			{
				return pluginManager.isPluginEnabled(plugin);
			}
		}
		return false;
	}

	/**
	 * Another plugin gating the same actions produces blocks this plugin can't explain,
	 * so the player is told where to look rather than left guessing. Opt-out, since
	 * anyone running both on purpose doesn't need reminding every half hour.
	 */
	private void warnConflictingPlugins()
	{
		if (!config.showConflictMessage())
		{
			return;
		}
		for (String name : activeConflictingPlugins())
		{
			queueChat("[Bronzeman TCG] - Plugin Conflict! Please note, '" + name + "' is also enabled. "
					+ "Items without cards may be locked. Double check your settings on both plugins or "
					+ "consider running just one! Disable this message in Bronzeman TCG Settings.");
		}
	}

	/** Display names of enabled plugins we know conflict with this one. */
	private List<String> activeConflictingPlugins()
	{
		List<String> found = new ArrayList<>();
		for (Plugin plugin : pluginManager.getPlugins())
		{
			PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
			// Installed-but-disabled plugins don't touch menus, so they aren't worth a warning.
			if (descriptor != null
				&& CONFLICTING_PLUGINS.contains(descriptor.name())
				&& pluginManager.isPluginEnabled(plugin))
			{
				found.add(descriptor.name());
			}
		}
		return found;
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
		if (!config.hideLockedEntities() || !(renderable instanceof NPC) || isEnforcementBypassed())
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
	 * Panel nav-button icon: the bundled card sprite (same art as the Plugin Hub icon),
	 * loaded synchronously from the classpath. The previous approach pulled the med-helm
	 * item sprite from the game cache asynchronously, which frequently wasn't ready when
	 * the toolbar first painted - leaving a blank button.
	 */
	private BufferedImage loadPanelIcon()
	{
		return ImageUtil.loadImageResource(BronzemanTcgPlugin.class, "/panel_icon.png");
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
		if (!config.hideLockedOptions() || isEnforcementBypassed())
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
			case WIDGET_TARGET:
			{
				// "Use" on an inventory item (WIDGET_TARGET, not a CC_OP item op). The click
				// path blocks it under forced drop via handleUseSelected, so mirror that here
				// or the option stays visible while its click is silently cancelled.
				if (config.forcedDropMode() == ForcedDropMode.OFF
					|| WidgetUtil.componentToInterface(entry.getParam1()) != InterfaceID.INVENTORY)
				{
					return false;
				}
				String usedItem = Text.removeTags(entry.getTarget()).trim();
				return !usedItem.isEmpty() && !isLootExempt(usedItem)
					&& !isUnlocked(itemCatalog, usedItem);
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
	 * Gathers the nearby tracked-NPC snapshot on the client thread every few ticks and
	 * hands it to the panel on the Swing EDT (game state must not be read from Swing).
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Ahead of the panel guard below - the greeting must still fire when the panel
		// is closed, and on every tick rather than one in five.
		tickWelcomeTimers();

		// Periodically re-render the panel so unlocks show without reopening it. Cheap and
		// only when visible; nothing here reads live scene state.
		if (panel == null || ++tickCounter % 5 != 0)
		{
			return;
		}
		BronzemanTcgPanel target = panel;
		SwingUtilities.invokeLater(() ->
		{
			if (target.isShowing())
			{
				target.refresh();
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
		if (isEnforcementBypassed())
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
			effectiveOwnedCards(), excludedRoles, forceAllInGroups);
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
			case "cooking":
				if (!config.restrictCooking())
				{
					return false;
				}
				enforceInputs = true;
				enforceOutput = true;
				break;
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
			effectiveOwnedCards(), enforceInputs, enforceOutput);
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
			&& !effectiveOwnedCards().contains(CRUSHED_GEM_CARD_LOWER))
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
	 * Single stand-down check for every enforcement path - menu hiding, entity hiding and
	 * click blocking all route through here, so no path can enforce while another is bypassed.
	 */
	private boolean isEnforcementBypassed()
	{
		return isLmsBypassed() || !collectionReader.isStateAvailable();
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
		Set<String> owned = effectiveOwnedCards();
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
		// Dose suffixes are stripped exactly as the catalogs do, so exempting "Prayer potion"
		// also covers the in-game "Prayer potion(4)".
		String needle = CardNames.stripDoseSuffix(itemName.trim().toLowerCase(Locale.ROOT));
		// Coins have their own toggle; the free-text list default is empty so RuneLite never
		// re-injects a value the player has cleared (which used to re-add "Coins" on update).
		if (config.exemptCoins() && "coins".equals(needle))
		{
			return true;
		}
		return exemptSet().contains(needle);
	}

	/** Lower-cased, dose-stripped exempt names; rebuilt only when the config string changes. */
	private Set<String> exemptSet()
	{
		String raw = config.lootExemptNames();
		if (!raw.equals(lootExemptRaw))
		{
			Set<String> exempt = new HashSet<>();
			for (String entry : raw.split(","))
			{
				String trimmed = CardNames.stripDoseSuffix(entry.trim().toLowerCase(Locale.ROOT));
				if (!trimmed.isEmpty())
				{
					exempt.add(trimmed);
				}
			}
			lootExemptSet = exempt;
			lootExemptRaw = raw;
		}
		return lootExemptSet;
	}

	/**
	 * Owned cards PLUS every exempt name, so an exempt item counts as unlocked everywhere -
	 * including recipe and node-rule requirements (item-on-item, item-on-object), which
	 * previously never consulted the exempt list at all. Cached against the reader's owned-set
	 * instance and the exempt set, because the menu-hide path calls this many times per frame.
	 */
	private Set<String> effectiveOwnedCards()
	{
		Set<String> owned = collectionReader.getOwnedCardNamesLowerCase();
		Set<String> exempt = exemptSet();
		boolean coins = config.exemptCoins();
		if (exempt.isEmpty() && !coins)
		{
			return owned;
		}
		if (owned != effectiveOwnedBase || exempt != effectiveOwnedExempt
			|| coins != effectiveOwnedCoins)
		{
			Set<String> combined = new HashSet<>(owned);
			combined.addAll(exempt);
			if (coins)
			{
				combined.add("coins");
			}
			effectiveOwned = combined;
			effectiveOwnedBase = owned;
			effectiveOwnedExempt = exempt;
			effectiveOwnedCoins = coins;
		}
		return effectiveOwned;
	}

	/**
	 * One-time migration to the split Coins toggle. RuneLite re-injects a config item's
	 * non-empty default whenever the stored value is cleared or unset, which used to re-add
	 * "Coins" to the exempt list after every update (a real player-reported bug). The list
	 * default is now empty and Coins exemption lives in its own toggle; this preserves each
	 * existing player's current Coins behaviour by turning that toggle off for anyone whose
	 * stored list did not already exempt Coins.
	 */
	private void migrateExemptList()
	{
		if (config.exemptListMigrated())
		{
			return;
		}
		// Mark migrated up front: this is a strict one-shot, so a crash mid-way can never
		// re-run against a half-updated list (which could mis-set the Coins toggle). Worst
		// case on a mid-way crash is Coins harmlessly lingering in the list - no data loss.
		configManager.setConfiguration(BronzemanTcgConfig.GROUP, "exemptListMigrated", true);

		String raw = configManager.getConfiguration(BronzemanTcgConfig.GROUP, "lootExemptNames");
		if (raw == null)
		{
			return;
		}
		// Move Coins out of the free-text list into its dedicated toggle so it lives in exactly
		// one place: drop only exact "Coins" (and blank) entries, set the toggle to whether the
		// list exempted them, and keep EVERY other item verbatim.
		boolean hadCoins = false;
		List<String> kept = new ArrayList<>();
		for (String entry : raw.split(","))
		{
			String trimmed = entry.trim();
			if ("coins".equalsIgnoreCase(trimmed))
			{
				hadCoins = true;
			}
			else if (!trimmed.isEmpty())
			{
				kept.add(trimmed);
			}
		}
		configManager.setConfiguration(BronzemanTcgConfig.GROUP, "exemptCoins", hadCoins);
		configManager.setConfiguration(BronzemanTcgConfig.GROUP, "lootExemptNames",
			String.join(", ", kept));
	}

	private List<String> missingCards(List<String> requiredCards)
	{
		Set<String> owned = effectiveOwnedCards();
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
