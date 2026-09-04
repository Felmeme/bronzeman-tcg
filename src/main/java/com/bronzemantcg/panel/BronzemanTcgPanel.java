package com.bronzemantcg.panel;

import com.bronzemantcg.BronzemanTcgConfig;
import com.bronzemantcg.catalog.QuestCatalog;
import com.bronzemantcg.catalog.QuestRequirementCatalog;
import com.bronzemantcg.interop.TcgCollectionReader;
import com.bronzemantcg.ownership.CardEntityKind;
import com.bronzemantcg.ownership.SharedUnlockStore;
import com.bronzemantcg.ownership.TcgOwnershipSnapshot;
import com.bronzemantcg.panel.collection.BetaCollectionSnapshotService;
import com.bronzemantcg.panel.collection.PanelBetaCollectionViewModel;
import com.bronzemantcg.panel.collection.PanelCollectionLayout;
import com.bronzemantcg.panel.collection.PanelCollectionViewModel;
import com.bronzemantcg.panel.collection.PanelSharedCardsViewModel;
import com.bronzemantcg.restriction.ExemptionList;
import com.bronzemantcg.restriction.LockState;
import com.bronzemantcg.settings.SidePanelSettings;
import com.bronzemantcg.settings.BetaCardsSettings;
import com.bronzemantcg.interop.BetaSaveImporter;
import com.google.gson.Gson;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ScheduledExecutorService;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

/**
 * Sidebar panel: card search, collection progress, and collapsible readiness checklists
 * for quests, slayer masters, PvM content and hunter rumour masters.
 * Threading contract: immutable view data is prepared on RuneLite's shared executor;
 * every Swing component is created or changed on the Swing EDT. The catalogs are
 * immutable after load and TcgCollectionReader/RecentUnlocksTracker are synchronized.
 * Live game state is never touched here.
 */
