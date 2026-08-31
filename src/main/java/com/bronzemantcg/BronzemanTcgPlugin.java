package com.bronzemantcg;

import com.bronzemantcg.catalog.QuestCatalog;
import com.bronzemantcg.catalog.QuestNpcIndex;
import com.bronzemantcg.catalog.QuestRequirementCatalog;
import com.bronzemantcg.catalog.RecipeCatalog;
import com.bronzemantcg.catalog.ResourceNodeCatalog;
import com.bronzemantcg.catalog.ToolNameClassifier;
import com.bronzemantcg.catalog.remote.RemoteCatalogService;
import com.bronzemantcg.feature.ChatFeedbackService;
import com.bronzemantcg.feature.DuelistCityController;
import com.bronzemantcg.feature.GroundItemLockTracker;
import com.bronzemantcg.feature.LockedItemMarkController;
import com.bronzemantcg.feature.PluginNoticeController;
import com.bronzemantcg.interop.OsrsTcgInteropService;
import com.bronzemantcg.interop.TcgCollectionReader;
import com.bronzemantcg.overlay.BronzemanTcgOverlay;
import com.bronzemantcg.overlay.LockedItemIconOverlay;
import com.bronzemantcg.ownership.ActiveCardIdentityCatalog;
import com.bronzemantcg.ownership.CardEntityKind;
import com.bronzemantcg.ownership.SharedUnlockStore;
import com.bronzemantcg.ownership.TcgOwnershipSnapshot;
import com.bronzemantcg.panel.BronzemanTcgPanel;
import com.bronzemantcg.panel.RecentUnlocksTracker;
import com.bronzemantcg.panel.PanelPresentationCatalog;
import com.bronzemantcg.panel.QuestV1Presentation;
import com.bronzemantcg.panel.V1PresentationState;
import com.bronzemantcg.panel.collection.BetaCollectionSnapshotService;
import com.bronzemantcg.panel.collection.PanelBetaCollectionViewModel;
import com.bronzemantcg.panel.collection.PanelCollectionViewModel;
import com.bronzemantcg.panel.collection.PanelSharedCardsViewModel;
import com.bronzemantcg.restriction.ExemptionList;
import com.bronzemantcg.restriction.ItemInteractionService;
import com.bronzemantcg.restriction.NpcRestrictionService;
import com.bronzemantcg.restriction.NpcRestrictionService.InteractionDecision;
import com.bronzemantcg.restriction.ResourceRestrictionService;
import com.bronzemantcg.restriction.RestrictionDecisionService;
import com.bronzemantcg.settings.ConfigMigrationService;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ScheduledExecutorService;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameStateChanged;
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
import net.runelite.api.GameState;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Renderable;
import net.runelite.api.TileItem;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

