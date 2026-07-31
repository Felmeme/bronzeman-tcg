package com.bronzemantcg;

import static com.bronzemantcg.PanelComponents.addSpacedDivider;
import static com.bronzemantcg.PanelComponents.hierarchyProgressRow;
import static com.bronzemantcg.PanelComponents.listDivider;
import static com.bronzemantcg.PanelComponents.makeClickable;
import static com.bronzemantcg.PanelComponents.mutedRow;
import static com.bronzemantcg.PanelComponents.progressRow;
import static com.bronzemantcg.PanelComponents.recentUnlockRow;
import static com.bronzemantcg.PanelComponents.row;
import static com.bronzemantcg.PanelComponents.sectionBody;
import static com.bronzemantcg.PanelComponents.sectionHeader;
import static com.bronzemantcg.PanelComponents.statusRow;
import static com.bronzemantcg.PanelComponents.styleHierarchyRow;
import static com.bronzemantcg.SidePanelModels.mergeSlayerRequirement;
import static com.bronzemantcg.SidePanelModels.satisfiedRequirements;
import static com.bronzemantcg.SidePanelModels.PanelSnapshot;
import static com.bronzemantcg.SidePanelModels.PreparedData;
import static com.bronzemantcg.SidePanelModels.SearchEntry;
import static com.bronzemantcg.SidePanelModels.SharedCategory;
import static com.bronzemantcg.SidePanelModels.SlayerMasterEntry;
import static com.bronzemantcg.SidePanelModels.SlayerTaskBuilder;
import static com.bronzemantcg.SidePanelModels.SlayerTaskEntry;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Deque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
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
class BronzemanTcgPanel extends PluginPanel
{
	private static final int MAX_SEARCH_RESULTS = 20;
	private static final String IMPORTANT_SHOW_LOCKED_KEY = "importantShowLocked";
	private static final String IMPORTANT_SHOW_UNLOCKED_KEY = "importantShowUnlocked";
	private static final String QUEST_HIDE_COMPLETED_KEY = "questHideCompleted";
	private static final String QUEST_HIDE_INCOMPLETABLE_KEY = "questHideIncompletable";
	private static final String SLAYER_SHOW_LOCKED_KEY = "slayerShowLocked";
	private static final String SLAYER_SHOW_UNLOCKED_KEY = "slayerShowUnlocked";
	private static final String PVM_SHOW_LOCKED_KEY = "pvmShowLocked";
	private static final String PVM_SHOW_UNLOCKED_KEY = "pvmShowUnlocked";
	private static final String RECENT_SHOW_SHARED_KEY = "recentShowShared";

	private final TrackedMonsterCatalog monsterCatalog;
	private final TrackedItemCatalog itemCatalog;
	private final ResourceNodeCatalog nodeCatalog;
	private final QuestCatalog questCatalog;
	private final ContentCatalog contentCatalog;
	private final MonsterAreaCatalog monsterAreaCatalog;
	private final TcgCollectionReader collectionReader;
	private final SharedUnlockStore sharedUnlockStore;
	private final RecentUnlocksTracker recentUnlocksTracker;
	private final ImportantUnlocksCatalog importantUnlocksCatalog;
	private final CardKnowledgeCatalog cardKnowledgeCatalog;
	private final BronzemanTcgConfig config;
	private final ConfigManager configManager;
	private final ScheduledExecutorService executor;
	private final AtomicBoolean refreshRunning = new AtomicBoolean();
	private final AtomicBoolean refreshAgain = new AtomicBoolean();
	private final EnumSet<PanelTab> dirtyTabs = EnumSet.allOf(PanelTab.class);
	private volatile PreparedData preparedData;
	private volatile boolean disposed;
	private PanelSnapshot snapshot;
	private PanelTab selectedTab = PanelTab.QUESTS;
	private PanelTab lastContentTab = PanelTab.QUESTS;

	private final IconTextField searchBar = new IconTextField();
	private final JPanel searchResults = sectionBody();
	private final JPanel progressList = sectionBody();

	// Every view remains attached and CardLayout switches visibility, avoiding a full
	// Swing rebuild when the bronze navigation buttons change selection.
	private final PanelCardDeck<PanelTab> tabDisplay =
		new PanelCardDeck<>(PanelTab.class);
	private final JScrollPane contentScroll;
	private final JPanel navigationGrid = sectionBody();
	private final Map<PanelTab, JButton> navigationButtons = new EnumMap<>(PanelTab.class);
	private boolean sharedCardsVisible;

	private final JPanel questPanel = sectionBody();
	private final JPanel questFilters = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
	private final JCheckBox hideCompletedQuests = new JCheckBox("Hide completed");
	private final JCheckBox hideIncompletableQuests = new JCheckBox("Hide incompletable");
	private final JPanel questList = sectionBody();
	private final Set<String> expandedQuests = new HashSet<>();
	private final Set<String> expandedQuestCategories =
		new HashSet<>(Collections.singleton("Quests"));
	private final Set<String> expandedQuestSections = new HashSet<>();
	private final Set<String> expandedQuestRequirements = new HashSet<>();
	private volatile Set<String> completedQuestNames = Collections.emptySet();
	private volatile QuestCatalog.RouteSelection questRoute =
		QuestCatalog.RouteSelection.UNKNOWN;

	private final JPanel slayerPanel = sectionBody();
	private final JPanel slayerFilters = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
	private final JCheckBox showLockedSlayer = new JCheckBox("Show locked");
	private final JCheckBox showUnlockedSlayer = new JCheckBox("Show unlocked");
	private final JPanel slayerList = sectionBody();
	private final Set<String> expandedSlayer = new HashSet<>();
	private final Set<String> expandedSlayerTasks = new HashSet<>();
	private final Set<String> expandedSlayerSuperiorGroups = new HashSet<>();
	private boolean expandedGlobalSuperiors;

	private final JPanel pvmPanel = sectionBody();
	private final JPanel pvmFilters = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
	private final JCheckBox showLockedPvm = new JCheckBox("Show locked");
	private final JCheckBox showUnlockedPvm = new JCheckBox("Show unlocked");
	private final JPanel contentList = sectionBody();
	private final Set<String> expandedPvmSections = new HashSet<>();
	private final Set<String> expandedPvmGroups = new HashSet<>();

	private final JPanel rumoursList = sectionBody();
	private final Set<String> expandedRumours = new HashSet<>();

	private final JPanel recentUnlocksPanel = sectionBody();
	private final IconTextField recentUnlocksSearchBar = new IconTextField();
	private final JCheckBox showSharedRecent = new JCheckBox("Show shared");
	private final JPanel recentUnlocksList = sectionBody();

	private final JPanel sharedCardsList = sectionBody();
	private final Set<String> expandedSharedCategories = new HashSet<>();
	private final Set<String> expandedSharedSubcategories = new HashSet<>();
	private final SidePanelSettings sidePanelSettings;

	private final JPanel importantUnlocksPanel = sectionBody();
	private final JPanel importantUnlocksFilters = new JPanel();

	private final JCheckBox showLockedImportant = new JCheckBox("Show locked");

	private final JCheckBox showUnlockedImportant = new JCheckBox("Show unlocked");

