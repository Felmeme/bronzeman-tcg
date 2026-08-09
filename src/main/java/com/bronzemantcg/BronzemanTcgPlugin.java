package com.bronzemantcg;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.NPCComposition;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Renderable;
import net.runelite.api.ScriptID;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.PlayerChanged;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.kit.KitType;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.FishingSpot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
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
 * Interop is read-only via osrs-tcg's PluginMessage API, falling back to
 * decoding its persisted ConfigManager state on hub versions that predate
 * the API (see {@link TcgCollectionReader}); there is no compile-time
 * dependency, so both plugins install independently from the Plugin Hub.
 * World and menu interactions work by consuming MenuOptionClicked. The standard
 * SkillMulti keyboard shortcuts are consumed separately through RuneLite's KeyManager.
 */
@Slf4j
@PluginDescriptor(
	name = "Bronzeman TCG",
	description = "Account restriction settings to work alongside the OSRS TCG Plugin.",
	tags = {"bronzeman", "tcg", "restriction", "ironman", "challenge"}
)
public class BronzemanTcgPlugin extends Plugin implements RenderCallback, KeyListener
{
	private static final int[] SKILL_MULTI_PRODUCTS = {
		InterfaceID.Skillmulti.A,
		InterfaceID.Skillmulti.B,
		InterfaceID.Skillmulti.C,
		InterfaceID.Skillmulti.D,
		InterfaceID.Skillmulti.E,
		InterfaceID.Skillmulti.F,
		InterfaceID.Skillmulti.G,
		InterfaceID.Skillmulti.H,
		InterfaceID.Skillmulti.I,
		InterfaceID.Skillmulti.J,
		InterfaceID.Skillmulti.K,
		InterfaceID.Skillmulti.L,
		InterfaceID.Skillmulti.M,
		InterfaceID.Skillmulti.N,
		InterfaceID.Skillmulti.O,
		InterfaceID.Skillmulti.P,
		InterfaceID.Skillmulti.Q,
		InterfaceID.Skillmulti.R
	};
	private static final String ATTACK_OPTION = "attack";
	/**
	 * Barbarian Training's Farming section. RuneLite has no VarbitID constant for it;
	 * the id was captured in-client with the Var Inspector (cache name
	 * brut_farming_planting). It counts up 0 -> 1 -> 2 -> 3 as the section progresses,
	 * and bare-handed planting is unlocked from 1, so this is a >= test, not == 1.
	 */
	private static final int BRUT_FARMING_PLANTING_VARBIT = 9609;

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
	// Locked item usage leaves an item only its disposal options; everything else
	// (equip, drink, use, Release...) is blocked and removed from the menu. Examine
	// survives because it routes through a different MenuAction entirely.
	private static final Set<String> FORCED_DROP_ALLOWED = new HashSet<>(List.of(
		"drop", "destroy"));
	// osrs-tcg's PluginDescriptor name - the collection this plugin is built around.
	// Checked via PluginManager rather than through its stored data, so this stays
	// valid whichever way we end up reading the collection.
	private static final String REQUIRED_PLUGIN = "OSRS TCG";
	// osrs-tcg's PluginMessage API (its OwnedCardNamesApiService). We query, it replies
	// with "owned-names" and pushes "owned-names-changed" after every collection change.
	// Hub versions without the API simply never answer; the config-decode fallback in
	// TcgCollectionReader carries those users.
	private static final String TCG_API_NAMESPACE = "osrstcg";
	private static final String TCG_API_QUERY = "query-owned-names";
	private static final String TCG_API_REPLY = "owned-names";
	private static final String TCG_API_CHANGED = "owned-names-changed";
	private static final String TCG_API_NAMES_KEY = "ownedNames";
	// This plugin's own PluginMessage API, for sibling plugins that run a group mode on top of the
	// same collection: they post their extra unlocked card names and the restriction engine honours
	// them. Without it those modes have no effect here, because every lock check reads this
	// player's collection alone. Constants are copied, not imported - Hub plugins cannot see each
	// other's classes. Post SHARED_UNLOCKS with SHARED_SOURCE_KEY (your plugin's name) and
	// SHARED_NAMES_KEY (your complete set, which replaces whatever you sent before); an empty list
	// withdraws it.
	private static final String SHARED_API_NAMESPACE = "bronzemantcg";
	private static final String SHARED_UNLOCKS = "shared-unlocks";
	// Asked whenever this plugin needs the current picture rather than waiting to be told: at
	// startup (a source that shared before we loaded would otherwise never reach us), after a
	// profile switch, and when the setting is turned on. Sources answer with SHARED_UNLOCKS.
	private static final String SHARED_QUERY = "query-shared-unlocks";
	private static final String SHARED_SOURCE_KEY = "source";
	private static final String SHARED_NAMES_KEY = "cardNames";
	// ~60s between query retries while unanswered - osrs-tcg may start after us, or be
	// a hub version without the API (in which case we retry forever, cheaply, on the
	// config fallback).
	private static final int API_QUERY_RETRY_TICKS = 100;
	// Game ticks are 600ms. ~9s clears the login chat burst; ~30min between reminders
	// is often enough to notice, rare enough not to nag.
	private static final int WELCOME_DELAY_TICKS = 15;
	private static final int REMINDER_TICKS = 3000;
	// ~60s. Checked far more often than the other notices: switching OSRS TCG off is a
	// quieter way to dodge restrictions than switching this plugin off, so it should
	// surface quickly rather than sit unnoticed for half an hour.
	private static final int REQUIRED_PLUGIN_TICKS = 100;
	// ~60s: long enough to browse a make menu before choosing, short enough that the
	// remembered material can't leak into an unrelated interface opened much later.
	private static final int MAKE_MATERIAL_MEMORY_TICKS = 100;
	// Production-interface verbs: the furnace smelting screen (and possibly other stations)
	// uses a different widget group than the SKILLMULTI/SMITHING ones we match directly, so
	// any interface click with one of these options gets a recipe lookup by product name.
	private static final Set<String> MAKE_VERBS = new HashSet<>(List.of(
		"smelt", "make", "make-x", "make-all", "make sets", "craft", "smith",
		"string", "mix", "cook", "bake", "fletch", "spin", "fire"));
	// Woodcutting axes only - suffix-matching " axe" would also catch carried COMBAT
	// axes (Zombie axe, Soulreaper axe, Morrigan's throwing axe - all carded), which
	// must never gate chopping. Pickaxes need no list: every "* pickaxe" is a mining
	// tool. Uncarded entries (felling axes) are future-proofing - untracked names are
	// never locked, so they stay inert until osrs-tcg cards them.
	private static final Set<String> WOODCUTTING_AXES = new HashSet<>(List.of(
		"bronze axe", "iron axe", "steel axe", "black axe", "mithril axe",
		"adamant axe", "rune axe", "dragon axe", "crystal axe", "infernal axe",
		"gilded axe", "3rd age axe", "corrupted axe",
		"bronze felling axe", "iron felling axe", "steel felling axe",
		"black felling axe", "mithril felling axe", "adamant felling axe",
		"rune felling axe", "dragon felling axe", "crystal felling axe"));
	// LMS island map regions, copied verbatim from RuneLite core's LootTrackerPlugin; a lenient
	// fallback so restrictions stay lifted even if a client update shifts the BR_INGAME timing.
	private static final Set<Integer> LMS_REGIONS = new HashSet<>(List.of(
		13658, 13659, 13660, 13914, 13915, 13916, 13918, 13919, 13920,
		14174, 14175, 14176, 14430, 14431, 14432));
	// Locked-item marking (Alch Blocker's technique): widget opacity runs 0 (solid)
	// to 255 (invisible); 140 reads as "faded but identifiable". The exact value used
	// also serves as our signature when restoring, so we never clobber another
	// plugin's opacity.
	private static final int LOCKED_ITEM_OPACITY = 140;
	// Containers that get the fade: main inventory, bank items, bank-side inventory,
	// shop stock and shop-side inventory.
	private static final int[] LOCKED_MARK_CONTAINERS = {
		InterfaceID.Inventory.ITEMS, InterfaceID.Bankmain.ITEMS, InterfaceID.Bankside.ITEMS,
		InterfaceID.Shopmain.ITEMS, InterfaceID.Shopside.ITEMS};

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
	private Gson gson;

	@Inject
	private TcgCollectionReader collectionReader;

	@Inject
	private SharedUnlockStore sharedUnlockStore;

	@Inject
	private RecentUnlocksTracker recentUnlocksTracker;

	@Inject
	private ImportantUnlocksCatalog importantUnlocksCatalog;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private TrackedMonsterCatalog monsterCatalog;

	@Inject
	private TrackedItemCatalog itemCatalog;

	@Inject
	private ExemptionList exemptionList;

	@Inject
	private ResourceNodeCatalog nodeCatalog;

	@Inject
	private RecipeCatalog recipeCatalog;

	@Inject
	private QuestCatalog questCatalog;

	@Inject
	private QuestRequirementCatalog questRequirementCatalog;

	@Inject
	private ContentCatalog contentCatalog;

	@Inject
	private MonsterAreaCatalog monsterAreaCatalog;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BronzemanTcgOverlay overlay;

	@Inject
	private RenderCallbackManager renderCallbackManager;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private KeyManager keyManager;

	@Inject
	private QuestNpcIndex questNpcIndex;

	@Inject
	private ConsumablesCatalog consumablesCatalog;

	@Inject
	private LockedItemIconOverlay lockedItemIconOverlay;

	@Inject
	private EventBus eventBus;

	@Inject
	private ScheduledExecutorService executor;

	private long lastBlockMessageMs;
	private boolean welcomeShown;
	// Countdowns in game ticks; negative means idle.
	private int welcomeDelayTicks = -1;
	private int reminderTicks = -1;
	private int requiredPluginTicks = -1;
	/**
	 * Set when the shared-unlocks picture is stale; the query goes out on the next tick. Volatile
	 * because the settings panel raises ConfigChanged on the EDT while the tick that acts on it runs
	 * on the client thread.
	 */
	private volatile boolean sharedQueryPending;

	private int apiQueryTicks = -1;
	private volatile BronzemanTcgPanel panel;
	private volatile NavigationButton navButton;
	private boolean presetOnboardingRequired;
	// Invalidates queued Swing startup work when the plugin is stopped or restarted
	// before the EDT has had a chance to install the navigation panel.
	private volatile long panelGeneration;
	private int tickCounter;
	private boolean questStateInitialized;
	private Set<String> effectiveOwned = Collections.emptySet();
	private Set<String> effectiveOwnedBase;
	private Set<String> effectiveOwnedExempt;
	private Set<String> effectiveOwnedShared;
	private boolean effectiveOwnedCoins;
	private FoodSettingsMode effectiveOwnedFoodMode;
	// Locked-mark lookup cache, keyed by item id; dropped whenever the owned set changes.
	private final Map<Integer, Boolean> lockedItemCache = new HashMap<>();
	private Set<String> lockedItemCacheOwned;
	// Duelist City Mode: fake Mystic cards (2h) on every player, client-side only. We must
	// remember each player's REAL weapon/shield ids so we can put them back when disabled.
	// Keyed by the player's scene index (stable while they're in view; freed on despawn).
	private static final int MYSTIC_CARDS_ITEM_ID = 27645;
	// Mystic cards' weapon stance, captured in-game 2026-07-21 via logLocalStanceOnChange:
	// idle, walk, run, idleRotateLeft, idleRotateRight, walkRotateLeft, walkRotateRight,
	// walkRotate180 - the order both applyStance() and the restore snapshot use.
	private static final int[] MYSTIC_STANCE = {9847, 9849, 9850, 823, 823, 9851, 9852, 820};
	// Per player (scene id): [weapon, shield, then the 8 real stance ids], kept so both the
	// model and the stance can be put back exactly when the mode is turned off.
	private final Map<Integer, int[]> duelistRealEquip = new HashMap<>();
	// Last-logged local-player stance, so the capture logger fires only when it changes.
	private int[] lastLoggedStance;
	// Carried tool NAMES (not lock states): recomputed on inventory/equipment change,
	// while lock state is evaluated per check so card unlocks apply without waiting
	// for the next container event. Read from the per-frame hide path - keep cheap.
	private Set<String> carriedPickaxes = Collections.emptySet();
	private Set<String> carriedAxes = Collections.emptySet();
	// Fishing rules use their role:"tool" groups as the allowlist. Keep every carried
	// item name here so bait, feathers and future spot-specific inputs stay data-driven.
	// Every visible ground item, tracked from spawn/despawn rather than by scanning the
	// scene: the outline overlay runs every frame and a 104x104x4 tile walk there would be
	// far too costly. The name is resolved once at spawn; `blocked` is recomputed only when
	// the effective owned set actually changes, so the render path just reads a boolean.
	private final Map<TileItem, GroundItem> groundItems = new HashMap<>();
	private Set<String> groundItemOwnedSnapshot;

	/** One tracked ground item: its tile, its resolved name, and whether it is locked. */
	static final class GroundItem
	{
		final Tile tile;
		final String name;
		boolean blocked;

		GroundItem(Tile tile, String name)
		{
			this.tile = tile;
			this.name = name;
		}
	}

	private Set<String> carriedFishingInputs = Collections.emptySet();
	private Set<String> inventoryItemNamesLower = Collections.emptySet();
	// The item-on-item pair that most recently opened a "make" interface. Some menus
	// label every tier identically ("Crossbow stock"), so the product name alone can't
	// say which item is being made - but the material used to open the menu can.
	// Both halves are kept because the click arrives either way round (knife on logs
	// or logs on knife); the catalog decides which one is the real material.
	private String lastUsedItemA;
	private String lastUsedItemB;
	private int lastUsedItemTick = Integer.MIN_VALUE;
	private boolean markRefreshQueued;
	private int markTickCounter;
	private volatile MakeInterfaceKeyboardState makeInterfaceKeyboardState =
		MakeInterfaceKeyboardState.NONE;