/**
 * Bronzeman-style restriction driven by the OSRS TCG plugin's collection:
 * attacking NPCs, looting, equipping, buying, gathering and processing are
 * gated behind owning the matching card(s).
 * Interop is read-only via osrs-tcg's PluginMessage API, falling back to
 * decoding its persisted ConfigManager state on hub versions that predate
 * the API (see {@link TcgCollectionReader}); there is no compile-time
 * dependency, so both plugins install independently from the Plugin Hub.
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
	@Inject
	private Client client;

	@Inject
	private ChatFeedbackService chatFeedbackService;

	@Inject
	private ItemManager itemManager;

	@Inject
	private BronzemanTcgConfig config;

	@Inject
	private DuelistCityController duelistCityController;

	@Inject
	private PluginNoticeController pluginNoticeController;

	@Inject
	private OsrsTcgInteropService osrsTcgInteropService;

	@Inject
	private RemoteCatalogService remoteCatalogService;

	@Inject
	private GroundItemLockTracker groundItemLockTracker;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ConfigMigrationService configMigrationService;

	@Inject
	private RestrictionDecisionService restrictionDecisionService;

	@Inject
	private NpcRestrictionService npcRestrictionService;

	@Inject
	private ItemInteractionService itemInteractionService;

	@Inject
	private ResourceRestrictionService resourceRestrictionService;

	@Inject
	private Gson gson;

	@Inject
	private TcgCollectionReader collectionReader;

	@Inject
	private SharedUnlockStore sharedUnlockStore;

	@Inject
	private RecentUnlocksTracker recentUnlocksTracker;

	@Inject
	private PanelCollectionViewModel panelCollectionViewModel;

	@Inject
	private PanelSharedCardsViewModel panelSharedCardsViewModel;

	@Inject
	private PanelBetaCollectionViewModel panelBetaCollectionViewModel;

	@Inject
	private BetaCollectionSnapshotService betaCollectionSnapshotService;

	@Inject
	private V1PresentationState v1PresentationState;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private ActiveCardIdentityCatalog activeCardIdentityCatalog;

	@Inject
	private ExemptionList exemptionList;

	@Inject
	private ResourceNodeCatalog nodeCatalog;

	@Inject
	private RecipeCatalog recipeCatalog;

	@Inject
	private QuestCatalog questCatalog;

	@Inject
	private QuestV1Presentation questV1Presentation;

	@Inject
	private QuestRequirementCatalog questRequirementCatalog;

	@Inject
	private PanelPresentationCatalog panelPresentationCatalog;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BronzemanTcgOverlay overlay;

	@Inject
	private RenderCallbackManager renderCallbackManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private QuestNpcIndex questNpcIndex;

	@Inject
	private LockedItemIconOverlay lockedItemIconOverlay;

	@Inject
	private LockedItemMarkController lockedItemMarkController;

	@Inject
	private EventBus eventBus;

	@Inject
	private ScheduledExecutorService executor;

	/**
	 * Set when the shared-unlocks picture is stale; the query goes out on the next tick. Volatile
	 * because the settings panel raises ConfigChanged on the EDT while the tick that acts on it runs
	 * on the client thread.
	 */
	private volatile boolean sharedQueryPending;

	private volatile BronzemanTcgPanel panel;
	private volatile NavigationButton navButton;
	private boolean presetOnboardingRequired;
	// Invalidates queued Swing startup work when the plugin is stopped or restarted
	// before the EDT has had a chance to install the navigation panel.
	private final AtomicLong panelGeneration = new AtomicLong();
	private int tickCounter;
	private int questRefreshTickCounter;
	private boolean questStateInitialized;

	@Override
	protected void startUp()
	{
		// Check before the legacy migrations write their markers. That is how a genuinely
		// fresh install is distinguished from an existing user upgrading this release.
		presetOnboardingRequired = configMigrationService.preparePresetOnboarding();
		betaCollectionSnapshotService.reload();
		remoteCatalogService.setListener(this::onActiveCatalogChanged);
		remoteCatalogService.startUp();
		remoteCatalogService.setEnabled(config.allowRemoteCatalog());
		// Arm the catalogue gate before requesting ownership so even an immediate v1 reply can
		// start the one permitted conditional fetch when the player has allowed it.
		osrsTcgInteropService.startUp();
		observeBetaCollectionSnapshot();
		recentUnlocksTracker.reload();
		// Nothing shared survives a restart of this plugin. Sources are asked to re-offer on the
		// first tick, so a set from before we unloaded can never linger unnoticed.
		sharedUnlockStore.clear();
		sharedQueryPending = true;
		configMigrationService.migrateLegacySettings(presetOnboardingRequired);
		lockedItemMarkController.startUp();
		// Mid-session enable fires no ItemContainerChanged, so seed the tool cache now.
		clientThread.invokeLater(this::refreshCarriedTools);
		questStateInitialized = false;
		long generation = panelGeneration.incrementAndGet();
		SwingUtilities.invokeLater(() -> installPanel(generation));
		overlayManager.add(overlay);
		overlayManager.add(lockedItemIconOverlay);
		renderCallbackManager.register(this);

		int npcEntities = activeCardIdentityCatalog.getView()
			.getEntityToCardNames(CardEntityKind.NPC).size();
		int itemEntities = activeCardIdentityCatalog.getView()
			.getEntityToCardNames(CardEntityKind.ITEM).size();
		log.info("Bronzeman TCG started. Tracking {} TCG-linked NPCs, {} items, {} node rules, {} recipe rules.",
			npcEntities, itemEntities, nodeCatalog.size(), recipeCatalog.size());

		pluginNoticeController.startUp();
	}

	@Override
	protected void shutDown()
	{
		panelGeneration.incrementAndGet();
		remoteCatalogService.setListener(null);
		remoteCatalogService.shutDown();
		sharedUnlockStore.clear();
		renderCallbackManager.unregister(this);
		overlayManager.remove(overlay);
		overlayManager.remove(lockedItemIconOverlay);
		lockedItemMarkController.shutDown();
		clientThread.invoke(() -> duelistCityController.shutDown());
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
		if (generation != panelGeneration.get())
		{
			return;
		}

		BronzemanTcgPanel newPanel = new BronzemanTcgPanel(
				gson,
				questCatalog,
				questV1Presentation,
				questRequirementCatalog,
				exemptionList,
				panelPresentationCatalog,
				collectionReader,
				sharedUnlockStore,
				recentUnlocksTracker,
				panelCollectionViewModel,
				panelBetaCollectionViewModel,
				panelSharedCardsViewModel,
				betaCollectionSnapshotService,
				v1PresentationState,
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

		if (generation != panelGeneration.get())
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
		groundItemLockTracker.track(item, event.getTile(),
			itemManager.getItemComposition(item.getId()).getName());
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		groundItemLockTracker.untrack(event.getItem());
	}

	/**
	 * Re-evaluate tracked ground items after personal/shared ownership or item exemptions
	 * change. Each source is identity-stable, so the common case is three reference
	 * comparisons per tick and no item work.
	 */
	private void refreshGroundItemLocks()
	{
		groundItemLockTracker.refresh();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		pluginNoticeController.onGameStateChanged(event.getGameState());
		switch (event.getGameState())
		{
			case LOADING:
				// New scene: every tracked Tile belongs to the old one.
				groundItemLockTracker.clear();
				break;
			case LOGGED_IN:
				questStateInitialized = false;
				break;
			default:
				break;
		}
	}

	/** Drives periodic work that remains outside the focused feature controllers. */
	private void tickPluginCoordination()
	{
		// Every ~3s: catches unlocks and config changes that happen without an
		// inventory redraw (the ScriptPostFired hook covers redraws immediately).
		// Quest states change even more rarely; the same cadence keeps the quest-NPC
		// override current without touching the render path.
		lockedItemMarkController.onGameTick();
		if (++questRefreshTickCounter % 5 == 0)
		{
			questNpcIndex.refresh(client);
		}
		osrsTcgInteropService.onGameTick();
		if (sharedQueryPending)
		{
			// Asked from a tick for the same reason as the osrs-tcg query above: a reply posted
			// during startUp would arrive before our @Subscribe methods are registered.
			sharedQueryPending = false;
			eventBus.post(new PluginMessage(SHARED_API_NAMESPACE, SHARED_QUERY));
		}
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
		if (!(renderable instanceof NPC))
		{
			return true;
		}
		return npcRestrictionService.shouldRender((NPC) renderable);
	}

	/**
	 * Panel nav-button icon: the bundled card sprite (same art as the Plugin Hub icon),
	 * loaded synchronously from the classpath. The previous approach pulled the med-helm
	 * item sprite from the game cache asynchronously, which frequently wasn't ready when
	 * the toolbar first painted - leaving a blank button.
	 */
	private BufferedImage loadPanelIcon()
	{
		return ImageUtil.loadImageResource(BronzemanTcgPlugin.class,
			"/assets/panel_icon.png");
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
		if (restrictionDecisionService.isEnforcementBypassed())
		{
			return;
		}
		MenuEntry entry = event.getMenuEntry();
		// NPC option visibility is owned exclusively by NPC Locks. Every other interaction
		// can remain visible for discoverability while the click path still blocks it and
		// explains the missing cards.
		if (config.showLockedMenuOptions()
			&& !NpcRestrictionService.isNpcMenuAction(entry.getType()))
		{
			return;
		}
		boolean hide = NpcRestrictionService.isNpcMenuAction(entry.getType())
			? npcRestrictionService.shouldHideMenuEntry(entry.getNpc(), entry.getOption())
			: itemInteractionService.shouldHideMenuEntry(entry);
		if (hide)
		{
			client.getMenu().removeMenuEntry(entry);
		}
	}

	/**
	 * Gathers the nearby tracked-NPC snapshot on the client thread every few ticks and
	 * hands it to the panel on the Swing EDT (game state must not be read from Swing).
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		refreshGroundItemLocks();
		TcgOwnershipSnapshot ownership = collectionReader.getOwnershipSnapshot();
		boolean stateAvailable = collectionReader.isStateAvailable();
		boolean newUnlock = recentUnlocksTracker.update(
			ownership.getOwnedCardNamesLowerCase(), stateAvailable);
		boolean betaSnapshotChanged = betaCollectionSnapshotService.observe(
			ownership, stateAvailable);
		if (newUnlock || betaSnapshotChanged)
		{
			refreshVisiblePanel();
		}

		// Ahead of the panel guard below - the greeting must still fire when the panel
		// is closed, and on every tick rather than one in five.
		tickPluginCoordination();
		pluginNoticeController.onGameTick();

		duelistCityController.onGameTick();

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
		lockedItemMarkController.onConfigChanged(event.getGroup(), event.getKey());

		// Duelist City Mode flipped: sweep every player in view right away rather than
		// waiting for their next appearance update. Composition edits touch client memory,
		// so they run on the client thread.
		if (BronzemanTcgConfig.GROUP.equals(event.getGroup())
			&& "duelistCityMode".equals(event.getKey()))
		{
			boolean on = Boolean.parseBoolean(event.getNewValue());
			clientThread.invoke(() -> duelistCityController.setEnabled(on));
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

		if (BronzemanTcgConfig.GROUP.equals(event.getGroup())
			&& "allowRemoteCatalog".equals(event.getKey()))
		{
			// Read the current setting so resetting it to its default is treated as disabled.
			remoteCatalogService.setEnabled(config.allowRemoteCatalog());
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
		duelistCityController.onPlayerSpawned(event.getPlayer());
	}

	@Subscribe
	public void onPlayerChanged(PlayerChanged event)
	{
		duelistCityController.onPlayerChanged(event.getPlayer());
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned event)
	{
		duelistCityController.onPlayerDespawned(event.getPlayer());
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		// New account/profile: never let a previous profile's collection linger. This
		// drops any API-provided data too, so re-arm the query for the new profile.
		osrsTcgInteropService.onProfileChanged();
		remoteCatalogService.setV1Capable(false);
		betaCollectionSnapshotService.reload();
		observeBetaCollectionSnapshot();
		recentUnlocksTracker.reload();
		// Shared unlocks describe a group this account was in, not this one; ask the sources for
		// the new profile's picture rather than waiting for one of them to notice.
		sharedUnlockStore.clear();
		sharedQueryPending = true;
		refreshVisiblePanel();
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
		// Enforcement reads the shared store on demand and the ~3s sweep re-applies item marks.
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
		boolean wasV1Capable = collectionReader.hasLiveV1Capability();
		OsrsTcgInteropService.UpdateResult update = osrsTcgInteropService.onPluginMessage(event);
		if (!update.isAccepted())
		{
			return;
		}
		boolean isV1Capable = collectionReader.hasLiveV1Capability();
		boolean presentationActivated =
			v1PresentationState.observeLiveCapability(isV1Capable);
		remoteCatalogService.setV1Capable(isV1Capable);
		if (update.isFirstPayload())
		{
			recentUnlocksTracker.resetBaseline();
		}
		boolean betaSnapshotChanged = observeBetaCollectionSnapshot();
		if (recentUnlocksTracker.update(collectionReader.getOwnedCardNamesLowerCase(), true)
			|| betaSnapshotChanged || presentationActivated
			|| wasV1Capable != isV1Capable)
		{
			refreshVisiblePanel();
		}
	}

	private boolean observeBetaCollectionSnapshot()
	{
		return betaCollectionSnapshotService.observe(collectionReader.getOwnershipSnapshot(),
			collectionReader.isStateAvailable());
	}

	private void onActiveCatalogChanged(long revision, boolean v1CatalogAvailable)
	{
		log.debug("Active card catalogue changed (revision={}, v1Available={})",
			revision, v1CatalogAvailable);
		refreshVisiblePanel();
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
		if (restrictionDecisionService.isEnforcementBypassed())
		{
			return;
		}
		NPC npc = event.getMenuEntry().getNpc();
		if (npc != null)
		{
			handleNpcInteraction(event, npc);
			return;
		}
		ItemInteractionService.InteractionDecision decision =
			itemInteractionService.evaluateInteraction(event);
		if (!decision.isBlocked())
		{
			return;
		}
		event.consume();
		if (decision.getBlockedItemName() != null)
		{
			chatFeedbackService.sendBlockedMessage(decision.getBlockedItemName());
		}
		else
		{
			chatFeedbackService.sendBlockedCardsMessage(decision.getMissingCards());
		}
	}

	// ------------------------------------------------------------------ NPC path

	private void handleNpcInteraction(MenuOptionClicked event, NPC npc)
	{
		InteractionDecision decision = npcRestrictionService.evaluateInteraction(
			npc, event.getMenuAction(), event.getMenuOption(), isSelectedWidgetSpell());
		if (!decision.isBlocked())
		{
			return;
		}
		event.consume();
		if (decision.getBlockedNpcName() != null)
		{
			chatFeedbackService.sendBlockedMessage(decision.getBlockedNpcName());
		}
		else
		{
			chatFeedbackService.sendBlockedCardsMessage(decision.getMissingCards());
		}
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
		resourceRestrictionService.updateCarriedState(
			pickaxes, axes, fishingInputs, collectItemNamesLower(inventory));
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
			if (ToolNameClassifier.isMiningPickaxe(name))
			{
				pickaxes.add(name);
			}
			else if (ToolNameClassifier.isWoodcuttingAxe(name))
			{
				axes.add(name);
			}
		}
	}

	@Subscribe(priority = -1.0f)
	public void onPostMenuSort(PostMenuSort event)
	{
		if (restrictionDecisionService.isEnforcementBypassed()
			|| config.showLockedMenuOptions())
		{
			return;
		}
		MenuEntry[] entries = client.getMenu().getMenuEntries();
		List<MenuEntry> visible = new ArrayList<>(entries.length);
		boolean changed = false;
		for (MenuEntry entry : entries)
		{
			if (itemInteractionService.shouldHideInventoryEntryAfterSort(entry))
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

	private boolean isSelectedWidgetSpell()
	{
		if (!client.isWidgetSelected())
		{
			return false;
		}
		Widget selected = client.getSelectedWidget();
		return selected != null && selected.getItemId() <= 0;
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		lockedItemMarkController.onScriptPostFired(event.getScriptId());
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		lockedItemMarkController.onWidgetLoaded(event.getGroupId());
	}

	@Provides
	BronzemanTcgConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BronzemanTcgConfig.class);
	}
}