	private final JPanel importantUnlocksList = sectionBody();
	private final Set<String> expandedImportantCategories = new HashSet<>();
	private final Set<String> expandedImportantSubcategories = new HashSet<>();
	private final JPanel collectionView = new JPanel(new CardLayout());
	private final CardDetailPanel cardDetailPanel;
	private int collectionListScrollPosition;
	private int collectionReturnScrollPosition;
	private PanelTab collectionReturnTab = PanelTab.IMPORTANT;
	private final Deque<String> collectionCardHistory = new ArrayDeque<>();

	BronzemanTcgPanel(
			TrackedMonsterCatalog monsterCatalog,
			TrackedItemCatalog itemCatalog,
			ResourceNodeCatalog nodeCatalog,
			QuestCatalog questCatalog,
			ContentCatalog contentCatalog,
			MonsterAreaCatalog monsterAreaCatalog,
			TcgCollectionReader collectionReader,
			SharedUnlockStore sharedUnlockStore,
			RecentUnlocksTracker recentUnlocksTracker,
			ImportantUnlocksCatalog importantUnlocksCatalog,
			CardKnowledgeCatalog cardKnowledgeCatalog,
			ItemManager itemManager,
			BronzemanTcgConfig config,
			ConfigManager configManager,
			ScheduledExecutorService executor)
	{
		this.monsterCatalog = monsterCatalog;
		this.itemCatalog = itemCatalog;
		this.nodeCatalog = nodeCatalog;
		this.questCatalog = questCatalog;
		this.contentCatalog = contentCatalog;
		this.monsterAreaCatalog = monsterAreaCatalog;
		this.collectionReader = collectionReader;
		this.sharedUnlockStore = sharedUnlockStore;
		this.recentUnlocksTracker = recentUnlocksTracker;
		this.importantUnlocksCatalog = importantUnlocksCatalog;
		this.cardKnowledgeCatalog = cardKnowledgeCatalog;
		this.config = config;
		this.configManager = configManager;
		this.executor = executor;
		this.cardDetailPanel = new CardDetailPanel(itemManager, questCatalog,
			this::displayCardName, this::openCollectionCard);
		this.sidePanelSettings = new SidePanelSettings(config, configManager);

		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

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

		configureQuestFilters();
		questPanel.add(questFilters);
		questPanel.add(Box.createVerticalStrut(4));
		questPanel.add(questList);

		configureVisibilityFilters(slayerFilters, showLockedSlayer, showUnlockedSlayer,
			SLAYER_SHOW_LOCKED_KEY, SLAYER_SHOW_UNLOCKED_KEY, PanelTab.SLAYER);
		slayerPanel.add(slayerFilters);
		slayerPanel.add(Box.createVerticalStrut(4));
		slayerPanel.add(slayerList);

		configureVisibilityFilters(pvmFilters, showLockedPvm, showUnlockedPvm,
			PVM_SHOW_LOCKED_KEY, PVM_SHOW_UNLOCKED_KEY, PanelTab.PVM);
		pvmPanel.add(pvmFilters);
		pvmPanel.add(Box.createVerticalStrut(4));
		pvmPanel.add(contentList);

		configureImportantUnlockFilters();
		importantUnlocksPanel.add(importantUnlocksFilters);
		importantUnlocksPanel.add(Box.createVerticalStrut(4));
		importantUnlocksPanel.add(importantUnlocksList);
		collectionView.setBackground(ColorScheme.DARK_GRAY_COLOR);
		collectionView.add(importantUnlocksPanel, "list");
		collectionView.add(cardDetailPanel.component(), "detail");

		progressList.add(mutedRow("Loading collection..."));
		questList.add(mutedRow("Loading quests..."));
		addView(questPanel, PanelTab.QUESTS);
		addView(slayerPanel, PanelTab.SLAYER);
		addView(pvmPanel, PanelTab.PVM);
		addView(rumoursList, PanelTab.RUMOURS);
		addView(recentUnlocksPanel, PanelTab.RECENT);
		addView(collectionView, PanelTab.IMPORTANT);
		addView(sharedCardsList, PanelTab.SHARED);
		addView(sidePanelSettings.component(), PanelTab.SETTINGS);

		JPanel header = sectionBody();
		header.add(createPlaceholderBanner());
		header.add(Box.createVerticalStrut(8));
		header.add(searchBar);
		header.add(Box.createVerticalStrut(4));
		header.add(searchResults);
		header.add(Box.createVerticalStrut(6));
		header.add(navigationGrid);
		rebuildNavigationGrid();
		add(header, BorderLayout.NORTH);

		contentScroll = new JScrollPane(tabDisplay,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
			JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		contentScroll.setBorder(null);
		contentScroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		contentScroll.getVerticalScrollBar().setUnitIncrement(16);
		contentScroll.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 320));
		add(contentScroll, BorderLayout.CENTER);