	@Override
	protected void startUp()
	{
		// Check before the legacy migrations write their markers. That is how a genuinely
		// fresh install is distinguished from an existing user upgrading this release.
		presetOnboardingRequired = preparePresetOnboarding();
		collectionReader.invalidate();
		recentUnlocksTracker.reload();
		// Nothing shared survives a restart of this plugin. Sources are asked to re-offer on the
		// first tick, so a set from before we unloaded can never linger unnoticed.
		sharedUnlockStore.clear();
		sharedQueryPending = true;
		migrateExemptList();
		migrateNpcVisibility();
		migrateSkillToggles();
		migrateFishingMode();
		migrateHunterMode();
		removeRetiredGeneralSettings();
		migrateTcgLockedDefaults(presetOnboardingRequired);
		// Mid-session enable fires no ItemContainerChanged, so seed the tool cache now.
		clientThread.invokeLater(this::refreshCarriedTools);
		welcomeShown = false;
		welcomeDelayTicks = -1;
		reminderTicks = -1;
		requiredPluginTicks = -1;
		questStateInitialized = false;
		// Query on the first tick, not here: RuneLite registers our @Subscribe methods
		// only after startUp returns, and EventBus.post is synchronous, so a reply to a
		// query posted from startUp would arrive before we can hear it.
		apiQueryTicks = 0;

		long generation = ++panelGeneration;
		SwingUtilities.invokeLater(() -> installPanel(generation));
		overlayManager.add(overlay);
		overlayManager.add(lockedItemIconOverlay);
		renderCallbackManager.register(this);
		keyManager.registerKeyListener(this);

		log.info("Bronzeman TCG started. Tracking {} TCG-linked NPCs, {} items, {} node rules, {} recipe rules.",
			monsterCatalog.size(), itemCatalog.size(), nodeCatalog.size(), recipeCatalog.size());

		// Enabling the plugin mid-session won't fire another LOGGED_IN, so greet here too.
		scheduleWelcome();
	}

	@Override
	protected void shutDown()
	{
		++panelGeneration;
		// Shared unlocks are only ever as current as the sources telling us about them, and while
		// this plugin is unloaded nothing is listening. Dropping them here means a restart starts
		// from nothing and re-asks, rather than reviving a set that may since have been withdrawn.
		sharedUnlockStore.clear();
		keyManager.unregisterKeyListener(this);
		makeInterfaceKeyboardState = MakeInterfaceKeyboardState.NONE;
		renderCallbackManager.unregister(this);
		overlayManager.remove(overlay);
		overlayManager.remove(lockedItemIconOverlay);
		// Widget opacity outlives the plugin, so faded items must be restored by hand.
		clientThread.invoke(this::clearLockedItemMarks);
		// Same for faked player models: put everyone's real gear back before we unload.
		clientThread.invoke(() -> sweepDuelistCity(false));
		BronzemanTcgPanel oldPanel = panel;
		NavigationButton oldNavButton = navButton;
		navButton = null;
		panel = null;
		SwingUtilities.invokeLater(() ->
		{
			if (oldPanel != null)
			{
				oldPanel.dispose();
			}
			if (oldNavButton != null)
			{
				clientToolbar.removeNavigation(oldNavButton);
			}
		});
		log.info("Bronzeman TCG stopped.");
	}

	/**
	 * Build and install Swing state on Swing's event thread. The generation check covers
	 * the enable-then-immediately-disable race: stale queued work must never resurrect
	 * the panel after shutDown().
	 */
	private void installPanel(long generation)
	{
		if (generation != panelGeneration)
		{
			return;
		}

		BronzemanTcgPanel newPanel = new BronzemanTcgPanel(
				gson,
				monsterCatalog,
				itemCatalog,
				nodeCatalog,
				questCatalog,
				questRequirementCatalog,
				exemptionList,
				contentCatalog,
				monsterAreaCatalog,
				collectionReader,
				sharedUnlockStore,
				recentUnlocksTracker,
				importantUnlocksCatalog,
				spriteManager,
				config,
				configManager,
				executor,
				presetOnboardingRequired);
		NavigationButton newNavButton = NavigationButton.builder()
			.tooltip("Bronzeman TCG")
			.icon(loadPanelIcon())
			.priority(7)
			.panel(newPanel)
			.build();

		if (generation != panelGeneration)
		{
			newPanel.dispose();
			return;
		}

		panel = newPanel;
		navButton = newNavButton;
		clientToolbar.addNavigation(newNavButton);
		newPanel.requestRefresh();
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		TileItem item = event.getItem();
		if (item == null)
		{
			return;
		}
		GroundItem tracked = new GroundItem(event.getTile(),
			itemManager.getItemComposition(item.getId()).getName());
		tracked.blocked = isBlockedItemName(tracked.name);
		groundItems.put(item, tracked);
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		groundItems.remove(event.getItem());
	}

	/** Tracked ground items for the outline overlay. */
	Map<TileItem, GroundItem> getGroundItems()
	{
		return groundItems;
	}