@Slf4j
public class BronzemanTcgPanel extends PluginPanel
{
	private static final int MAX_SEARCH_RESULTS = 20;
	private static final Color UNLOCKED = ColorScheme.PROGRESS_COMPLETE_COLOR;
	private static final Color LOCKED = ColorScheme.PROGRESS_ERROR_COLOR;
	private static final DateTimeFormatter UNLOCK_TIME_FORMAT = DateTimeFormatter
		.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault());
	private static final String COLLECTION_SHOW_LOCKED_KEY = "importantShowLocked";
	private static final String COLLECTION_SHOW_UNLOCKED_KEY = "importantShowUnlocked";
	private static final String COLLECTION_HIDE_BETA_PROGRESS_KEY =
		"collectionHideBetaProgress";
	private static final String BETA_COLLECTION_SHOW_LOCKED_KEY = "betaCollectionShowLocked";
	private static final String BETA_COLLECTION_SHOW_UNLOCKED_KEY = "betaCollectionShowUnlocked";
	private static final String QUEST_HIDE_COMPLETED_KEY = "questHideCompleted";
	private static final String QUEST_HIDE_INCOMPLETABLE_KEY = "questHideIncompletable";
	private static final String QUEST_REQUIRE_POINTS_KEY = "questRequirePoints";
	private static final String QUEST_REQUIRE_SKILLS_KEY = "questRequireSkills";
	private static final String QUEST_REQUIRE_PREREQUISITES_KEY = "questRequirePrerequisites";
	private static final String QUEST_REQUIRE_CARDS_KEY = "questRequireCards";
	private static final String SLAYER_SHOW_LOCKED_KEY = "slayerShowLocked";
	private static final String SLAYER_SHOW_UNLOCKED_KEY = "slayerShowUnlocked";
	private static final String PVM_SHOW_LOCKED_KEY = "pvmShowLocked";
	private static final String PVM_SHOW_UNLOCKED_KEY = "pvmShowUnlocked";
	private static final String RECENT_SHOW_SHARED_KEY = "recentShowShared";

	private final QuestCatalog questCatalog;
	private final QuestV1Presentation questV1Presentation;
	private final QuestRequirementCatalog questRequirementCatalog;
	private final ExemptionList exemptionList;
	private final PanelPresentationCatalog presentationCatalog;
	private final TcgCollectionReader collectionReader;
	private final SharedUnlockStore sharedUnlockStore;
	private final RecentUnlocksTracker recentUnlocksTracker;
	private final PanelCollectionViewModel collectionViewModel;
	private final PanelBetaCollectionViewModel betaCollectionViewModel;
	private final PanelSharedCardsViewModel sharedCardsViewModel;
	private final BetaCollectionSnapshotService betaCollectionSnapshotService;
	private final V1PresentationState v1PresentationState;
	private final SpriteManager spriteManager;
	private final BronzemanTcgConfig config;
	private final ConfigManager configManager;
	private final ScheduledExecutorService executor;
	private final AtomicBoolean refreshRunning = new AtomicBoolean();
	private final AtomicBoolean refreshAgain = new AtomicBoolean();
	private final EnumSet<PanelTab> dirtyTabs = EnumSet.allOf(PanelTab.class);
	private volatile PreparedData preV1PreparedData;
	private volatile PreparedData v1PreparedData;
	private volatile boolean disposed;
	private PanelSnapshot snapshot;
	private PanelTab selectedTab = PanelTab.ACTIVITIES;
	private PanelTab lastContentTab = PanelTab.ACTIVITIES;
	private PanelNavigationModel.State navigationState =
		PanelNavigationModel.resolve(false, false, true);
	private final Map<PanelTab, JButton> navigationButtons = new EnumMap<>(PanelTab.class);
	private final Map<Integer, Icon> sectionIconCache = new HashMap<>();
	/** Square, sized around the cog icon rather than the banner's height. */
	private static final int SETTINGS_BUTTON = 26;

	/**
	 * The game's own Options tab sprite. RuneLite only names this one on the deprecated
	 * net.runelite.api.SpriteID (RS2_TAB_OPTIONS), and gameval has no equivalent, so the
	 * raw id is used rather than importing a deprecated class.
	 */
	private static final int SETTINGS_TAB_SPRITE = 785;
	private static final int SECTION_ICON_SIZE = 16;
	private static final int QUEST_SECTION_SPRITE = SpriteID.Mapfunction.QUEST_START;
	private static final int MINIQUEST_SECTION_SPRITE = SpriteID.Mapfunction.MINIGAME;
	private static final int HUNTER_SECTION_SPRITE = SpriteID.Mapfunction.HUNTER_TRAINING;
	private static final int SLAYER_SECTION_SPRITE = SpriteID.Mapfunction.SLAYER_MASTER;
	private static final int PVM_SECTION_SPRITE = SpriteID.Mapfunction.COMBAT_TRAINING;
	private static final int CARDS_FILTER_SPRITE = SpriteID.ClanRankIcons._240;
	/*
	 * These named sprites exist only on deprecated net.runelite.api.SpriteID and
	 * have no gameval equivalents. Keep their verified IDs here, as with the
	 * Options-tab sprite above, to avoid depending on the deprecated class.
	 */
	private static final int QUEST_REQUIREMENTS_FILTER_SPRITE = 835;
	private static final int SKILL_LEVELS_FILTER_SPRITE = 882;

	/**
	 * The tick and cross glyphs are the panel's original look and are kept wherever the
	 * font can actually draw them. On the minority of clients whose font cannot, they
	 * render as empty boxes, so those fall back to equivalent drawn icons instead.
	 */
	private static final boolean MARK_GLYPHS_SUPPORTED = markGlyphsSupported();
	private static final String TICK_GLYPH = "✓";
	private static final String CROSS_GLYPH = "✗";
	private static final int MARK_SIZE = 12;
	private static final Icon TICK_ICON = markIcon(true, UNLOCKED);
	private static final Icon CROSS_ICON = markIcon(false, LOCKED);

	private static boolean markGlyphsSupported()
	{
		// UIManager rather than a live component: this runs at class load, which is not
		// guaranteed to be on the EDT.
		Font font = UIManager.getFont("Label.font");
		return font != null
			&& font.canDisplay(TICK_GLYPH.charAt(0))
			&& font.canDisplay(CROSS_GLYPH.charAt(0));
	}

	private final JButton settingsButton = new JButton();

	private final IconTextField searchBar = new IconTextField();
	private final JPanel searchResults = sectionBody();
	private final JPanel contentControls = sectionBody();
	private final JPanel navigationGrid = sectionBody();
	private final JLabel progressHeader = progressHeader();
	private final JPanel progressList = sectionBody();
	private final JPanel progressFooter = sectionBody();

	// Keep every view attached and let CardLayout switch visibility. The surrounding
	// JScrollPane then scrolls only the selected content while navigation and progress stay fixed.
	private final SelectedCardPanel tabDisplay = new SelectedCardPanel();
	private final JScrollPane contentScroll;

	private final IconTextField questSearchBar = new IconTextField();
	private final JPanel questFilters = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
	private final JPanel questRequirementFilters =
		new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
	private final JCheckBox hideCompletedQuests = new JCheckBox("Hide completed");
	private final JCheckBox requireQuestPoints = new JCheckBox();
	private final JCheckBox requireSkillLevels = new JCheckBox();
	private final JCheckBox requirePrerequisiteQuests = new JCheckBox();
	private final JCheckBox requireCards = new JCheckBox();
	private final JLabel questPointsFilterLabel = new JLabel("QP");
	private final JLabel skillLevelsFilterLabel = new JLabel("Lvl");
	private final JLabel prerequisiteQuestsFilterLabel = new JLabel("Req");
	private final JLabel cardsFilterLabel = new JLabel("Cards");
	private final JPanel questList = sectionBody();
	private final Set<String> expandedQuests = new HashSet<>();
	private final Set<String> expandedQuestCategories = new HashSet<>();
	private final Set<String> expandedQuestSections = new HashSet<>();
	private final Set<String> expandedQuestRequirements = new HashSet<>();
	private volatile Set<String> completedQuestNames = Collections.emptySet();
	private volatile QuestCatalog.RouteSelection questRoute =
		QuestCatalog.RouteSelection.UNKNOWN;
	// Real levels indexed by Skill.ordinal(), or null while logged out. Null must leave
	// the "Show meets reqs" filter inert - unknown levels would otherwise hide everything.
	private volatile int[] realSkillLevels;
	private volatile int questPoints;

	private final JCheckBox showLockedSlayer = new JCheckBox("Show locked");
	private final JCheckBox showUnlockedSlayer = new JCheckBox("Show unlocked");
	private final JPanel slayerSectionPanel = sectionBody();
	private final JPanel slayerList = sectionBody();
	private final Set<String> expandedSlayer = new HashSet<>();
	private final Set<String> expandedSlayerTasks = new HashSet<>();
	private final Set<String> expandedSlayerSuperiorGroups = new HashSet<>();
	private boolean expandedGlobalSuperiors;

	private final JCheckBox showLockedPvm = new JCheckBox("Show locked");
	private final JCheckBox showUnlockedPvm = new JCheckBox("Show unlocked");
	private final JPanel pvmSectionPanel = sectionBody();
	private final JPanel contentList = sectionBody();
	private final Set<String> expandedPvmSections = new HashSet<>();
	private final Set<String> expandedPvmGroups = new HashSet<>();

	private final JPanel rumoursList = sectionBody();
	private final JPanel activitiesRumourSection = sectionBody();
	private final Set<String> expandedRumours = new HashSet<>();
	private boolean expandedRumourSection;

	private final JPanel slayerPvmSections = sectionBody();
	private final Set<String> expandedSlayerPvmSections = new HashSet<>();

	private final IconTextField recentUnlocksSearchBar = new IconTextField();
	private final JCheckBox showSharedRecent = new JCheckBox("Show shared");
	private final JPanel recentUnlocksList = sectionBody();

	private final IconTextField sharedCardsSearchBar = new IconTextField();
	private final JPanel sharedCardsList = sectionBody();
	private final Set<String> expandedSharedCategories = new HashSet<>();
	private final Set<String> expandedSharedSubcategories = new HashSet<>();
	private final SidePanelSettings sidePanelSettings;

	private final IconTextField collectionSearchBar = new IconTextField();
	private final JPanel collectionFilters = new JPanel();

	private final JCheckBox showLockedCollection = new JCheckBox("Show locked");

	private final JCheckBox showUnlockedCollection = new JCheckBox("Show unlocked");
	private final JCheckBox hideBetaCardProgress =
		new JCheckBox("Hide beta card progress");

	private final JPanel collectionList = sectionBody();
	private final Set<String> expandedCollectionSections = new HashSet<>();
	private final Set<String> expandedCollectionCategories = new HashSet<>();

	private final IconTextField betaCollectionSearchBar = new IconTextField();
	private final JCheckBox showLockedBetaCollection = new JCheckBox("Show locked");
	private final JCheckBox showUnlockedBetaCollection = new JCheckBox("Show unlocked");
	private final JButton saveBetaCollectionButton = new JButton("Save Beta Collection");
	private final JLabel betaCollectionSaveStatus = mutedRow("No beta collection saved yet");
	private final JPanel betaCollectionSaveControls = sectionBody();
	private final JPanel betaCollectionList = sectionBody();
	private final Set<String> expandedBetaCollectionSections = new HashSet<>();
	private final Set<String> expandedBetaCollectionCategories = new HashSet<>();
	private final Set<String> expandedBetaCollectionParents = new HashSet<>();

	public BronzemanTcgPanel(
			Gson gson,
			QuestCatalog questCatalog,
			QuestV1Presentation questV1Presentation,
			QuestRequirementCatalog questRequirementCatalog,
			ExemptionList exemptionList,
			PanelPresentationCatalog presentationCatalog,
			TcgCollectionReader collectionReader,
			SharedUnlockStore sharedUnlockStore,
			RecentUnlocksTracker recentUnlocksTracker,
			PanelCollectionViewModel collectionViewModel,
			PanelBetaCollectionViewModel betaCollectionViewModel,
			PanelSharedCardsViewModel sharedCardsViewModel,
			BetaCollectionSnapshotService betaCollectionSnapshotService,
			V1PresentationState v1PresentationState,
			SpriteManager spriteManager,
			BronzemanTcgConfig config,
			ConfigManager configManager,
			ScheduledExecutorService executor,
			boolean presetOnboardingRequired)
	{
		super(false);
		this.questCatalog = questCatalog;
		this.questV1Presentation = questV1Presentation;
		this.questRequirementCatalog = questRequirementCatalog;
		this.exemptionList = exemptionList;
		this.presentationCatalog = presentationCatalog;
		this.collectionReader = collectionReader;
		this.sharedUnlockStore = sharedUnlockStore;
		this.recentUnlocksTracker = recentUnlocksTracker;
		this.collectionViewModel = collectionViewModel;
		this.betaCollectionViewModel = betaCollectionViewModel;
		this.sharedCardsViewModel = sharedCardsViewModel;
		this.betaCollectionSnapshotService = betaCollectionSnapshotService;
		this.v1PresentationState = v1PresentationState;
		this.spriteManager = spriteManager;
		this.config = config;
		this.configManager = configManager;
		this.executor = executor;
		this.sidePanelSettings = new SidePanelSettings(gson, config, configManager,
			presetOnboardingRequired, () -> selectContentTab(PanelTab.ACTIVITIES),
			new BetaCardsSettings(betaCollectionSnapshotService, new BetaSaveImporter(gson),
				betaCollectionViewModel, executor, () -> disposed, this::requestRefresh));

		JPanel activitiesPanel = sectionBody();
		JPanel questPanel = sectionBody();
		JPanel slayerPvmPanel = sectionBody();
		JPanel slayerFilters = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		JPanel pvmFilters = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		JPanel recentUnlocksPanel = sectionBody();
		JPanel sharedCardsPanel = sectionBody();
		JPanel collectionPanel = sectionBody();
		JPanel betaCollectionPanel = sectionBody();

		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
		contentScroll = new JScrollPane(tabDisplay,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
			JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		contentScroll.setBorder(null);
		contentScroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		contentScroll.getVerticalScrollBar().setUnitIncrement(16);
		contentScroll.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 320));

		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 28));
		searchBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		searchBar.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				refreshSearch();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				refreshSearch();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				refreshSearch();
			}
		});

		searchBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		searchResults.setAlignmentX(Component.LEFT_ALIGNMENT);
		progressList.setAlignmentX(Component.LEFT_ALIGNMENT);
		tabDisplay.setAlignmentX(Component.LEFT_ALIGNMENT);
		tabDisplay.setBackground(ColorScheme.DARK_GRAY_COLOR);

		recentUnlocksSearchBar.setIcon(IconTextField.Icon.SEARCH);
		recentUnlocksSearchBar.setToolTipText("Search recent unlocks");
		recentUnlocksSearchBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		recentUnlocksSearchBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		recentUnlocksSearchBar.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				refreshRecentUnlocks();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				refreshRecentUnlocks();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				refreshRecentUnlocks();
			}
		});
		recentUnlocksPanel.add(recentUnlocksSearchBar);
		recentUnlocksPanel.add(Box.createVerticalStrut(4));
		showSharedRecent.setSelected(getSavedBoolean(RECENT_SHOW_SHARED_KEY, false));
		showSharedRecent.setOpaque(false);
		showSharedRecent.addActionListener(event ->
		{
			configManager.setConfiguration(BronzemanTcgConfig.GROUP,
				RECENT_SHOW_SHARED_KEY, showSharedRecent.isSelected());
			dirtyTabs.add(PanelTab.RECENT);
			renderSelectedTab();
		});
		recentUnlocksPanel.add(showSharedRecent);
		recentUnlocksPanel.add(Box.createVerticalStrut(4));
		recentUnlocksPanel.add(recentUnlocksList);

		questSearchBar.setIcon(IconTextField.Icon.SEARCH);
		questSearchBar.setToolTipText("Search quests and miniquests");
		questSearchBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		questSearchBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		questSearchBar.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent event)
			{
				updateQuestSearch();
			}

			@Override
			public void removeUpdate(DocumentEvent event)
			{
				updateQuestSearch();
			}

			@Override
			public void changedUpdate(DocumentEvent event)
			{
				updateQuestSearch();
			}
		});
		questPanel.add(questSearchBar);
		questPanel.add(Box.createVerticalStrut(4));

		configureQuestFilters();
		questPanel.add(questList);
		activitiesPanel.add(questPanel);
		activitiesPanel.add(Box.createVerticalStrut(6));
		activitiesPanel.add(activitiesRumourSection);

		configureVisibilityFilters(slayerFilters, showLockedSlayer, showUnlockedSlayer,
			SLAYER_SHOW_LOCKED_KEY, SLAYER_SHOW_UNLOCKED_KEY, PanelTab.SLAYER_PVM);
		slayerSectionPanel.add(slayerFilters);
		slayerSectionPanel.add(Box.createVerticalStrut(4));
		slayerSectionPanel.add(slayerList);

		configureVisibilityFilters(pvmFilters, showLockedPvm, showUnlockedPvm,
			PVM_SHOW_LOCKED_KEY, PVM_SHOW_UNLOCKED_KEY, PanelTab.SLAYER_PVM);
		pvmSectionPanel.add(pvmFilters);
		pvmSectionPanel.add(Box.createVerticalStrut(4));
		pvmSectionPanel.add(contentList);
		configureConsolidatedSections();
		slayerPvmPanel.add(slayerPvmSections);

		configureTabSearchBar(collectionSearchBar, "Search Collection",
			this::refreshCollection);
		collectionPanel.add(collectionSearchBar);
		collectionPanel.add(Box.createVerticalStrut(4));
		configureCollectionFilters();
		collectionPanel.add(collectionFilters);
		collectionPanel.add(Box.createVerticalStrut(4));
		collectionPanel.add(collectionList);

		configureTabSearchBar(betaCollectionSearchBar, "Search Beta Collection",
			this::refreshBetaCollection);
		betaCollectionPanel.add(betaCollectionSearchBar);
		betaCollectionPanel.add(Box.createVerticalStrut(4));
		JPanel betaCollectionFilters = new JPanel();
		configureVisibilityFilters(betaCollectionFilters,
			showLockedBetaCollection, showUnlockedBetaCollection,
			BETA_COLLECTION_SHOW_LOCKED_KEY, BETA_COLLECTION_SHOW_UNLOCKED_KEY,
			PanelTab.BETA_COLLECTION);
		betaCollectionPanel.add(betaCollectionFilters);
		betaCollectionPanel.add(Box.createVerticalStrut(4));
		saveBetaCollectionButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		saveBetaCollectionButton.setFocusable(false);
		saveBetaCollectionButton.setToolTipText(
			"Save your current personal OSRS TCG beta collection before migrating to v1");
		saveBetaCollectionButton.addActionListener(event -> saveBetaCollectionSnapshot());
		betaCollectionSaveControls.add(saveBetaCollectionButton);
		betaCollectionSaveControls.add(Box.createVerticalStrut(2));
		betaCollectionSaveControls.add(betaCollectionSaveStatus);
		betaCollectionSaveControls.add(Box.createVerticalStrut(4));
		betaCollectionPanel.add(betaCollectionSaveControls);
		betaCollectionPanel.add(betaCollectionList);

		configureTabSearchBar(sharedCardsSearchBar, "Search shared cards",
			this::refreshSharedCards);
		sharedCardsPanel.add(sharedCardsSearchBar);
		sharedCardsPanel.add(Box.createVerticalStrut(4));
		sharedCardsPanel.add(sharedCardsList);

		progressList.add(mutedRow("Loading collection..."));
		questList.add(mutedRow("Loading quests..."));
		configureActivitiesRumourSection();
		addView(activitiesPanel, PanelTab.ACTIVITIES);
		addView(slayerPvmPanel, PanelTab.SLAYER_PVM);
		addView(recentUnlocksPanel, PanelTab.RECENT);
		addView(collectionPanel, PanelTab.COLLECTION);
		addView(betaCollectionPanel, PanelTab.BETA_COLLECTION);
		addView(sharedCardsPanel, PanelTab.SHARED);
		sidePanelSettings.component().setAlignmentX(Component.LEFT_ALIGNMENT);
		tabDisplay.addCard(PanelTab.SETTINGS, sidePanelSettings.component());

		contentControls.add(searchBar);
		contentControls.add(Box.createVerticalStrut(4));
		contentControls.add(searchResults);
		contentControls.add(Box.createVerticalStrut(6));
		contentControls.add(navigationGrid);
		JPanel header = sectionBody();
		header.add(createPlaceholderBanner());
		header.add(Box.createVerticalStrut(8));
		header.add(contentControls);
		add(header, BorderLayout.NORTH);

		add(contentScroll, BorderLayout.CENTER);
		progressFooter.add(progressHeader);
		progressFooter.add(progressList);
		add(progressFooter, BorderLayout.SOUTH);

		applyNavigationState(currentNavigationState());
		selectContentTab(PanelTab.ACTIVITIES);
		if (presetOnboardingRequired)
		{
			showSettings();
		}

	}

	private void configureActivitiesRumourSection()
	{
		activitiesRumourSection.removeAll();
		JPanel header = topLevelDropdownRow("Hunter Rumours", expandedRumourSection);
		applySectionSprite(header, HUNTER_SECTION_SPRITE);
		makeClickable(header, () ->
		{
			expandedRumourSection = !expandedRumourSection;
			refreshActivities();
		});
		activitiesRumourSection.add(header);
		if (expandedRumourSection)
		{
			activitiesRumourSection.add(Box.createVerticalStrut(4));
			activitiesRumourSection.add(rumoursList);
		}
		activitiesRumourSection.revalidate();
		activitiesRumourSection.repaint();
	}

	private void configureConsolidatedSections()
	{
		slayerPvmSections.removeAll();
		int[] slayerProgress = slayerProgress();
		addConsolidatedSection("Slayer", slayerSectionPanel,
			slayerProgress[0], slayerProgress[1], SLAYER_SECTION_SPRITE);
		slayerPvmSections.add(Box.createVerticalStrut(6));
		int[] pvmProgress = pvmProgress();
		addConsolidatedSection("PvM", pvmSectionPanel,
			pvmProgress[0], pvmProgress[1], PVM_SECTION_SPRITE);
		slayerPvmSections.revalidate();
		slayerPvmSections.repaint();
	}

	private void addConsolidatedSection(String name, JPanel content,
		int have, int total, int spriteId)
	{
		boolean expanded = expandedSlayerPvmSections.contains(name);
		JPanel header = compactProgressRow(name, have, total);
		styleCategoryHeader(header, expanded, 8, false);
		applySectionSprite(header, spriteId);
		makeClickable(header, () ->
		{
			if (!expandedSlayerPvmSections.remove(name))
			{
				expandedSlayerPvmSections.add(name);
			}
			refreshSlayerPvm();
		});
		slayerPvmSections.add(header);
		if (expanded)
		{
			slayerPvmSections.add(Box.createVerticalStrut(4));
			slayerPvmSections.add(content);
		}
	}

	private int[] slayerProgress()
	{
		int have = 0;
		int total = 0;
		if (snapshot != null)
		{
			for (SlayerMasterEntry master : snapshot.data.slayer)
			{
				have += master.satisfiedCount(snapshot.usableCards,
					snapshot.includeSlayerSuperiors);
				total += master.requirementCount(snapshot.includeSlayerSuperiors);
			}
		}
		return new int[]{have, total};
	}

	private int[] pvmProgress()
	{
		int have = 0;
		int total = 0;
		if (snapshot != null)
		{
			for (QuestCatalog.QuestEntry entry : snapshot.data.contents)
			{
				have += entry.satisfiedCount(snapshot.usableCards);
				total += entry.requirements.size();
			}
		}
		return new int[]{have, total};
	}

	private void saveBetaCollectionSnapshot()
	{
		saveBetaCollectionButton.setEnabled(false);
		betaCollectionSaveStatus.setText("Saving beta collection...");
		executor.execute(() ->
		{
			collectionReader.refreshNow();
			BetaCollectionSnapshotService.SaveResult result =
				betaCollectionSnapshotService.saveCurrent(
					collectionReader.getOwnershipSnapshot(),
					collectionReader.isStateAvailable());
			SwingUtilities.invokeLater(() -> applyBetaSnapshotSaveResult(result));
		});
	}

	private void applyBetaSnapshotSaveResult(
		BetaCollectionSnapshotService.SaveResult result)
	{
		if (disposed)
		{
			return;
		}
		switch (result.getOutcome())
		{
			case UNAVAILABLE:
				betaCollectionSaveStatus.setText("OSRS TCG collection unavailable");
				betaCollectionSaveStatus.setToolTipText(
					"Nothing changed because the personal OSRS TCG collection could not be read.");
				saveBetaCollectionButton.setEnabled(true);
				break;
			case PERSISTENCE_FAILED:
				betaCollectionSaveStatus.setText("Save failed; nothing changed");
				betaCollectionSaveStatus.setToolTipText(
					"Bronzeman could not store the snapshot, so the earlier copy was kept.");
				saveBetaCollectionButton.setEnabled(true);
				break;
			default:
				applyBetaSnapshotControls(result.getSnapshot());
				requestRefresh();
		}
	}

	private void applyBetaSnapshotControls(
		BetaCollectionSnapshotService.SnapshotView view)
	{
		int count = view.getOwnedNamesLowerCase().size();
		betaCollectionSaveStatus.setToolTipText(null);
		switch (view.getStatus())
		{
			case PROVISIONAL:
				betaCollectionSaveStatus.setText("Saved " + count + " unique beta "
					+ (count == 1 ? "card" : "cards"));
				saveBetaCollectionButton.setEnabled(true);
				break;
			case FROZEN_CAPTURED:
			case FROZEN_INFERRED:
			case IMPORTED:
				betaCollectionSaveStatus.setText("Beta collection secured · " + count
					+ " " + (count == 1 ? "card" : "cards"));
				saveBetaCollectionButton.setEnabled(false);
				break;
			case CLEARED:
				betaCollectionSaveStatus.setText("Beta history cleared; use Beta Card Imports");
				saveBetaCollectionButton.setEnabled(false);
				break;
			case INCOMPATIBLE:
				betaCollectionSaveStatus.setText("Saved beta data is incompatible");
				betaCollectionSaveStatus.setToolTipText(
					"The existing snapshot was left untouched because it does not match this release.");
				saveBetaCollectionButton.setEnabled(false);
				break;
			default:
				betaCollectionSaveStatus.setText("No beta collection saved yet");
				saveBetaCollectionButton.setEnabled(true);
		}
	}

	private void addView(JPanel content, PanelTab panelTab)
	{
		content.setAlignmentX(Component.LEFT_ALIGNMENT);
		tabDisplay.addCard(panelTab, content);
	}

	private void rebuildNavigationGrid()
	{
		navigationGrid.removeAll();
		navigationButtons.clear();
		List<List<PanelNavigationModel.Tab>> rows = navigationState.getRows();
		for (int i = 0; i < rows.size(); i++)
		{
			List<PanelNavigationModel.Tab> rowTabs = rows.get(i);
			JPanel buttonRow = row(new GridLayout(1, rowTabs.size(), 6, 0));
			for (PanelNavigationModel.Tab modelTab : rowTabs)
			{
				PanelTab panelTab = fromNavigationTab(modelTab);
				buttonRow.add(navigationButton(navigationLabel(panelTab), panelTab));
			}
			navigationGrid.add(buttonRow);
			if (i + 1 < rows.size())
			{
				navigationGrid.add(Box.createVerticalStrut(6));
			}
		}
		updateNavigationSelection();
		navigationGrid.revalidate();
		navigationGrid.repaint();
	}

	private JButton navigationButton(String label, PanelTab tab)
	{
		Color bronze = new Color(153, 102, 51);
		JButton button = new JButton(label);
		button.setFocusable(false);
		button.setForeground(Color.WHITE);
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		button.setBorder(BorderFactory.createLineBorder(bronze));
		button.setPreferredSize(new Dimension(0, 30));
		button.addActionListener(event -> selectContentTab(tab));
		button.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent event)
			{
				if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event))
				{
					collapseOpenSections(tab);
				}
			}

			@Override
			public void mouseEntered(MouseEvent event)
			{
				if (selectedTab != tab)
				{
					button.setBackground(ColorScheme.DARK_GRAY_COLOR);
				}
			}

			@Override
			public void mouseExited(MouseEvent event)
			{
				updateNavigationButton(button, tab);
			}
		});
		navigationButtons.put(tab, button);
		return button;
	}

	private void collapseOpenSections(PanelTab tab)
	{
		switch (tab)
		{
			case ACTIVITIES:
				expandedQuestCategories.clear();
				expandedQuests.clear();
				expandedQuestSections.clear();
				expandedQuestRequirements.clear();
				expandedRumours.clear();
				expandedRumourSection = false;
				break;
			case SLAYER_PVM:
				expandedSlayerPvmSections.clear();
				expandedSlayer.clear();
				expandedSlayerTasks.clear();
				expandedSlayerSuperiorGroups.clear();
				expandedPvmSections.clear();
				expandedPvmGroups.clear();
				expandedGlobalSuperiors = false;
				break;
			case COLLECTION:
				expandedCollectionSections.clear();
				expandedCollectionCategories.clear();
				break;
			case BETA_COLLECTION:
				expandedBetaCollectionSections.clear();
				expandedBetaCollectionCategories.clear();
				expandedBetaCollectionParents.clear();
				break;
			case SHARED:
				expandedSharedCategories.clear();
				expandedSharedSubcategories.clear();
				break;
			default:
				return;
		}

		dirtyTabs.add(tab);
		if (selectedTab == tab)
		{
			renderSelectedTab();
		}
	}

	private void updateNavigationSelection()
	{
		for (Map.Entry<PanelTab, JButton> entry : navigationButtons.entrySet())
		{
			updateNavigationButton(entry.getValue(), entry.getKey());
		}
	}

	private void updateNavigationButton(JButton button, PanelTab tab)
	{
		button.setBackground(selectedTab == tab
			? new Color(92, 65, 38) : ColorScheme.DARKER_GRAY_COLOR);
	}

	private JPanel createPlaceholderBanner()
	{
		Color bronze = new Color(153, 102, 51);
		JPanel banner = row(new BorderLayout(8, 0));
		banner.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		banner.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(bronze),
			BorderFactory.createEmptyBorder(4, 8, 4, 8)));
		banner.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 46));
		banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

		Image helmet = ImageUtil.loadImageResource(
			BronzemanTcgPanel.class, "/assets/panel_icon.png")
			.getScaledInstance(24, 36, Image.SCALE_SMOOTH);
		banner.add(new JLabel(new ImageIcon(helmet)), BorderLayout.WEST);

		JLabel title = new JLabel("Bronzeman TCG");
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
		banner.add(title, BorderLayout.CENTER);

		settingsButton.setToolTipText("Settings");
		settingsButton.setFocusable(false);
		settingsButton.setForeground(Color.WHITE);
		settingsButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
		settingsButton.setBorder(BorderFactory.createLineBorder(bronze));
		settingsButton.setPreferredSize(new Dimension(SETTINGS_BUTTON, SETTINGS_BUTTON));
		settingsButton.setMargin(new Insets(0, 0, 0, 0));
		// The game's Options tab sprite, used at its native size - it already suits the
		// button. Loaded from the player's cache, so the icon appears once sprites are
		// ready and the button simply stays blank until then.
		spriteManager.getSpriteAsync(SETTINGS_TAB_SPRITE, 0, sprite ->
			SwingUtilities.invokeLater(() ->
			{
				if (sprite != null)
				{
					settingsButton.setIcon(new ImageIcon(sprite));
				}
			}));
		settingsButton.addActionListener(event ->
		{
			if (selectedTab == PanelTab.SETTINGS)
			{
				selectContentTab(lastContentTab);
			}
			else
			{
				showSettings();
			}
		});

		// BorderLayout stretches an EAST child to the banner's full height, which would
		// make the cog a tall rectangle. GridBagLayout centres it at its preferred size
		// instead, keeping it square and sized around the glyph.
		JPanel settingsHolder = new JPanel(new GridBagLayout());
		settingsHolder.setOpaque(false);
		settingsHolder.add(settingsButton);
		banner.add(settingsHolder, BorderLayout.EAST);
		return banner;
	}

	private void showSettings()
	{
		if (selectedTab != PanelTab.SETTINGS)
		{
			lastContentTab = selectedTab;
		}
		selectedTab = PanelTab.SETTINGS;
		updateSettingsButton(true);
		setCollectionSummaryVisible(false);
		tabDisplay.showCard(PanelTab.SETTINGS);
		updateNavigationSelection();
		sidePanelSettings.refresh();
	}

	private void selectContentTab(PanelTab tab)
	{
		if (!isVisibleNavigationTab(tab))
		{
			tab = fallbackForHidden(tab);
		}
		setCollectionSummaryVisible(true);
		updateSettingsButton(false);
		selectedTab = tab;
		lastContentTab = tab;
		tabDisplay.showCard(tab);
		updateNavigationSelection();
		SwingUtilities.invokeLater(() ->
		{
			revalidate();
			repaint();
			renderSelectedTab();
		});
	}

	private void updateSettingsButton(boolean selected)
	{
		settingsButton.setBackground(selected
			? ColorScheme.BRAND_ORANGE : ColorScheme.DARK_GRAY_COLOR);
		settingsButton.setForeground(selected
			? ColorScheme.DARKER_GRAY_COLOR : Color.WHITE);
		repaint();
	}

	private void setCollectionSummaryVisible(boolean visible)
	{
		contentControls.setVisible(visible);
		progressFooter.setVisible(visible);
		revalidate();
		repaint();
	}

	/** Keep the compact controls in sync with changes made by presets or RuneLite's panel. */
	public void onConfigChanged()
	{
		applyNavigationState(currentNavigationState());
		if (selectedTab == PanelTab.SETTINGS)
		{
			sidePanelSettings.refresh();
		}
	}

	private PanelNavigationModel.State currentNavigationState()
	{
		boolean liveV1Capable = collectionReader.hasLiveV1Capability();
		return currentNavigationState(v1PresentationState.isActive(liveV1Capable));
	}

	private PanelNavigationModel.State currentNavigationState(boolean v1Capable)
	{
		return PanelNavigationModel.resolve(v1Capable,
			config.acceptSharedUnlocks(), config.showBetaCollectionTab());
	}

	private void applyNavigationState(PanelNavigationModel.State state)
	{
		navigationState = state;
		rebuildNavigationGrid();

		if (!isVisibleNavigationTab(lastContentTab))
		{
			lastContentTab = fallbackForHidden(lastContentTab);
		}
		if (selectedTab != PanelTab.SETTINGS && !isVisibleNavigationTab(selectedTab))
		{
			selectContentTab(fallbackForHidden(selectedTab));
			return;
		}
		updateNavigationSelection();
	}

	private boolean isVisibleNavigationTab(PanelTab tab)
	{
		PanelNavigationModel.Tab modelTab = toNavigationTab(tab);
		return modelTab != null && navigationState.isVisible(modelTab);
	}

	private PanelTab fallbackForHidden(PanelTab hidden)
	{
		PanelNavigationModel.Tab modelTab = toNavigationTab(hidden);
		if (modelTab == null)
		{
			return PanelTab.ACTIVITIES;
		}
		PanelNavigationModel.Tab fallback = navigationState.selectionAfterHiding(modelTab);
		return fallback == PanelNavigationModel.Tab.COLLECTION
			? PanelTab.COLLECTION
			: fallback == PanelNavigationModel.Tab.BETA
				? PanelTab.BETA_COLLECTION : PanelTab.ACTIVITIES;
	}

	private static PanelNavigationModel.Tab toNavigationTab(PanelTab tab)
	{
		switch (tab)
		{
			case ACTIVITIES:
				return PanelNavigationModel.Tab.ACTIVITIES;
			case SLAYER_PVM:
				return PanelNavigationModel.Tab.SLAYER_PVM;
			case RECENT:
				return PanelNavigationModel.Tab.RECENT;
			case COLLECTION:
				return PanelNavigationModel.Tab.COLLECTION;
			case BETA_COLLECTION:
				return PanelNavigationModel.Tab.BETA;
			case SHARED:
				return PanelNavigationModel.Tab.SHARED;
			default:
				return null;
		}
	}

	private static PanelTab fromNavigationTab(PanelNavigationModel.Tab tab)
	{
		switch (tab)
		{
			case ACTIVITIES:
				return PanelTab.ACTIVITIES;
			case SLAYER_PVM:
				return PanelTab.SLAYER_PVM;
			case RECENT:
				return PanelTab.RECENT;
			case COLLECTION:
				return PanelTab.COLLECTION;
			case SHARED:
				return PanelTab.SHARED;
			case BETA:
				return PanelTab.BETA_COLLECTION;
			default:
				throw new IllegalArgumentException("Unknown navigation tab: " + tab);
		}
	}

	private static String navigationLabel(PanelTab tab)
	{
		switch (tab)
		{
			case ACTIVITIES:
				return "Activities";
			case SLAYER_PVM:
				return "Slayer/PvM";
			case RECENT:
				return "Recent";
			case COLLECTION:
				return "Collection";
			case SHARED:
				return "Shared";
			case BETA_COLLECTION:
				return "Beta";
			default:
				return "";
		}
	}

	/**
	 * Queue one background snapshot. Calls arriving while one is in flight collapse into
	 * one follow-up, so game ticks and PluginMessage pushes cannot flood either thread.
	 */
	public void requestRefresh()
	{
		if (disposed)
		{
			return;
		}
		if (!refreshRunning.compareAndSet(false, true))
		{
			refreshAgain.set(true);
			return;
		}

		executor.execute(() ->
		{
			PanelSnapshot next = null;
			try
			{
				next = buildSnapshot();
			}
			catch (RuntimeException ex)
			{
				log.warn("Could not prepare Bronzeman TCG panel data", ex);
			}
			PanelSnapshot completed = next;
			SwingUtilities.invokeLater(() -> finishRefresh(completed));
		});
	}

	/** Receives an immutable-friendly quest-state snapshot captured on the client thread. */
	public void updateQuestState(Set<String> completed, QuestCatalog.RouteSelection route,
		int[] levels, int points)
	{
		completedQuestNames = completed == null
			? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(completed));
		questRoute = route == null ? QuestCatalog.RouteSelection.UNKNOWN : route;
		realSkillLevels = levels == null ? null : levels.clone();
		questPoints = points;
	}

	private void configureTabSearchBar(IconTextField field, String tooltip,
		Runnable refresh)
	{
		field.setIcon(IconTextField.Icon.SEARCH);
		field.setToolTipText(tooltip);
		field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		field.setAlignmentX(Component.LEFT_ALIGNMENT);
		field.getDocument().addDocumentListener(new DocumentListener()
		{
			private void update()
			{
				if (snapshot != null)
				{
					refresh.run();
				}
			}

			@Override
			public void insertUpdate(DocumentEvent event)
			{
				update();
			}

			@Override
			public void removeUpdate(DocumentEvent event)
			{
				update();
			}

			@Override
			public void changedUpdate(DocumentEvent event)
			{
				update();
			}
		});
	}

	/** Stop queued work from touching a panel that has been removed from the toolbar. */
	public void dispose()
	{
		disposed = true;
		refreshAgain.set(false);
	}

	private PanelSnapshot buildSnapshot()
	{
		TcgOwnershipSnapshot personalOwnership = collectionReader.getOwnershipSnapshot();
		boolean liveV1Capable = personalOwnership.hasEntityIds(CardEntityKind.ITEM)
			&& personalOwnership.hasEntityIds(CardEntityKind.NPC);
		boolean v1Presentation = v1PresentationState.isActive(liveV1Capable);
		PreparedData data = v1Presentation ? v1PreparedData : preV1PreparedData;
		long identityRevision = v1Presentation
			? questV1Presentation.getRevision() : PreparedData.PRE_V1_REVISION;
		if (data == null || data.identityRevision != identityRevision)
		{
			data = prepareStaticData(v1Presentation);
			if (v1Presentation)
			{
				v1PreparedData = data;
			}
			else
			{
				preV1PreparedData = data;
			}
		}

		Set<String> owned = Collections.unmodifiableSet(
			new HashSet<>(personalOwnership.getOwnedCardNamesLowerCase()));
		Set<String> shared = new HashSet<>(sharedUnlockStore.getSharedCardNamesLowerCase());
		shared.removeAll(owned);
		Set<String> visibleShared = config.acceptSharedUnlocks()
			? Collections.unmodifiableSet(shared) : Collections.emptySet();
		// Readiness asks "can I do this", not "do I own the card", so it counts anything
		// the plugin will never restrict: shared cards, the exempt list and the Coins
		// toggle. Kept separate from `owned` because the row indicators still need to
		// distinguish a card you own from one the group is sharing.
		Set<String> usableCards = new HashSet<>(owned);
		usableCards.addAll(visibleShared);
		usableCards.addAll(exemptionList.resolve(config.lootExemptNames())
			.getCardNamesLowerCase());
		if (config.coinMode() == LockState.UNLOCKED)
		{
			usableCards.add("coins");
		}
		boolean includeSlayerSuperiors = config.restrictSlayerSuperiors();
		Set<String> completed = completedQuestNames;
		QuestCatalog.RouteSelection route = questRoute;
		BetaCollectionSnapshotService.SnapshotView betaSnapshot =
			betaCollectionSnapshotService.getView();
		Set<String> frozenBetaNames = betaSnapshot.getStatus().isFrozen()
			? new HashSet<>(betaSnapshot.getOwnedNamesLowerCase()) : new HashSet<>();
		// Unreviewed imported names remain historical evidence, not live-parent assignments.
		frozenBetaNames.removeAll(betaCollectionSnapshotService.unmatchedNames(frozenBetaNames));
		boolean hideBetaProgress = getSavedProfileBoolean(
			COLLECTION_HIDE_BETA_PROGRESS_KEY, false);
		PanelCollectionViewModel.State collection = collectionViewModel.prepare(
			personalOwnership, visibleShared, frozenBetaNames,
			hideBetaProgress);
		PanelBetaCollectionViewModel.State betaCollection = betaCollectionViewModel.prepare(
			betaSnapshot.getOwnedNamesLowerCase(), betaSnapshot.getStatus());
		PanelSharedCardsViewModel.State sharedCards = sharedCardsViewModel.prepare(
			visibleShared, collection);

		return new PanelSnapshot(data, owned, visibleShared, recentUnlocksTracker.getRecent(),
			recentUnlocksTracker.getSharedRecent(),
			Collections.unmodifiableSet(usableCards), includeSlayerSuperiors, completed, route,
			realSkillLevels, questPoints, collection, betaCollection, sharedCards,
			v1Presentation, currentNavigationState(v1Presentation),
			hideBetaProgress);
	}

	private PreparedData prepareStaticData(boolean v1Capable)
	{
		PanelPresentationCatalog.Data presentation =
			presentationCatalog.select(v1Capable);
		QuestV1Presentation.Data questPresentation = v1Capable
			? questV1Presentation.project(questCatalog) : null;
		List<QuestCatalog.QuestEntry> quests = sortedEntries(v1Capable
			? questPresentation.getQuests() : questCatalog.getQuests());
		List<QuestCatalog.QuestEntry> miniquests = sortedEntries(v1Capable
			? questPresentation.getMiniquests() : questCatalog.getMiniquests());
		List<QuestCatalog.QuestEntry> contents = sortedEntries(
			buildPvmEntries(presentation.getContents(), v1Capable));
		List<SlayerMasterEntry> slayer = buildSlayerEntries(
			presentation.getSlayerRules());
		List<QuestCatalog.Requirement> allSuperiors = buildGlobalSuperiors(slayer);
		List<QuestCatalog.QuestEntry> rumours =
			sortedEntries(buildRumourMasterEntries(presentation.getRumourRules()));

		return new PreparedData(v1Capable ? questPresentation.getRevision()
			: PreparedData.PRE_V1_REVISION, quests, miniquests, contents, slayer,
			allSuperiors, rumours);
	}

	private void finishRefresh(PanelSnapshot next)
	{
		try
		{
			if (!disposed && next != null)
			{
				applySnapshot(next);
			}
		}
		finally
		{
			refreshRunning.set(false);
			if (!disposed && refreshAgain.getAndSet(false))
			{
				requestRefresh();
			}
		}
	}

	private void applySnapshot(PanelSnapshot next)
	{
		PanelSnapshot previous = snapshot;
		boolean first = previous == null;
		boolean ownedChanged = first || !previous.owned.equals(next.owned);
		boolean sharedChanged = first || !previous.shared.equals(next.shared);
		boolean collectionChanged = first || !previous.collection.equals(next.collection);
		boolean betaCollectionChanged = first
			|| !previous.betaCollection.equals(next.betaCollection);
		boolean recentChanged = first || !sameUnlocks(previous.recentUnlocks, next.recentUnlocks)
			|| !sameUnlocks(previous.sharedRecentUnlocks, next.sharedRecentUnlocks);
		boolean slayerChanged = first
			|| previous.includeSlayerSuperiors != next.includeSlayerSuperiors;
		boolean presentationChanged = first
			|| previous.v1Presentation != next.v1Presentation;
		boolean questStateChanged = first
			|| !previous.completedQuests.equals(next.completedQuests)
			|| !previous.usableCards.equals(next.usableCards)
			|| previous.questRoute != next.questRoute
			|| !java.util.Arrays.equals(previous.realSkillLevels, next.realSkillLevels)
			|| previous.questPoints != next.questPoints;
		snapshot = next;
		hideBetaCardProgress.setSelected(next.hideBetaProgress);
		betaCollectionSaveControls.setVisible(!next.v1Presentation);
		// Party-sharing controls whether this view exists. The Recent Unlocks
		// "Show shared" preference only filters that tab and must not affect this one.
		applyNavigationState(next.navigation);

		if (ownedChanged)
		{
			dirtyTabs.addAll(EnumSet.allOf(PanelTab.class));
		}
		if (collectionChanged)
		{
			dirtyTabs.add(PanelTab.COLLECTION);
			refreshProgress(next);
			refreshSearch();
		}
		if (betaCollectionChanged)
		{
			dirtyTabs.add(PanelTab.BETA_COLLECTION);
		}
		if (recentChanged)
		{
			dirtyTabs.add(PanelTab.RECENT);
		}
		if (sharedChanged)
		{
			dirtyTabs.add(PanelTab.SHARED);
		}
		if (slayerChanged)
		{
			dirtyTabs.add(PanelTab.SLAYER_PVM);
		}
		if (presentationChanged)
		{
			dirtyTabs.add(PanelTab.ACTIVITIES);
			dirtyTabs.add(PanelTab.SLAYER_PVM);
		}
		if (questStateChanged)
		{
			dirtyTabs.add(PanelTab.ACTIVITIES);
			dirtyTabs.add(PanelTab.SLAYER_PVM);
		}
		renderSelectedTab();
	}

	private void renderSelectedTab()
	{
		if (snapshot == null || !dirtyTabs.remove(selectedTab))
		{
			return;
		}

		switch (selectedTab)
		{
			case ACTIVITIES:
				refreshActivities();
				break;
			case SLAYER_PVM:
				refreshSlayerPvm();
				break;
			case RECENT:
				refreshRecentUnlocks();
				break;
			case COLLECTION:
				refreshCollection();
				break;
			case BETA_COLLECTION:
				refreshBetaCollection();
				break;
			case SHARED:
				refreshSharedCards();
				break;
			default:
				break;
		}
	}

	// ------------------------------------------------------------------ collapsible checklists

	private void refreshActivities()
	{
		refreshQuests();
		if (expandedRumourSection)
		{
			refreshRumours();
		}
		configureActivitiesRumourSection();
	}

	private void refreshSlayerPvm()
	{
		if (expandedSlayerPvmSections.contains("Slayer"))
		{
			refreshSlayer();
		}
		if (expandedSlayerPvmSections.contains("PvM"))
		{
			refreshContent();
		}
		configureConsolidatedSections();
	}

	private void refreshQuests()
	{
		questList.removeAll();
		List<QuestCatalog.QuestEntry> quests = visibleQuests(snapshot.data.quests);
		List<QuestCatalog.QuestEntry> miniquests = visibleQuests(snapshot.data.miniquests);

		addQuestCategory("Quests", quests, snapshot.data.quests.isEmpty());
		addSpacedDivider(questList);
		addQuestCategory("Miniquests", miniquests, snapshot.data.miniquests.isEmpty());
		questList.revalidate();
		questList.repaint();
	}

	private List<QuestCatalog.QuestEntry> visibleQuests(
		List<QuestCatalog.QuestEntry> source)
	{
		String query = questSearchBar.getText() == null ? ""
			: questSearchBar.getText().trim().toLowerCase(Locale.ROOT);
		List<QuestCatalog.QuestEntry> visible = new ArrayList<>();
		for (QuestCatalog.QuestEntry quest : source)
		{
			boolean completed = isQuestCompleted(quest.questName);
			boolean cardsMet = quest.satisfiedCount(snapshot.usableCards,
				snapshot.questRoute) == quest.requirements.size();
			if (QuestFilterModel.isVisible(completed, hideCompletedQuests.isSelected(),
				cardsMet, requireCards.isSelected(), questRequirementCatalog.get(quest.name),
				snapshot.realSkillLevels, snapshot.questPoints, snapshot.completedQuests,
				requireQuestPoints.isSelected(), requireSkillLevels.isSelected(),
				requirePrerequisiteQuests.isSelected())
				&& (query.isEmpty() || quest.name.toLowerCase(Locale.ROOT).contains(query)))
			{
				visible.add(quest);
			}
		}
		return visible;
	}

	/**
	 * Quest-title hover: the requirement checklist plus any catalogue note. Requirements
	 * live only here, never in the dropdowns. Returns null when there is nothing to show.
	 */
	private String questTooltip(QuestCatalog.QuestEntry entry)
	{
		QuestRequirementCatalog.Requirements requirements =
			questRequirementCatalog.get(entry.name);
		if (requirements == null && entry.notes.isEmpty())
		{
			return null;
		}

		StringBuilder text = new StringBuilder("<html><b>")
			.append(escapeHtml(entry.name)).append("</b>");
		if (!entry.notes.isEmpty())
		{
			text.append("<br>").append(escapeHtml(entry.notes));
		}
		if (requirements != null)
		{
			int[] levels = snapshot == null ? null : snapshot.realSkillLevels;
			for (QuestRequirementCatalog.SkillRequirement skill : requirements.skills)
			{
				int ordinal = skill.skill.ordinal();
				Integer actual = levels != null && ordinal < levels.length
					? levels[ordinal] : null;
				boolean met = actual != null && actual >= skill.level;
				String suffix = actual != null && !met ? " (you have " + actual + ")" : "";
				text.append(markedLine(met, actual != null,
					skill.level + " " + skillLabel(skill.skill) + suffix));
			}
			if (requirements.questPoints > 0)
			{
				boolean known = levels != null;
				text.append(markedLine(known && snapshot.questPoints >= requirements.questPoints,
					known, requirements.questPoints + " Quest Points"));
			}
			for (QuestRequirementCatalog.QuestRequirement quest : requirements.quests)
			{
				boolean known = snapshot != null;
				text.append(markedLine(known && snapshot.completedQuests.contains(
					quest.quest.getName().toLowerCase(Locale.ROOT)), known, quest.name));
			}
			// Real gates the wiki states as free text. Shown so the player knows about
			// them, but never part of the filter - hence a neutral marker, not a cross.
			for (String note : requirements.other)
			{
				text.append("<br>&nbsp;&nbsp;").append(bullet()).append(escapeHtml(note));
			}
		}
		return text.append("</html>").toString();
	}

	/**
	 * Swing shows the deepest child's tooltip, and compactProgressRow gives its name
	 * label one so clipped names stay readable at the fixed panel width. Without this the
	 * checklist would only appear over the progress bar and padding, never over the name.
	 */
	private static void applyToolTipDeep(JComponent component, String text)
	{
		component.setToolTipText(text);
		for (Component child : component.getComponents())
		{
			if (child instanceof JComponent)
			{
				applyToolTipDeep((JComponent) child, text);
			}
		}
	}

	/** Bullet for a line with no pass/fail state, matching the mark's font support. */
	private static String bullet()
	{
		return MARK_GLYPHS_SUPPORTED ? "&#8226; " : "- ";
	}

	/**
	 * Tooltips are HTML rather than Swing components, so a drawn icon would mean
	 * embedding image data. The glyphs are used wherever the font supports them and
	 * degrade to ASCII where it does not; colour carries the meaning either way.
	 */
	private static String markedLine(boolean met, boolean known, String label)
	{
		if (!known)
		{
			return "<br>&nbsp;&nbsp;" + bullet() + escapeHtml(label);
		}
		String colour = String.format("#%06x",
			(met ? UNLOCKED : LOCKED).getRGB() & 0xFFFFFF);
		String mark = MARK_GLYPHS_SUPPORTED
			? (met ? "&#10004;" : "&#10008;") : (met ? "[x]" : "[ ]");
		return "<br>&nbsp;&nbsp;<font color='" + colour + "'>"
			+ mark + "</font> " + escapeHtml(label);
	}

	/** A tick or cross drawn once at class load and shared by every row that needs it. */
	private static Icon markIcon(boolean tick, Color color)
	{
		BufferedImage image = new BufferedImage(
			MARK_SIZE, MARK_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
			RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(color);
		graphics.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND,
			BasicStroke.JOIN_ROUND));
		if (tick)
		{
			graphics.drawPolyline(new int[]{2, 5, 10}, new int[]{6, 9, 2}, 3);
		}
		else
		{
			graphics.drawLine(2, 2, 9, 9);
			graphics.drawLine(9, 2, 2, 9);
		}
		graphics.dispose();
		return new ImageIcon(image);
	}

	private static String skillLabel(Skill skill)
	{
		String name = skill.name();
		return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
	}

	private static String escapeHtml(String value)
	{
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** Takes the RuneLite game name, which a shortened display name may not equal. */
	private boolean isQuestCompleted(String name)
	{
		String key = name.toLowerCase(Locale.ROOT);
		if (snapshot.completedQuests.contains(key))
		{
			return true;
		}
		return key.endsWith(" (miniquest)") && snapshot.completedQuests.contains(
			key.substring(0, key.length() - " (miniquest)".length()));
	}

	private void addQuestCategory(String label, List<QuestCatalog.QuestEntry> entries,
		boolean dataEmpty)
	{
		int completable = 0;
		for (QuestCatalog.QuestEntry entry : entries)
		{
			if (entry.satisfiedCount(snapshot.usableCards, snapshot.questRoute)
				== entry.requirements.size())
			{
				completable++;
			}
		}
		boolean expanded = expandedQuestCategories.contains(label);
		JPanel categoryRow = compactProgressRow(label, completable, entries.size());
		styleCategoryHeader(categoryRow, expanded, 4, false);
		applySectionSprite(categoryRow, "Quests".equals(label)
			? QUEST_SECTION_SPRITE : MINIQUEST_SECTION_SPRITE);
		makeClickable(categoryRow, () ->
		{
			if (!expandedQuestCategories.remove(label))
			{
				expandedQuestCategories.add(label);
			}
			refreshQuests();
		});
		questList.add(categoryRow);
		if (!expanded)
		{
			return;
		}
		if ("Quests".equals(label))
		{
			questList.add(Box.createVerticalStrut(4));
			questList.add(questFilters);
		}
		if (entries.isEmpty())
		{
			questList.add(Box.createVerticalStrut(4));
			questList.add(mutedRow(dataEmpty
				? "  No data bundled" : "  No entries match the current filters"));
			return;
		}
		questList.add(Box.createVerticalStrut(4));
		int index = 0;
		for (QuestCatalog.QuestEntry entry : entries)
		{
			if (index++ > 0)
			{
				addSpacedDivider(questList);
			}
			questList.add(questEntryRow(entry));
			if (expandedQuests.contains(entry.name))
			{
				renderQuestSections(entry);
			}
		}
	}

	private JPanel questEntryRow(QuestCatalog.QuestEntry entry)
	{
		int have = entry.satisfiedCount(snapshot.usableCards, snapshot.questRoute);
		int total = entry.requirements.size();
		boolean expanded = expandedQuests.contains(entry.name);
		JPanel row = compactProgressRow(entry.name, have, total);
		styleCategoryHeader(row, expanded, 4, false);
		String tooltip = questTooltip(entry);
		if (tooltip != null)
		{
			applyToolTipDeep(row, tooltip);
		}
		makeClickable(row, () ->
		{
			if (!expandedQuests.remove(entry.name))
			{
				expandedQuests.add(entry.name);
			}
			refreshQuests();
		});
		return row;
	}

	private void renderQuestSections(QuestCatalog.QuestEntry entry)
	{
		if (entry.sections.isEmpty())
		{
			questList.add(mutedRow("  No card-backed requirements"));
			return;
		}
		for (int sectionIndex = 0; sectionIndex < entry.sections.size(); sectionIndex++)
		{
			QuestCatalog.Section section = entry.sections.get(sectionIndex);
			String label = section.label.isEmpty() ? "Requirements" : section.label;
			String key = entry.name + "\0section\0" + sectionIndex;
			boolean expanded = expandedQuestSections.contains(key);
			int have = section.satisfiedCount(snapshot.usableCards, snapshot.questRoute);
			int total = section.requirements.size();
			JPanel row = questSectionRow(label, have, total, expanded);
			makeClickable(row, () ->
			{
				if (!expandedQuestSections.remove(key))
				{
					expandedQuestSections.add(key);
				}
				refreshQuests();
			});
			questList.add(row);
			if (!expanded)
			{
				continue;
			}
			if (section.requirements.isEmpty())
			{
				questList.add(mutedRow("    No card-backed requirements"));
			}
			List<QuestCatalog.Requirement> ordered = new ArrayList<>(section.requirements);
			if ("Items".equalsIgnoreCase(label))
			{
				// Keep ordinary checklist rows first and the expandable any-of/route
				// groups together at the bottom of the Items section.
				ordered.sort(Comparator.comparing(BronzemanTcgPanel::isExpandableQuestRequirement));
			}
			for (QuestCatalog.Requirement requirement : ordered)
			{
				int sourceIndex = section.requirements.indexOf(requirement);
				renderQuestRequirement(requirement,
					key + "\0requirement\0" + sourceIndex);
			}
		}
	}

	private static boolean isExpandableQuestRequirement(QuestCatalog.Requirement requirement)
	{
		return !requirement.children.isEmpty() || requirement.displayCards.size() > 1;
	}

	private void renderQuestRequirement(QuestCatalog.Requirement requirement, String key)
	{
		boolean expandable = isExpandableQuestRequirement(requirement);
		if (!expandable)
		{
			JPanel row = requirementRow(requirement, questRequirementState(requirement));
			styleListContent(row);
			questList.add(row);
			return;
		}
		boolean expanded = expandedQuestRequirements.contains(key);
		JPanel row = contentDropdownRow(requirement.label, expanded);
		makeClickable(row, () ->
		{
			if (!expandedQuestRequirements.remove(key))
			{
				expandedQuestRequirements.add(key);
			}
			refreshQuests();
		});
		questList.add(row);
		if (!expanded)
		{
			return;
		}
		if (requirement.displayCardsOnly)
		{
			Map<String, String> cards = new LinkedHashMap<>();
			collectRequirementCards(requirement, cards);
			for (String card : cards.values())
			{
				JPanel cardRow = statusRow("      " + displayCardName(card),
					cardState(card.toLowerCase(Locale.ROOT)));
				styleListContent(cardRow);
				questList.add(cardRow);
			}
			return;
		}
		if (!requirement.children.isEmpty())
		{
			for (int index = 0; index < requirement.children.size(); index++)
			{
				renderQuestRequirement(requirement.children.get(index),
					key + "\0child\0" + index);
			}
		}
		else
		{
			for (String card : requirement.displayCards)
			{
				JPanel cardRow = statusRow("      " + displayCardName(card),
					cardState(card.toLowerCase(Locale.ROOT)));
				styleListContent(cardRow);
				questList.add(cardRow);
			}
		}
	}

	private static JPanel questSectionRow(String label, int have, int total,
		boolean expanded)
	{
		return hierarchyCountRow(label, have, total, expanded, 8);
	}

	private static JPanel hierarchyCountRow(String label, int have, int total,
		boolean expanded, int leftPadding)
	{
		JPanel row = row(new BorderLayout(6, 0));
		styleCategoryHeader(row, expanded, leftPadding, true);

		JLabel name = new JLabel(label);
		name.setForeground(Color.WHITE);
		row.add(name, BorderLayout.CENTER);

		JLabel count = new JLabel(have + "/" + total);
		count.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		count.setHorizontalAlignment(JLabel.RIGHT);
		count.setPreferredSize(new Dimension(38, 16));
		row.add(count, BorderLayout.EAST);
		return row;
	}

	private static JPanel contentDropdownRow(String label, boolean expanded)
	{
		JPanel row = row(new BorderLayout());
		styleCategoryHeader(row, expanded, 12, true);
		JLabel name = new JLabel(label);
		name.setForeground(Color.WHITE);
		row.add(name, BorderLayout.CENTER);
		return row;
	}

	private static JPanel topLevelDropdownRow(String label, boolean expanded)
	{
		JPanel row = row(new BorderLayout());
		styleCategoryHeader(row, expanded, 8, false);
		JLabel name = new JLabel(label);
		name.setForeground(Color.WHITE);
		row.add(name, BorderLayout.CENTER);
		return row;
	}

	private void applySectionSprite(JPanel row, int spriteId)
	{
		Component centre = ((BorderLayout) row.getLayout())
			.getLayoutComponent(BorderLayout.CENTER);
		if (!(centre instanceof JLabel))
		{
			return;
		}
		JLabel label = (JLabel) centre;
		Icon cached = sectionIconCache.get(spriteId);
		if (cached != null)
		{
			label.setIcon(cached);
			label.setIconTextGap(6);
			return;
		}
		spriteManager.getSpriteAsync(spriteId, 0, sprite ->
			SwingUtilities.invokeLater(() ->
			{
				if (sprite == null || disposed)
				{
					return;
				}
				Icon icon = new ImageIcon(ImageUtil.resizeImage(
					sprite, SECTION_ICON_SIZE, SECTION_ICON_SIZE));
				sectionIconCache.put(spriteId, icon);
				label.setIcon(icon);
				label.setIconTextGap(6);
				row.revalidate();
				row.repaint();
			}));
	}

	/**
	 * Every expandable row in the tabs, styled to match the settings view: a bronze
	 * border marks "this opens", and an expanded row fills warm brown. Leaf card rows
	 * keep {@link #styleListContent} so content stays visually distinct from controls.
	 */
	private static void styleCategoryHeader(JPanel row, boolean expanded, int leftPadding,
		boolean nested)
	{
		PanelComponents.styleHierarchyRow(row, expanded, nested, leftPadding);
	}

	private static void styleListContent(JPanel row)
	{
		row.setBackground(Color.BLACK);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR),
			BorderFactory.createEmptyBorder(4, 6, 4, 6)));
	}

	private static void collectRequirementCards(QuestCatalog.Requirement requirement,
		Map<String, String> cards)
	{
		for (String card : requirement.displayCards)
		{
			cards.putIfAbsent(card.toLowerCase(Locale.ROOT), card);
		}
		for (QuestCatalog.Requirement child : requirement.children)
		{
			collectRequirementCards(child, cards);
		}
	}

	private void refreshContent()
	{
		contentList.removeAll();
		boolean visible = addPvmSection("Instances/Raids", snapshot.data.contents);
		if (snapshot.data.contents.isEmpty())
		{
			contentList.add(mutedRow("No PvM data bundled"));
		}
		else if (!visible)
		{
			contentList.add(mutedRow("No PvM entries match these filters"));
		}
		contentList.revalidate();
		contentList.repaint();
	}

	private void refreshSlayer()
	{
		slayerList.removeAll();
		slayerList.add(Box.createVerticalStrut(4));

		boolean visible = false;
		if (!snapshot.includeSlayerSuperiors)
		{
			visible = addGlobalSuperiorList();
		}
		for (SlayerMasterEntry master : snapshot.data.slayer)
		{
			if (hasVisibleSlayerContent(master))
			{
				if (visible)
				{
					addSpacedDivider(slayerList);
				}
				addSlayerMaster(master);
				visible = true;
			}
		}
		if (snapshot.data.slayer.isEmpty())
		{
			slayerList.add(mutedRow("No slayer data bundled"));
		}
		else if (!visible)
		{
			slayerList.add(mutedRow("No Slayer entries match these filters"));
		}
		slayerList.revalidate();
		slayerList.repaint();
	}

	private boolean addGlobalSuperiorList()
	{
		List<QuestCatalog.Requirement> superiors = snapshot.data.allSuperiors;
		boolean hasVisibleSuperior = false;
		for (QuestCatalog.Requirement superior : superiors)
		{
			if (hasVisibleSlayerCards(superior.displayCards))
			{
				hasVisibleSuperior = true;
				break;
			}
		}
		if (!hasVisibleSuperior)
		{
			return false;
		}

		int have = satisfiedRequirements(superiors, snapshot.usableCards);
		slayerList.add(clickableProgressRow("Superior Creatures",
			have, superiors.size(), expandedGlobalSuperiors, () ->
			{
				expandedGlobalSuperiors = !expandedGlobalSuperiors;
				refreshSlayer();
			}));
		if (expandedGlobalSuperiors)
		{
			for (QuestCatalog.Requirement superior : superiors)
			{
				if (hasVisibleSlayerCards(superior.displayCards))
				{
					slayerList.add(statusRow("  " + superior.label,
						requirementState(superior)));
				}
			}
		}
		return true;
	}

	private void addSlayerMaster(SlayerMasterEntry master)
	{
		boolean expanded = expandedSlayer.contains(master.name);
		int have = master.satisfiedCount(snapshot.usableCards, snapshot.includeSlayerSuperiors);
		int total = master.requirementCount(snapshot.includeSlayerSuperiors);
		slayerList.add(clickableProgressRow(master.name, have, total, expanded, () ->
		{
			if (!expandedSlayer.remove(master.name))
			{
				expandedSlayer.add(master.name);
			}
			refreshSlayer();
		}));
		if (!expanded)
		{
			return;
		}

		for (SlayerTaskEntry task : master.tasks)
		{
			if (!hasVisibleSlayerCards(task.requirements))
			{
				continue;
			}
			String key = nestedKey(master.name, task.label);
			boolean taskExpanded = expandedSlayerTasks.contains(key);
			if (task.locationSpecific)
			{
				int locationsOwned = satisfiedRequirements(task.requirements, snapshot.usableCards);
				slayerList.add(clickableHierarchyCountRow(
					task.label, locationsOwned, task.requirements.size(), taskExpanded,
					() ->
					{
						if (!expandedSlayerTasks.remove(key))
						{
							expandedSlayerTasks.add(key);
						}
						refreshSlayer();
					}));
			}
			else
			{
				QuestCatalog.Requirement requirement = task.requirements.get(0);
				int variantsOwned = countOwned(requirement.displayCards, snapshot.usableCards);
				slayerList.add(clickableHierarchyCountRow(
					task.label, variantsOwned, requirement.displayCards.size(), taskExpanded,
					() ->
					{
						if (!expandedSlayerTasks.remove(key))
						{
							expandedSlayerTasks.add(key);
						}
						refreshSlayer();
					}));
			}
			if (!taskExpanded)
			{
				continue;
			}

			for (QuestCatalog.Requirement requirement : task.requirements)
			{
				if (!hasVisibleSlayerCards(requirement.displayCards))
				{
					continue;
				}
				if (task.locationSpecific)
				{
					slayerList.add(statusRow("    " + requirement.label,
						requirementState(requirement)));
				}
				for (String card : requirement.displayCards)
				{
					if (isSlayerCardVisible(card))
					{
						String indent = task.locationSpecific ? "      " : "    ";
						slayerList.add(statusRow(indent + displayCardName(card),
							cardState(card.toLowerCase(Locale.ROOT))));
					}
				}
			}
		}

		if (snapshot.includeSlayerSuperiors
			&& hasVisibleSlayerCards(master.superiors))
		{
			String key = master.name;
			boolean superiorExpanded = expandedSlayerSuperiorGroups.contains(key);
			slayerList.add(clickableSuperiorDropdownRow(superiorExpanded, () ->
				{
					if (!expandedSlayerSuperiorGroups.remove(key))
					{
						expandedSlayerSuperiorGroups.add(key);
					}
					refreshSlayer();
				}));
			if (superiorExpanded)
			{
				for (QuestCatalog.Requirement superior : master.superiors)
				{
					if (hasVisibleSlayerCards(superior.displayCards))
					{
						slayerList.add(statusRow("    " + superior.label,
							requirementState(superior)));
					}
				}
			}
		}
	}

	private boolean addPvmSection(String sectionName, List<QuestCatalog.QuestEntry> entries)
	{
		boolean hasVisibleEntry = false;
		for (QuestCatalog.QuestEntry entry : entries)
		{
			if (hasVisiblePvmRequirement(entry))
			{
				hasVisibleEntry = true;
				break;
			}
		}
		if (!hasVisibleEntry)
		{
			return false;
		}

		boolean expanded = expandedPvmSections.contains(sectionName);
		int ready = 0;
		for (QuestCatalog.QuestEntry entry : entries)
		{
			if (entry.satisfiedCount(snapshot.usableCards) == entry.requirements.size())
			{
				ready++;
			}
		}
		contentList.add(clickableProgressRow(sectionName, ready, entries.size(), expanded, () ->
		{
			if (!expandedPvmSections.remove(sectionName))
			{
				expandedPvmSections.add(sectionName);
			}
			refreshContent();
		}));
		if (!expanded)
		{
			return true;
		}
		contentList.add(Box.createVerticalStrut(4));

		int entryIndex = 0;
		for (QuestCatalog.QuestEntry entry : entries)
		{
			if (!hasVisiblePvmRequirement(entry))
			{
				continue;
			}
			if (entryIndex++ > 0)
			{
				addSpacedDivider(contentList);
			}
			String key = nestedKey(sectionName, entry.name);
			boolean groupExpanded = expandedPvmGroups.contains(key);
			contentList.add(clickableHierarchyCountRow(entry.name,
				entry.satisfiedCount(snapshot.usableCards), entry.requirements.size(),
				groupExpanded, () ->
				{
					if (!expandedPvmGroups.remove(key))
					{
						expandedPvmGroups.add(key);
					}
					refreshContent();
				}));
			if (groupExpanded)
			{
				for (QuestCatalog.Requirement requirement : entry.requirements)
				{
					CardState state = requirementState(requirement);
					if (isPvmEntryVisible(state != CardState.LOCKED))
					{
						contentList.add(statusRow("    " + requirement.label, state));
					}
				}
			}
		}
		return true;
	}

	private boolean hasVisibleSlayerContent(SlayerMasterEntry master)
	{
		for (SlayerTaskEntry task : master.tasks)
		{
			if (hasVisibleSlayerCards(task.requirements))
			{
				return true;
			}
		}
		return snapshot.includeSlayerSuperiors
			&& hasVisibleSlayerCards(master.superiors);
	}

	private boolean hasVisibleSlayerCards(List<QuestCatalog.Requirement> requirements)
	{
		for (QuestCatalog.Requirement requirement : requirements)
		{
			if (hasVisibleSlayerCards(requirement.displayCards))
			{
				return true;
			}
		}
		return false;
	}

	private boolean hasVisibleSlayerCards(Iterable<String> cards)
	{
		for (String card : cards)
		{
			if (isSlayerCardVisible(card))
			{
				return true;
			}
		}
		return false;
	}

	private boolean isSlayerCardVisible(String card)
	{
		boolean unlocked = snapshot.usableCards.contains(card.toLowerCase(Locale.ROOT));
		return unlocked ? showUnlockedSlayer.isSelected() : showLockedSlayer.isSelected();
	}

	private boolean hasVisiblePvmRequirement(QuestCatalog.QuestEntry entry)
	{
		for (QuestCatalog.Requirement requirement : entry.requirements)
		{
			if (isPvmEntryVisible(requirement.isSatisfied(snapshot.usableCards)))
			{
				return true;
			}
		}
		return false;
	}

	private boolean isPvmEntryVisible(boolean unlocked)
	{
		return unlocked ? showUnlockedPvm.isSelected() : showLockedPvm.isSelected();
	}

	private JPanel clickableProgressRow(String label, int have, int total,
		boolean expanded, Runnable action)
	{
		return clickableProgressRow(label, have, total, have >= total, expanded, action);
	}

	private JPanel clickableProgressRow(String label, int have, int total,
		boolean complete, boolean expanded, Runnable action)
	{
		JPanel row = compactProgressRow(label, have, total, complete);
		styleCategoryHeader(row, expanded, 4, false);
		makeClickable(row, action);
		return row;
	}

	private JPanel clickableHierarchyCountRow(String label, int have, int total,
		boolean expanded, Runnable action)
	{
		JPanel row = hierarchyCountRow(label, have, total, expanded, 8);
		makeClickable(row, action);
		return row;
	}

	private JPanel clickableSuperiorDropdownRow(boolean expanded, Runnable action)
	{
		JPanel row = contentDropdownRow("Superior Creatures", expanded);
		makeClickable(row, action);
		return row;
	}

	/**
	 * Swing mouse events do not bubble from labels and progress bars to their parent
	 * panel, so bind the same action to every visible part of an expandable row.
	 */
	private static void makeClickable(Component component, Runnable action)
	{
		component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		component.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent event)
			{
				action.run();
			}
		});
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				makeClickable(child, action);
			}
		}
	}

	private static String nestedKey(String parent, String child)
	{
		return parent + "\0" + child;
	}

	private static int satisfiedRequirements(List<QuestCatalog.Requirement> requirements,
		Set<String> owned)
	{
		int count = 0;
		for (QuestCatalog.Requirement requirement : requirements)
		{
			if (requirement.isSatisfied(owned))
			{
				count++;
			}
		}
		return count;
	}

	private void refreshRumours()
	{
		rumoursList.removeAll();

		List<QuestCatalog.QuestEntry> entries = snapshot.data.rumours;
		if (entries.isEmpty())
		{
			rumoursList.add(mutedRow("No rumour data bundled"));
		}
		// Entries are sorted once by the background preparation pass. A name never moves
		// when the owned collection changes.
		int rumourIndex = 0;
		for (QuestCatalog.QuestEntry entry : entries)
		{
			if (rumourIndex++ > 0)
			{
				addSpacedDivider(rumoursList);
			}
			rumoursList.add(checklistRow(entry, snapshot.usableCards, expandedRumours,
				this::refreshRumours));
			if (expandedRumours.contains(entry.name))
			{
				for (QuestCatalog.Requirement requirement : entry.requirements)
				{
					rumoursList.add(requirementRow(requirement, requirementState(requirement)));
				}
				if (entry.requirements.isEmpty())
				{
					rumoursList.add(mutedRow("  No card-backed requirements"));
				}
			}
		}
		rumoursList.revalidate();
		rumoursList.repaint();
	}

	private void refreshRecentUnlocks()
	{
		recentUnlocksList.removeAll();
		if (snapshot == null)
		{
			recentUnlocksList.add(mutedRow("Loading recent unlocks..."));
			recentUnlocksList.revalidate();
			recentUnlocksList.repaint();
			return;
		}
		String query = recentUnlocksSearchBar.getText() == null ? ""
			: recentUnlocksSearchBar.getText().trim().toLowerCase(Locale.ROOT);
		List<RecentUnlocksTracker.Unlock> unlocks = new ArrayList<>(snapshot.recentUnlocks);
		if (showSharedRecent.isSelected())
		{
			unlocks.addAll(snapshot.sharedRecentUnlocks);
			unlocks.sort(Comparator.comparingLong(
				(RecentUnlocksTracker.Unlock unlock) -> unlock.time).reversed()
				.thenComparing(unlock -> unlock.name));
		}
		for (RecentUnlocksTracker.Unlock unlock : unlocks)
		{
			String name = displayCardName(unlock.name);
			if (query.isEmpty() || name.toLowerCase(Locale.ROOT).contains(query))
			{
				recentUnlocksList.add(recentUnlockRow(name, unlock.time, unlock.shared));
			}
		}
		if (recentUnlocksList.getComponentCount() == 0)
		{
			recentUnlocksList.add(mutedRow(query.isEmpty()
				? "No new unlocks recorded yet" : "No recent unlocks match"));
		}
		recentUnlocksList.revalidate();
		recentUnlocksList.repaint();
	}

	private void refreshSharedCards()
	{
		sharedCardsList.removeAll();
		String query = searchText(sharedCardsSearchBar);
		boolean searching = !query.isEmpty();
		List<PanelSharedCardsViewModel.Category> categories = filterSharedCategories(
			snapshot.sharedCards.getCategories(), query);
		for (int index = 0; index < categories.size(); index++)
		{
			PanelSharedCardsViewModel.Category category = categories.get(index);
			if (index > 0)
			{
				addSpacedDivider(sharedCardsList);
			}
			sharedCardsList.add(sharedCategoryRow(category, searching));
			if (!searching && !expandedSharedCategories.contains(category.getName()))
			{
				continue;
			}
			for (String card : category.getItems())
			{
				sharedCardsList.add(statusRow("  " + displayCardName(card),
					CardState.SHARED));
			}
			for (Map.Entry<String, List<String>> entry
				: category.getSubcategories().entrySet())
			{
				String key = importantSubcategoryKey(category.getName(), entry.getKey());
				sharedCardsList.add(sharedSubcategoryRow(category.getName(), entry.getKey(),
					entry.getValue().size(), searching));
				if (searching || expandedSharedSubcategories.contains(key))
				{
					for (String card : entry.getValue())
					{
						sharedCardsList.add(statusRow(
							"    " + displayCardName(card), CardState.SHARED));
					}
				}
			}
		}
		if (categories.isEmpty())
		{
			sharedCardsList.add(mutedRow(searching
				? "No shared cards match"
				: "No shared cards currently available, check your party is correctly synced in TCG Locked Side Panel."));
		}
		sharedCardsList.revalidate();
		sharedCardsList.repaint();
	}

	private JPanel sharedCategoryRow(PanelSharedCardsViewModel.Category category,
		boolean forceExpanded)
	{
		boolean expanded = forceExpanded
			|| expandedSharedCategories.contains(category.getName());
		JPanel row = collapsibleCountRow(category.getName(), category.size(), expanded, 0);
		makeClickable(row, () ->
		{
			if (!expandedSharedCategories.remove(category.getName()))
			{
				expandedSharedCategories.add(category.getName());
			}
			refreshSharedCards();
		});
		return row;
	}

	private JPanel sharedSubcategoryRow(String category, String subcategory, int count,
		boolean forceExpanded)
	{
		String key = importantSubcategoryKey(category, subcategory);
		boolean expanded = forceExpanded || expandedSharedSubcategories.contains(key);
		JPanel row = collapsibleCountRow(subcategory, count, expanded, 2);
		makeClickable(row, () ->
		{
			if (!expandedSharedSubcategories.remove(key))
			{
				expandedSharedSubcategories.add(key);
			}
			refreshSharedCards();
		});
		return row;
	}

	private static JPanel collapsibleCountRow(String label, int count, boolean expanded, int indent)
	{
		JPanel row = row(new BorderLayout(6, 0));
		styleCategoryHeader(row, expanded, indent + 4, indent > 0);
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		JLabel name = new JLabel(label);
		name.setForeground(Color.WHITE);
		row.add(name, BorderLayout.CENTER);
		JLabel total = new JLabel(Integer.toString(count));
		total.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(total, BorderLayout.EAST);
		return row;
	}

	private void configureQuestFilters()
	{
		hideCompletedQuests.setSelected(getSavedBoolean(QUEST_HIDE_COMPLETED_KEY, true));
		boolean legacyCombined = getSavedBoolean(QUEST_HIDE_INCOMPLETABLE_KEY, false);
		requireQuestPoints.setSelected(getSavedBoolean(
			QUEST_REQUIRE_POINTS_KEY, legacyCombined));
		requireSkillLevels.setSelected(getSavedBoolean(
			QUEST_REQUIRE_SKILLS_KEY, legacyCombined));
		requirePrerequisiteQuests.setSelected(getSavedBoolean(
			QUEST_REQUIRE_PREREQUISITES_KEY, legacyCombined));
		requireCards.setSelected(getSavedBoolean(
			QUEST_REQUIRE_CARDS_KEY, legacyCombined));

		questFilters.setOpaque(false);
		questFilters.setAlignmentX(Component.LEFT_ALIGNMENT);
		questFilters.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
		questFilters.setLayout(new BoxLayout(questFilters, BoxLayout.Y_AXIS));
		questRequirementFilters.setOpaque(false);
		questRequirementFilters.setAlignmentX(Component.LEFT_ALIGNMENT);
		questRequirementFilters.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

		for (JCheckBox checkBox : new JCheckBox[]{
			hideCompletedQuests, requireQuestPoints, requireSkillLevels,
			requirePrerequisiteQuests, requireCards})
		{
			checkBox.setOpaque(false);
			checkBox.setForeground(Color.WHITE);
			checkBox.setFocusable(false);
			checkBox.setMargin(new Insets(0, 0, 0, 0));
			checkBox.addActionListener(event -> updateQuestFilters());
		}

		hideCompletedQuests.setAlignmentX(Component.LEFT_ALIGNMENT);
		questFilters.add(hideCompletedQuests);
		questFilters.add(Box.createVerticalStrut(2));
		questPointsFilterLabel.setFont(FontManager.getRunescapeSmallFont());
		questRequirementFilters.add(questFilterControl(requireQuestPoints,
			questPointsFilterLabel,
			"Only show quests whose Quest point requirement is met"));
		questRequirementFilters.add(questFilterControl(requireSkillLevels,
			skillLevelsFilterLabel,
			"Only show quests whose real skill-level requirements are met"));
		questRequirementFilters.add(questFilterControl(requirePrerequisiteQuests,
			prerequisiteQuestsFilterLabel,
			"Only show quests whose prerequisite quests are complete"));
		questRequirementFilters.add(questFilterControl(requireCards,
			cardsFilterLabel,
			"Only show quests whose Bronzeman card requirements are met"));
		applyQuestFilterSprite(skillLevelsFilterLabel, SKILL_LEVELS_FILTER_SPRITE);
		applyQuestFilterSprite(prerequisiteQuestsFilterLabel,
			QUEST_REQUIREMENTS_FILTER_SPRITE);
		applyQuestFilterSprite(cardsFilterLabel, CARDS_FILTER_SPRITE);
		questFilters.add(questRequirementFilters);
	}

	private static JPanel questFilterControl(JCheckBox checkBox, JLabel label,
		String tooltip)
	{
		JPanel control = new JPanel();
		control.setLayout(new BoxLayout(control, BoxLayout.X_AXIS));
		control.setOpaque(false);
		checkBox.setToolTipText(tooltip);
		checkBox.setAlignmentY(Component.CENTER_ALIGNMENT);
		label.setToolTipText(tooltip);
		label.setForeground(Color.WHITE);
		Dimension labelSize = new Dimension(SECTION_ICON_SIZE, 18);
		label.setPreferredSize(labelSize);
		label.setMinimumSize(labelSize);
		label.setMaximumSize(labelSize);
		label.setHorizontalAlignment(JLabel.CENTER);
		label.setVerticalAlignment(JLabel.CENTER);
		label.setAlignmentY(Component.CENTER_ALIGNMENT);
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent event)
			{
				checkBox.doClick();
			}
		});
		control.add(checkBox);
		control.add(Box.createHorizontalStrut(2));
		control.add(label);
		control.add(Box.createHorizontalStrut(6));
		return control;
	}

	private void applyQuestFilterSprite(JLabel label, int spriteId)
	{
		Icon cached = sectionIconCache.get(spriteId);
		if (cached != null)
		{
			label.setText(null);
			label.setIcon(cached);
			return;
		}
		spriteManager.getSpriteAsync(spriteId, 0, sprite ->
			SwingUtilities.invokeLater(() ->
			{
				if (sprite == null || disposed)
				{
					return;
				}
				Icon icon = new ImageIcon(ImageUtil.resizeImage(
					sprite, SECTION_ICON_SIZE, SECTION_ICON_SIZE));
				sectionIconCache.put(spriteId, icon);
				label.setText(null);
				label.setIcon(icon);
				label.revalidate();
				label.repaint();
			}));
	}

	private List<PanelSharedCardsViewModel.Category> filterSharedCategories(
		List<PanelSharedCardsViewModel.Category> categories, String query)
	{
		if (query.isEmpty())
		{
			return categories;
		}
		List<PanelSharedCardsViewModel.Category> filtered = new ArrayList<>();
		for (PanelSharedCardsViewModel.Category category : categories)
		{
			boolean categoryMatches = matchesSearch(category.getName(), query);
			List<String> items = matchingCards(
				category.getItems(), query, categoryMatches);
			Map<String, List<String>> subcategories = new LinkedHashMap<>();
			for (Map.Entry<String, List<String>> entry
				: category.getSubcategories().entrySet())
			{
				boolean headingMatches = categoryMatches
					|| matchesSearch(entry.getKey(), query);
				List<String> matches = matchingCards(entry.getValue(), query, headingMatches);
				if (!matches.isEmpty())
				{
					subcategories.put(entry.getKey(), matches);
				}
			}
			if (!items.isEmpty() || !subcategories.isEmpty())
			{
				filtered.add(new PanelSharedCardsViewModel.Category(
					category.getName(), items, subcategories));
			}
		}
		return filtered;
	}

	private List<String> matchingCards(List<String> cards, String query,
		boolean includeAll)
	{
		List<String> matches = new ArrayList<>();
		for (String card : cards)
		{
			if (includeAll || matchesSearch(displayCardName(card), query))
			{
				matches.add(card);
			}
		}
		return matches;
	}

	private void updateQuestSearch()
	{
		dirtyTabs.add(PanelTab.ACTIVITIES);
		if (snapshot != null && selectedTab == PanelTab.ACTIVITIES)
		{
			refreshQuests();
			dirtyTabs.remove(PanelTab.ACTIVITIES);
		}
	}

	private void updateQuestFilters()
	{
		configManager.setConfiguration(BronzemanTcgConfig.GROUP,
			QUEST_HIDE_COMPLETED_KEY, hideCompletedQuests.isSelected());
		configManager.setConfiguration(BronzemanTcgConfig.GROUP,
			QUEST_REQUIRE_POINTS_KEY, requireQuestPoints.isSelected());
		configManager.setConfiguration(BronzemanTcgConfig.GROUP,
			QUEST_REQUIRE_SKILLS_KEY, requireSkillLevels.isSelected());
		configManager.setConfiguration(BronzemanTcgConfig.GROUP,
			QUEST_REQUIRE_PREREQUISITES_KEY, requirePrerequisiteQuests.isSelected());
		configManager.setConfiguration(BronzemanTcgConfig.GROUP,
			QUEST_REQUIRE_CARDS_KEY, requireCards.isSelected());
		// Preserve downgrade behavior for older builds that only know the combined key.
		configManager.setConfiguration(BronzemanTcgConfig.GROUP,
			QUEST_HIDE_INCOMPLETABLE_KEY, requireQuestPoints.isSelected()
				&& requireSkillLevels.isSelected()
				&& requirePrerequisiteQuests.isSelected()
				&& requireCards.isSelected());
		dirtyTabs.add(PanelTab.ACTIVITIES);
		if (selectedTab == PanelTab.ACTIVITIES)
		{
			renderSelectedTab();
		}
	}

	private void configureVisibilityFilters(JPanel filterPanel,
		JCheckBox showLocked, JCheckBox showUnlocked,
		String lockedKey, String unlockedKey, PanelTab tab)
	{
		showLocked.setSelected(getSavedBoolean(lockedKey, true));
		showUnlocked.setSelected(getSavedBoolean(unlockedKey, true));

		filterPanel.setOpaque(false);
		filterPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		filterPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
		filterPanel.setLayout(new BoxLayout(filterPanel, BoxLayout.Y_AXIS));

		for (JCheckBox checkBox : new JCheckBox[]{showLocked, showUnlocked})
		{
			checkBox.setOpaque(false);
			checkBox.setForeground(Color.WHITE);
			checkBox.setFocusable(false);
			checkBox.setMargin(new Insets(0, 0, 0, 0));
			checkBox.setAlignmentX(Component.LEFT_ALIGNMENT);
			filterPanel.add(checkBox);
			checkBox.addActionListener(event ->
				updateVisibilityFilters(showLocked, showUnlocked,
					lockedKey, unlockedKey, tab));
		}
	}

	private void updateVisibilityFilters(JCheckBox showLocked, JCheckBox showUnlocked,
		String lockedKey, String unlockedKey, PanelTab tab)
	{
		configManager.setConfiguration(BronzemanTcgConfig.GROUP,
			lockedKey, showLocked.isSelected());
		configManager.setConfiguration(BronzemanTcgConfig.GROUP,
			unlockedKey, showUnlocked.isSelected());
		dirtyTabs.add(tab);
		if (selectedTab == tab)
		{
			renderSelectedTab();
		}
	}

	private void configureCollectionFilters()
	{
		showLockedCollection.setSelected(
			getSavedBoolean(COLLECTION_SHOW_LOCKED_KEY, true));
		showUnlockedCollection.setSelected(
			getSavedBoolean(COLLECTION_SHOW_UNLOCKED_KEY, true));
		hideBetaCardProgress.setSelected(
			getSavedProfileBoolean(COLLECTION_HIDE_BETA_PROGRESS_KEY, false));

		collectionFilters.setOpaque(false);
		collectionFilters.setAlignmentX(Component.LEFT_ALIGNMENT);
		collectionFilters.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
		collectionFilters.setLayout(new BoxLayout(collectionFilters, BoxLayout.Y_AXIS));

		JCheckBox[] checkBoxes = {
			showLockedCollection, showUnlockedCollection, hideBetaCardProgress};
		for (JCheckBox checkBox : checkBoxes)
		{
			checkBox.setOpaque(false);
			checkBox.setForeground(Color.WHITE);
			checkBox.setFocusable(false);
			checkBox.setMargin(new Insets(0, 0, 0, 0));
			checkBox.setAlignmentX(Component.LEFT_ALIGNMENT);
			collectionFilters.add(checkBox);
			checkBox.addActionListener(event -> updateCollectionFilters());
		}
	}

	private boolean getSavedBoolean(String key, boolean defaultValue)
	{
		String stored = configManager.getConfiguration(BronzemanTcgConfig.GROUP, key);
		return stored == null ? defaultValue : Boolean.parseBoolean(stored);
	}

	private void updateCollectionFilters()
	{
		configManager.setConfiguration(BronzemanTcgConfig.GROUP,
			COLLECTION_SHOW_LOCKED_KEY, showLockedCollection.isSelected());
		configManager.setConfiguration(BronzemanTcgConfig.GROUP,
			COLLECTION_SHOW_UNLOCKED_KEY, showUnlockedCollection.isSelected());
		setSavedProfileBoolean(COLLECTION_HIDE_BETA_PROGRESS_KEY,
			hideBetaCardProgress.isSelected());

		dirtyTabs.add(PanelTab.COLLECTION);
		requestRefresh();
	}

	private boolean getSavedProfileBoolean(String key, boolean defaultValue)
	{
		try
		{
			String stored = configManager.getRSProfileConfiguration(
				BronzemanTcgConfig.GROUP, key);
			return stored == null ? defaultValue : Boolean.parseBoolean(stored);
		}
		catch (RuntimeException ex)
		{
			log.warn("Unable to read profile-scoped panel preference {}", key, ex);
			return defaultValue;
		}
	}

	private void setSavedProfileBoolean(String key, boolean value)
	{
		try
		{
			configManager.setRSProfileConfiguration(
				BronzemanTcgConfig.GROUP, key, Boolean.toString(value));
		}
		catch (RuntimeException ex)
		{
			log.warn("Unable to save profile-scoped panel preference {}", key, ex);
		}
	}

	private boolean shouldShowCollectionCard(PanelCollectionViewModel.Status status)
	{
		return status == PanelCollectionViewModel.Status.LOCKED
			? showLockedCollection.isSelected() : showUnlockedCollection.isSelected();
	}

	private void refreshCollection()
	{
		collectionList.removeAll();
		if (!snapshot.collection.isCatalogAvailable())
		{
			collectionList.add(mutedRow("v1 catalogue unavailable — Beta fallback active"));
			collectionList.revalidate();
			collectionList.repaint();
			return;
		}
		String query = searchText(collectionSearchBar);
		boolean searching = !query.isEmpty();
		if (!showLockedCollection.isSelected() && !showUnlockedCollection.isSelected())
		{
			collectionList.add(mutedRow("Select a status to display cards"));
			collectionList.revalidate();
			collectionList.repaint();
			return;
		}

		int visibleSections = 0;
		for (PanelCollectionViewModel.Section section : snapshot.collection.getSections())
		{
			boolean sectionMatches = matchesSearch(section.getName(), query);
			Map<PanelCollectionViewModel.Category,
				List<PanelCollectionLayout.CollectionCard>> visibleCategories =
				new LinkedHashMap<>();
			for (PanelCollectionViewModel.Category category : section.getCategories())
			{
				boolean headingMatches = sectionMatches
					|| matchesSearch(category.getName(), query);
				List<PanelCollectionLayout.CollectionCard> cards = visibleCollectionCards(
					category.getCards(), query, headingMatches);
				if (!cards.isEmpty())
				{
					visibleCategories.put(category, cards);
				}
			}
			if (visibleCategories.isEmpty())
			{
				continue;
			}
			if (visibleSections++ > 0)
			{
				addSpacedDivider(collectionList);
			}
			collectionList.add(collectionSectionRow(section, searching));
			if (searching || expandedCollectionSections.contains(section.getId()))
			{
				for (Map.Entry<PanelCollectionViewModel.Category,
					List<PanelCollectionLayout.CollectionCard>> entry
					: visibleCategories.entrySet())
				{
					PanelCollectionViewModel.Category category = entry.getKey();
					collectionList.add(collectionCategoryRow(category, searching));
					if (searching || expandedCollectionCategories.contains(category.getId()))
					{
						for (PanelCollectionLayout.CollectionCard card : entry.getValue())
						{
							collectionList.add(statusRow("    " + card.getCardName(),
								collectionCardState(card)));
						}
					}
				}
			}
		}
		if (snapshot.collection.getSections().isEmpty())
		{
			collectionList.add(mutedRow("No v1 Collection data bundled"));
		}
		else if (visibleSections == 0)
		{
			collectionList.add(mutedRow("No Collection cards match these filters"));
		}
		collectionList.revalidate();
		collectionList.repaint();
	}

	private List<PanelCollectionLayout.CollectionCard> visibleCollectionCards(
		List<PanelCollectionLayout.CollectionCard> cards, String query, boolean includeAll)
	{
		List<PanelCollectionLayout.CollectionCard> visible = new ArrayList<>();
		for (PanelCollectionLayout.CollectionCard card : cards)
		{
			PanelCollectionViewModel.Status status = snapshot.collection.getStatus(card);
			if (shouldShowCollectionCard(status)
				&& (includeAll || matchesSearch(card.getCardName(), query)))
			{
				visible.add(card);
			}
		}
		return visible;
	}

	private JPanel collectionSectionRow(PanelCollectionViewModel.Section section,
		boolean forceExpanded)
	{
		boolean expanded = forceExpanded || expandedCollectionSections.contains(section.getId());
		JPanel row = compactProgressRow(section.getName(),
			countCollectedParents(section.getCards()), section.getCards().size());
		styleCategoryHeader(row, expanded, 4, false);
		makeClickable(row, () ->
		{
			if (!expandedCollectionSections.remove(section.getId()))
			{
				expandedCollectionSections.add(section.getId());
			}
			refreshCollection();
		});
		return row;
	}

	private JPanel collectionCategoryRow(PanelCollectionViewModel.Category category,
		boolean forceExpanded)
	{
		boolean expanded = forceExpanded
			|| expandedCollectionCategories.contains(category.getId());
		JPanel row = hierarchyCountRow(category.getName(),
			countCollectedParents(category.getCards()), category.getCards().size(), expanded, 8);
		makeClickable(row, () ->
		{
			if (!expandedCollectionCategories.remove(category.getId()))
			{
				expandedCollectionCategories.add(category.getId());
			}
			refreshCollection();
		});
		return row;
	}

	private int countCollectedParents(List<PanelCollectionLayout.CollectionCard> cards)
	{
		int count = 0;
		for (PanelCollectionLayout.CollectionCard card : cards)
		{
			if (snapshot.collection.getStatus(card) == PanelCollectionViewModel.Status.OWNED)
			{
				count++;
			}
		}
		return count;
	}

	private CardState collectionCardState(PanelCollectionLayout.CollectionCard card)
	{
		switch (snapshot.collection.getStatus(card))
		{
			case OWNED:
				return CardState.OWNED;
			case SHARED:
				return CardState.SHARED;
			default:
				return CardState.LOCKED;
		}
	}

	private void refreshBetaCollection()
	{
		betaCollectionList.removeAll();
		applyBetaSnapshotControls(betaCollectionSnapshotService.getView());
		String query = searchText(betaCollectionSearchBar);
		boolean searching = !query.isEmpty();
		if (!showLockedBetaCollection.isSelected()
			&& !showUnlockedBetaCollection.isSelected())
		{
			betaCollectionList.add(mutedRow("Select a status to display cards"));
			betaCollectionList.revalidate();
			betaCollectionList.repaint();
			return;
		}
		betaCollectionList.add(mutedRow(String.format("%d/%d beta parents collected",
			snapshot.betaCollection.getOwnedParents(), betaCollectionViewModel.getParentTotal())));
		betaCollectionList.add(betaSnapshotStatusRow(
			snapshot.betaCollection.getSnapshotStatus()));
		Set<String> unmatchedBetaNames = snapshot.betaCollection.getUnmatchedNames();
		if (!unmatchedBetaNames.isEmpty() && showUnlockedBetaCollection.isSelected())
		{
			betaCollectionList.add(mutedRow(unmatchedBetaNames.size()
				+ " imported names outside the catalogue (not in totals)"));
			int shown = 0;
			for (String name : unmatchedBetaNames)
			{
				if (matchesSearch(name, query))
				{
					if (shown++ == 200)
					{
						betaCollectionList.add(mutedRow("Showing 200 matches; refine your search"));
						break;
					}
					JLabel label = mutedRow("");
					label.putClientProperty("html.disable", Boolean.TRUE);
					label.setText(name);
					betaCollectionList.add(label);
				}
			}
		}

		int visibleSections = 0;
		for (PanelBetaCollectionViewModel.Section section : betaCollectionViewModel.getSections())
		{
			boolean sectionMatches = matchesSearch(section.getName(), query);
			Map<PanelBetaCollectionViewModel.Category,
				List<PanelCollectionLayout.BetaCollectionCard>> visibleCategories =
				new LinkedHashMap<>();
			for (PanelBetaCollectionViewModel.Category category : section.getCategories())
			{
				boolean headingMatches = sectionMatches
					|| matchesSearch(category.getName(), query);
				List<PanelCollectionLayout.BetaCollectionCard> cards =
					visibleBetaCollectionParents(category.getCards(), query, headingMatches);
				if (!cards.isEmpty())
				{
					visibleCategories.put(category, cards);
				}
			}
			if (visibleCategories.isEmpty())
			{
				continue;
			}
			if (visibleSections++ > 0)
			{
				addSpacedDivider(betaCollectionList);
			}
			betaCollectionList.add(betaCollectionSectionRow(section, searching));
			if (searching || expandedBetaCollectionSections.contains(section.getId()))
			{
				for (Map.Entry<PanelBetaCollectionViewModel.Category,
					List<PanelCollectionLayout.BetaCollectionCard>> entry
					: visibleCategories.entrySet())
				{
					PanelBetaCollectionViewModel.Category category = entry.getKey();
					betaCollectionList.add(betaCollectionCategoryRow(category, searching));
					if (searching
						|| expandedBetaCollectionCategories.contains(category.getId()))
					{
						for (PanelCollectionLayout.BetaCollectionCard parent : entry.getValue())
						{
							boolean expandable = betaCollectionViewModel
								.hasVariantBreakdown(parent);
							List<PanelCollectionLayout.BetaVariant> variantMatches =
								searching && expandable
								? betaCollectionViewModel.matchingVariants(parent, query)
								: Collections.emptyList();
							boolean forceExpanded = searching && !variantMatches.isEmpty();
							betaCollectionList.add(betaCollectionParentRow(
								parent, forceExpanded, expandable));
							if (expandable && (forceExpanded
								|| expandedBetaCollectionParents.contains(parent.getKey())))
							{
								List<PanelCollectionLayout.BetaVariant> variants = forceExpanded
									? variantMatches : parent.getVariants();
								for (PanelCollectionLayout.BetaVariant variant : variants)
								{
									betaCollectionList.add(statusRow(
										"      " + variant.getName(),
										betaCollectionVariantState(variant)));
								}
							}
						}
					}
				}
			}
		}
		if (betaCollectionViewModel.getSections().isEmpty())
		{
			betaCollectionList.add(mutedRow("No Beta Collection data bundled"));
		}
		else if (visibleSections == 0)
		{
			betaCollectionList.add(mutedRow(
				"No Beta Collection cards match these filters"));
		}
		betaCollectionList.revalidate();
		betaCollectionList.repaint();
	}

	private List<PanelCollectionLayout.BetaCollectionCard> visibleBetaCollectionParents(
		List<PanelCollectionLayout.BetaCollectionCard> cards, String query, boolean includeAll)
	{
		List<PanelCollectionLayout.BetaCollectionCard> visible = new ArrayList<>();
		for (PanelCollectionLayout.BetaCollectionCard card : cards)
		{
			PanelCollectionViewModel.Status status =
				snapshot.betaCollection.getParentStatus(card);
			boolean matches = includeAll
				|| betaCollectionViewModel.parentNameMatches(card, query)
				|| !betaCollectionViewModel.matchingVariants(card, query).isEmpty();
			if (shouldShowBetaCollectionParent(status) && matches)
			{
				visible.add(card);
			}
		}
		return visible;
	}

	private boolean shouldShowBetaCollectionParent(PanelCollectionViewModel.Status status)
	{
		return status == PanelCollectionViewModel.Status.LOCKED
			? showLockedBetaCollection.isSelected() : showUnlockedBetaCollection.isSelected();
	}

	private JPanel betaCollectionSectionRow(PanelBetaCollectionViewModel.Section section,
		boolean forceExpanded)
	{
		boolean expanded = forceExpanded
			|| expandedBetaCollectionSections.contains(section.getId());
		JPanel row = compactProgressRow(section.getName(),
			countOwnedBetaParents(section.getCards()), section.getCards().size());
		styleCategoryHeader(row, expanded, 4, false);
		makeClickable(row, () ->
		{
			if (!expandedBetaCollectionSections.remove(section.getId()))
			{
				expandedBetaCollectionSections.add(section.getId());
			}
			refreshBetaCollection();
		});
		return row;
	}

	private JPanel betaCollectionCategoryRow(PanelBetaCollectionViewModel.Category category,
		boolean forceExpanded)
	{
		boolean expanded = forceExpanded
			|| expandedBetaCollectionCategories.contains(category.getId());
		JPanel row = hierarchyCountRow(category.getName(),
			countOwnedBetaParents(category.getCards()), category.getCards().size(), expanded, 8);
		makeClickable(row, () ->
		{
			if (!expandedBetaCollectionCategories.remove(category.getId()))
			{
				expandedBetaCollectionCategories.add(category.getId());
			}
			refreshBetaCollection();
		});
		return row;
	}

	private JPanel betaCollectionParentRow(PanelCollectionLayout.BetaCollectionCard parent,
		boolean forceExpanded, boolean expandable)
	{
		if (!expandable)
		{
			return statusRow("    " + betaCollectionViewModel.getDisplayName(parent),
				betaCollectionParentState(parent));
		}
		boolean expanded = forceExpanded
			|| expandedBetaCollectionParents.contains(parent.getKey());
		JPanel row = statusRow(betaCollectionViewModel.getDisplayName(parent),
			betaCollectionParentState(parent));
		styleCategoryHeader(row, expanded, 12, true);
		makeClickable(row, () ->
		{
			if (!expandedBetaCollectionParents.remove(parent.getKey()))
			{
				expandedBetaCollectionParents.add(parent.getKey());
			}
			refreshBetaCollection();
		});
		return row;
	}

	private int countOwnedBetaParents(
		List<PanelCollectionLayout.BetaCollectionCard> cards)
	{
		int count = 0;
		for (PanelCollectionLayout.BetaCollectionCard card : cards)
		{
			if (snapshot.betaCollection.getParentStatus(card)
				== PanelCollectionViewModel.Status.OWNED)
			{
				count++;
			}
		}
		return count;
	}

	private CardState betaCollectionParentState(
		PanelCollectionLayout.BetaCollectionCard parent)
	{
		return betaCollectionCardState(snapshot.betaCollection.getParentStatus(parent));
	}

	private CardState betaCollectionVariantState(PanelCollectionLayout.BetaVariant variant)
	{
		return betaCollectionCardState(snapshot.betaCollection.getVariantStatus(variant));
	}

	private static CardState betaCollectionCardState(PanelCollectionViewModel.Status status)
	{
		switch (status)
		{
			case OWNED:
				return CardState.OWNED;
			case SHARED:
				return CardState.SHARED;
			default:
				return CardState.LOCKED;
		}
	}

	private static JLabel betaSnapshotStatusRow(BetaCollectionSnapshotService.Status status)
	{
		String text;
		String detail;
		switch (status)
		{
			case PROVISIONAL:
				text = "Beta snapshot: preparing (not frozen)";
				detail = "";
				break;
			case FROZEN_CAPTURED:
				text = "Beta snapshot: saved from pre-v1";
				detail = "";
				break;
			case FROZEN_INFERRED:
				text = "Beta snapshot: estimated from v1";
				detail = "";
				break;
			case INCOMPATIBLE:
				text = "Beta snapshot: saved data incompatible";
				detail = "";
				break;
			case IMPORTED:
				text = "Beta snapshot: imported from save";
				detail = "";
				break;
			case CLEARED:
				text = "Beta snapshot: cleared by you";
				detail = "";
				break;
			default:
				text = "Beta snapshot: waiting for collection";
				detail = "";
		}
		JLabel row = mutedRow(text);
		row.setToolTipText(detail);
		return row;
	}

	private static String importantSubcategoryKey(String category, String subcategory)
	{
		return category + "\0" + subcategory;
	}

	private List<QuestCatalog.QuestEntry> buildPvmEntries(
		List<PanelPresentationCatalog.Checklist> sourceEntries, boolean v1Capable)
	{
		List<QuestCatalog.QuestEntry> entries = new ArrayList<>();
		for (PanelPresentationCatalog.Checklist source : sourceEntries)
		{
			List<QuestCatalog.Requirement> requirements = new ArrayList<>();
			for (String card : source.cards)
			{
				QuestCatalog.Requirement requirement =
					presentationRequirement(card, v1Capable);
				if (requirement != null)
				{
					requirements.add(requirement);
				}
			}
			entries.add(new QuestCatalog.QuestEntry(source.name, false, requirements,
				source.notes));
		}
		return entries;
	}

	private QuestCatalog.Requirement presentationRequirement(String card,
		boolean v1Capable)
	{
		return v1Capable ? pvmRequirement(card) : new QuestCatalog.Requirement(
			card, Collections.singletonList(card));
	}

	private QuestCatalog.Requirement pvmRequirement(String canonicalParent)
	{
		PanelCollectionLayout.CollectionCard card = collectionViewModel
			.findCard(CardEntityKind.NPC, canonicalParent).orElse(null);
		if (card == null)
		{
			return null;
		}
		return new QuestCatalog.Requirement(card.getCardName(),
			new ArrayList<>(card.getAcceptedNamesLowerCase()));
	}

	private List<SlayerMasterEntry> buildSlayerEntries(
		Map<String, PanelPresentationCatalog.Rule> sourceRules)
	{
		Map<String, Map<String, SlayerTaskBuilder>> tasksByMaster =
			new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		Map<String, Map<String, QuestCatalog.Requirement>> superiorsByMaster =
			new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (Map.Entry<String, PanelPresentationCatalog.Rule> entry :
			sourceRules.entrySet())
		{
			String masterName = slayerPanelMasterName(entry.getKey());
			boolean locationSpecific =
				"konar quo maten".equalsIgnoreCase(entry.getKey());
			Map<String, SlayerTaskBuilder> tasks = tasksByMaster.computeIfAbsent(
				masterName, ignored -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
			Map<String, QuestCatalog.Requirement> superiors =
				superiorsByMaster.computeIfAbsent(masterName,
					ignored -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
			for (PanelPresentationCatalog.Group group : entry.getValue().groups)
			{
				QuestCatalog.Requirement requirement =
					new QuestCatalog.Requirement(group.label, group.displayCards);
				if ("monsters".equals(group.role))
				{
					String taskLabel = group.label;
					String requirementLabel = group.label;
					if (locationSpecific)
					{
						int separator = group.label.indexOf(" \u2014 ");
						if (separator >= 0)
						{
							taskLabel = group.label.substring(0, separator);
							requirementLabel = group.label.substring(separator + 3);
						}
					}
					final String finalTaskLabel = taskLabel;
					SlayerTaskBuilder task = tasks.computeIfAbsent(finalTaskLabel,
						ignored -> new SlayerTaskBuilder(
							finalTaskLabel, locationSpecific));
					task.add(new QuestCatalog.Requirement(
						requirementLabel, group.displayCards));
				}
				else if ("superiors".equals(group.role))
				{
					mergeSlayerRequirement(superiors, requirement);
				}
			}
		}

		List<SlayerMasterEntry> entries = new ArrayList<>();
		for (Map.Entry<String, Map<String, SlayerTaskBuilder>> entry :
			tasksByMaster.entrySet())
		{
			Map<String, QuestCatalog.Requirement> superiors =
				superiorsByMaster.getOrDefault(entry.getKey(), Collections.emptyMap());
			List<SlayerTaskEntry> tasks = new ArrayList<>();
			for (SlayerTaskBuilder task : entry.getValue().values())
			{
				tasks.add(task.build());
			}
			entries.add(new SlayerMasterEntry(entry.getKey(),
				tasks,
				new ArrayList<>(superiors.values())));
		}
		return Collections.unmodifiableList(entries);
	}

	private static String slayerPanelMasterName(String masterName)
	{
		switch (masterName.toLowerCase(Locale.ROOT))
		{
			case "achtryn":
			case "mazchna":
				return "Mazchna/Achtryn";
			case "aya":
			case "turael":
				return "Turael/Aya";
			case "nieve":
			case "steve":
				return "Nieve/Steve";
			case "duradel":
			case "kuradal":
				return "Duradel/Kuradal";
			default:
				return display(masterName);
		}
	}

	private static void mergeSlayerRequirement(
		Map<String, QuestCatalog.Requirement> requirements,
		QuestCatalog.Requirement incoming)
	{
		QuestCatalog.Requirement existing = requirements.get(incoming.label);
		if (existing == null)
		{
			requirements.put(incoming.label, incoming);
			return;
		}

		Map<String, String> cards = new LinkedHashMap<>();
		for (String card : existing.displayCards)
		{
			cards.put(card.toLowerCase(Locale.ROOT), card);
		}
		for (String card : incoming.displayCards)
		{
			cards.putIfAbsent(card.toLowerCase(Locale.ROOT), card);
		}
		requirements.put(existing.label,
			new QuestCatalog.Requirement(existing.label, new ArrayList<>(cards.values())));
	}

	private static List<QuestCatalog.Requirement> buildGlobalSuperiors(
		List<SlayerMasterEntry> masters)
	{
		Map<String, String> cards = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (SlayerMasterEntry master : masters)
		{
			for (QuestCatalog.Requirement superior : master.superiors)
			{
				for (String card : superior.displayCards)
				{
					cards.putIfAbsent(card, card);
				}
			}
		}
		List<QuestCatalog.Requirement> result = new ArrayList<>();
		for (String card : cards.values())
		{
			result.add(new QuestCatalog.Requirement(card, Collections.singletonList(card)));
		}
		return Collections.unmodifiableList(result);
	}

	/** Adapts rumour-master rules into the checklist shape shared with quests. */
	private List<QuestCatalog.QuestEntry> buildRumourMasterEntries(
		Map<String, PanelPresentationCatalog.Rule> sourceRules)
	{
		List<QuestCatalog.QuestEntry> entries = new ArrayList<>();
		for (Map.Entry<String, PanelPresentationCatalog.Rule> e
			: sourceRules.entrySet())
		{
			List<QuestCatalog.Requirement> reqs = new ArrayList<>();
			for (PanelPresentationCatalog.Group group : e.getValue().groups)
			{
				if (!group.displayCards.isEmpty())
				{
					reqs.add(new QuestCatalog.Requirement(group.label, group.displayCards));
				}
			}
			entries.add(new QuestCatalog.QuestEntry(display(e.getKey()), false, reqs, ""));
		}
		return entries;
	}

	private JPanel checklistRow(QuestCatalog.QuestEntry entry, Set<String> owned,
		Set<String> expandedNames, Runnable refresh)
	{
		int have = entry.satisfiedCount(owned);
		int total = entry.requirements.size();
		boolean expanded = expandedNames.contains(entry.name);
		String label = entry.name + (entry.miniquest ? " (mini)" : "");
		JPanel row = compactProgressRow(label, have, total);
		styleCategoryHeader(row, expanded, 4, false);
		if (!entry.notes.isEmpty())
		{
			row.setToolTipText(entry.notes);
		}
		else if (total == 0)
		{
			row.setToolTipText("No card-backed requirements - always completable");
		}
		makeClickable(row, () ->
		{
			if (!expandedNames.remove(entry.name))
			{
				expandedNames.add(entry.name);
			}
			refresh.run();
		});
		return row;
	}

	private JPanel requirementRow(QuestCatalog.Requirement requirement, CardState state)
	{
		String alternatives = requirement.displayCards.size() > 1
			? ": " + String.join(" / ", requirement.displayCards)
			: "";
		JPanel row = statusRow("  " + requirement.label + alternatives, state);
		styleListContent(row);
		return row;
	}

	// ------------------------------------------------------------------ search

	private static String searchText(IconTextField field)
	{
		return field.getText() == null ? ""
			: field.getText().trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * True only while a group is actually sharing something. Without this the "Shared"
	 * label would be dead weight for solo players, who are the majority.
	 */
	private boolean sharingActive()
	{
		return snapshot != null && !snapshot.shared.isEmpty();
	}

	/** State of one card name, already lower-cased. */
	private CardState cardState(String cardLowerCase)
	{
		if (snapshot == null)
		{
			return CardState.LOCKED;
		}
		if (snapshot.owned.contains(cardLowerCase))
		{
			return CardState.OWNED;
		}
		return sharingActive() && snapshot.shared.contains(cardLowerCase)
			? CardState.SHARED : CardState.LOCKED;
	}

	/**
	 * Quest rows resolve through the route-aware overload, so a Shield of Arrav branch
	 * the player has not chosen is not mistaken for a group-supplied card.
	 */
	private CardState questRequirementState(QuestCatalog.Requirement requirement)
	{
		if (snapshot == null)
		{
			return CardState.LOCKED;
		}
		if (requirement.isSatisfied(snapshot.owned, snapshot.questRoute))
		{
			return CardState.OWNED;
		}
		return sharingActive()
			&& requirement.isSatisfied(snapshot.usableCards, snapshot.questRoute)
			? CardState.SHARED : CardState.LOCKED;
	}

	/**
	 * A requirement is SHARED when the player cannot satisfy it alone but the group can.
	 * Evaluated against the requirement's own any-of/all-of logic rather than card names,
	 * so a group-supplied card inside a nested group still resolves correctly.
	 */
	private CardState requirementState(QuestCatalog.Requirement requirement)
	{
		if (snapshot == null)
		{
			return CardState.LOCKED;
		}
		if (requirement.isSatisfied(snapshot.owned))
		{
			return CardState.OWNED;
		}
		return sharingActive() && requirement.isSatisfied(snapshot.usableCards)
			? CardState.SHARED : CardState.LOCKED;
	}

	private static boolean matchesSearch(String value, String query)
	{
		return query.isEmpty() || value.toLowerCase(Locale.ROOT).contains(query);
	}

	private void refreshSearch()
	{
		searchResults.removeAll();
		String query = searchBar.getText() == null ? "" : searchBar.getText().trim().toLowerCase(Locale.ROOT);
		if (query.length() >= 2)
		{
			if (snapshot == null)
			{
				searchResults.add(mutedRow("Loading card index..."));
				searchResults.revalidate();
				searchResults.repaint();
				return;
			}

			int shown = 0;
			int matches = 0;
			for (PanelCollectionViewModel.SearchCard entry :
				snapshot.collection.getSearchCards())
			{
				if (!entry.getSearchName().contains(query))
				{
					continue;
				}
				matches++;
				if (++shown <= MAX_SEARCH_RESULTS)
				{
					CardState state = collectionCardState(entry.getCard());
					searchResults.add(statusRow(entry.getDisplayName(), state));
				}
			}
			if (matches > MAX_SEARCH_RESULTS)
			{
				searchResults.add(mutedRow("...and " + (matches - MAX_SEARCH_RESULTS) + " more"));
			}
			if (matches == 0)
			{
				searchResults.add(mutedRow("No v1 card matches"));
			}
		}
		searchResults.revalidate();
		searchResults.repaint();
	}

	// ------------------------------------------------------------------ progress

	private void refreshProgress(PanelSnapshot current)
	{
		progressList.removeAll();
		if (current.v1Presentation && !current.collection.isCatalogAvailable())
		{
			progressList.add(mutedRow("v1 catalogue unavailable — Beta fallback active"));
			progressList.revalidate();
			progressList.repaint();
			return;
		}

		progressList.add(progressRow("Items collected",
			current.collection.getOwnedItems(), current.collection.getItemTotal()));
		progressList.add(progressRow("NPCs collected",
			current.collection.getOwnedNpcs(), current.collection.getNpcTotal()));

		progressList.revalidate();
		progressList.repaint();
	}

	private static int countOwned(List<String> cards, Set<String> owned)
	{
		int count = 0;
		for (String card : cards)
		{
			if (owned.contains(card.toLowerCase(Locale.ROOT)))
			{
				count++;
			}
		}
		return count;
	}

	private static List<QuestCatalog.QuestEntry> sortedEntries(
		List<QuestCatalog.QuestEntry> entries)
	{
		List<QuestCatalog.QuestEntry> sorted = new ArrayList<>(entries);
		sorted.sort(Comparator.comparing(entry -> questSortName(entry.name),
			String.CASE_INSENSITIVE_ORDER));
		return Collections.unmodifiableList(sorted);
	}

	private static String questSortName(String name)
	{
		if (name.regionMatches(true, 0, "The ", 0, 4))
		{
			return name.substring(4);
		}
		return name.regionMatches(true, 0, "A ", 0, 2) ? name.substring(2) : name;
	}

	private static boolean sameUnlocks(List<RecentUnlocksTracker.Unlock> first,
		List<RecentUnlocksTracker.Unlock> second)
	{
		if (first.size() != second.size())
		{
			return false;
		}
		for (int i = 0; i < first.size(); i++)
		{
			RecentUnlocksTracker.Unlock a = first.get(i);
			RecentUnlocksTracker.Unlock b = second.get(i);
			if (a.time != b.time || a.shared != b.shared || !a.name.equals(b.name))
			{
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ widgets

	/**
	 * Row container for a BoxLayout column: height tracks the live preferred height, so
	 * rows never stretch or jitter when a list is rebuilt. Fixing this at construction
	 * time (the old setMaximumSize call) froze a height computed before layout settled.
	 */
	private static JPanel row(LayoutManager layout)
	{
		JPanel panel = new JPanel(layout)
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	private static JPanel sectionBody()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	private static JLabel progressHeader()
	{
		JLabel label = new JLabel("Overall Progress");
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/**
	 * A card the player owns, one a group member is sharing, or one nobody has. "Shared"
	 * is only ever reported for cards the player does not own themselves - the snapshot's
	 * shared set already has the owned cards removed - so pulling a card yourself flips
	 * the row from "Shared" to a tick with no extra bookkeeping.
	 */
	enum CardState
	{
		OWNED,
		SHARED,
		LOCKED
	}

	private static JPanel statusRow(String name, CardState state)
	{
		JPanel row = row(new BorderLayout(6, 0));
		styleListContent(row);

		JLabel nameLabel = new JLabel(name);
		nameLabel.setForeground(Color.WHITE);
		row.add(nameLabel, BorderLayout.CENTER);

		JLabel status;
		if (state == CardState.SHARED)
		{
			// Same treatment as the Recent Unlocks tab, so "Shared" reads consistently.
			status = new JLabel("Shared");
			status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			status.setFont(status.getFont().deriveFont(10f));
		}
		else if (MARK_GLYPHS_SUPPORTED)
		{
			boolean unlocked = state == CardState.OWNED;
			status = new JLabel(unlocked ? TICK_GLYPH : CROSS_GLYPH);
			status.setForeground(unlocked ? UNLOCKED : LOCKED);
			status.setFont(status.getFont().deriveFont(Font.BOLD));
		}
		else
		{
			status = new JLabel(state == CardState.OWNED ? TICK_ICON : CROSS_ICON);
		}
		row.add(status, BorderLayout.EAST);

		return row;
	}

	private static JPanel recentUnlockRow(String name, long time, boolean shared)
	{
		JPanel row = row(new BorderLayout(6, 0));
		styleListContent(row);

		JLabel nameLabel = new JLabel(name);
		nameLabel.setForeground(Color.WHITE);
		row.add(nameLabel, BorderLayout.CENTER);

		JLabel status;
		if (shared)
		{
			status = new JLabel("Shared");
			status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			status.setFont(status.getFont().deriveFont(10f));
		}
		else if (MARK_GLYPHS_SUPPORTED)
		{
			status = new JLabel(TICK_GLYPH);
			status.setForeground(UNLOCKED);
			status.setFont(status.getFont().deriveFont(Font.BOLD));
		}
		else
		{
			status = new JLabel(TICK_ICON);
		}
		row.add(status, BorderLayout.EAST);

		JLabel when = new JLabel((shared ? "Shared " : "Unlocked ")
			+ UNLOCK_TIME_FORMAT.format(Instant.ofEpochMilli(time)));
		when.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		when.setFont(when.getFont().deriveFont(11f));
		row.add(when, BorderLayout.SOUTH);
		return row;
	}

	private static JLabel mutedRow(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private static JPanel listDivider()
	{
		JPanel divider = new JPanel();
		divider.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		divider.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 2));
		divider.setMinimumSize(new Dimension(0, 2));
		divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
		divider.setAlignmentX(Component.LEFT_ALIGNMENT);
		return divider;
	}

	private static void addSpacedDivider(JPanel container)
	{
		container.add(Box.createVerticalStrut(2));
		container.add(listDivider());
		container.add(Box.createVerticalStrut(3));
	}

	private static JPanel progressRow(String label, int done, int total)
	{
		return progressRow(label, done, total, done >= total);
	}

	private static JPanel progressRow(String label, int done, int total, boolean complete)
	{
		JPanel row = row(new BorderLayout(0, 2));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));

		JLabel text = new JLabel(label + "  " + done + "/" + total);
		text.setForeground(Color.WHITE);
		row.add(text, BorderLayout.NORTH);

		JProgressBar bar = new JProgressBar(0, Math.max(total, 1));
		bar.setValue(total == 0 && complete ? 1 : done);
		bar.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 6));
		bar.setForeground(complete ? UNLOCKED : ColorScheme.BRAND_ORANGE);
		// The unfilled track must not match either row state, or the bar disappears until
		// expanded: collapsed rows are DARKER_GRAY (#1E1E1E), the same colour it used to
		// be. MEDIUM_GRAY (#4D4D4D) reads against both that and the expanded brown.
		bar.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
		row.add(bar, BorderLayout.SOUTH);
		return row;
	}

	/**
	 * Compact hierarchy row for expandable tab content: the title gets the flexible
	 * space, while a small progress bar and an accurate x/y count stay aligned right.
	 */
	private static JPanel compactProgressRow(String label, int done, int total)
	{
		return compactProgressRow(label, done, total, done >= total);
	}

	private static JPanel compactProgressRow(String label, int done, int total,
		boolean complete)
	{
		JPanel row = row(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 4));

		JLabel text = new JLabel(label);
		text.setForeground(Color.WHITE);
		text.setToolTipText(label.trim());
		row.add(text, BorderLayout.CENTER);

		JPanel progress = new JPanel(new BorderLayout(6, 0));
		progress.setOpaque(false);

		JProgressBar bar = new JProgressBar(0, Math.max(total, 1));
		bar.setValue(total == 0 && complete ? 1 : done);
		bar.setPreferredSize(new Dimension(48, 6));
		bar.setForeground(complete ? UNLOCKED : ColorScheme.BRAND_ORANGE);
		// The unfilled track must not match either row state, or the bar disappears until
		// expanded: collapsed rows are DARKER_GRAY (#1E1E1E), the same colour it used to
		// be. MEDIUM_GRAY (#4D4D4D) reads against both that and the expanded brown.
		bar.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
		progress.add(bar, BorderLayout.CENTER);

		JLabel count = new JLabel(done + "/" + total);
		count.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		count.setHorizontalAlignment(JLabel.RIGHT);
		count.setPreferredSize(new Dimension(38, 16));
		progress.add(count, BorderLayout.EAST);

		row.add(progress, BorderLayout.EAST);
		return row;
	}

	private static String display(String lowerName)
	{
		if (lowerName.isEmpty())
		{
			return lowerName;
		}
		return Character.toUpperCase(lowerName.charAt(0)) + lowerName.substring(1);
	}

	private String displayCardName(String cardName)
	{
		return sharedCardsViewModel.displayCardName(cardName);
	}

	private enum PanelTab
	{
		ACTIVITIES,
		SLAYER_PVM,
		RECENT,
		COLLECTION,
		BETA_COLLECTION,
		SHARED,
		SETTINGS
	}

	/**
	 * CardLayout keeps every tab attached, but its default preferred size is the largest
	 * card. The sidebar should instead follow the visible card so shorter tabs do not
	 * inherit a long hidden tab's scroll height.
	 */
	private static class SelectedCardPanel extends JPanel implements Scrollable
	{
		private final CardLayout cardLayout = new CardLayout();
		private final Map<PanelTab, Component> cards = new EnumMap<>(PanelTab.class);
		private Component selected;

		private SelectedCardPanel()
		{
			setLayout(cardLayout);
		}

		private void addCard(PanelTab key, Component component)
		{
			cards.put(key, component);
			add(component, key.name());
			if (selected == null)
			{
				selected = component;
			}
		}

		private void showCard(PanelTab key)
		{
			selected = cards.get(key);
			cardLayout.show(this, key.name());
			revalidate();
			repaint();
		}

		@Override
		public Dimension getPreferredSize()
		{
			if (selected == null)
			{
				return super.getPreferredSize();
			}
			Dimension size = selected.getPreferredSize();
			Insets insets = getInsets();
			return new Dimension(size.width + insets.left + insets.right,
				size.height + insets.top + insets.bottom);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect,
			int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect,
			int orientation, int direction)
		{
			return Math.max(16, visibleRect.height - 16);
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	/** Immutable catalog-derived data built once on the background executor. */
	private static class PreparedData
	{
		private static final long PRE_V1_REVISION = -1L;
		private final long identityRevision;
		private final List<QuestCatalog.QuestEntry> quests;
		private final List<QuestCatalog.QuestEntry> miniquests;
		private final List<QuestCatalog.QuestEntry> contents;
		private final List<SlayerMasterEntry> slayer;
		private final List<QuestCatalog.Requirement> allSuperiors;
		private final List<QuestCatalog.QuestEntry> rumours;

		private PreparedData(long identityRevision,
			List<QuestCatalog.QuestEntry> quests,
			List<QuestCatalog.QuestEntry> miniquests,
			List<QuestCatalog.QuestEntry> contents,
			List<SlayerMasterEntry> slayer,
			List<QuestCatalog.Requirement> allSuperiors,
			List<QuestCatalog.QuestEntry> rumours)
		{
			this.identityRevision = identityRevision;
			this.quests = quests;
			this.miniquests = miniquests;
			this.contents = contents;
			this.slayer = slayer;
			this.allSuperiors = allSuperiors;
			this.rumours = rumours;
		}
	}

	private static class SlayerMasterEntry
	{
		private final String name;
		private final List<SlayerTaskEntry> tasks;
		private final List<QuestCatalog.Requirement> superiors;

		private SlayerMasterEntry(String name, List<SlayerTaskEntry> tasks,
			List<QuestCatalog.Requirement> superiors)
		{
			this.name = name;
			this.tasks = Collections.unmodifiableList(new ArrayList<>(tasks));
			this.superiors = Collections.unmodifiableList(new ArrayList<>(superiors));
		}

		private int satisfiedCount(Set<String> owned, boolean includeSuperiors)
		{
			int count = 0;
			for (SlayerTaskEntry task : tasks)
			{
				count += satisfiedRequirements(task.requirements, owned);
			}
			return count + (includeSuperiors
				? satisfiedRequirements(superiors, owned) : 0);
		}

		private int requirementCount(boolean includeSuperiors)
		{
			int count = 0;
			for (SlayerTaskEntry task : tasks)
			{
				count += task.requirements.size();
			}
			return count + (includeSuperiors ? superiors.size() : 0);
		}
	}

	private static class SlayerTaskEntry
	{
		private final String label;
		private final boolean locationSpecific;
		private final List<QuestCatalog.Requirement> requirements;

		private SlayerTaskEntry(String label, boolean locationSpecific,
			List<QuestCatalog.Requirement> requirements)
		{
			this.label = label;
			this.locationSpecific = locationSpecific;
			this.requirements = Collections.unmodifiableList(
				new ArrayList<>(requirements));
		}
	}

	private static class SlayerTaskBuilder
	{
		private final String label;
		private final boolean locationSpecific;
		private final Map<String, QuestCatalog.Requirement> requirements =
			new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

		private SlayerTaskBuilder(String label, boolean locationSpecific)
		{
			this.label = label;
			this.locationSpecific = locationSpecific;
		}

		private void add(QuestCatalog.Requirement requirement)
		{
			mergeSlayerRequirement(requirements, requirement);
		}

		private SlayerTaskEntry build()
		{
			return new SlayerTaskEntry(label, locationSpecific,
				new ArrayList<>(requirements.values()));
		}
	}

	/** Immutable player-state snapshot passed from the executor to Swing. */
	private static class PanelSnapshot
	{
		private final PreparedData data;
		private final Set<String> owned;
		private final Set<String> shared;
		private final Set<String> usableCards;
		private final List<RecentUnlocksTracker.Unlock> recentUnlocks;
		private final List<RecentUnlocksTracker.Unlock> sharedRecentUnlocks;
		private final boolean includeSlayerSuperiors;
		private final Set<String> completedQuests;
		private final QuestCatalog.RouteSelection questRoute;
		/** Real levels by Skill.ordinal(), or null while logged out. */
		private final int[] realSkillLevels;
		private final int questPoints;
		private final PanelCollectionViewModel.State collection;
		private final PanelBetaCollectionViewModel.State betaCollection;
		private final PanelSharedCardsViewModel.State sharedCards;
		private final boolean v1Presentation;
		private final PanelNavigationModel.State navigation;
		private final boolean hideBetaProgress;

		private PanelSnapshot(PreparedData data, Set<String> owned, Set<String> shared,
			List<RecentUnlocksTracker.Unlock> recentUnlocks,
			List<RecentUnlocksTracker.Unlock> sharedRecentUnlocks,
			Set<String> usableCards, boolean includeSlayerSuperiors,
			Set<String> completedQuests, QuestCatalog.RouteSelection questRoute,
			int[] realSkillLevels, int questPoints,
			PanelCollectionViewModel.State collection,
			PanelBetaCollectionViewModel.State betaCollection,
			PanelSharedCardsViewModel.State sharedCards,
			boolean v1Presentation, PanelNavigationModel.State navigation,
			boolean hideBetaProgress)
		{
			this.realSkillLevels = realSkillLevels;
			this.questPoints = questPoints;
			this.data = data;
			this.owned = owned;
			this.shared = shared;
			this.usableCards = usableCards;
			this.recentUnlocks = recentUnlocks;
			this.sharedRecentUnlocks = sharedRecentUnlocks;
			this.includeSlayerSuperiors = includeSlayerSuperiors;
			this.completedQuests = completedQuests;
			this.questRoute = questRoute;
			this.collection = collection;
			this.betaCollection = betaCollection;
			this.sharedCards = sharedCards;
			this.v1Presentation = v1Presentation;
			this.navigation = navigation;
			this.hideBetaProgress = hideBetaProgress;
		}
	}

}