		JPanel footer = sectionBody();
		footer.add(sectionHeader("Overall Progress"));
		footer.add(progressList);
		add(footer, BorderLayout.SOUTH);
		selectTab(PanelTab.QUESTS);
	}

	private void addView(JPanel content, PanelTab panelTab)
	{
		content.setAlignmentX(Component.LEFT_ALIGNMENT);
		tabDisplay.addCard(panelTab, content);
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
			BronzemanTcgPanel.class, "/panel_icon.png")
			.getScaledInstance(24, 36, Image.SCALE_SMOOTH);
		JPanel identity = row(new BorderLayout(8, 0));
		identity.setOpaque(false);
		identity.add(new JLabel(new ImageIcon(helmet)), BorderLayout.WEST);

		JLabel title = new JLabel("Bronzeman TCG");
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
		identity.add(title, BorderLayout.CENTER);
		makeClickable(identity, () -> selectTab(lastContentTab));
		banner.add(identity, BorderLayout.CENTER);

		JButton settings = new JButton("\u2699");
		settings.setToolTipText("Settings");
		settings.setFocusable(false);
		settings.setForeground(Color.WHITE);
		settings.setBackground(ColorScheme.DARK_GRAY_COLOR);
		settings.setBorder(BorderFactory.createLineBorder(bronze));
		settings.setPreferredSize(new Dimension(32, 32));
		settings.addActionListener(event -> selectTab(
			selectedTab == PanelTab.SETTINGS ? lastContentTab : PanelTab.SETTINGS));
		banner.add(settings, BorderLayout.EAST);
		return banner;
	}

	private void rebuildNavigationGrid()
	{
		navigationGrid.removeAll();
		navigationButtons.clear();
		addNavigationPair("Quests", PanelTab.QUESTS, "Slayer", PanelTab.SLAYER);
		addNavigationPair("PvM", PanelTab.PVM, "Rumours", PanelTab.RUMOURS);
		addNavigationPair("Recent", PanelTab.RECENT, "Collection", PanelTab.IMPORTANT);
		if (sharedCardsVisible)
		{
			JPanel sharedRow = row(new BorderLayout());
			sharedRow.add(navigationButton("Shared Cards", PanelTab.SHARED),
				BorderLayout.CENTER);
			navigationGrid.add(sharedRow);
			navigationGrid.add(Box.createVerticalStrut(6));
		}
		updateNavigationSelection();
		navigationGrid.revalidate();
		navigationGrid.repaint();
	}

	private void addNavigationPair(String leftLabel, PanelTab leftTab,
		String rightLabel, PanelTab rightTab)
	{
		JPanel pair = row(new GridLayout(1, 2, 6, 0));
		pair.add(navigationButton(leftLabel, leftTab));
		pair.add(navigationButton(rightLabel, rightTab));
		navigationGrid.add(pair);
		navigationGrid.add(Box.createVerticalStrut(6));
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
		button.addActionListener(event -> selectTab(tab));
		button.addMouseListener(new MouseAdapter()
		{
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

	private void selectTab(PanelTab tab)
	{
		if (selectedTab == PanelTab.IMPORTANT && tab != PanelTab.IMPORTANT
			&& !collectionCardHistory.isEmpty())
		{
			collectionCardHistory.clear();
			((CardLayout) collectionView.getLayout()).show(collectionView, "list");
		}
		if (tab != PanelTab.SETTINGS)
		{
			lastContentTab = tab;
		}
		selectedTab = tab;
		tabDisplay.showCard(tab);
		updateNavigationSelection();
		SwingUtilities.invokeLater(this::renderSelectedTab);
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

	private void updateSharedTabVisibility(boolean visible)
	{
		if (sharedCardsVisible == visible)
		{
			return;
		}
		sharedCardsVisible = visible;
		if (!visible && lastContentTab == PanelTab.SHARED)
		{
			lastContentTab = PanelTab.QUESTS;
		}
		if (!visible && selectedTab == PanelTab.SHARED)
		{
			selectTab(PanelTab.QUESTS);
		}
		rebuildNavigationGrid();
	}

	/**
	 * Queue one background snapshot. Calls arriving while one is in flight collapse into
	 * one follow-up, so game ticks and PluginMessage pushes cannot flood either thread.
	 */
	void requestRefresh()
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
	void updateQuestState(Set<String> completed, QuestCatalog.RouteSelection route)
	{
		completedQuestNames = completed == null
			? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(completed));
		questRoute = route == null ? QuestCatalog.RouteSelection.UNKNOWN : route;
	}

	/** Stop queued work from touching a panel that has been removed from the toolbar. */
	void dispose()
	{
		disposed = true;
		refreshAgain.set(false);
	}

	private PanelSnapshot buildSnapshot()
	{
		PreparedData data = preparedData;
		if (data == null)
		{
			data = prepareStaticData();
			preparedData = data;
		}

		Set<String> owned = Collections.unmodifiableSet(
			new HashSet<>(collectionReader.getOwnedCardNamesLowerCase()));
		Set<String> shared = new HashSet<>(sharedUnlockStore.getSharedCardNamesLowerCase());
		shared.removeAll(owned);
		Set<String> visibleShared = config.acceptSharedUnlocks()
			? Collections.unmodifiableSet(shared) : Collections.emptySet();
		boolean includeSlayerSuperiors = config.restrictSlayerSuperiors();
		Set<String> completed = completedQuestNames;
		QuestCatalog.RouteSelection route = questRoute;

		return new PanelSnapshot(data, owned, visibleShared, recentUnlocksTracker.getRecent(),
			recentUnlocksTracker.getSharedRecent(),
			includeSlayerSuperiors, completed, route,
			countUnlocked(monsterCatalog.getEntityToCards(), owned),
			countUnlocked(itemCatalog.getEntityToCards(), owned));
	}

	private PreparedData prepareStaticData()
	{
		List<QuestCatalog.QuestEntry> quests = sortedEntries(questCatalog.getQuests());
		List<QuestCatalog.QuestEntry> miniquests = sortedEntries(questCatalog.getMiniquests());
		List<QuestCatalog.QuestEntry> contents = sortedEntries(contentCatalog.getContents());
		List<QuestCatalog.QuestEntry> areas = buildAreaEntries();
		List<SlayerMasterEntry> slayer = buildSlayerEntries();
		List<QuestCatalog.Requirement> allSuperiors = buildGlobalSuperiors(slayer);
		List<QuestCatalog.QuestEntry> rumours =
			sortedEntries(buildMasterEntries("hunter-rumours"));

		List<SearchEntry> searchEntries = new ArrayList<>();
		for (Map.Entry<String, Set<String>> entry :
			new TreeMap<>(monsterCatalog.getEntityToCards()).entrySet())
		{
			String npcName = entry.getKey() + " (npc)";
			searchEntries.add(new SearchEntry(npcName, display(npcName), entry.getValue()));
		}
		for (Map.Entry<String, Set<String>> entry :
			new TreeMap<>(itemCatalog.getEntityToCards()).entrySet())
		{
			searchEntries.add(new SearchEntry(entry.getKey(), display(entry.getKey()), entry.getValue()));
		}

		return new PreparedData(quests, miniquests, contents, areas, slayer, allSuperiors,
			rumours, searchEntries);
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
		boolean recentChanged = first || !sameUnlocks(previous.recentUnlocks, next.recentUnlocks)
			|| !sameUnlocks(previous.sharedRecentUnlocks, next.sharedRecentUnlocks);
		boolean slayerChanged = first
			|| previous.includeSlayerSuperiors != next.includeSlayerSuperiors;
		boolean questStateChanged = first
			|| !previous.completedQuests.equals(next.completedQuests)
			|| previous.questRoute != next.questRoute;
		snapshot = next;
		// Party-sharing controls whether this view exists. The Recent Unlocks
		// "Show shared" preference only filters that tab and must not affect this one.
		updateSharedTabVisibility(config.acceptSharedUnlocks());

		if (ownedChanged)
		{
			dirtyTabs.addAll(EnumSet.allOf(PanelTab.class));
			refreshProgress(next);
			refreshSearch();
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
			dirtyTabs.add(PanelTab.SLAYER);
		}
		if (questStateChanged)
		{
			dirtyTabs.add(PanelTab.QUESTS);
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
			case QUESTS:
				refreshQuests();
				break;
			case SLAYER:
				refreshSlayer();
				break;
			case PVM:
				refreshContent();
				break;
			case RUMOURS:
				refreshRumours();
				break;
			case RECENT:
				refreshRecentUnlocks();
				break;
			case IMPORTANT:
				refreshImportantUnlocks();
				break;
			case SHARED:
				refreshSharedCards();
				break;
			case SETTINGS:
				sidePanelSettings.refresh();
				break;
			default:
				break;
		}
	}

	// ------------------------------------------------------------------ collapsible checklists

	private void refreshQuests()
	{
		questList.removeAll();
		List<QuestCatalog.QuestEntry> quests = visibleQuests(snapshot.data.quests);
		List<QuestCatalog.QuestEntry> miniquests = visibleQuests(snapshot.data.miniquests);

		addQuestCategory("Quests", quests, snapshot.data.quests.isEmpty());
		questList.add(Box.createVerticalStrut(5));
		addQuestCategory("Miniquests", miniquests, snapshot.data.miniquests.isEmpty());
		questList.revalidate();
		questList.repaint();
	}

	private List<QuestCatalog.QuestEntry> visibleQuests(
		List<QuestCatalog.QuestEntry> source)
	{
		List<QuestCatalog.QuestEntry> visible = new ArrayList<>();
		for (QuestCatalog.QuestEntry quest : source)
		{
			boolean completed = isQuestCompleted(quest.name);
			boolean completable = quest.satisfiedCount(snapshot.owned, snapshot.questRoute)
				== quest.requirements.size();
			if ((!hideCompletedQuests.isSelected() || !completed)
				&& (!hideIncompletableQuests.isSelected() || completable))
			{
				visible.add(quest);
			}
		}
		return visible;
	}

	private boolean isQuestCompleted(String name)
	{
		String key = name.toLowerCase(Locale.ROOT);
		if (snapshot.completedQuests.contains(key))
		{
			return true;
		}
		// The bundled catalogue disambiguates this miniquest, while RuneLite's
		// Quest enum exposes the shorter display name.
		return key.endsWith(" (miniquest)") && snapshot.completedQuests.contains(
			key.substring(0, key.length() - " (miniquest)".length()));
	}

	private void addQuestCategory(String label, List<QuestCatalog.QuestEntry> entries,
		boolean dataEmpty)
	{
		int completable = 0;
		for (QuestCatalog.QuestEntry entry : entries)
		{
			if (entry.satisfiedCount(snapshot.owned, snapshot.questRoute)
				== entry.requirements.size())
			{
				completable++;
			}
		}
		boolean expanded = expandedQuestCategories.contains(label);
		questList.add(clickableProgressRow(label, completable, entries.size(), expanded,
			false, () ->
			{
				if (!expandedQuestCategories.remove(label))
				{
					expandedQuestCategories.add(label);
				}
				refreshQuests();
			}));
		if (!expanded)
		{
			return;
		}
		if (entries.isEmpty())
		{
			questList.add(mutedRow(dataEmpty
				? "  No data bundled" : "  No entries match these filters"));
			return;
		}
		int entryIndex = 0;
		for (QuestCatalog.QuestEntry entry : entries)
		{
			if (entryIndex++ > 0)
			{
				questList.add(Box.createVerticalStrut(5));
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
		int have = entry.satisfiedCount(snapshot.owned, snapshot.questRoute);
		int total = entry.requirements.size();
		boolean expanded = expandedQuests.contains(entry.name);
		JPanel row = clickableProgressRow(entry.name, have, total, have >= total,
			expanded, true, () ->
			{
				if (!expandedQuests.remove(entry.name))
				{
					expandedQuests.add(entry.name);
				}
				refreshQuests();
			});
		if (!entry.notes.isEmpty())
		{
			row.setToolTipText(entry.notes);
		}
		return row;
	}

	private void renderQuestSections(QuestCatalog.QuestEntry entry)
	{
		if (entry.sections.isEmpty())
		{
			questList.add(mutedRow("    No card-backed requirements"));
			return;
		}
		for (int sectionIndex = 0; sectionIndex < entry.sections.size(); sectionIndex++)
		{
			QuestCatalog.Section section = entry.sections.get(sectionIndex);
			String sectionLabel = section.label.isEmpty() ? "Requirements" : section.label;
			String key = entry.name + "\0section\0" + sectionIndex;
			boolean expanded = expandedQuestSections.contains(key);
			int have = section.satisfiedCount(snapshot.owned, snapshot.questRoute);
			int total = section.requirements.size();
			questList.add(clickableProgressRow(sectionLabel, have, total, have >= total,
				expanded, true, () ->
				{
					if (!expandedQuestSections.remove(key))
					{
						expandedQuestSections.add(key);
					}
					refreshQuests();
				}));
			if (!expanded)
			{
				continue;
			}
			if (section.requirements.isEmpty())
			{
				questList.add(mutedRow("      No card-backed requirements"));
			}
			for (int requirementIndex = 0;
				requirementIndex < section.requirements.size(); requirementIndex++)
			{
				renderQuestRequirement(section.requirements.get(requirementIndex),
					key + "\0requirement\0" + requirementIndex, 0);
			}
		}
		if (!entry.notes.isEmpty())
		{
			JLabel notes = wrappedDetailText(entry.notes);
			notes.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			questList.add(notes);
		}
	}

	private void renderQuestRequirement(QuestCatalog.Requirement requirement,
		String key, int depth)
	{
		boolean expandable = !requirement.children.isEmpty()
			|| requirement.displayCards.size() > 1;
		String label = requirement.label;
		if (!requirement.selectorValue.isEmpty()
			&& requirement.selectorValue.equals(snapshot.questRoute.name()))
		{
			label += " (your route)";
		}
		if (!expandable)
		{
			boolean satisfied = requirement.isSatisfied(snapshot.owned, snapshot.questRoute);
			JPanel row = statusRow("      " + label, satisfied, null);
			if (requirement.displayCards.size() == 1)
			{
				String card = requirement.displayCards.get(0);
				makeClickable(row, () -> openCollectionCard(card));
				row.setToolTipText("View " + displayCardName(card));
			}
			questList.add(row);
			return;
		}

		boolean expanded = expandedQuestRequirements.contains(key);
		int have = requirement.displaySatisfied(snapshot.owned, snapshot.questRoute);
		int total = requirement.displayTotal(snapshot.questRoute);
		questList.add(clickableProgressRow(label, have, total, have >= total,
			expanded, true, () ->
			{
				if (!expandedQuestRequirements.remove(key))
				{
					expandedQuestRequirements.add(key);
				}
				refreshQuests();
			}));
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
				questList.add(linkedStatusRow("        " + displayCardName(card), card,
					snapshot.owned.contains(card.toLowerCase(Locale.ROOT))));
			}
			return;
		}
		if (!requirement.children.isEmpty())
		{
			for (int index = 0; index < requirement.children.size(); index++)
			{
				renderQuestRequirement(requirement.children.get(index),
					key + "\0child\0" + index, depth + 1);
			}
		}
		else
		{
			for (String card : requirement.displayCards)
			{
				questList.add(linkedStatusRow("        " + displayCardName(card), card,
					snapshot.owned.contains(card.toLowerCase(Locale.ROOT))));
			}
		}
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
		boolean visible = addPvmSection("Instanced Content/Raids", snapshot.data.contents);
		visible |= addPvmSection("Monsters by Area", snapshot.data.areas);
		if (snapshot.data.contents.isEmpty() && snapshot.data.areas.isEmpty())
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
		int ready = 0;
		for (SlayerMasterEntry master : snapshot.data.slayer)
		{
			if (master.satisfiedCount(snapshot.owned, snapshot.includeSlayerSuperiors)
				== master.requirementCount(snapshot.includeSlayerSuperiors))
			{
				ready++;
			}
		}
		slayerList.add(mutedRow(String.format("%d/%d masters ready",
			ready, snapshot.data.slayer.size())));

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
					slayerList.add(Box.createVerticalStrut(5));
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

		int have = satisfiedRequirements(superiors, snapshot.owned);
		slayerList.add(clickableProgressRow("Superior Creatures",
			have, superiors.size(), expandedGlobalSuperiors, false, () ->
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
					slayerList.add(requirementRow(superior,
						superior.isSatisfied(snapshot.owned), "  "));
				}
			}
		}
		return true;
	}

	private void addSlayerMaster(SlayerMasterEntry master)
	{
		boolean expanded = expandedSlayer.contains(master.name);
		int have = master.satisfiedCount(snapshot.owned, snapshot.includeSlayerSuperiors);
		int total = master.requirementCount(snapshot.includeSlayerSuperiors);
		slayerList.add(clickableProgressRow(master.name, have, total,
			expanded, false, () ->
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
				int locationsOwned = satisfiedRequirements(task.requirements, snapshot.owned);
				slayerList.add(clickableProgressRow(
					task.label, locationsOwned, task.requirements.size(),
					taskExpanded, true,
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
				int variantsOwned = countOwned(requirement.displayCards, snapshot.owned);
				slayerList.add(clickableProgressRow(
					task.label, variantsOwned, requirement.displayCards.size(),
					variantsOwned > 0, taskExpanded, true, () ->
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
					slayerList.add(requirementRow(requirement,
						requirement.isSatisfied(snapshot.owned), "    "));
				}
				for (String card : requirement.displayCards)
				{
					if (isSlayerCardVisible(card))
					{
						String indent = task.locationSpecific ? "      " : "    ";
						slayerList.add(linkedStatusRow(indent + displayCardName(card),
							card, snapshot.owned.contains(card.toLowerCase(Locale.ROOT))));
					}
				}
			}
		}

		if (snapshot.includeSlayerSuperiors
			&& hasVisibleSlayerCards(master.superiors))
		{
			String key = master.name;
			boolean superiorExpanded = expandedSlayerSuperiorGroups.contains(key);
			int superiorHave = satisfiedRequirements(master.superiors, snapshot.owned);
			slayerList.add(clickableProgressRow(
				"Superior Creatures", superiorHave, master.superiors.size(),
				superiorExpanded, true, () ->
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
						slayerList.add(requirementRow(superior,
							superior.isSatisfied(snapshot.owned), "    "));
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
		if (contentList.getComponentCount() > 0)
		{
			contentList.add(Box.createVerticalStrut(5));
		}

		boolean expanded = expandedPvmSections.contains(sectionName);
		int ready = 0;
		for (QuestCatalog.QuestEntry entry : entries)
		{
			if (entry.satisfiedCount(snapshot.owned) == entry.requirements.size())
			{
				ready++;
			}
		}
		contentList.add(clickableProgressRow(sectionName, ready, entries.size(),
			expanded, false, () ->
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

		for (QuestCatalog.QuestEntry entry : entries)
		{
			if (!hasVisiblePvmRequirement(entry))
			{
				continue;
			}
			String key = nestedKey(sectionName, entry.name);
			boolean groupExpanded = expandedPvmGroups.contains(key);
			contentList.add(clickableProgressRow(entry.name,
				entry.satisfiedCount(snapshot.owned), entry.requirements.size(),
				groupExpanded, true, () ->
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
					boolean unlocked = requirement.isSatisfied(snapshot.owned);
					if (isPvmEntryVisible(unlocked))
					{
						contentList.add(requirementRow(requirement, unlocked, "    "));
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
		boolean unlocked = snapshot.owned.contains(card.toLowerCase(Locale.ROOT));
		return unlocked ? showUnlockedSlayer.isSelected() : showLockedSlayer.isSelected();
	}

	private boolean hasVisiblePvmRequirement(QuestCatalog.QuestEntry entry)
	{
		for (QuestCatalog.Requirement requirement : entry.requirements)
		{
			if (isPvmEntryVisible(requirement.isSatisfied(snapshot.owned)))
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
		boolean expanded, boolean nested, Runnable action)
	{
		return clickableProgressRow(label, have, total, have >= total,
			expanded, nested, action);
	}

	private JPanel clickableProgressRow(String label, int have, int total,
		boolean complete, boolean expanded, boolean nested, Runnable action)
	{
		JPanel row = hierarchyProgressRow(label, have, total, complete, expanded, nested);
		makeClickable(row, action);
		return row;
	}

	private static String nestedKey(String parent, String child)
	{
		return parent + "\0" + child;
	}

	private void refreshRumours()
	{
		refreshChecklist(rumoursList, "masters ready",
			snapshot.data.rumours, snapshot.owned, expandedRumours,
			this::refreshRumours, "No rumour data bundled", true);
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
				JPanel unlockRow = recentUnlockRow(name, unlock.time, unlock.shared);
				makeClickable(unlockRow, () -> openCollectionCard(unlock.name));
				recentUnlocksList.add(unlockRow);
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
		List<SharedCategory> categories = buildSharedCategories(snapshot.shared);
		for (int index = 0; index < categories.size(); index++)
		{
			SharedCategory category = categories.get(index);
			if (index > 0)
			{
				sharedCardsList.add(Box.createVerticalStrut(5));
			}
			sharedCardsList.add(sharedCategoryRow(category));
			if (!expandedSharedCategories.contains(category.name))
			{
				continue;
			}
			for (String card : category.items)
			{
				sharedCardsList.add(collectionCardRow(
					"  " + displayCardName(card), card, true));
			}
			for (Map.Entry<String, List<String>> entry : category.subcategories.entrySet())
			{
				String key = importantSubcategoryKey(category.name, entry.getKey());
				sharedCardsList.add(sharedSubcategoryRow(category.name, entry.getKey(),
					entry.getValue().size()));
				if (expandedSharedSubcategories.contains(key))
				{
					for (String card : entry.getValue())
					{
						sharedCardsList.add(collectionCardRow(
							"    " + displayCardName(card), card, true));
					}
				}
			}
		}
		if (categories.isEmpty())
		{
			sharedCardsList.add(mutedRow("No shared cards currently available,"
					+ "<br> check your party is correctly synced "
					+ "<br> in TCG Locked Side Panel."));
		}
		sharedCardsList.revalidate();
		sharedCardsList.repaint();
	}

	private List<SharedCategory> buildSharedCategories(Set<String> shared)
	{
		Set<String> assigned = new HashSet<>();
		List<SharedCategory> result = new ArrayList<>();
		for (ImportantUnlocksCatalog.Category source : importantUnlocksCatalog.getCategories())
		{
			List<String> items = sharedMatches(source.items, shared, assigned);
			Map<String, List<String>> subcategories = new LinkedHashMap<>();
			for (ImportantUnlocksCatalog.Subcategory subcategory : source.subcategories)
			{
				List<String> matches = sharedMatches(subcategory.items, shared, assigned);
				if (!matches.isEmpty())
				{
					subcategories.put(subcategory.name, matches);
				}
			}
			if (!items.isEmpty() || !subcategories.isEmpty())
			{
				result.add(new SharedCategory(source.name, items, subcategories));
			}
		}

		List<String> monsters = new ArrayList<>();
		List<String> otherItems = new ArrayList<>();
		for (String card : shared)
		{
			if (assigned.contains(card))
			{
				continue;
			}
			if (monsterCatalog.findDisplayCardName(card) != null)
			{
				monsters.add(card);
			}
			else
			{
				otherItems.add(card);
			}
		}
		sortCardNames(monsters);
		sortCardNames(otherItems);
		if (!monsters.isEmpty())
		{
			result.add(new SharedCategory("Monsters", monsters, Collections.emptyMap()));
		}
		if (!otherItems.isEmpty())
		{
			result.add(new SharedCategory("Other Items", otherItems, Collections.emptyMap()));
		}
		return result;
	}

	private List<String> sharedMatches(List<String> candidates, Set<String> shared,
		Set<String> assigned)
	{
		List<String> matches = new ArrayList<>();
		for (String candidate : candidates)
		{
			String normalized = candidate.toLowerCase(Locale.ROOT);
			if (shared.contains(normalized) && assigned.add(normalized))
			{
				matches.add(candidate);
			}
		}
		return matches;
	}

	private void sortCardNames(List<String> cards)
	{
		cards.sort(Comparator.comparing(this::displayCardName, String.CASE_INSENSITIVE_ORDER));
	}

	private JPanel sharedCategoryRow(SharedCategory category)
	{
		boolean expanded = expandedSharedCategories.contains(category.name);
		JPanel row = hierarchyCountRow(
			category.name, category.size(), expanded, false);
		makeClickable(row, () ->
		{
			if (!expandedSharedCategories.remove(category.name))
			{
				expandedSharedCategories.add(category.name);
			}
			refreshSharedCards();
		});
		return row;
	}

	private JPanel sharedSubcategoryRow(String category, String subcategory, int count)
	{
		String key = importantSubcategoryKey(category, subcategory);
		boolean expanded = expandedSharedSubcategories.contains(key);
		JPanel row = hierarchyCountRow(subcategory, count, expanded, true);
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

	private static JPanel hierarchyCountRow(String label, int count,
		boolean expanded, boolean nested)
	{
		JPanel row = row(new BorderLayout(6, 0));
		styleHierarchyRow(row, expanded, nested);
		JLabel name = new JLabel(label);
		name.setForeground(Color.WHITE);
		row.add(name, BorderLayout.CENTER);
		JLabel total = new JLabel(Integer.toString(count));
		total.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		if (nested)
		{
			total.setFont(total.getFont().deriveFont(11f));
		}
		row.add(total, BorderLayout.EAST);
		return row;
	}

	private void configureQuestFilters()
	{
		hideCompletedQuests.setSelected(getSavedBoolean(QUEST_HIDE_COMPLETED_KEY, true));
		hideIncompletableQuests.setSelected(
			getSavedBoolean(QUEST_HIDE_INCOMPLETABLE_KEY, false));

		questFilters.setOpaque(false);
		questFilters.setAlignmentX(Component.LEFT_ALIGNMENT);
		questFilters.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
		questFilters.setLayout(new BoxLayout(questFilters, BoxLayout.Y_AXIS));

		for (JCheckBox checkBox : new JCheckBox[]{
			hideCompletedQuests, hideIncompletableQuests})
		{
			checkBox.setOpaque(false);
			checkBox.setForeground(Color.WHITE);
			checkBox.setFocusable(false);
			checkBox.setMargin(new Insets(0, 0, 0, 0));
			checkBox.setAlignmentX(Component.LEFT_ALIGNMENT);
			questFilters.add(checkBox);
			checkBox.addActionListener(event -> updateQuestFilters());
		}
	}

	private void updateQuestFilters()
	{
		configManager.setConfiguration(BronzemanTcgConfig.GROUP,
			QUEST_HIDE_COMPLETED_KEY, hideCompletedQuests.isSelected());
		configManager.setConfiguration(BronzemanTcgConfig.GROUP,
			QUEST_HIDE_INCOMPLETABLE_KEY, hideIncompletableQuests.isSelected());
		dirtyTabs.add(PanelTab.QUESTS);
		if (selectedTab == PanelTab.QUESTS)
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

	private void configureImportantUnlockFilters()
	{
		showLockedImportant.setSelected(
			getSavedBoolean(IMPORTANT_SHOW_LOCKED_KEY, true));
		showUnlockedImportant.setSelected(
			getSavedBoolean(IMPORTANT_SHOW_UNLOCKED_KEY, true));

		importantUnlocksFilters.setOpaque(false);
		importantUnlocksFilters.setAlignmentX(Component.LEFT_ALIGNMENT);
		importantUnlocksFilters.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
		importantUnlocksFilters.setLayout(
			new BoxLayout(importantUnlocksFilters, BoxLayout.Y_AXIS));

		JCheckBox[] checkBoxes = {showLockedImportant, showUnlockedImportant};
		for (int index = 0; index < checkBoxes.length; index++)
		{
			JCheckBox checkBox = checkBoxes[index];
			checkBox.setOpaque(false);
			checkBox.setForeground(Color.WHITE);
			checkBox.setFocusable(false);
			checkBox.setMargin(new Insets(0, 0, 0, 0));
			checkBox.setAlignmentX(Component.LEFT_ALIGNMENT);
			importantUnlocksFilters.add(checkBox);
			checkBox.addActionListener(event -> updateImportantUnlockFilters());
		}
	}

	private boolean getSavedBoolean(String key, boolean defaultValue)
	{
		String stored = configManager.getConfiguration(BronzemanTcgConfig.GROUP, key);
		return stored == null ? defaultValue : Boolean.parseBoolean(stored);
	}

	private void updateImportantUnlockFilters()
	{
		configManager.setConfiguration(BronzemanTcgConfig.GROUP,
			IMPORTANT_SHOW_LOCKED_KEY, showLockedImportant.isSelected());
		configManager.setConfiguration(BronzemanTcgConfig.GROUP,
			IMPORTANT_SHOW_UNLOCKED_KEY, showUnlockedImportant.isSelected());

		dirtyTabs.add(PanelTab.IMPORTANT);
		if (selectedTab == PanelTab.IMPORTANT)
		{
			renderSelectedTab();
		}
	}

	private boolean shouldShowImportantItem(boolean unlocked)
	{
		return unlocked ? showUnlockedImportant.isSelected() : showLockedImportant.isSelected();
	}

	private void refreshImportantUnlocks()
	{
		importantUnlocksList.removeAll();
		Set<String> owned = snapshot.owned;
		if (!showLockedImportant.isSelected() && !showUnlockedImportant.isSelected())
		{
			importantUnlocksList.add(mutedRow("Select a status to display items"));
			importantUnlocksList.revalidate();
			importantUnlocksList.repaint();
			return;
		}

		int visibleCategories = 0;
		for (ImportantUnlocksCatalog.Category category : importantUnlocksCatalog.getCategories())
		{
			List<String> visibleItems = visibleImportantItems(category.items, owned);
			Map<ImportantUnlocksCatalog.Subcategory, List<String>> visibleSubcategories =
				new LinkedHashMap<>();
			for (ImportantUnlocksCatalog.Subcategory subcategory : category.subcategories)
			{
				List<String> subcategoryItems = visibleImportantItems(subcategory.items, owned);
				if (!subcategoryItems.isEmpty())
				{
					visibleSubcategories.put(subcategory, subcategoryItems);
				}
			}
			if (visibleItems.isEmpty() && visibleSubcategories.isEmpty())
			{
				continue;
			}

			if (visibleCategories > 0)
			{
				importantUnlocksList.add(Box.createVerticalStrut(5));
			}
			visibleCategories++;
			int have = countOwned(category.allItems, owned);
			importantUnlocksList.add(importantCategoryRow(category, have));
			if (expandedImportantCategories.contains(category.name))
			{
				for (String card : visibleItems)
				{
					boolean unlocked = owned.contains(card.toLowerCase(Locale.ROOT));
					importantUnlocksList.add(collectionCardRow(
						"  " + displayCardName(card), card, unlocked));
				}
				int visibleSubcategoryIndex = 0;
				for (Map.Entry<ImportantUnlocksCatalog.Subcategory, List<String>> entry
					: visibleSubcategories.entrySet())
				{
					if (visibleSubcategoryIndex > 0)
					{
						importantUnlocksList.add(Box.createVerticalStrut(3));
					}
					visibleSubcategoryIndex++;
					ImportantUnlocksCatalog.Subcategory subcategory = entry.getKey();
					importantUnlocksList.add(importantSubcategoryRow(category, subcategory,
						countOwned(subcategory.items, owned)));
					if (expandedImportantSubcategories.contains(
						importantSubcategoryKey(category.name, subcategory.name)))
					{
						for (String card : entry.getValue())
						{
							boolean unlocked = owned.contains(card.toLowerCase(Locale.ROOT));
							importantUnlocksList.add(collectionCardRow(
								"    " + displayCardName(card), card, unlocked));
						}
					}
				}
			}
		}
		if (importantUnlocksCatalog.getCategories().isEmpty())
		{
			importantUnlocksList.add(mutedRow("No Important Unlocks data bundled"));
		}
		else if (visibleCategories == 0)
		{
			importantUnlocksList.add(mutedRow("No Important Unlocks match these filters"));
		}
		importantUnlocksList.revalidate();
		importantUnlocksList.repaint();
	}

	private JPanel collectionCardRow(String label, String cardName, boolean unlocked)
	{
		JPanel cardRow = statusRow(label, unlocked, null);
		makeClickable(cardRow, () -> openCollectionCard(cardName));
		cardRow.setToolTipText("View " + displayCardName(cardName));
		return cardRow;
	}

	private void openCollectionCard(String cardName)
	{
		if (collectionCardHistory.isEmpty())
		{
			collectionReturnTab = selectedTab;
			collectionReturnScrollPosition =
				contentScroll.getVerticalScrollBar().getValue();
			if (selectedTab == PanelTab.IMPORTANT)
			{
				collectionListScrollPosition = collectionReturnScrollPosition;
			}
			else
			{
				selectTab(PanelTab.IMPORTANT);
			}
		}
		if (collectionCardHistory.isEmpty()
			|| !collectionCardHistory.peekLast().equalsIgnoreCase(cardName))
		{
			collectionCardHistory.addLast(cardName);
		}
		renderCollectionCard(cardName, true);
	}

	private void renderCollectionCard(String cardName, boolean snapToTop)
	{
		int currentScrollPosition = contentScroll.getVerticalScrollBar().getValue();
		CardKnowledgeCatalog.Card card = cardKnowledgeCatalog.find(cardName);
		cardDetailPanel.render(card, snapshot.owned, collectionBackButton(),
			name -> renderCollectionCard(name, false));
		((CardLayout) collectionView.getLayout()).show(collectionView, "detail");
		collectionView.revalidate();
		collectionView.repaint();
		SwingUtilities.invokeLater(() -> contentScroll.getVerticalScrollBar()
			.setValue(snapToTop ? 0 : currentScrollPosition));
	}

	private JButton collectionBackButton()
	{
		String destination = collectionReturnTab == PanelTab.IMPORTANT
			? "Collection" : panelTabLabel(collectionReturnTab);
		JButton back = new JButton(collectionCardHistory.size() > 1
			? "\u2190 Back" : "\u2190 Back to " + destination);
		back.setFocusable(false);
		back.setForeground(Color.WHITE);
		back.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		back.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(153, 102, 51)),
			BorderFactory.createEmptyBorder(5, 8, 5, 8)));
		back.setAlignmentX(Component.LEFT_ALIGNMENT);
		back.addActionListener(event ->
		{
			if (collectionCardHistory.size() > 1)
			{
				collectionCardHistory.removeLast();
				renderCollectionCard(collectionCardHistory.peekLast(), true);
				return;
			}
			collectionCardHistory.clear();
			if (collectionReturnTab == PanelTab.IMPORTANT)
			{
				((CardLayout) collectionView.getLayout()).show(collectionView, "list");
				collectionView.revalidate();
				collectionView.repaint();
				SwingUtilities.invokeLater(() -> contentScroll.getVerticalScrollBar()
					.setValue(collectionListScrollPosition));
			}
			else
			{
				PanelTab returnTab = collectionReturnTab;
				int returnScroll = collectionReturnScrollPosition;
				selectTab(returnTab);
				SwingUtilities.invokeLater(() ->
					contentScroll.getVerticalScrollBar().setValue(returnScroll));
			}
		});
		return back;
	}

	private static String panelTabLabel(PanelTab tab)
	{
		switch (tab)
		{
			case QUESTS:
				return "Quests";
			case SLAYER:
				return "Slayer";
			case PVM:
				return "PvM";
			case RUMOURS:
				return "Rumours";
			case RECENT:
				return "Recent";
			case SHARED:
				return "Shared Cards";
			default:
				return "Collection";
		}
	}

	private static JLabel wrappedDetailText(String text)
	{
		JLabel label = new JLabel("<html><body style='width:175px'>"
			+ escapeHtml(text) + "</body></html>");
		label.setForeground(Color.WHITE);
		label.setBorder(BorderFactory.createEmptyBorder(3, 6, 6, 6));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private static int safeSize(List<?> values)
	{
		return values == null ? 0 : values.size();
	}

	private static String escapeHtml(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;")
			.replace(">", "&gt;").replace("\"", "&quot;");
	}

	private List<String> visibleImportantItems(List<String> items, Set<String> owned)
	{
		List<String> visible = new ArrayList<>();
		for (String card : items)
		{
			boolean unlocked = owned.contains(card.toLowerCase(Locale.ROOT));
			if (shouldShowImportantItem(unlocked))
			{
				visible.add(card);
			}
		}
		return visible;
	}

	private JPanel importantCategoryRow(ImportantUnlocksCatalog.Category category, int have)
	{
		boolean expanded = expandedImportantCategories.contains(category.name);
		JPanel row = hierarchyProgressRow(category.name,
			have, category.allItems.size(), have >= category.allItems.size(),
			expanded, false);
		makeClickable(row, () ->
		{
			if (!expandedImportantCategories.remove(category.name))
			{
				expandedImportantCategories.add(category.name);
			}
			refreshImportantUnlocks();
		});
		return row;
	}

	private JPanel importantSubcategoryRow(ImportantUnlocksCatalog.Category category,
		ImportantUnlocksCatalog.Subcategory subcategory, int have)
	{
		String key = importantSubcategoryKey(category.name, subcategory.name);
		boolean expanded = expandedImportantSubcategories.contains(key);
		JPanel row = hierarchyProgressRow(subcategory.name,
			have, subcategory.items.size(), have >= subcategory.items.size(),
			expanded, true);
		makeClickable(row, () ->
		{
			if (!expandedImportantSubcategories.remove(key))
			{
				expandedImportantSubcategories.add(key);
			}
			refreshImportantUnlocks();
		});
		return row;
	}

	private static String importantSubcategoryKey(String category, String subcategory)
	{
		return category + "\0" + subcategory;
	}

	private List<QuestCatalog.QuestEntry> buildAreaEntries()
	{
		List<QuestCatalog.QuestEntry> entries = new ArrayList<>();
		for (MonsterAreaCatalog.Area area : monsterAreaCatalog.getAreas())
		{
			List<QuestCatalog.Requirement> requirements = new ArrayList<>();
			for (String card : area.monsterCards)
			{
				requirements.add(new QuestCatalog.Requirement(card,
					Collections.singletonList(card)));
			}
			entries.add(new QuestCatalog.QuestEntry(area.name, false, requirements, ""));
		}
		return sortedEntries(entries);
	}

	private List<SlayerMasterEntry> buildSlayerEntries()
	{
		Map<String, Map<String, SlayerTaskBuilder>> tasksByMaster =
			new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		Map<String, Map<String, QuestCatalog.Requirement>> superiorsByMaster =
			new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (Map.Entry<String, ResourceNodeCatalog.Rule> entry :
			distinctRules("slayer").entrySet())
		{
			String masterName = slayerPanelMasterName(entry.getKey());
			boolean locationSpecific =
				"konar quo maten".equalsIgnoreCase(entry.getKey());
			Map<String, SlayerTaskBuilder> tasks = tasksByMaster.computeIfAbsent(
				masterName, ignored -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
			Map<String, QuestCatalog.Requirement> superiors =
				superiorsByMaster.computeIfAbsent(masterName,
					ignored -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
			for (ResourceNodeCatalog.CardGroup group : entry.getValue().groups)
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
	private List<QuestCatalog.QuestEntry> buildMasterEntries(String category)
	{
		List<QuestCatalog.QuestEntry> entries = new ArrayList<>();
		for (Map.Entry<String, ResourceNodeCatalog.Rule> e : distinctRules(category).entrySet())
		{
			List<QuestCatalog.Requirement> reqs = new ArrayList<>();
			for (ResourceNodeCatalog.CardGroup group : e.getValue().groups)
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

	private void refreshChecklist(JPanel container, String summaryNoun,
		List<QuestCatalog.QuestEntry> entries, Set<String> owned,
		Set<String> expandedNames, Runnable refresh, String emptyText,
		boolean showEntryDividers)
	{
		container.removeAll();

		int completable = 0;
		for (QuestCatalog.QuestEntry entry : entries)
		{
			if (entry.satisfiedCount(owned) == entry.requirements.size())
			{
				completable++;
			}
		}
		container.add(mutedRow(String.format("%d/%d %s", completable, entries.size(), summaryNoun)));

		if (entries.isEmpty())
		{
			container.add(mutedRow(emptyText));
		}
		// Entries are sorted once by the background preparation pass. A name never moves
		// when the owned collection changes.
		int entryIndex = 0;
		for (QuestCatalog.QuestEntry entry : entries)
		{
			if (showEntryDividers && entryIndex > 0)
			{
				container.add(Box.createVerticalStrut(5));
			}
			entryIndex++;
			container.add(checklistRow(entry, owned, expandedNames, refresh));
			if (expandedNames.contains(entry.name))
			{
				for (QuestCatalog.Requirement requirement : entry.requirements)
				{
					container.add(requirementRow(requirement, requirement.isSatisfied(owned)));
				}
				if (entry.requirements.isEmpty())
				{
					container.add(mutedRow("  No card-backed requirements"));
				}
				if (!entry.notes.isEmpty())
				{
					JLabel notes = wrappedDetailText(entry.notes);
					notes.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
					container.add(notes);
				}
			}
		}
		container.revalidate();
		container.repaint();
	}

	private JPanel checklistRow(QuestCatalog.QuestEntry entry, Set<String> owned,
		Set<String> expandedNames, Runnable refresh)
	{
		int have = entry.satisfiedCount(owned);
		int total = entry.requirements.size();
		boolean expanded = expandedNames.contains(entry.name);
		String label = entry.name + (entry.miniquest ? " (mini)" : "");
		JPanel row = hierarchyProgressRow(label, have, Math.max(total, 0),
			have >= total, expanded, false);
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

	private JPanel requirementRow(QuestCatalog.Requirement requirement, boolean have)
	{
		return requirementRow(requirement, have, "  ");
	}

	private JPanel requirementRow(QuestCatalog.Requirement requirement, boolean have,
		String indent)
	{
		String alternatives = requirement.displayCards.size() > 1
			? ": " + String.join(" / ", requirement.displayCards)
			: "";
		JPanel row = statusRow(indent + requirement.label + alternatives, have, null);
		if (requirement.displayCards.size() == 1)
		{
			String card = requirement.displayCards.get(0);
			makeClickable(row, () -> openCollectionCard(card));
			row.setToolTipText("View " + displayCardName(card));
		}
		else
		{
			row.setToolTipText("Multiple alternative cards");
		}
		return row;
	}

	private JPanel linkedStatusRow(String label, String card, boolean unlocked)
	{
		JPanel result = statusRow(label, unlocked, null);
		makeClickable(result, () -> openCollectionCard(card));
		result.setToolTipText("View " + displayCardName(card));
		return result;
	}

	// ------------------------------------------------------------------ search

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
			for (SearchEntry entry : snapshot.data.searchEntries)
			{
				if (!entry.searchName.contains(query))
				{
					continue;
				}
				matches++;
				if (++shown <= MAX_SEARCH_RESULTS)
				{
					boolean unlocked = ownsAny(snapshot.owned, entry.cards);
					JPanel result = statusRow(entry.displayName, unlocked,
						unlocked ? null : String.join(" / ", entry.cards));
					if (entry.cards.size() == 1)
					{
						String card = entry.cards.iterator().next();
						makeClickable(result, () -> openCollectionCard(card));
						result.setToolTipText("View " + displayCardName(card));
					}
					searchResults.add(result);
				}
			}
			if (matches > MAX_SEARCH_RESULTS)
			{
				searchResults.add(mutedRow("...and " + (matches - MAX_SEARCH_RESULTS) + " more"));
			}
			if (matches == 0)
			{
				searchResults.add(mutedRow("No tracked NPC or item matches"));
			}
		}
		searchResults.revalidate();
		searchResults.repaint();
	}

	// ------------------------------------------------------------------ progress

	private void refreshProgress(PanelSnapshot current)
	{
		progressList.removeAll();

		progressList.add(progressRow("NPCs unlocked",
			current.unlockedMonsters, monsterCatalog.size()));
		progressList.add(Box.createVerticalStrut(5));
		progressList.add(progressRow("Items unlocked",
			current.unlockedItems, itemCatalog.size()));

		progressList.revalidate();
		progressList.repaint();
	}

	private Map<String, ResourceNodeCatalog.Rule> distinctRules(String category)
	{
		Map<String, ResourceNodeCatalog.Rule> byName = new TreeMap<>();
		for (Map.Entry<String, ResourceNodeCatalog.Rule> e : nodeCatalog.getRuleEntries().entrySet())
		{
			if (category.equals(e.getValue().category))
			{
				String[] parts = e.getKey().split("\\|", 3);
				byName.put(parts.length > 1 ? parts[1] : e.getKey(), e.getValue());
			}
		}
		return byName;
	}

	private static int countUnlocked(Map<String, Set<String>> entities, Set<String> owned)
	{
		int count = 0;
		for (Set<String> variants : entities.values())
		{
			if (ownsAny(owned, variants))
			{
				count++;
			}
		}
		return count;
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
		return name.regionMatches(true, 0, "The ", 0, 4) ? name.substring(4) : name;
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

	private static boolean ownsAny(Set<String> owned, Set<String> variants)
	{
		for (String card : variants)
		{
			if (owned.contains(card))
			{
				return true;
			}
		}
		return false;
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
		String itemName = itemCatalog.findDisplayCardName(cardName);
		if (itemName != null)
		{
			return itemName;
		}
		String monsterName = monsterCatalog.findDisplayCardName(cardName);
		return monsterName == null ? display(cardName) : monsterName;
	}

	private enum PanelTab
	{
		QUESTS,
		SLAYER,
		PVM,
		RUMOURS,
		RECENT,
		IMPORTANT,
		SHARED,
		SETTINGS
	}

	/**
	 * CardLayout keeps every tab attached, but its default preferred size is the largest
	 * card. The sidebar should instead follow the visible card so shorter tabs do not
	 * inherit a long hidden tab's scroll height.
	 */

}