	/**
	 * Re-evaluate tracked ground items after a card is gained or lost, or the exempt list
	 * changes. effectiveOwnedCards() is identity-cached, so the common case is one
	 * reference comparison per tick and no work at all.
	 */
	private void refreshGroundItemLocks()
	{
		Set<String> owned = effectiveOwnedCards();
		if (owned == groundItemOwnedSnapshot)
		{
			return;
		}
		groundItemOwnedSnapshot = owned;
		for (GroundItem tracked : groundItems.values())
		{
			tracked.blocked = isBlockedItemName(tracked.name);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOADING:
				// New scene: every tracked Tile belongs to the old one.
				groundItems.clear();
				break;
			case LOGGED_IN:
				questStateInitialized = false;
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
	 * Drives both the delayed greeting and recurring health notices. Ticks only
	 * arrive while logged in, so a logged-out session never counts down.
	 */
	private void tickWelcomeTimers()
	{
		// Every ~3s: catches unlocks and config changes that happen without an
		// inventory redraw (the ScriptPostFired hook covers redraws immediately).
		// Quest states change even more rarely; the same cadence keeps the quest-NPC
		// override current without touching the render path.
		if (++markTickCounter % 5 == 0)
		{
			scheduleLockedItemMarks();
			questNpcIndex.refresh(client);
		}
		if (apiQueryTicks >= 0 && --apiQueryTicks < 0)
		{
			if (!collectionReader.hasApiData())
			{
				eventBus.post(new PluginMessage(TCG_API_NAMESPACE, TCG_API_QUERY));
			}
			// EventBus.post is synchronous, so an answered query flips hasApiData before
			// this line. Once answered, pushes keep us current - no more polling.
			apiQueryTicks = collectionReader.hasApiData() ? -1 : API_QUERY_RETRY_TICKS;
		}
		if (sharedQueryPending)
		{
			// Asked from a tick for the same reason as the osrs-tcg query above: a reply posted
			// during startUp would arrive before our @Subscribe methods are registered.
			sharedQueryPending = false;
			eventBus.post(new PluginMessage(SHARED_API_NAMESPACE, SHARED_QUERY));
		}
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
		reminderTicks = REMINDER_TICKS;
		requiredPluginTicks = REQUIRED_PLUGIN_TICKS;

		if (collectionReader.isStateAvailable())
		{
			queueChat("[Bronzeman TCG] Plugin is active - Good luck on the pulls!");
			if (!collectionReader.hasApiData())
			{
				// Fallback path (pre-API osrs-tcg, or its API hasn't answered yet): the config
				// read lags behind card pulls, so nudge the player to relog. Self-retiring -
				// once osrs-tcg's API version connects, hasApiData() is true and this stops.
				queueChat("[Bronzeman TCG] Not Connected to OSRS TCG API - Please relog if you are "
					+ "missing new card unlocks. Last known collection still active. Check OSRS TCG is enabled.");
			}
		}
		else
		{
			// Greeting the player with "0/6,376 collected" would misreport an unread
			// collection as an empty one.
			warnCollectionUnreadable();
		}
		warnRequiredPluginDisabled();
	}

	/** Re-checks periodically, so both notices keep surfacing while they still apply. */
	private void postPeriodicNotices()
	{
		reminderTicks = REMINDER_TICKS;
		warnCollectionUnreadable();
		// warnRequiredPluginDisabled() deliberately absent - it runs on its own faster timer.
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

	/**
	 * True when any plugin with this display name is installed and currently enabled.
	 * Several instances can share a name (a disabled hub copy alongside a sideloaded dev
	 * build), so every match is checked rather than trusting the first one found.
	 */
	private boolean isPluginEnabled(String displayName)
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
			if (descriptor != null && displayName.equals(descriptor.name())
				&& pluginManager.isPluginEnabled(plugin))
			{
				return true;
			}
		}
		return false;
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
		if (config.npcVisibilityMode() != NpcVisibilityMode.HIDE || !(renderable instanceof NPC)
			|| isEnforcementBypassed())
		{
			return true;
		}
		NPC npc = (NPC) renderable;
		if (NpcRestrictionPolicy.isCardRestrictionExempt(npc.getId()))
		{
			return true;
		}
		String name = resolveNpcName(npc);
		if (name == null || name.isEmpty())
		{
			return true;
		}
		// Quest override: an NPC of any started quest stays visible - hiding it would
		// silently brick the quest. Quest progression is the permit; no toggle.
		if (questNpcIndex.isShownQuestNpc(name))
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
		if (isEnforcementBypassed())
		{
			return;
		}
		MenuEntry entry = event.getMenuEntry();
		// NPC option visibility is owned exclusively by NPC Locks. Every other interaction
		// can remain visible for discoverability while the click path still blocks it and
		// explains the missing cards.
		if (config.showLockedMenuOptions() && !isNpcMenuOption(entry.getType()))
		{
			return;
		}
		if (shouldHideEntry(entry))
		{
			client.getMenu().removeMenuEntry(entry);
		}
	}

	private boolean isNpcMenuOption(MenuAction action)
	{
		switch (action)
		{
			case NPC_FIRST_OPTION:
			case NPC_SECOND_OPTION:
			case NPC_THIRD_OPTION:
			case NPC_FOURTH_OPTION:
			case NPC_FIFTH_OPTION:
				return true;
			default:
				return false;
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
		int menuGroup = WidgetUtil.componentToInterface(entry.getParam1());

		// Menu Entry Swapper promotes genuine inventory operations only after the menu has
		// assembled. Removing Wear/Drink/Use entries here prevents a configured Drop operation
		// from being promoted reliably. Keep inventory options present and let the click path
		// consume every locked action except the explicit Drop/Destroy disposal choices.
		if (isInventoryMenuVisibilityExempt(type, menuGroup))
		{
			return false;
		}

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
				if (NpcRestrictionPolicy.isCardRestrictionExempt(npc.getId()))
				{
					return false;
				}
				// Fishing restrictions remain click-only. Fishing spots have several valid
				// methods and the owner wants those choices visible even while their cards
				// are locked; selecting one still runs the normal blocking path below.
				if (FishingSpot.findSpot(npc.getId()) != null)
				{
					return false;
				}
				String name = resolveNpcName(npc);
				if (name == null || name.isEmpty())
				{
					return false;
				}
				NpcVisibilityMode mode = config.npcVisibilityMode();
				if (mode != NpcVisibilityMode.OFF && ATTACK_OPTION.equals(optionLower)
					&& !isUnlocked(monsterCatalog, name))
				{
					return true;
				}
				// Prevent Interaction strips every option (Examine is a different MenuAction, so it
				// survives untouched). Slayer masters answer only to the Slayer section, and
				// started-quest NPCs keep Prevent Combat treatment so quests never brick.
				if (mode.strictOptions() && !isUnlocked(monsterCatalog, name)
					&& !nodeCatalog.isSlayerNpc(name)
					&& !questNpcIndex.isShownQuestNpc(name))
				{
					return true;
				}
				// NPC Locks has had first refusal above. With discoverability enabled,
				// keep resource/activity options such as Catch, Tease and Pickpocket
				// visible; their click handlers still enforce and explain the rule.
				if (config.showLockedMenuOptions())
				{
					return false;
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
				String itemName = itemManager.getItemComposition(entry.getIdentifier()).getName();
				// Hunter traps retain Lay after being dropped. Reuse the exact inventory
				// activity rule so moving a snare to the ground cannot bypass Hunter mode.
				if (isGroundPlacementOption(option)
					&& itemName != null && !itemName.isEmpty()
					&& evaluateNodeRule(ResourceNodeCatalog.KIND_INVENTORY,
						itemName, option) != null)
				{
					return true;
				}
				// Restored after 0.2.15 made these unconditionally visible: with Ground Items
				// on Require Card, a locked Take is hidden again. Exempt names and unlocked
				// items keep their option, and the click path stays the final guard.
				if (config.groundItemsMode() != LockState.LOCKED
					|| !TAKE_OPTION.equals(optionLower))
				{
					return false;
				}
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
				if (objectName.isEmpty())
				{
					return false;
				}
				ResourceNodeCatalog.Rule rule = nodeCatalog.find(ResourceNodeCatalog.KIND_OBJECT,
					objectName, option, entry.getIdentifier());
				return rule != null && shouldHideWorldObjectCategory(rule.category)
					&& evaluateNodeRule(ResourceNodeCatalog.KIND_OBJECT, objectName, option,
						entry.getIdentifier()) != null;
			}
			case WIDGET_TARGET:
			{
				// "Use" on an inventory item (WIDGET_TARGET, not a CC_OP item op). The click
				// path blocks it under forced drop via handleUseSelected, so mirror that here
				// or the option stays visible while its click is silently cancelled.
				if (config.itemUsageMode() != LockState.LOCKED
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
				// A standard shop interface does not identify its currency reliably. Gate the
				// acquired item only; assuming Coins false-blocks Tokkul and other currency shops.
				if (menuGroup == InterfaceID.SHOPMAIN && optionLower.startsWith("buy"))
				{
					return entry.getItemId() > 0
						&& isBlockedItemName(itemManager.getItemComposition(entry.getItemId()).getName());
				}
				// Selling is disposal and never acquires the shop's currency card directly.
				// Leave it visible and unrestricted.
				// Otherwise inventory item ops only; bank/interface menus stay consume-only.
				if (!entry.isItemOp() || entry.getItemId() <= 0
					|| menuGroup != InterfaceID.INVENTORY)
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
				// Locked usage means the item keeps only its disposal options; equip, drink,
				// use and everything else fall under the same rule.
				return config.itemUsageMode() == LockState.LOCKED
					&& !isLockedItemDisposalOption(optionLower);
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
	public void onClientTick(ClientTick event)
	{
		makeInterfaceKeyboardState = readMakeInterfaceKeyboardState();
	}

	@Override
	public void keyPressed(KeyEvent event)
	{
		List<String> missing = event.getKeyCode() == KeyEvent.VK_SPACE
			? makeInterfaceKeyboardState.spaceMissing
			: makeInterfaceKeyboardState.missingByShortcut.get(
				MakeInterfaceKeyboardPolicy.shortcutForKeyCode(event.getKeyCode()));
		consumeBlockedMakeShortcut(event, missing);
	}

	@Override
	public void keyTyped(KeyEvent event)
	{
		List<String> missing = event.getKeyChar() == ' '
			? makeInterfaceKeyboardState.spaceMissing
			: makeInterfaceKeyboardState.missingByShortcut.get(
				MakeInterfaceKeyboardPolicy.shortcutForCharacter(event.getKeyChar()));
		consumeBlockedMakeShortcut(event, missing);
	}

	@Override
	public void keyReleased(KeyEvent event)
	{
		// The SkillMulti shortcuts act on press/typed events; releases remain untouched.
	}

	private void consumeBlockedMakeShortcut(KeyEvent event, List<String> missing)
	{
		if (missing == null || missing.isEmpty())
		{
			return;
		}
		event.consume();
		List<String> messageCards = missing;
		clientThread.invokeLater(() -> sendBlockedCardsMessage(messageCards));
	}

	private MakeInterfaceKeyboardState readMakeInterfaceKeyboardState()
	{
		if (isEnforcementBypassed())
		{
			return MakeInterfaceKeyboardState.NONE;
		}
		Widget quantityPrompt = client.getWidget(InterfaceID.Chatbox.MES_TEXT);
		if (quantityPrompt != null && !quantityPrompt.isHidden()
			&& MakeInterfaceKeyboardPolicy.isQuantityAmountPrompt(quantityPrompt.getText()))
		{
			// Number keys here are quantity input, not product selection.
			return MakeInterfaceKeyboardState.NONE;
		}

		Map<Character, List<String>> missingByShortcut = new LinkedHashMap<>();
		List<String> spaceMissing = null;
		int previousSelection = client.getVarpValue(VarPlayerID.SKILLMULTI_PREVIOUSSELECTION);
		for (int index = 0; index < SKILL_MULTI_PRODUCTS.length; index++)
		{
			Widget product = client.getWidget(SKILL_MULTI_PRODUCTS[index]);
			if (product == null || product.isHidden())
			{
				continue;
			}
			List<String> missing = evaluateMakeProductWidget(product);
			if (missing == null || missing.isEmpty())
			{
				continue;
			}
			char shortcut = MakeInterfaceKeyboardPolicy.shortcutForIndex(index);
			if (shortcut != '\0')
			{
				missingByShortcut.put(shortcut, missing);
			}
			if (previousSelection == index)
			{
				spaceMissing = missing;
			}
		}
		return missingByShortcut.isEmpty() ? MakeInterfaceKeyboardState.NONE
			: new MakeInterfaceKeyboardState(missingByShortcut, spaceMissing);
	}

	private List<String> evaluateMakeProductWidget(Widget widget)
	{
		Set<String> candidates = new LinkedHashSet<>();
		collectMakeProductCandidates(widget, 0, candidates);
		List<String> missing = new ArrayList<>();
		for (String candidate : candidates)
		{
			List<String> candidateMissing = evaluateNodeRule(
				ResourceNodeCatalog.KIND_INTERFACE, candidate,
				ResourceNodeCatalog.ANY_OPTION);
			if (candidateMissing == null)
			{
				candidateMissing = evaluateRecipe(RecipeCatalog.KIND_INTERFACE, candidate,
					resolveInterfaceMaterial(candidate));
			}
			if (candidateMissing != null)
			{
				for (String card : candidateMissing)
				{
					if (!missing.contains(card))
					{
						missing.add(card);
					}
				}
			}
		}
		return missing.isEmpty() ? null : Collections.unmodifiableList(missing);
	}

	private void collectMakeProductCandidates(Widget widget, int depth, Set<String> candidates)
	{
		if (widget == null || widget.isHidden() || depth > 3)
		{
			return;
		}
		addMakeProductItemCandidate(widget.getItemId(), candidates);
		Object[] onKeyListener = widget.getOnKeyListener();
		if (onKeyListener != null)
		{
			for (Object argument : onKeyListener)
			{
				if (argument instanceof Number)
				{
					addMakeProductItemCandidate(((Number) argument).intValue(), candidates);
				}
			}
		}
		addMakeProductTextCandidate(widget.getText(), candidates);
		addMakeProductTextCandidate(widget.getName(), candidates);
		String[] actions = widget.getActions();
		if (actions != null)
		{
			for (String action : actions)
			{
				addMakeProductTextCandidate(action, candidates);
			}
		}
		collectMakeProductCandidates(widget.getDynamicChildren(), depth, candidates);
		collectMakeProductCandidates(widget.getStaticChildren(), depth, candidates);
		collectMakeProductCandidates(widget.getNestedChildren(), depth, candidates);
	}

	private void collectMakeProductCandidates(Widget[] children, int depth,
		Set<String> candidates)
	{
		if (children == null)
		{
			return;
		}
		for (Widget child : children)
		{
			collectMakeProductCandidates(child, depth + 1, candidates);
		}
	}

	private void addMakeProductItemCandidate(int itemId, Set<String> candidates)
	{
		if (itemId > 0)
		{
			addMakeProductTextCandidate(
				itemManager.getItemComposition(itemId).getName(), candidates);
		}
	}

	private static void addMakeProductTextCandidate(String value, Set<String> candidates)
	{
		if (value == null)
		{
			return;
		}
		String candidate = stripProductQuantity(Text.removeTags(value));
		if (!candidate.isEmpty())
		{
			candidates.add(candidate);
		}
	}

	private static final class MakeInterfaceKeyboardState
	{
		private static final MakeInterfaceKeyboardState NONE =
			new MakeInterfaceKeyboardState(Collections.emptyMap(), null);

		private final Map<Character, List<String>> missingByShortcut;
		private final List<String> spaceMissing;

		private MakeInterfaceKeyboardState(Map<Character, List<String>> missingByShortcut,
			List<String> spaceMissing)
		{
			this.missingByShortcut = Collections.unmodifiableMap(
				new LinkedHashMap<>(missingByShortcut));
			this.spaceMissing = spaceMissing;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		refreshGroundItemLocks();
		boolean newUnlock = recentUnlocksTracker.update(
			collectionReader.getOwnedCardNamesLowerCase(), collectionReader.isStateAvailable());
		if (newUnlock)
		{
			refreshVisiblePanel();
		}

		// Ahead of the panel guard below - the greeting must still fire when the panel
		// is closed, and on every tick rather than one in five.
		tickWelcomeTimers();

		logLocalStanceOnChange();

		// Periodically ask the visible panel for a snapshot. The panel coalesces requests
		// and skips rendering when the collection/configuration has not changed.
		if (panel == null || ++tickCounter % 5 != 0)
		{
			return;
		}
		BronzemanTcgPanel target = panel;
		// Quest states change rarely. Capture immediately after login/panel creation,
		// then every 25 ticks (~15 seconds), instead of walking every Quest enum on each
		// five-tick panel refresh.
		if (!questStateInitialized || tickCounter % 25 == 0)
		{
			target.updateQuestState(captureCompletedQuests(), captureQuestRoute(),
				captureRealSkillLevels(), captureQuestPoints());
			questStateInitialized = true;
		}
		SwingUtilities.invokeLater(() ->
		{
			if (target.isShowing())
			{
				target.requestRefresh();
			}
		});
	}

	/** Read RuneLite quest state on the client thread; the panel only receives names. */
	private Set<String> captureCompletedQuests()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return Collections.emptySet();
		}
		Set<String> completed = new HashSet<>();
		for (Quest quest : Quest.values())
		{
			if (quest.getState(client) == QuestState.FINISHED)
			{
				completed.add(quest.getName().toLowerCase(Locale.ROOT));
			}
		}
		return Collections.unmodifiableSet(completed);
	}

	/**
	 * Real (unboosted) levels for the side panel's "Show meets reqs" filter, indexed by
	 * {@link net.runelite.api.Skill#ordinal()}. Returns null when logged out: unknown
	 * levels must leave the filter inert rather than hide every quest.
	 */
	private int[] captureRealSkillLevels()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}
		int[] levels = client.getRealSkillLevels();
		return levels == null ? null : levels.clone();
	}

	private int captureQuestPoints()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return 0;
		}
		return client.getVarpValue(VarPlayerID.QP);
	}

	/**
	 * Quest Helper identifies the Heroes' Quest route from BLACKARMGANG >= 4.
	 * Before Shield of Arrav is complete, accept either branch rather than guessing.
	 */
	private QuestCatalog.RouteSelection captureQuestRoute()
	{
		if (client.getGameState() != GameState.LOGGED_IN
			|| Quest.SHIELD_OF_ARRAV.getState(client) != QuestState.FINISHED)
		{
			return QuestCatalog.RouteSelection.UNKNOWN;
		}
		return client.getVarpValue(VarPlayerID.BLACKARMGANG) >= 4
			? QuestCatalog.RouteSelection.BLACK_ARM
			: QuestCatalog.RouteSelection.PHOENIX;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		// Item Usage temporarily controls whether marks are active, but it must not overwrite
		// the player's chosen visual mode. Refresh immediately in both directions so returning
		// to Card Required restores the indicators without a relog or another setting change.
		if (BronzemanTcgConfig.GROUP.equals(event.getGroup())
			&& ("itemUsageMode".equals(event.getKey())
				|| "lockedItemMarkMode".equals(event.getKey())))
		{
			scheduleLockedItemMarks();
		}

		// Duelist City Mode flipped: sweep every player in view right away rather than
		// waiting for their next appearance update. Composition edits touch client memory,
		// so they run on the client thread.
		if (BronzemanTcgConfig.GROUP.equals(event.getGroup())
			&& "duelistCityMode".equals(event.getKey()))
		{
			boolean on = Boolean.parseBoolean(event.getNewValue());
			clientThread.invoke(() -> sweepDuelistCity(on));
		}

		// Shared unlocks switched off: forget what was offered rather than hold it aside, so the
		// setting can never quietly restore a set the player has not seen arrive. Switched on: ask
		// for the current picture, since sources have had no reason to send anything while it was
		// off and would otherwise stay silent until their next change.
		if (BronzemanTcgConfig.GROUP.equals(event.getGroup())
			&& "acceptSharedUnlocks".equals(event.getKey()))
		{
			// Read the setting rather than parse the event: returning an item to its default clears
			// the stored value, and a null parses as false. This one defaults to on, so that would
			// read a reset as "switched off" and wipe the store instead of re-asking.
			if (config.acceptSharedUnlocks())
			{
				sharedQueryPending = true;
			}
			else
			{
				sharedUnlockStore.clear();
			}
		}

		if (BronzemanTcgConfig.GROUP.equals(event.getGroup()))
		{
			refreshVisibleSettings();
			refreshVisiblePanel();
		}
	}

	// ------------------------------------------------------------------ Duelist City Mode

	@Subscribe
	public void onPlayerSpawned(PlayerSpawned event)
	{
		if (config.duelistCityMode())
		{
			applyDuelistCards(event.getPlayer());
		}
	}

	@Subscribe
	public void onPlayerChanged(PlayerChanged event)
	{
		// Fires when the server re-sends a player's appearance (they changed gear, or just
		// came into range). At this instant the composition holds their REAL equipment, so
		// this is where we snapshot it before overwriting - and where our fake would be
		// wiped if we didn't re-apply.
		if (config.duelistCityMode())
		{
			applyDuelistCards(event.getPlayer());
		}
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned event)
	{
		// Player left view; their cached real equipment is no longer needed (a fresh
		// snapshot is taken if they return). Prevents the map growing without bound.
		duelistRealEquip.remove(event.getPlayer().getId());
	}

	/**
	 * Capture aid (debug only, permanent): logs the local player's 8 weapon-stance pose
	 * animation ids whenever they change - equip a weapon and its stance ids print once.
	 * ItemComposition doesn't expose these, so an in-game capture is the reliable source
	 * for hard-coding a specific weapon's stance (e.g. Mystic cards for Duelist City Mode).
	 * Order matches how the stance is applied: idle, walk, run, then the five turn poses.
	 */
	private void logLocalStanceOnChange()
	{
		if (!log.isDebugEnabled())
		{
			return;
		}
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return;
		}
		int[] stance = {
			me.getIdlePoseAnimation(), me.getWalkAnimation(), me.getRunAnimation(),
			me.getIdleRotateLeft(), me.getIdleRotateRight(),
			me.getWalkRotateLeft(), me.getWalkRotateRight(), me.getWalkRotate180(),
		};
		if (java.util.Arrays.equals(stance, lastLoggedStance))
		{
			return;
		}
		lastLoggedStance = stance;
		log.debug("local stance: idle={} walk={} run={} idleRotL={} idleRotR={} "
				+ "walkRotL={} walkRotR={} walkRot180={}",
			stance[0], stance[1], stance[2], stance[3], stance[4], stance[5], stance[6], stance[7]);
	}

	/** Give one player the Mystic-cards look, remembering their real weapon/shield first. */
	private void applyDuelistCards(Player player)
	{
		PlayerComposition comp = player == null ? null : player.getPlayerComposition();
		if (comp == null)
		{
			return;
		}
		int[] equip = comp.getEquipmentIds();
		int weaponIdx = KitType.WEAPON.getIndex();
		int shieldIdx = KitType.SHIELD.getIndex();

		// Snapshot the real weapon/shield AND the real stance the first time we touch this
		// player (or whenever the server hands us a genuine update - PlayerChanged carries
		// real values, so if the weapon isn't already our fake, the appearance was freshly
		// decoded and the pose fields hold the true weapon's stance to restore to).
		int fakeWeapon = MYSTIC_CARDS_ITEM_ID + PlayerComposition.ITEM_OFFSET;
		if (equip[weaponIdx] != fakeWeapon)
		{
			duelistRealEquip.put(player.getId(), new int[]{
				equip[weaponIdx], equip[shieldIdx],
				player.getIdlePoseAnimation(), player.getWalkAnimation(), player.getRunAnimation(),
				player.getIdleRotateLeft(), player.getIdleRotateRight(),
				player.getWalkRotateLeft(), player.getWalkRotateRight(), player.getWalkRotate180(),
			});
		}

		// Two-handed: fill the weapon slot with Mystic cards and blank the shield slot, then
		// force a model rebuild. setHash() re-renders the MODEL but not the pose animations,
		// so the stance is applied separately.
		equip[weaponIdx] = fakeWeapon;
		equip[shieldIdx] = 0;
		comp.setHash();
		applyStance(player, MYSTIC_STANCE);
	}

	/** Put one player's real weapon/shield AND real stance back, and rebuild their model. */
	private void restoreDuelistCards(Player player)
	{
		PlayerComposition comp = player == null ? null : player.getPlayerComposition();
		int[] real = player == null ? null : duelistRealEquip.remove(player.getId());
		if (comp == null || real == null)
		{
			return;
		}
		int[] equip = comp.getEquipmentIds();
		equip[KitType.WEAPON.getIndex()] = real[0];
		equip[KitType.SHIELD.getIndex()] = real[1];
		comp.setHash();
		applyStance(player, java.util.Arrays.copyOfRange(real, 2, real.length));
	}

	/** Set all eight weapon-stance pose animations on a player. Order matches MYSTIC_STANCE. */
	private static void applyStance(Player p, int[] s)
	{
		p.setIdlePoseAnimation(s[0]);
		p.setWalkAnimation(s[1]);
		p.setRunAnimation(s[2]);
		p.setIdleRotateLeft(s[3]);
		p.setIdleRotateRight(s[4]);
		p.setWalkRotateLeft(s[5]);
		p.setWalkRotateRight(s[6]);
		p.setWalkRotate180(s[7]);
	}

	/** Apply (or restore) the Mystic-cards look across every player currently in view. */
	private void sweepDuelistCity(boolean enable)
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return;
		}
		for (Player player : worldView.players())
		{
			if (enable)
			{
				applyDuelistCards(player);
			}
			else
			{
				restoreDuelistCards(player);
			}
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		// New account/profile: never let a previous profile's collection linger. This
		// drops any API-provided data too, so re-arm the query for the new profile.
		collectionReader.invalidate();
		recentUnlocksTracker.reload();
		// Shared unlocks describe a group this account was in, not this one; ask the sources for
		// the new profile's picture rather than waiting for one of them to notice.
		sharedUnlockStore.clear();
		sharedQueryPending = true;
		apiQueryTicks = 0;
	}

	/**
	 * A sibling plugin offering extra unlocked cards, e.g. a party mode where a card owned by any
	 * member counts for the group. Honoured only while the player has switched shared unlocks on,
	 * so nothing another plugin does can loosen the restrictions behind their back.
	 */
	private void onSharedUnlocks(PluginMessage event)
	{
		if (!SHARED_UNLOCKS.equals(event.getName()))
		{
			return;
		}
		Map<String, Object> data = event.getData();
		Object source = data == null ? null : data.get(SHARED_SOURCE_KEY);
		Object names = data == null ? null : data.get(SHARED_NAMES_KEY);
		if (!(source instanceof String) || !(names instanceof List))
		{
			return;
		}
		if (!config.acceptSharedUnlocks())
		{
			// Remembering it while switched off would make flipping the setting on apply a set the
			// player never saw arrive, so the offer is simply dropped.
			return;
		}
		// Menus read effectiveOwnedCards() on demand and the ~3s sweep re-applies item marks.
		// The panel additionally shows the live shared set and an explicitly labelled shared
		// history, so refresh only when either current state or that history changes.
		boolean changed = sharedUnlockStore.put((String) source, (List<?>) names);
		Set<String> sharedOnly = new HashSet<>(
			sharedUnlockStore.getSharedCardNamesLowerCase());
		sharedOnly.removeAll(collectionReader.getOwnedCardNamesLowerCase());
		boolean newSharedUnlock = recentUnlocksTracker.updateShared(sharedOnly);
		if (changed || newSharedUnlock)
		{
			refreshVisiblePanel();
		}
	}

	/**
	 * osrs-tcg's PluginMessage API: both the reply to our query and unsolicited pushes
	 * after collection changes carry the same owned-names payload, so they share a path.
	 */
	@Subscribe
	public void onPluginMessage(PluginMessage event)
	{
		if (SHARED_API_NAMESPACE.equals(event.getNamespace()))
		{
			onSharedUnlocks(event);
			return;
		}
		if (!TCG_API_NAMESPACE.equals(event.getNamespace())
			|| (!TCG_API_REPLY.equals(event.getName()) && !TCG_API_CHANGED.equals(event.getName())))
		{
			return;
		}
		Map<String, Object> data = event.getData();
		Object names = data == null ? null : data.get(TCG_API_NAMES_KEY);
		if (!(names instanceof List))
		{
			return;
		}
		boolean firstPayload = !collectionReader.hasApiData();
		collectionReader.onApiOwnedNames((List<?>) names);
		if (firstPayload)
		{
			recentUnlocksTracker.resetBaseline();
		}
		if (recentUnlocksTracker.update(collectionReader.getOwnedCardNamesLowerCase(), true))
		{
			refreshVisiblePanel();
		}
		if (firstPayload && collectionReader.hasApiData())
		{
			log.info("osrs-tcg PluginMessage API active; collection now push-updated.");
		}
	}

	/** Refresh only a visible Swing panel; tracking itself continues while it is closed. */
	private void refreshVisiblePanel()
	{
		BronzemanTcgPanel target = panel;
		if (target == null)
		{
			return;
		}
		SwingUtilities.invokeLater(() ->
		{
			if (target.isShowing())
			{
				target.requestRefresh();
			}
		});
	}

	/** Only trees still remove a blocked world-object option; other nodes use chat feedback. */
	static boolean shouldHideWorldObjectCategory(String category)
	{
		return "woodcutting".equals(category);
	}

	private void refreshVisibleSettings()
	{
		BronzemanTcgPanel target = panel;
		if (target == null)
		{
			return;
		}
		SwingUtilities.invokeLater(() ->
		{
			if (target.isShowing())
			{
				target.onConfigChanged();
			}
		});
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
		if (NpcRestrictionPolicy.isCardRestrictionExempt(npc.getId()))
		{
			return;
		}
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
		if (isRestrictedNpcInteraction(event, npcName) && !isUnlocked(monsterCatalog, npcName))
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

		// Fishing spots are NPCs, but their shared display names are too broad for rules.
		// RuneLite maintains the authoritative NPC-id -> FishingSpot mapping, so key the
		// resource data by its enum name plus the exact clicked method.
		FishingSpot fishingSpot = FishingSpot.findSpot(npc.getId());
		if (fishingSpot != null)
		{
			checkNodeRule(event, ResourceNodeCatalog.KIND_FISHING_SPOT,
				fishingSpot.name(), cleanOption);
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
		// He follows the thieving dropdown like every other target - no "Master Farmer"
		// card exists, so below All the requirement is just Coins + Coin pouch. The
		// Insanity toggle only bites in All mode, mirroring H.A.M.
		ThievingMode mode = config.thievingMode();
		if (mode == ThievingMode.OFF || mode == ThievingMode.NPC_CARD_ONLY)
		{
			return null;
		}
		List<String> required = List.of("Coins", "Coin pouch");
		if (mode == ThievingMode.NPC_AND_LOOT && config.masterFarmerInsanity())
		{
			List<String> seeds = nodeCatalog.getMasterFarmerSeedCards();
			if (!seeds.isEmpty())
			{
				// Seed list missing from the data file would silently disable Insanity,
				// so the Coins+Pouch base is the fallback.
				required = seeds;
			}
		}
		return missingCards(required);
	}

	// ------------------------------------------------------------------ ground items

	private void handleGroundItemInteraction(MenuOptionClicked event, MenuAction action)
	{
		if (action != MenuAction.WIDGET_TARGET_ON_GROUND_ITEM)
		{
			String option = event.getMenuOption();
			ItemComposition composition = itemManager.getItemComposition(event.getId());
			String itemName = composition.getName();
			if (isGroundPlacementOption(option) && itemName != null && !itemName.isEmpty()
				&& checkNodeRule(event, ResourceNodeCatalog.KIND_INVENTORY, itemName,
					Text.removeTags(option).trim()))
			{
				return;
			}
			// Telegrab (widget-on-ground-item) is always loot; plain clicks only for "Take".
			if (option == null
				|| !TAKE_OPTION.equals(Text.removeTags(option).trim().toLowerCase(Locale.ROOT)))
			{
				return;
			}
		}
		if (config.groundItemsMode() != LockState.LOCKED)
		{
			return;
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

	static boolean isGroundPlacementOption(String option)
	{
		return option != null
			&& "lay".equals(Text.removeTags(option).trim().toLowerCase(Locale.ROOT));
	}

	// ------------------------------------------------------------------ carried tools

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		// Equipment too: a pickaxe equipped before its card locked (or before the
		// plugin was enabled) still mines.
		if (event.getContainerId() == InventoryID.INV
			|| event.getContainerId() == InventoryID.WORN)
		{
			refreshCarriedTools();
		}
	}

	private void refreshCarriedTools()
	{
		Set<String> pickaxes = new HashSet<>();
		Set<String> axes = new HashSet<>();
		Set<String> fishingInputs = new HashSet<>();
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		collectTools(inventory, pickaxes, axes, fishingInputs);
		collectTools(equipment, pickaxes, axes, fishingInputs);
		carriedPickaxes = pickaxes;
		carriedAxes = axes;
		carriedFishingInputs = fishingInputs;
		inventoryItemNamesLower = collectItemNamesLower(inventory);
	}

	private Set<String> collectItemNamesLower(ItemContainer container)
	{
		if (container == null)
		{
			return Collections.emptySet();
		}
		Set<String> names = new HashSet<>();
		for (Item item : container.getItems())
		{
			if (item.getId() >= 0)
			{
				String name = itemManager.getItemComposition(item.getId()).getName();
				if (name != null)
				{
					names.add(name.toLowerCase(Locale.ROOT));
				}
			}
		}
		return names;
	}

	private void collectTools(ItemContainer container, Set<String> pickaxes, Set<String> axes,
		Set<String> fishingInputs)
	{
		if (container == null)
		{
			return;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() < 0)
			{
				continue;
			}
			String name = itemManager.getItemComposition(item.getId()).getName();
			if (name == null)
			{
				continue;
			}
			fishingInputs.add(name);
			String lower = name.toLowerCase(Locale.ROOT);
			if (lower.endsWith(" pickaxe"))
			{
				pickaxes.add(name);
			}
			else if (WOODCUTTING_AXES.contains(lower))
			{
				axes.add(name);
			}
		}
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
		if (log.isDebugEnabled() && (objectName.toLowerCase(Locale.ROOT).contains("shipwreck")
			|| objectName.toLowerCase(Locale.ROOT).contains("salvaging hook")
			|| objectName.equalsIgnoreCase("Boat schematics")))
		{
			log.debug("sailing object click id={} name='{}' option='{}' action={}",
				event.getId(), objectName, Text.removeTags(option), event.getMenuAction());
		}
		checkNodeRule(event, ResourceNodeCatalog.KIND_OBJECT, objectName, Text.removeTags(option),
			event.getId());
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
		Widget eventWidget = event.getWidget();
		int itemId = eventWidget != null && eventWidget.getItemId() > 0
			? eventWidget.getItemId() : entry.getItemId();
		if ((group == InterfaceID.SAILING_MENU
			|| group == InterfaceID.SAILING_CUSTOMISATION
			|| group == InterfaceID.SKILLMULTI
			|| group == InterfaceID.SMITHING
			|| isMakeVerb(optionLower))
			&& checkSailingUpgradeRecipe(event, itemId))
		{
			return;
		}

		if (group == InterfaceID.SAILING_MENU || group == InterfaceID.SAILING_CUSTOMISATION)
		{
			Widget widget = eventWidget;
			String itemName = null;
			if (itemId > 0)
			{
				itemName = itemManager.getItemComposition(itemId).getName();
			}
			if (log.isDebugEnabled())
			{
				log.debug("sailing interface group={} component={} child={} option='{}' target='{}' "
						+ "itemId={} itemName='{}' text='{}' name='{}'",
					group,
					widget == null ? -1 : widget.getId(),
					widget == null ? -1 : widget.getIndex(),
					option,
					Text.removeTags(event.getMenuTarget()),
					itemId,
					itemName == null ? "(no item id)" : itemName,
					widget == null || widget.getText() == null
						? "" : Text.removeTags(widget.getText()),
					widget == null || widget.getName() == null
						? "" : Text.removeTags(widget.getName()));
			}
			if (itemName != null && !itemName.isEmpty()
				&& nodeCatalog.find(ResourceNodeCatalog.KIND_INTERFACE, itemName,
					ResourceNodeCatalog.ANY_OPTION) != null)
			{
				checkNodeRule(event, ResourceNodeCatalog.KIND_INTERFACE, itemName,
					ResourceNodeCatalog.ANY_OPTION);
				return;
			}
			// If this widget carries no item id, continue into the existing make-verb
			// fallback. The debug record above supplies the stable component evidence
			// needed for a later exact mapping; no label is guessed here.
		}

		// Bank: discriminate on option text (the bank-side panels are not the inventory group).
		if (optionLower.startsWith("withdraw"))
		{
			// Deposit Only keeps the bank a holding pen; only Full Banking releases items.
			if (config.itemUsageMode() == LockState.LOCKED
				&& config.bankingMode() != BankingMode.FULL)
			{
				blockIfLockedItem(event, itemOpName(event, entry));
			}
			return;
		}
		if (optionLower.startsWith("deposit"))
		{
			if (config.itemUsageMode() == LockState.LOCKED
				&& config.bankingMode() == BankingMode.OFF)
			{
				blockIfLockedItem(event, itemOpName(event, entry));
			}
			return;
		}

		if (group == InterfaceID.SHOPMAIN)
		{
			// RuneLite exposes the shop interface and acquired item, but not one dependable
			// currency identity for every shop. Gate the acquired item without assuming Coins.
			if (optionLower.startsWith("buy"))
			{
				blockIfLockedItem(event, itemOpName(event, entry));
			}
			return;
		}

		if (group == InterfaceID.SHOPSIDE)
		{
			// Selling is disposal. Do not guess which currency the shop will return.
			return;
		}

		if (group == InterfaceID.SKILLMULTI || group == InterfaceID.SMITHING)
		{
			// Make-X product click: only the product name is reliable. The parallel
			// KeyManager path applies the same decision to SkillMulti shortcuts.
			// Node rules get first refusal, same as the make-verb fallback below - cooking
			// lives there, and this interface is how range-click "Cook" flows arrive.
			String product = stripProductQuantity(Text.removeTags(event.getMenuTarget()));
			logInterfaceProduct(event, product);
			if (!product.isEmpty()
				&& !checkNodeRule(event, ResourceNodeCatalog.KIND_INTERFACE, product,
					ResourceNodeCatalog.ANY_OPTION))
			{
				checkRecipe(event, RecipeCatalog.KIND_INTERFACE, product,
					resolveInterfaceMaterial(product));
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
		if (config.grandExchangeMode() == LockState.LOCKED
			&& group == InterfaceID.CHATBOX && isGrandExchangeOpen())
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
			String product = stripProductQuantity(Text.removeTags(event.getMenuTarget()));
			logInterfaceProduct(event, product);
			if (!product.isEmpty()
				&& !checkNodeRule(event, ResourceNodeCatalog.KIND_INTERFACE, product,
					ResourceNodeCatalog.ANY_OPTION))
			{
				checkRecipe(event, RecipeCatalog.KIND_INTERFACE, product,
					resolveInterfaceMaterial(product));
			}
		}
	}

	/** @return true when this item ID is an exact Sailing recipe, blocked or allowed. */
	private boolean checkSailingUpgradeRecipe(MenuOptionClicked event, int itemId)
	{
		SailingUpgradeRecipeCatalog.Recipe recipe = SailingUpgradeRecipeCatalog.find(itemId);
		if (recipe == null)
		{
			return false;
		}
		List<String> missing = recipe.missingRequirements(
			effectiveOwnedCards(), config.sailingUpgradeMode());
		if (!missing.isEmpty())
		{
			event.consume();
			sendBlockedCardsMessage(missing);
		}
		return true;
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

		// Locked usage: only the disposal options survive; equip/drink/use all fall here.
		if (config.itemUsageMode() == LockState.LOCKED
			&& !isLockedItemDisposalOption(optionLower))
		{
			blockIfLockedItem(event, itemName);
		}
	}

	static boolean isInventoryMenuVisibilityExempt(MenuAction type, int menuGroup)
	{
		if (menuGroup != InterfaceID.INVENTORY)
		{
			return false;
		}
		return type == MenuAction.WIDGET_TARGET
			|| type == MenuAction.CC_OP
			|| type == MenuAction.CC_OP_LOW_PRIORITY;
	}

	/**
	 * Inventory entries must survive MenuEntryAdded so Menu Entry Swapper can see the complete
	 * operation list. Its own PostMenuSort subscriber runs at the default priority; this lower
	 * priority pass removes the blocked operations only after the configured left-click swap has
	 * been applied, preserving a promoted Drop entry.
	 */
	@Subscribe(priority = -1.0f)
	public void onPostMenuSort(PostMenuSort event)
	{
		if (isEnforcementBypassed() || config.showLockedMenuOptions())
		{
			return;
		}
		MenuEntry[] entries = client.getMenu().getMenuEntries();
		List<MenuEntry> visible = new ArrayList<>(entries.length);
		boolean changed = false;
		for (MenuEntry entry : entries)
		{
			if (shouldHideInventoryEntryAfterSort(entry))
			{
				changed = true;
			}
			else
			{
				visible.add(entry);
			}
		}
		if (changed)
		{
			client.getMenu().setMenuEntries(visible.toArray(new MenuEntry[0]));
		}
	}

	private boolean shouldHideInventoryEntryAfterSort(MenuEntry entry)
	{
		MenuAction type = entry.getType();
		int menuGroup = WidgetUtil.componentToInterface(entry.getParam1());
		if (!isInventoryMenuVisibilityExempt(type, menuGroup))
		{
			return false;
		}

		String option = Text.removeTags(entry.getOption()).trim();
		if (option.isEmpty())
		{
			return false;
		}
		if (type == MenuAction.WIDGET_TARGET)
		{
			String itemName = Text.removeTags(entry.getTarget()).trim();
			return shouldHideLockedInventoryOption(option,
				!itemName.isEmpty() && isBlockedItemName(itemName));
		}
		if (!entry.isItemOp() || entry.getItemId() <= 0)
		{
			return false;
		}
		String itemName = itemManager.getItemComposition(entry.getItemId()).getName();
		if (itemName == null || itemName.isEmpty())
		{
			return false;
		}
		if (evaluateNodeRule(ResourceNodeCatalog.KIND_INVENTORY, itemName, option) != null)
		{
			return true;
		}
		return shouldHideLockedInventoryOption(option,
			config.itemUsageMode() == LockState.LOCKED && isBlockedItemName(itemName));
	}

	static boolean shouldHideLockedInventoryOption(String option, boolean blocked)
	{
		return blocked && !isLockedItemDisposalOption(option);
	}

	/**
	 * Menu Entry Swapper only changes which genuine item operation is promoted to
	 * left-click. Keeping Drop allowed here means an explicit per-item left-click
	 * Drop choice works without Bronzeman reading or copying another plugin's config.
	 */
	static boolean isLockedItemDisposalOption(String option)
	{
		return option != null
			&& FORCED_DROP_ALLOWED.contains(option.toLowerCase(Locale.ROOT));
	}

	/** "Use" with an inventory item selected: a locked item can't be used on anything. */
	private void handleUseSelected(MenuOptionClicked event)
	{
		if (config.itemUsageMode() != LockState.LOCKED)
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

		// Remember the pair: if this click opens a make interface, the product label may
		// be too generic to identify the item, and this is the only record of what was used.
		lastUsedItemA = source;
		lastUsedItemB = destination;
		lastUsedItemTick = client.getTickCount();

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

		// Recipes get first refusal (owner ruling 2026-07-20): their block message names
		// EVERY missing card, where the generic item lock below would stop at the first
		// locked item (e.g. "Feather" alone when the bolts card is also missing). A
		// passing recipe implies its material cards are owned, so the item lock cannot
		// disagree with it. Data keys tool->material; clicks can arrive either way round.
		if (checkRecipe(event, RecipeCatalog.KIND_ITEM_ON_ITEM, source, destination)
			|| checkRecipe(event, RecipeCatalog.KIND_ITEM_ON_ITEM, destination, source))
		{
			return;
		}

		// Locked usage: a locked item can't be used on anything (or be used upon).
		if (config.itemUsageMode() == LockState.LOCKED)
		{
			if (!blockIfLockedItem(event, source))
			{
				blockIfLockedItem(event, destination);
			}
		}
	}

	/**
	 * Make-interface product widgets can carry a batch quantity in their name - the
	 * owner's debug capture shows knife-menu shafts as "45 arrow shafts" (count scales
	 * with log tier). Recipe/node keys hold plain product names, so strip a leading
	 * count ("45 ", "45 x ") and a trailing "xN". Digits-without-space names ("3rd age
	 * pickaxe") and plain products pass through unchanged.
	 */
	private static String stripProductQuantity(String product)
	{
		return product.trim()
			.replaceAll("(?i)\\s*x\\s*\\d{1,5}$", "")
			.replaceAll("(?i)^\\d{1,5}\\s*(x\\s+|\\s)", "")
			.trim();
	}

	/**
	 * Companion to the "node lookup" line: every menu-keyed rule depends on the exact
	 * string an interface sends, and guessing it has been the single biggest source of
	 * rules that silently never fire. Also reports the clicked widget's item id, which
	 * identifies the product outright where the label cannot - the knife menu labels
	 * every tier "Crossbow stock", but the item id distinguishes Willow from Magic.
	 */
	/**
	 * Resolve the material behind a make-interface product click, for the menus whose
	 * label is the same for every tier ("Crossbow stock"). Returns whichever remembered
	 * item actually has a rule for this product - an exact lookup, since the normal
	 * any-target fallback would match every candidate and prove nothing. Null when the
	 * menu wasn't opened by a recent item-on-item, in which case the product is simply
	 * not restricted rather than guessed at.
	 */
	private String resolveInterfaceMaterial(String product)
	{
		if (client.getTickCount() - lastUsedItemTick > MAKE_MATERIAL_MEMORY_TICKS)
		{
			return null;
		}
		for (String candidate : new String[]{lastUsedItemA, lastUsedItemB})
		{
			if (candidate != null && !candidate.isEmpty()
				&& recipeCatalog.findExact(RecipeCatalog.KIND_INTERFACE, product, candidate) != null)
			{
				return candidate;
			}
		}
		return null;
	}

	private void logInterfaceProduct(MenuOptionClicked event, String product)
	{
		if (!log.isDebugEnabled())
		{
			return;
		}
		Widget widget = event.getWidget();
		int itemId = widget == null ? -1 : widget.getItemId();
		String itemName = itemId > 0
			? itemManager.getItemComposition(itemId).getName() : "(no item id)";
		log.debug("interface product raw='{}' stripped='{}' widgetItemId={} itemName='{}'",
			Text.removeTags(event.getMenuTarget()), product, itemId, itemName);
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
		return checkNodeRule(event, kind, name, option, -1);
	}

	/** @return true when the event was consumed (blocked). */
	private boolean checkNodeRule(MenuOptionClicked event, String kind, String name, String option,
		int targetId)
	{
		// Data-string mismatches (an option or name that differs from the wiki-sourced value)
		// fail silently and look exactly like "the restriction is ignored", so log the lookup.
		// Click path only: the hide path calls evaluateNodeRule many times per frame.
		if (log.isDebugEnabled())
		{
			ResourceNodeCatalog.Rule rule = nodeCatalog.find(kind, name, option, targetId);
			log.debug("node lookup kind={} name='{}' option='{}' targetId={} -> {}",
				kind, name, option, targetId,
				rule == null ? "NO RULE" : "rule[" + rule.category + "]");
		}
		List<String> missing = evaluateNodeRule(kind, name, option, targetId);
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
		return evaluateNodeRule(kind, name, option, -1);
	}

	private List<String> evaluateNodeRule(String kind, String name, String option, int targetId)
	{
		ResourceNodeCatalog.Rule rule = nodeCatalog.find(kind, name, option, targetId);
		if (rule == null)
		{
			return null;
		}

		if ("mining".equals(rule.category) || "woodcutting".equals(rule.category))
		{
			return evaluateGatheringRule(rule);
		}
		if ("fishing".equals(rule.category))
		{
			return evaluateFishingRule(rule);
		}
		if ("hunter-salamanders".equals(rule.category))
		{
			return evaluateSalamanderRule(targetId);
		}
		if ("hunter-birds".equals(rule.category))
		{
			return evaluateAreaTrapRule(rule, true);
		}
		if ("hunter-chins".equals(rule.category))
		{
			return evaluateAreaTrapRule(rule, false);
		}
		if ("hunter-butterflies".equals(rule.category)
			|| "hunter-implings".equals(rule.category))
		{
			return evaluateHunterNetRule(rule);
		}
		if ("runecrafting".equals(rule.category))
		{
			return evaluateRunecraftingRule(rule);
		}
		if ("pickpocketing".equals(rule.category)
			&& config.thievingMode() == ThievingMode.NPC_CARD_ONLY)
		{
			List<String> missing = rule.missingRequirementsForRole(
				effectiveOwnedCards(), "npc");
			return missing.isEmpty() ? null : missing;
		}

		boolean forceAllInGroups = false;
		Set<String> excludedRoles;
		if ("thieving-stalls".equals(rule.category)
			|| "thieving-chests".equals(rule.category))
		{
			// Stalls/chests carry one any-of loot group; the mode dial forces all-of for "All".
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

	private List<String> evaluateSalamanderRule(int objectId)
	{
		HunterMode mode = config.hunterMode();
		if (mode == HunterMode.OFF)
		{
			return null;
		}

		List<String> required = new ArrayList<>(List.of("Rope", "Small fishing net"));
		HunterTrapType trap = HunterTrapType.fromObjectId(objectId);
		if (mode == HunterMode.ALL_CARDS && trap != null)
		{
			required.add(trap.getCardName());
		}
		return missingOwnedCards(required);
	}

	private List<String> evaluateAreaTrapRule(ResourceNodeCatalog.Rule rule, boolean bird)
	{
		HunterMode mode = config.hunterMode();
		if (mode == HunterMode.OFF)
		{
			return null;
		}

		List<String> missing = rule.missingRequirements(
			effectiveOwnedCards(), Collections.emptySet(), false);
		if (mode == HunterMode.ALL_CARDS)
		{
			addMissingCards(missing, bird
				? HunterAreaSpecies.birdCards(currentRegionId())
				: HunterAreaSpecies.chinchompaCards(currentRegionId()));
		}
		return missing.isEmpty() ? null : missing;
	}

	private List<String> evaluateHunterNetRule(ResourceNodeCatalog.Rule rule)
	{
		HunterMode mode = config.hunterMode();
		if (mode == HunterMode.OFF)
		{
			return null;
		}
		Set<String> excluded = mode == HunterMode.TOOLS_ONLY
			? Set.of("output") : Collections.emptySet();
		List<String> missing = rule.missingRequirements(
			effectiveOwnedCards(), excluded, false);
		return missing.isEmpty() ? null : missing;
	}

	private List<String> evaluateRunecraftingRule(ResourceNodeCatalog.Rule rule)
	{
		RunecraftingMode mode = config.runecraftingMode();
		if (mode == RunecraftingMode.OFF)
		{
			return null;
		}

		// GotR supplies Guardian essence and portal access. Those are not the normal
		// altar inputs, so only the resulting rune card belongs to the strict mode.
		if (client.getVarbitValue(VarbitID.GOTR_IS_PLAYING) != 0)
		{
			if (mode == RunecraftingMode.TALISMAN)
			{
				return null;
			}
			List<String> missing = rule.missingRequirements(
				effectiveOwnedCards(), Set.of("essence", "talisman"), false);
			return missing.isEmpty() ? null : missing;
		}

		Set<String> excluded = new HashSet<>();
		excluded.add("essence");
		if (mode == RunecraftingMode.TALISMAN)
		{
			excluded.add("rune");
		}
		List<String> missing = rule.missingRequirements(
			effectiveOwnedCards(), excluded, false);

		ResourceNodeCatalog.CardGroup essenceGroup = null;
		for (ResourceNodeCatalog.CardGroup group : rule.groups)
		{
			if ("essence".equals(group.role))
			{
				essenceGroup = group;
				break;
			}
		}
		if (essenceGroup == null)
		{
			return missing.isEmpty() ? null : missing;
		}

		boolean foundCarriedEssence = false;
		for (int i = 0; i < essenceGroup.lowerCards.size(); i++)
		{
			if (inventoryItemNamesLower.contains(essenceGroup.lowerCards.get(i)))
			{
				foundCarriedEssence = true;
				addMissingCard(missing, essenceGroup.displayCards.get(i));
			}
		}
		if (!foundCarriedEssence && !essenceGroup.isSatisfied(effectiveOwnedCards()))
		{
			// Essence may be entirely inside pouches, where the client exposes no item
			// variant at click time. Preserve the existing any-valid-essence fallback.
			missing.add(String.join(" / ", essenceGroup.displayCards));
		}
		return missing.isEmpty() ? null : missing;
	}

	private int currentRegionId()
	{
		Player local = client.getLocalPlayer();
		return local == null ? -1 : local.getWorldLocation().getRegionID();
	}

	private List<String> missingOwnedCards(List<String> cards)
	{
		List<String> missing = new ArrayList<>();
		for (String card : cards)
		{
			addMissingCard(missing, card);
		}
		return missing.isEmpty() ? null : missing;
	}

	private void addMissingCard(List<String> missing, String card)
	{
		if (card != null && !isLootExempt(card)
			&& !effectiveOwnedCards().contains(card.toLowerCase(Locale.ROOT))
			&& !missing.contains(card))
		{
			missing.add(card);
		}
	}

	private void addMissingCards(List<String> missing, List<String> cards)
	{
		for (String card : cards)
		{
			addMissingCard(missing, card);
		}
	}

	/**
	 * Mining/woodcutting share a four-way dial whose halves are the node's yield cards
	 * (ore/logs) and the carried tool. The tool half blocks while ANY carried pickaxe/axe
	 * is locked - the client silently uses the best tool carried, so one unlocked tool
	 * must not alibi a locked better one (owner's ruling). Untracked tool variants
	 * (ornamented kits, uncarded felling axes) are never locked, per the standing rule.
	 */
	private List<String> evaluateGatheringRule(ResourceNodeCatalog.Rule rule)
	{
		boolean mining = "mining".equals(rule.category);
		boolean enforceYield;
		boolean enforceTool;
		if (mining)
		{
			MiningMode mode = config.miningMode();
			if (mode == MiningMode.OFF)
			{
				return null;
			}
			// Both remaining modes need the pickaxe; only "Tool + Ore" also needs the ore card.
			enforceYield = mode == MiningMode.CARD_REQUIRED;
			enforceTool = true;
		}
		else
		{
			WoodcuttingMode mode = config.woodcuttingMode();
			if (mode == WoodcuttingMode.OFF)
			{
				return null;
			}
			// Both remaining modes need the axe; only "Logs + Tools Needed" also needs the
			// logs card. (Chopping always uses an axe, so a logs-card-without-tool mode made
			// little sense - the owner folded it into the tool requirement.)
			enforceYield = mode == WoodcuttingMode.LOGS_ONLY;
			enforceTool = true;
		}

		List<String> missing = enforceYield
			? rule.missingRequirements(effectiveOwnedCards(), Collections.emptySet(), false)
			: new ArrayList<>();
		if (enforceTool)
		{
			for (String tool : mining ? carriedPickaxes : carriedAxes)
			{
				if (!isLootExempt(tool) && !isUnlocked(itemCatalog, tool))
				{
					missing.add(tool);
				}
			}
		}
		return missing.isEmpty() ? null : missing;
	}

	/**
	 * Fishing requirements come from the exact RuneLite FishingSpot + menu-option rule.
	 * role:"tool" includes equipment, bait, feathers and other consumed inputs. Only
	 * applicable inputs actually carried are checked, so optional bait is not invented;
	 * carrying any locked applicable variant blocks (the same strict ruling as gathering).
	 * Catch groups are ignored in Tools Only, any-of in Tools + Any Fish, and expanded
	 * to every carded catch in Tools + Fish.
	 */
	private List<String> evaluateFishingRule(ResourceNodeCatalog.Rule rule)
	{
		FishingRestrictionMode mode = config.fishingMode();
		if (mode == FishingRestrictionMode.OFF)
		{
			return null;
		}

		List<String> missing = new ArrayList<>();
		for (ResourceNodeCatalog.CardGroup group : rule.groups)
		{
			if (!"tool".equals(group.role))
			{
				continue;
			}
			for (String carried : carriedFishingInputs)
			{
				if (group.lowerCards.contains(carried.toLowerCase(Locale.ROOT))
					&& !isLootExempt(carried) && !isUnlocked(itemCatalog, carried)
					&& !missing.contains(carried))
				{
					missing.add(carried);
				}
			}
		}

		if (mode == FishingRestrictionMode.CARD_REQUIRED
			|| mode == FishingRestrictionMode.ALL_CATCHES)
		{
			for (String catchCard : rule.missingRequirements(
				effectiveOwnedCards(), Collections.singleton("tool"),
				mode == FishingRestrictionMode.ALL_CATCHES))
			{
				if (!missing.contains(catchCard))
				{
					missing.add(catchCard);
				}
			}
		}
		return missing.isEmpty() ? null : missing;
	}

	/**
	 * Config gate per node category.
	 *
	 * @return roles to skip during evaluation, or null when the category is switched off.
	 *         Unknown categories restrict fully so new data stays loud rather than inert.
	 */
	/**
	 * True once Barbarian Training's Farming section is under way, which lets the player
	 * plant seeds bare-handed. Read directly rather than scheduled: every caller is
	 * already on the client thread, same as the LMS and Tutorial Island checks.
	 */
	private boolean hasBareHandedPlanting()
	{
		return client.getVarbitValue(BRUT_FARMING_PLANTING_VARBIT) >= 1;
	}

	private Set<String> excludedRolesFor(String category)
	{
		switch (category)
		{
			case "pickpocketing":
				switch (config.thievingMode())
				{
					case NPC_CARD_ONLY:
						// Handled before generic role exclusion so unlabelled Coins/Pouch
						// groups are ignored rather than accidentally enforced.
						return null;
					case COINS_POUCH:
						return Set.of("npc", "loot", "loot-ham", "loot-elf");
					case NPC_ONLY:
						return Set.of("loot", "loot-ham", "loot-elf");
					case NPC_AND_LOOT:
						return config.hamFullLoot()
								? Collections.emptySet()
								: Set.of("loot-ham");
					default:
						return null;
				}
			case "cooking":
			{
				// Cooking rules carry input (raw) / output (cooked) / burnt roles. The
				// Cooking dropdown picks the food card (input or output); the separate Burnt
				// Food Cards dropdown layers the burnt card on top - but only while cooking
				// is actually restricted (a burnt requirement with cooking off is nonsense).
				CookingMode mode = config.cookingMode();
				if (mode == CookingMode.OFF)
				{
					return null;
				}
				Set<String> excluded = new HashSet<>();
				if (mode == CookingMode.INPUT_ONLY)
				{
					excluded.add("output");
				}
				// INPUT_OUTPUT enforces both the raw and cooked cards (excludes neither).
				if (config.burntFoodMode() != BurntFoodMode.REQUIRE_CARD)
				{
					excluded.add("burnt");
				}
				return excluded;
			}
			case "farming-plant":
			{
				if (config.farmingRakeMode() == FarmingRakeMode.OFF)
				{
					return null;
				}
				Set<String> excluded = new HashSet<>();
				// Farming never requires what the patch yields, only what goes into it.
				excluded.add("produce");
				if (config.farmingRakeMode() == FarmingRakeMode.TOOLS)
				{
					excluded.add("seed");
				}
				// Completing (or starting) the Farming portion of Barbarian Training removes
				// the game's own seed dibber requirement, so stop demanding its card.
				if (hasBareHandedPlanting())
				{
					excluded.add("tool");
				}
				return excluded;
			}
			case "farming-compost":
				return config.compostMode() == CardRequirement.CARD_REQUIRED
					? Collections.emptySet() : null;
			case "hunter-rumours":
				return config.restrictHunterRumours() ? Collections.emptySet() : null;
			case "quest-cots":
				// CotS guard marking waives itself while the quest is actually running -
				// quest progression is the permit, no toggle.
				return questNpcIndex.isCotsInProgress() ? null : Collections.emptySet();
			case "farming-rake":
				// Weeds are what raking yields, so they fall under the same "never require
				// the end product" rule as produce. Raking therefore only ever asks for the
				// rake itself, and both restricted modes behave identically here.
				return config.farmingRakeMode() == FarmingRakeMode.OFF
					? null : Collections.singleton("weeds");
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
				SlayerMode mode = config.slayerMode();
				if (mode == SlayerMode.OFF)
				{
					return null;
				}
				// Both modes enforce the master card; only Full Task List adds the monster
				// list, and only then (plus the opt-in) do the superior variants apply.
				Set<String> excluded = new HashSet<>();
				if (mode != SlayerMode.FULL)
				{
					excluded.add("monsters");
				}
				if (mode != SlayerMode.FULL || !config.restrictSlayerSuperiors())
				{
					excluded.add("superiors");
				}
				return excluded;
			}
			case "hunter-pitfalls":
				switch (config.hunterMode())
				{
					case TOOLS_ONLY:
						return Set.of("monster", "loot");
					case ALL_CARDS:
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
		List<String> missing = evaluateRecipe(kind, name, target);
		if (missing == null)
		{
			return false;
		}
		event.consume();
		sendBlockedCardsMessage(missing);
		return true;
	}

	/** Restriction decision shared by mouse clicks and SkillMulti keyboard shortcuts. */
	private List<String> evaluateRecipe(String kind, String name, String target)
	{
		RecipeCatalog.Recipe recipe = recipeCatalog.find(kind, name, target);
		if (recipe == null)
		{
			return null;
		}

		boolean enforceInputs;
		boolean enforceOutput;
		boolean firemakingTinderboxOnly = false;
		switch (recipe.category)
		{
			case "firemaking":
			{
				// Firemaking now gates ONLY the Tinderbox card - the logs are already gated
				// when you obtain them (Woodcutting / loot), so re-checking them here was the
				// convoluted part. Enforce inputs to compute the Tinderbox requirement, then
				// keep only it (the logs are dropped from the block by the filter below).
				if (config.tinderboxMode() != CardRequirement.CARD_REQUIRED)
				{
					return null;
				}
				enforceInputs = true;
				enforceOutput = false;
				firemakingTinderboxOnly = true;
				break;
			}
			case "smithing-smelt":
			{
				SmeltingMode mode = config.smeltingMode();
				if (mode == SmeltingMode.OFF)
				{
					return null;
				}
				enforceInputs = mode == SmeltingMode.ORE || mode == SmeltingMode.BOTH;
				enforceOutput = mode == SmeltingMode.BOTH;
				break;
			}
			case "smithing-forge":
			{
				SmithingMode mode = config.smithingMode();
				if (mode == SmithingMode.OFF)
				{
					return null;
				}
				enforceInputs = mode == SmithingMode.BARS || mode == SmithingMode.BOTH;
				enforceOutput = mode == SmithingMode.BOTH;
				break;
			}
			case "cooking":
			{
				// Recipe-path cooking (e.g. slicing a banana): no burnt product exists, so the
				// Burnt Food Cards dropdown doesn't apply here - only the input/output choice.
				CookingMode mode = config.cookingMode();
				if (mode == CookingMode.OFF)
				{
					return null;
				}
				enforceInputs = mode == CookingMode.INPUT_ONLY || mode == CookingMode.INPUT_OUTPUT;
				enforceOutput = mode == CookingMode.INPUT_OUTPUT;
				break;
			}
			case "crafting":
			{
				CraftingMode mode = config.craftingMode();
				if (mode == CraftingMode.OFF)
				{
					return null;
				}
				enforceInputs = mode == CraftingMode.INPUT_ONLY || mode == CraftingMode.BOTH;
				enforceOutput = mode == CraftingMode.BOTH;
				break;
			}
			case "enchanting":
				if (!config.restrictEnchanting())
				{
					return null;
				}
				enforceInputs = true;
				enforceOutput = true;
				break;
			case "fletching":
			{
				FletchingMode mode = config.fletchingMode();
				if (mode == FletchingMode.OFF)
				{
					return null;
				}
				enforceInputs = mode == FletchingMode.INPUT_ONLY
					|| mode == FletchingMode.PRODUCT_AND_MATERIALS;
				enforceOutput = mode == FletchingMode.PRODUCT_AND_MATERIALS;
				break;
			}
			case "herblore":
			{
				HerbloreMode mode = config.herbloreMode();
				if (mode == HerbloreMode.OFF)
				{
					return null;
				}
				enforceInputs = mode.enforcesInputs();
				enforceOutput = mode.enforcesOutput(recipe.herbloreStage);
				break;
			}
			default:
				enforceInputs = true;
				enforceOutput = true;
		}

		List<String> missing = recipe.missingRequirements(
			effectiveOwnedCards(), enforceInputs, enforceOutput);
		if (firemakingTinderboxOnly)
		{
			// Keep only the Tinderbox card in the block - the logs are gated elsewhere.
			missing.removeIf(card -> !"Tinderbox".equalsIgnoreCase(card));
		}
		// Crushed gem rides on top of the crafting requirement, for gems that can shatter.
		if (recipe.crushable && config.requireCrushedGem()
			&& !effectiveOwnedCards().contains(CRUSHED_GEM_CARD_LOWER))
		{
			missing.add(CRUSHED_GEM_CARD);
		}
		if (missing.isEmpty())
		{
			return null;
		}
		return missing;
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
	/** blockIfLockedItem's decision without the consume - for menu hiding. */
	private boolean isBlockedItemName(String itemName)
	{
		return itemName != null && !itemName.isEmpty() && !isLootExempt(itemName)
			&& !isUnlocked(itemCatalog, itemName);
	}

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
		return isTutorialIslandBypassed()
			|| isLmsBypassed()
			|| !collectionReader.isStateAvailable();
	}

	/** Tutorial supplies required items and teaches unrestricted actions; restrictions resume at 1000. */
	private boolean isTutorialIslandBypassed()
	{
		return isTutorialIslandProgress(client.getVarpValue(VarPlayerID.TUTORIAL));
	}

	static boolean isTutorialIslandProgress(int progress)
	{
		return progress > 0 && progress < 1000;
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
		if (client.getVarbitValue(VarbitID.BR_INGAME) == 1)
		{
			return true;
		}
		// Map regions moved to the WorldView (multi-world support); top level = the
		// player's world. Null before the world exists - no world means no LMS match,
		// so restrictions stand (the conservative default).
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return false;
		}
		for (int region : worldView.getMapRegions())
		{
			if (LMS_REGIONS.contains(region))
			{
				return true;
			}
		}
		return false;
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		// Redraw scripts reset widget opacity, so re-fade as soon as they finish.
		if (event.getScriptId() == ScriptID.INVENTORY_DRAWITEM
			|| event.getScriptId() == ScriptID.BANKMAIN_BUILD)
		{
			scheduleLockedItemMarks();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		// Shops build their stock on open; fade it immediately rather than waiting for
		// the periodic sweep.
		if (event.getGroupId() == InterfaceID.SHOPMAIN || event.getGroupId() == InterfaceID.SHOPSIDE)
		{
			scheduleLockedItemMarks();
		}
	}

	/** Coalesces the many per-slot INVENTORY_DRAWITEM firings into one pass per tick. */
	private void scheduleLockedItemMarks()
	{
		if (markRefreshQueued)
		{
			return;
		}
		markRefreshQueued = true;
		clientThread.invokeAtTickEnd(() ->
		{
			markRefreshQueued = false;
			applyLockedItemMarks();
		});
	}

	/**
	 * Fade locked items in the inventory and bank via widget opacity (Alch Blocker's
	 * technique). Runs on the client thread; every check below is a map lookup after
	 * the first sighting of an item id.
	 */
	private void applyLockedItemMarks()
	{
		boolean marking = isLockedItemMarkingActive(config.itemUsageMode(),
			config.lockedItemMarkMode(), isEnforcementBypassed());
		for (int componentId : LOCKED_MARK_CONTAINERS)
		{
			Widget container = client.getWidget(componentId);
			Widget[] children = container == null ? null : container.getChildren();
			if (children == null)
			{
				continue;
			}
			for (Widget child : children)
			{
				if (child == null || child.getItemId() <= 0)
				{
					continue;
				}
				if (marking && isItemMarkedLocked(child.getItemId()))
				{
					child.setOpacity(LOCKED_ITEM_OPACITY);
				}
				else if (child.getOpacity() == LOCKED_ITEM_OPACITY)
				{
					child.setOpacity(0);
				}
			}
		}
	}

	/** Restore every fade we applied; used on shutdown. Client thread only. */
	private void clearLockedItemMarks()
	{
		for (int componentId : LOCKED_MARK_CONTAINERS)
		{
			Widget container = client.getWidget(componentId);
			Widget[] children = container == null ? null : container.getChildren();
			if (children == null)
			{
				continue;
			}
			for (Widget child : children)
			{
				if (child != null && child.getOpacity() == LOCKED_ITEM_OPACITY)
				{
					child.setOpacity(0);
				}
			}
		}
	}

	/** The icon overlay's entry point: lock state plus the shared stand-down check. */
	boolean shouldMarkLocked(int itemId)
	{
		return isLockedItemMarkingActive(config.itemUsageMode(),
			config.lockedItemMarkMode(), isEnforcementBypassed())
			&& isItemMarkedLocked(itemId);
	}

	static boolean isLockedItemMarkingActive(LockState itemUsageMode,
		LockedItemMarkMode markMode, boolean enforcementBypassed)
	{
		return itemUsageMode == LockState.LOCKED
			&& markMode != LockedItemMarkMode.OFF
			&& !enforcementBypassed;
	}

	private boolean isItemMarkedLocked(int itemId)
	{
		Set<String> owned = effectiveOwnedCards();
		if (owned != lockedItemCacheOwned)
		{
			lockedItemCache.clear();
			lockedItemCacheOwned = owned;
		}
		Boolean cached = lockedItemCache.get(itemId);
		if (cached != null)
		{
			return cached;
		}
		String name = itemManager.getItemComposition(itemId).getName();
		// Dose suffixes fold away so "Attack potion(3)" matches its card, same as drinking.
		boolean locked = name != null && !isUnlocked(itemCatalog, CardNames.stripDoseSuffix(name));
		lockedItemCache.put(itemId, locked);
		return locked;
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

	private boolean isRestrictedNpcInteraction(MenuOptionClicked event, String npcName)
	{
		MenuAction action = event.getMenuAction();
		NpcVisibilityMode mode = config.npcVisibilityMode();
		if (action == null || mode == NpcVisibilityMode.OFF)
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
				String option = event.getMenuOption();
				if (option != null
					&& ATTACK_OPTION.equals(Text.removeTags(option).trim().toLowerCase(Locale.ROOT)))
				{
					return true;
				}
				// Prevent Interaction: every option is gated (Examine routes through a different
				// MenuAction and stays free). Slayer masters keep their own rules, and
				// started-quest NPCs drop to Prevent Combat treatment - matches the
				// menu-hiding path exactly.
				return mode.strictOptions()
					&& !nodeCatalog.isSlayerNpc(npcName)
					&& !questNpcIndex.isShownQuestNpc(npcName);
			}
			case WIDGET_TARGET_ON_NPC:
			{
				// Spell cast on NPC, or item used on NPC. Prevent Combat blocks only the
				// spell (closing the mage bypass) while item-on-NPC stays free for quest
				// interactions; Prevent Interaction blocks both - except for slayer
				// masters and started-quest NPCs, who get the Prevent Combat treatment.
				if (mode.strictOptions() && !nodeCatalog.isSlayerNpc(npcName)
					&& !questNpcIndex.isShownQuestNpc(npcName))
				{
					return true;
				}
				String option = event.getMenuOption();
				boolean spell = (option != null
					&& "cast".equals(Text.removeTags(option).trim().toLowerCase(Locale.ROOT)))
					|| isSelectedWidgetSpell();
				return spell;
			}
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
		if (config.coinMode() == LockState.UNLOCKED && "coins".equals(needle))
		{
			return true;
		}
		return exemptions().containsEntity(needle);
	}

	/** Cached exact/wildcard exemptions, rebuilt only when the config string changes. */
	private ExemptionList.Snapshot exemptions()
	{
		return exemptionList.resolve(config.lootExemptNames());
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
		Set<String> exempt = exemptions().getCardNamesLowerCase();
		// Extra unlocks offered by a sibling plugin (a party mode, say). Folded in here with the
		// exempt list so every lock check in the plugin honours them in one place, and cached by
		// identity like the rest - the store hands back the same instance until it changes.
		Set<String> shared = config.acceptSharedUnlocks()
			? sharedUnlockStore.getSharedCardNamesLowerCase() : Collections.emptySet();
		boolean coins = config.coinMode() == LockState.UNLOCKED;
		// Food Settings works exactly like the exempt list: an allowed class's names
		// merge into the effective-owned set, so every lock check in the plugin
		// (usage, pickup, shops, GE, marking, recipes) honours it in one place.
		FoodSettingsMode foodMode = config.foodSettingsMode();
		if (exempt.isEmpty() && shared.isEmpty() && !coins && foodMode == FoodSettingsMode.LOCKED)
		{
			return owned;
		}
		if (owned != effectiveOwnedBase || exempt != effectiveOwnedExempt
			|| shared != effectiveOwnedShared
			|| coins != effectiveOwnedCoins || foodMode != effectiveOwnedFoodMode)
		{
			Set<String> combined = new HashSet<>(owned);
			combined.addAll(exempt);
			combined.addAll(shared);
			if (coins)
			{
				combined.add("coins");
			}
			if (foodMode.foodUsable())
			{
				combined.addAll(consumablesCatalog.getFoodNamesLower());
			}
			if (foodMode.potionsUsable())
			{
				combined.addAll(consumablesCatalog.getPotionNamesLower());
			}
			effectiveOwned = combined;
			effectiveOwnedBase = owned;
			effectiveOwnedExempt = exempt;
			effectiveOwnedShared = shared;
			effectiveOwnedCoins = coins;
			effectiveOwnedFoodMode = foodMode;
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
	/**
	 * One-time mapping of the three retired NPC toggles onto the visibility dropdown,
	 * preserving each player's effective behaviour. Same strict one-shot pattern as
	 * migrateExemptList: flag first, so a mid-way crash can never re-run it.
	 */
	private boolean preparePresetOnboarding()
	{
		String complete = configManager.getConfiguration(BronzemanTcgConfig.GROUP,
			"presetOnboardingComplete");
		if (Boolean.parseBoolean(complete))
		{
			return false;
		}
		String pending = configManager.getConfiguration(BronzemanTcgConfig.GROUP,
			"presetOnboardingPending");
		if (Boolean.parseBoolean(pending))
		{
			return true;
		}

		String[] existingInstallMarkers = {
			"npcVisibilityMigrated", "exemptListMigrated", "fishingModeMigrated",
			"hunterModeMigrated"
		};
		for (String marker : existingInstallMarkers)
		{
			if (configManager.getConfiguration(BronzemanTcgConfig.GROUP, marker) != null)
			{
				configManager.setConfiguration(BronzemanTcgConfig.GROUP,
					"presetOnboardingComplete", true);
				return false;
			}
		}

		configManager.setConfiguration(BronzemanTcgConfig.GROUP,
			"presetOnboardingPending", true);
		return true;
	}

	/**
	 * Makes TCG Locked the baseline for genuinely new installs without changing an
	 * existing player's effective setup. RuneLite commonly leaves old default values
	 * unstored, so existing installs receive explicit copies of only the defaults that
	 * changed. Explicit user choices are never overwritten.
	 */
	private void migrateTcgLockedDefaults(boolean freshInstall)
	{
		String migrated = configManager.getConfiguration(BronzemanTcgConfig.GROUP,
			"tcgLockedDefaultsMigrated");
		if (Boolean.parseBoolean(migrated))
		{
			return;
		}

		// Mark first so a partial migration can never be repeated over later user changes.
		configManager.setConfiguration(BronzemanTcgConfig.GROUP,
			"tcgLockedDefaultsMigrated", true);
		if (freshInstall)
		{
			return;
		}

		for (Map.Entry<String, String> entry : BronzemanPreset.getPreTcgLockedDefaults().entrySet())
		{
			if (configManager.getConfiguration(BronzemanTcgConfig.GROUP, entry.getKey()) == null)
			{
				configManager.setConfiguration(BronzemanTcgConfig.GROUP,
					entry.getKey(), entry.getValue());
			}
		}
	}

	private void migrateNpcVisibility()
	{
		if (config.npcVisibilityMigrated())
		{
			return;
		}
		configManager.setConfiguration(BronzemanTcgConfig.GROUP, "npcVisibilityMigrated", true);

		String hide = configManager.getConfiguration(BronzemanTcgConfig.GROUP, "hideLockedEntities");
		String attacks = configManager.getConfiguration(BronzemanTcgConfig.GROUP, "restrictAttacks");
		NpcVisibilityMode mode;
		if (Boolean.parseBoolean(hide))
		{
			mode = NpcVisibilityMode.HIDE;
		}
		else if ("false".equals(attacks))
		{
			mode = NpcVisibilityMode.OFF;
		}
		else
		{
			// Today's default; nothing maps to PREVENT_INTERACTION - it's a new severity.
			mode = NpcVisibilityMode.PREVENT_COMBAT;
		}
		// Stored as the enum constant's name (never the display label), matching how
		// RuneLite persists dropdown choices.
		configManager.setConfiguration(BronzemanTcgConfig.GROUP, "npcVisibilityMode", mode.name());

		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "restrictAttacks");
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "restrictSpellCasts");
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "hideLockedEntities");

		// Master Farmer's dial became the Insanity toggle in the same release. Only the
		// INSANITY choice carries over; his old Off/Coins+Pouch are now expressed by the
		// thieving dropdown itself.
		if ("INSANITY".equals(configManager.getConfiguration(BronzemanTcgConfig.GROUP, "masterFarmerMode")))
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "masterFarmerInsanity", true);
		}
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "masterFarmerMode");

		// Item settings pass: every old boolean/mode becomes a dropdown. RuneLite only
		// stores non-default values, so the rule is simple - an explicitly lenient old
		// choice maps to the lenient new value; everyone else follows the new (stricter)
		// defaults. Forcing a restriction back on that a player deliberately disabled
		// could brick their gameplay; under-restricting is one click to fix.
		if ("false".equals(configManager.getConfiguration(BronzemanTcgConfig.GROUP, "restrictLoot")))
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "groundItemsMode",
				LockState.UNLOCKED.name());
		}
		if ("false".equals(configManager.getConfiguration(BronzemanTcgConfig.GROUP, "restrictEquipping"))
			|| "false".equals(configManager.getConfiguration(BronzemanTcgConfig.GROUP, "restrictPotionDrinking")))
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "itemUsageMode",
				LockState.UNLOCKED.name());
		}
		if ("false".equals(configManager.getConfiguration(BronzemanTcgConfig.GROUP, "restrictBuying")))
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "grandExchangeMode",
				LockState.UNLOCKED.name());
		}
		if ("false".equals(configManager.getConfiguration(BronzemanTcgConfig.GROUP, "exemptCoins")))
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "coinMode",
				LockState.LOCKED.name());
		}
		// Old DROP mode forbade banking entirely; ALLOW_BANKING matches the new
		// Deposit Only default, so it needs no write.
		if ("DROP".equals(configManager.getConfiguration(BronzemanTcgConfig.GROUP, "forcedDropMode")))
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "bankingMode",
				BankingMode.OFF.name());
		}
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "restrictLoot");
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "restrictEquipping");
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "restrictPotionDrinking");
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "restrictItemUsage");
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "restrictBuying");
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "forcedDropMode");
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "exemptCoins");
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "hideLockedOptions");
		// The CotS toggle is replaced by automatic quest-state awareness.
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "allowCotsGuards");
	}

	/**
	 * Skill toggles become mode dropdowns as the skills sweep reaches them. Deliberately
	 * unguarded: each mapping reads a retired key and then unsets it, so it self-disarms
	 * after one run. (The fletching mapping moved here from migrateNpcVisibility, whose
	 * one-shot flag was already set for players who ran 0.2.1 - inside that guard it
	 * would never have fired for them.) Same lenient-carries-over rule as the item
	 * settings pass: only an explicit "false" maps to the off value; everyone else gets
	 * the new (stricter) default rather than a forced re-lock of a deliberate opt-out.
	 */
	private void migrateSkillToggles()
	{
		if ("false".equals(configManager.getConfiguration(BronzemanTcgConfig.GROUP, "restrictFletching")))
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "fletchingMode",
				FletchingMode.OFF.name());
		}
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "restrictFletching");

		// The retired "Output Only" fletching mode (enum PRODUCT) -> Input Only. Preserves
		// leniency (owner ruling): left to RuneLite's unparseable-enum fallback it would
		// instead land on the Input + Output default, silently making a lenient choice the
		// strictest one. Self-disarming: the value is only ever "PRODUCT" for old configs.
		if ("PRODUCT".equals(configManager.getConfiguration(BronzemanTcgConfig.GROUP, "fletchingMode")))
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "fletchingMode",
				FletchingMode.INPUT_ONLY.name());
		}

		if ("false".equals(configManager.getConfiguration(BronzemanTcgConfig.GROUP, "restrictMining")))
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "miningMode",
				MiningMode.OFF.name());
		}
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "restrictMining");

		if ("false".equals(configManager.getConfiguration(BronzemanTcgConfig.GROUP, "restrictWoodcutting")))
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "woodcuttingMode",
				WoodcuttingMode.OFF.name());
		}
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "restrictWoodcutting");

		// Crafting joined the four-way wording (Require Card / Input Only / Output
		// Required / No Card Needed). Its old toggle enforced both halves, which is
		// the new default, so only an explicit opt-out carries over.
		if ("false".equals(configManager.getConfiguration(BronzemanTcgConfig.GROUP, "restrictCrafting")))
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "craftingMode",
				CraftingMode.OFF.name());
		}
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "restrictCrafting");

		// "Output Required" was removed from Crafting (crafting always needs the materials,
		// so an output-only gate was redundant). Anyone who had selected it moves to Input
		// Only rather than being silently escalated to the Input + Output default. Reads the
		// raw stored string, so it still matches now that the enum constant is gone.
		if ("OUTPUT_ONLY".equals(configManager.getConfiguration(BronzemanTcgConfig.GROUP, "craftingMode")))
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "craftingMode",
				CraftingMode.INPUT_ONLY.name());
		}

		// Compost's toggle became a two-option dropdown.
		if ("false".equals(configManager.getConfiguration(BronzemanTcgConfig.GROUP, "restrictCompost")))
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "compostMode",
				CardRequirement.NO_CARD.name());
		}
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "restrictCompost");

		// Smelting / Smithing dropped their output-only option (you always need the inputs).
		// Anyone who had it moves to Input Only rather than the Input + Output default.
		if ("BARS".equals(configManager.getConfiguration(BronzemanTcgConfig.GROUP, "smeltingMode")))
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "smeltingMode",
				SmeltingMode.ORE.name());
		}
		if ("ITEMS".equals(configManager.getConfiguration(BronzemanTcgConfig.GROUP, "smithingMode")))
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "smithingMode",
				SmithingMode.BARS.name());
		}

		// Woodcutting dropped "Card Required" (both) and repurposed "Logs Only" as
		// "Logs + Tools Needed" (both). The old both-mode maps onto the new both-mode; the
		// old logs-only choice is gone, so its users now also need the axe unlocked.
		if ("CARD_REQUIRED".equals(configManager.getConfiguration(BronzemanTcgConfig.GROUP, "woodcuttingMode")))
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "woodcuttingMode",
				WoodcuttingMode.LOGS_ONLY.name());
		}

		// Firemaking became the Tinderbox Use dropdown (logs are gated elsewhere now). Old
		// "Logs + Tinderbox" wanted the Tinderbox card -> Card Required; "Off" and the old
		// logs-only default explicitly did NOT want it -> No Card Needed (leniency kept).
		String oldFiremaking = configManager.getConfiguration(BronzemanTcgConfig.GROUP, "firemakingMode");
		if ("BOTH".equals(oldFiremaking))
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "tinderboxMode",
				CardRequirement.CARD_REQUIRED.name());
		}
		else if ("OFF".equals(oldFiremaking) || "JUST_LOGS".equals(oldFiremaking))
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "tinderboxMode",
				CardRequirement.NO_CARD.name());
		}
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "firemakingMode");

		// Mining dropped "Ore Only" (no-tool) and repurposed "Card Required" as "Tool + Ore".
		// Ore-only users keep their ore gating and gain the tool check.
		if ("ORE_ONLY".equals(configManager.getConfiguration(BronzemanTcgConfig.GROUP, "miningMode")))
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "miningMode",
				MiningMode.CARD_REQUIRED.name());
		}

		// Slayer's Require-masters + Require-monsters toggles became the Slayer Options
		// dropdown (Superiors stays its own opt-in). masters+monsters -> Full Task List;
		// masters alone -> Require Slayer Master; anything else falls to the No Restrictions
		// default (owner ruling: don't special-case a monsters-without-master setup).
		boolean slMasters = "true".equals(
			configManager.getConfiguration(BronzemanTcgConfig.GROUP, "restrictSlayerMasters"));
		boolean slMonsters = "true".equals(
			configManager.getConfiguration(BronzemanTcgConfig.GROUP, "restrictSlayerMonsters"));
		if (slMasters && slMonsters)
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "slayerMode",
				SlayerMode.FULL.name());
		}
		else if (slMasters)
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "slayerMode",
				SlayerMode.MASTER.name());
		}
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "restrictSlayerMasters");
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "restrictSlayerMonsters");

		// Cooking's two toggles became two dropdowns. restrictCooking=false -> No
		// restrictions; everyone else falls to the INPUT_OUTPUT default. Old cooking gated
		// only the cooked card and there is no cooked-only option now, so INPUT_OUTPUT keeps
		// that cooked requirement and adds the raw card (never drops gating). The old
		// "Require burnt food" toggle maps straight to the new Burnt Food Cards dropdown.
		if ("false".equals(configManager.getConfiguration(BronzemanTcgConfig.GROUP, "restrictCooking")))
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "cookingMode",
				CookingMode.OFF.name());
		}
		if ("true".equals(configManager.getConfiguration(BronzemanTcgConfig.GROUP, "restrictBurntFood")))
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "burntFoodMode",
				BurntFoodMode.REQUIRE_CARD.name());
		}
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "restrictCooking");
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "restrictBurntFood");

		// Herblore's old toggle enforced every input and output. An explicit opt-out
		// remains Off; everyone else receives the equivalent Require All default.
		if ("false".equals(configManager.getConfiguration(
			BronzemanTcgConfig.GROUP, "restrictHerblore")))
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "herbloreMode",
				HerbloreMode.OFF.name());
		}
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "restrictHerblore");
	}

	/**
	 * One-time mapping of the retired fishing dial (Any of / Require ALL) onto the
	 * exact-spot modes. Any of keeps its any-catch meaning and Require ALL keeps its
	 * all-catches meaning; both now add the applicable carried inputs. Off survives as
	 * the same enum constant, so it needs no explicit case. Same strict one-shot pattern:
	 * flag first, so a mid-way crash can never re-run it. Reads the raw stored strings,
	 * so they still match now that the enum constants are gone.
	 */
	private void migrateFishingMode()
	{
		if (config.fishingModeMigrated())
		{
			return;
		}
		configManager.setConfiguration(BronzemanTcgConfig.GROUP, "fishingModeMigrated", true);

		String stored = configManager.getConfiguration(BronzemanTcgConfig.GROUP, "fishingMode");
		if ("ANY_OF".equals(stored))
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "fishingMode",
				FishingRestrictionMode.CARD_REQUIRED.name());
		}
		else if ("REQUIRE_ALL".equals(stored))
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "fishingMode",
				FishingRestrictionMode.ALL_CATCHES.name());
		}
	}

	/**
	 * Consolidates six per-method Hunter controls into one shared difficulty dial.
	 * A fully explicit opt-out stays off; an explicitly selected strict bird,
	 * salamander or pitfall mode stays strict. Mixed/ordinary configurations use the
	 * new Tools Only default, since no single value can preserve six independent choices.
	 */
	private void migrateHunterMode()
	{
		if (config.hunterModeMigrated())
		{
			return;
		}
		configManager.setConfiguration(BronzemanTcgConfig.GROUP, "hunterModeMigrated", true);

		String birds = configManager.getConfiguration(BronzemanTcgConfig.GROUP, "hunterBirdsMode");
		String butterflies = configManager.getConfiguration(BronzemanTcgConfig.GROUP, "butterflyMode");
		String implings = configManager.getConfiguration(BronzemanTcgConfig.GROUP, "implingMode");
		String chins = configManager.getConfiguration(BronzemanTcgConfig.GROUP, "restrictChins");
		String salamanders = configManager.getConfiguration(BronzemanTcgConfig.GROUP, "salamanderMode");
		String pitfalls = configManager.getConfiguration(BronzemanTcgConfig.GROUP, "pitfallMode");

		boolean allOff = "OFF".equals(birds)
			&& "OFF".equals(butterflies)
			&& "OFF".equals(implings)
			&& "false".equals(chins)
			&& "OFF".equals(salamanders)
			&& "OFF".equals(pitfalls);
		boolean explicitlyStrict = "ALL_DROPS".equals(birds)
			|| "ITEMS_SALLY".equals(salamanders)
			|| "ALL".equals(pitfalls);
		if (allOff)
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "hunterMode",
				HunterMode.OFF.name());
		}
		else if (explicitlyStrict)
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP, "hunterMode",
				HunterMode.ALL_CARDS.name());
		}

		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "hunterBirdsMode");
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "butterflyMode");
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "implingMode");
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "restrictChins");
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "salamanderMode");
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "pitfallMode");
	}

	/** Retired choices are now unconditional: feedback is on and LMS always bypasses. */
	private void removeRetiredGeneralSettings()
	{
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "chatFeedback");
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, "allowInLms");
	}

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
