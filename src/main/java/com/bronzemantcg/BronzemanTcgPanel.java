package com.bronzemantcg;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
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
import java.util.Deque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;
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
	private static final Color UNLOCKED = ColorScheme.PROGRESS_COMPLETE_COLOR;
	private static final Color LOCKED = ColorScheme.PROGRESS_ERROR_COLOR;
	private static final DateTimeFormatter UNLOCK_TIME_FORMAT = DateTimeFormatter
		.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault());
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
	private final ItemManager itemManager;
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
	private final SelectedCardPanel tabDisplay = new SelectedCardPanel();
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
	private volatile Set<String> completedQuestNames = Collections.emptySet();

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
	private final JPanel settingsList = sectionBody();
	private final Set<String> expandedSettingsCategories = new HashSet<>();
	private final Set<String> expandedSettingsSections = new HashSet<>();

	private final JPanel importantUnlocksPanel = sectionBody();
	private final JPanel importantUnlocksFilters = new JPanel();

	private final JCheckBox showLockedImportant = new JCheckBox("Show locked");

	private final JCheckBox showUnlockedImportant = new JCheckBox("Show unlocked");

	private final JPanel importantUnlocksList = sectionBody();
	private final Set<String> expandedImportantCategories = new HashSet<>();
	private final Set<String> expandedImportantSubcategories = new HashSet<>();
	private final JPanel collectionView = new JPanel(new CardLayout());
	private final JPanel collectionDetailPanel = sectionBody();
	private int collectionListScrollPosition;
	private final Deque<String> collectionCardHistory = new ArrayDeque<>();
	private final Set<String> expandedKnowledgeSections = new HashSet<>();

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
		this.itemManager = itemManager;
		this.config = config;
		this.configManager = configManager;
		this.executor = executor;

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
		collectionView.add(collectionDetailPanel, "detail");

		progressList.add(mutedRow("Loading collection..."));
		questList.add(mutedRow("Loading quests..."));
		addView(questPanel, PanelTab.QUESTS);
		addView(slayerPanel, PanelTab.SLAYER);
		addView(pvmPanel, PanelTab.PVM);
		addView(rumoursList, PanelTab.RUMOURS);
		addView(recentUnlocksPanel, PanelTab.RECENT);
		addView(collectionView, PanelTab.IMPORTANT);
		addView(sharedCardsList, PanelTab.SHARED);
		addView(settingsList, PanelTab.SETTINGS);

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
	void updateCompletedQuests(Set<String> completed)
	{
		completedQuestNames = completed == null
			? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(completed));
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

		return new PanelSnapshot(data, owned, visibleShared, recentUnlocksTracker.getRecent(),
			recentUnlocksTracker.getSharedRecent(),
			includeSlayerSuperiors, completed,
			countUnlocked(monsterCatalog.getEntityToCards(), owned),
			countUnlocked(itemCatalog.getEntityToCards(), owned));
	}

	private PreparedData prepareStaticData()
	{
		List<QuestCatalog.QuestEntry> quests = sortedEntries(questCatalog.getQuests());
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

		return new PreparedData(quests, contents, areas, slayer, allSuperiors,
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
			|| !previous.completedQuests.equals(next.completedQuests);
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
				refreshSettings();
				break;
			default:
				break;
		}
	}

	// ------------------------------------------------------------------ collapsible checklists

	private void refreshQuests()
	{
		List<QuestCatalog.QuestEntry> visible = new ArrayList<>();
		for (QuestCatalog.QuestEntry quest : snapshot.data.quests)
		{
			boolean completed = snapshot.completedQuests.contains(
				quest.name.toLowerCase(Locale.ROOT));
			boolean completable =
				quest.satisfiedCount(snapshot.owned) == quest.requirements.size();
			if (hideCompletedQuests.isSelected() && completed)
			{
				continue;
			}
			if (hideIncompletableQuests.isSelected() && !completable)
			{
				continue;
			}
			visible.add(quest);
		}
		refreshChecklist(questList, "quests completable",
			visible, snapshot.owned, expandedQuests,
			this::refreshQuests, snapshot.data.quests.isEmpty()
				? "No quest data bundled" : "No quests match these filters", true);
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
					slayerList.add(statusRow("  " + superior.label,
						superior.isSatisfied(snapshot.owned), null));
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
					slayerList.add(statusRow("    " + requirement.label,
						requirement.isSatisfied(snapshot.owned), null));
				}
				for (String card : requirement.displayCards)
				{
					if (isSlayerCardVisible(card))
					{
						String indent = task.locationSpecific ? "      " : "    ";
						slayerList.add(statusRow(indent + displayCardName(card),
							snapshot.owned.contains(card.toLowerCase(Locale.ROOT)), null));
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
						slayerList.add(statusRow("    " + superior.label,
							superior.isSatisfied(snapshot.owned), null));
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
						contentList.add(statusRow("    " + requirement.label,
							unlocked, null));
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
				sharedCardsList.add(statusRow("  " + displayCardName(card), true, null));
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
						sharedCardsList.add(statusRow(
							"    " + displayCardName(card), true, null));
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

	// ------------------------------------------------------------------ side-panel settings

	private void refreshSettings()
	{
		settingsList.removeAll();
		List<SettingsCategory> categories = settingsCategories();
		for (int categoryIndex = 0; categoryIndex < categories.size(); categoryIndex++)
		{
			SettingsCategory category = categories.get(categoryIndex);
			if (categoryIndex > 0)
			{
				settingsList.add(Box.createVerticalStrut(5));
			}
			boolean categoryExpanded = expandedSettingsCategories.contains(category.name);
			JPanel categoryRow = hierarchyLabelRow(
				category.name, categoryExpanded, false);
			makeClickable(categoryRow, () ->
			{
				toggleExpanded(expandedSettingsCategories, category.name);
				refreshSettings();
			});
			settingsList.add(categoryRow);
			if (!categoryExpanded)
			{
				continue;
			}

			for (SettingsSection section : category.sections)
			{
				settingsList.add(Box.createVerticalStrut(3));
				boolean sectionExpanded = expandedSettingsSections.contains(section.key);
				JPanel sectionRow = hierarchyLabelRow(
					section.name, sectionExpanded, true);
				makeClickable(sectionRow, () ->
				{
					toggleExpanded(expandedSettingsSections, section.key);
					refreshSettings();
				});
				settingsList.add(sectionRow);
				if (!sectionExpanded)
				{
					continue;
				}
				settingsList.add(listDivider());
				for (Method method : section.items)
				{
					settingsList.add(settingControl(method));
				}
			}
		}
		settingsList.revalidate();
		settingsList.repaint();
	}

	private static JPanel hierarchyLabelRow(String label, boolean expanded, boolean nested)
	{
		JPanel row = row(new BorderLayout());
		styleHierarchyRow(row, expanded, nested);
		JLabel name = new JLabel(label);
		name.setForeground(Color.WHITE);
		row.add(name, BorderLayout.CENTER);
		return row;
	}

	private List<SettingsCategory> settingsCategories()
	{
		Map<String, List<Method>> bySection = new HashMap<>();
		for (Method method : BronzemanTcgConfig.class.getMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item == null || item.hidden())
			{
				continue;
			}
			bySection.computeIfAbsent(item.section(), ignored -> new ArrayList<>()).add(method);
		}
		for (List<Method> methods : bySection.values())
		{
			methods.sort(Comparator.comparingInt(
				method -> method.getAnnotation(ConfigItem.class).position()));
		}

		List<SettingsCategory> categories = new ArrayList<>();
		categories.add(settingsCategory("Gathering", bySection,
			settingsSection("Farming", BronzemanTcgConfig.farmingSection),
			settingsSection("Fishing", BronzemanTcgConfig.fishingSection),
			settingsSection("Hunter", BronzemanTcgConfig.hunterSection),
			settingsSection("Mining", BronzemanTcgConfig.miningSection),
			settingsSection("Thieving", BronzemanTcgConfig.thievingSection),
			settingsSection("Woodcutting", BronzemanTcgConfig.woodcuttingSection)));
		categories.add(settingsCategory("Production", bySection,
			settingsSection("Cooking", BronzemanTcgConfig.cookingSection),
			settingsSection("Crafting", BronzemanTcgConfig.craftingSection),
			settingsSection("Firemaking", BronzemanTcgConfig.firemakingSection),
			settingsSection("Fletching", BronzemanTcgConfig.fletchingSection),
			settingsSection("Herblore", BronzemanTcgConfig.herbloreSection),
			settingsSection("Runecrafting", BronzemanTcgConfig.runecraftingSection),
			settingsSection("Smithing", BronzemanTcgConfig.smithingSection)));
		categories.add(settingsCategory("Other", bySection,
			settingsSection("General", ""),
			settingsSection("Sailing", BronzemanTcgConfig.sailingSection),
			settingsSection("Slayer", BronzemanTcgConfig.slayerSection),
			settingsSection("Visuals", BronzemanTcgConfig.visualsSection),
			settingsSection("External Plugins", BronzemanTcgConfig.externalPluginsSection)));
		return categories;
	}

	private static SettingsCategory settingsCategory(String name,
		Map<String, List<Method>> bySection, SettingsSection... definitions)
	{
		List<SettingsSection> sections = new ArrayList<>();
		for (SettingsSection definition : definitions)
		{
			List<Method> methods = bySection.getOrDefault(definition.key, Collections.emptyList());
			if (!methods.isEmpty())
			{
				sections.add(new SettingsSection(definition.name, definition.key, methods));
			}
		}
		return new SettingsCategory(name, sections);
	}

	private static SettingsSection settingsSection(String name, String key)
	{
		return new SettingsSection(name, key, Collections.emptyList());
	}

	private JPanel settingControl(Method method)
	{
		ConfigItem item = method.getAnnotation(ConfigItem.class);
		Object value;
		try
		{
			value = method.invoke(config);
		}
		catch (ReflectiveOperationException ex)
		{
			log.warn("Could not read side-panel setting {}", item.keyName(), ex);
			JPanel unavailable = settingRow();
			unavailable.add(mutedRow(item.name() + " (unavailable)"));
			return unavailable;
		}

		String tooltip = item.description().replace("<br>", " ");
		if (value instanceof Boolean)
		{
			JCheckBox checkBox = new JCheckBox(item.name(), (Boolean) value);
			styleSettingComponent(checkBox, tooltip);
			checkBox.addActionListener(event ->
				saveSetting(item.keyName(), checkBox.isSelected()));
			JPanel row = settingRow();
			row.add(checkBox);
			return row;
		}

		JPanel row = settingRow();
		JLabel label = new JLabel(item.name());
		label.setForeground(Color.WHITE);
		label.setToolTipText(tooltip);
		row.add(label);
		row.add(Box.createVerticalStrut(3));

		if (value instanceof Enum)
		{
			Object[] values = value.getClass().getEnumConstants();
			JComboBox<Object> combo = new JComboBox<>(values);
			combo.setSelectedItem(value);
			styleSettingComponent(combo, tooltip);
			combo.addActionListener(event ->
				saveSetting(item.keyName(), ((Enum<?>) combo.getSelectedItem()).name()));
			row.add(combo);
		}
		else if (value instanceof Integer)
		{
			Range range = method.getAnnotation(Range.class);
			int min = range == null ? Integer.MIN_VALUE : range.min();
			int max = range == null ? Integer.MAX_VALUE : range.max();
			JSpinner spinner = new JSpinner(
				new SpinnerNumberModel(((Integer) value).intValue(), min, max, 1));
			styleSettingComponent(spinner, tooltip);
			spinner.addChangeListener(event ->
				saveSetting(item.keyName(), spinner.getValue()));
			row.add(spinner);
		}
		else if (value instanceof Color)
		{
			JButton colour = new JButton("Choose colour");
			colour.setBackground((Color) value);
			styleSettingComponent(colour, tooltip);
			colour.addActionListener(event ->
			{
				Color selected = JColorChooser.showDialog(this, item.name(),
					colour.getBackground());
				if (selected != null)
				{
					colour.setBackground(selected);
					saveSetting(item.keyName(), selected);
				}
			});
			row.add(colour);
		}
		else
		{
			JTextField text = new JTextField(value == null ? "" : value.toString());
			styleSettingComponent(text, tooltip);
			Runnable save = () -> saveSetting(item.keyName(), text.getText());
			text.addActionListener(event -> save.run());
			text.addFocusListener(new FocusAdapter()
			{
				@Override
				public void focusLost(FocusEvent event)
				{
					save.run();
				}
			});
			row.add(text);
		}
		return row;
	}

	private static JPanel settingRow()
	{
		JPanel row = sectionBody();
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(5, 18, 5, 4));
		return row;
	}

	private static void styleSettingComponent(Component component, String tooltip)
	{
		if (component instanceof javax.swing.JComponent)
		{
			javax.swing.JComponent swing = (javax.swing.JComponent) component;
			swing.setToolTipText(tooltip);
			swing.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
			swing.setAlignmentX(Component.LEFT_ALIGNMENT);
		}
		if (component instanceof JCheckBox)
		{
			((JCheckBox) component).setOpaque(false);
			((JCheckBox) component).setForeground(Color.WHITE);
		}
	}

	private void saveSetting(String key, Object value)
	{
		configManager.setConfiguration(BronzemanTcgConfig.GROUP, key, value);
	}

	private static void toggleExpanded(Set<String> expanded, String key)
	{
		if (!expanded.remove(key))
		{
			expanded.add(key);
		}
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
			collectionListScrollPosition = contentScroll.getVerticalScrollBar().getValue();
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
		collectionDetailPanel.removeAll();
		if (card == null)
		{
			collectionDetailPanel.add(collectionBackButton());
			collectionDetailPanel.add(mutedRow("No card information is available."));
		}
		else
		{
			collectionDetailPanel.add(collectionBackButton());
			collectionDetailPanel.add(Box.createVerticalStrut(8));
			collectionDetailPanel.add(collectionCardHeader(card));
			collectionDetailPanel.add(Box.createVerticalStrut(8));
			addDetailField(collectionDetailPanel, "Status",
				snapshot.owned.contains(card.name.toLowerCase(Locale.ROOT))
					? "Unlocked" : "Locked");
			addDetailField(collectionDetailPanel, "Type",
				card.isResource() ? "Resource" : "Monster");
			if (!card.categoryLabels().isEmpty())
			{
				addDetailField(collectionDetailPanel, "Categories",
					String.join(", ", card.categoryLabels()));
			}
			if (card.combatLevel != null && card.combatLevel > 0)
			{
				addDetailField(collectionDetailPanel, "Combat level",
					String.valueOf(card.combatLevel));
			}
			if (card.slayerLevel != null && card.slayerLevel > 0)
			{
				addDetailField(collectionDetailPanel, "Slayer level",
					String.valueOf(card.slayerLevel));
			}
			if (card.examine != null && !card.examine.isEmpty())
			{
				collectionDetailPanel.add(sectionHeader("Examine"));
				collectionDetailPanel.add(wrappedDetailText(card.examine));
			}
			if (card.isResource())
			{
				addResourceDetails(card);
			}
			else
			{
				addCollectionPreview(card);
			}
		}
		collectionDetailPanel.revalidate();
		collectionDetailPanel.repaint();
		((CardLayout) collectionView.getLayout()).show(collectionView, "detail");
		collectionView.revalidate();
		collectionView.repaint();
		SwingUtilities.invokeLater(() -> contentScroll.getVerticalScrollBar()
			.setValue(snapToTop ? 0 : currentScrollPosition));
	}

	private JButton collectionBackButton()
	{
		JButton back = new JButton(collectionCardHistory.size() > 1
			? "\u2190 Back" : "\u2190 Back to Collection");
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
			((CardLayout) collectionView.getLayout()).show(collectionView, "list");
			collectionView.revalidate();
			collectionView.repaint();
			SwingUtilities.invokeLater(() ->
				contentScroll.getVerticalScrollBar().setValue(collectionListScrollPosition));
		});
		return back;
	}

	private JPanel collectionCardHeader(CardKnowledgeCatalog.Card card)
	{
		JPanel header = row(new BorderLayout(8, 0));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(153, 102, 51)),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)));

		if (card.isResource() && card.primaryId() >= 0)
		{
			JLabel image = new JLabel();
			image.setPreferredSize(new Dimension(36, 36));
			AsyncBufferedImage sprite = itemManager.getImage(card.primaryId());
			sprite.addTo(image);
			header.add(image, BorderLayout.WEST);
		}

		JLabel title = new JLabel("<html>" + escapeHtml(card.name) + "</html>");
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
		header.add(title, BorderLayout.CENTER);
		return header;
	}

	private void addResourceDetails(CardKnowledgeCatalog.Card card)
	{
		CardKnowledgeCatalog.Sources sources = card.sources;
		if (sources == null)
		{
			collectionDetailPanel.add(mutedRow("No source information available."));
			return;
		}

		List<CardKnowledgeCatalog.MonsterSource> monsters = new ArrayList<>();
		if (sources.monsters != null)
		{
			monsters.addAll(sources.monsters);
		}
		if (sources.rareDropTable != null)
		{
			monsters.addAll(sources.rareDropTable);
		}
		addKnowledgeSection(card.name, "Monster sources", monsters.size(), body ->
		{
			for (CardKnowledgeCatalog.MonsterSource source : monsters)
			{
				String detail = source.fraction == null || source.fraction.isEmpty()
					? source.rarity : source.rarity + " \u00b7 " + source.fraction;
				if (!source.confirmedByMonsterDrops)
				{
					detail += " \u00b7 one-sided";
				}
				body.add(linkedKnowledgeRow(source.card, detail));
			}
		});

		if (sources.gathering != null)
		{
			addKnowledgeSection(card.name, "Gathering", 1, body ->
			{
				CardKnowledgeCatalog.Gathering gathering = sources.gathering;
				addDetailField(body, "Method", valueOrDash(gathering.method));
				addDetailField(body, "Skill", levelLabel(gathering.skill, gathering.level));
				addDetailField(body, "Tool", valueOrDash(gathering.tool));
				if (gathering.xp != null)
				{
					addDetailField(body, "XP", formatNumber(gathering.xp));
				}
			});
		}

		if (sources.production != null)
		{
			int cardIngredients = 0;
			int hiddenIngredients = 0;
			for (CardKnowledgeCatalog.Ingredient ingredient : sources.production.ingredients)
			{
				if (ingredient.hasCard)
				{
					cardIngredients++;
				}
				else
				{
					hiddenIngredients++;
				}
			}
			final int shown = cardIngredients;
			final int hidden = hiddenIngredients;
			addKnowledgeSection(card.name, "Production", shown + hidden, body ->
			{
				CardKnowledgeCatalog.Production production = sources.production;
				addDetailField(body, "Skill", levelLabel(production.skill, production.level));
				if (production.facilities != null)
				{
					addDetailField(body, "Facility", production.facilities);
				}
				if (production.tools != null && !production.tools.isEmpty())
				{
					addDetailField(body, "Tools", String.join(", ", production.tools));
				}
				for (CardKnowledgeCatalog.Ingredient ingredient : production.ingredients)
				{
					if (ingredient.hasCard)
					{
						body.add(linkedKnowledgeRow(ingredient.item,
							"×" + formatNumber(ingredient.quantity)));
					}
				}
				if (hidden > 0)
				{
					body.add(mutedRow(hidden + " non-card ingredient"
						+ (hidden == 1 ? "" : "s") + " hidden"));
				}
			});
		}

		addKnowledgeSection(card.name, "Used in", safeSize(sources.usedIn), body ->
		{
			for (CardKnowledgeCatalog.UsedIn usage : sources.usedIn)
			{
				body.add(linkedKnowledgeRow(usage.card,
					"×" + formatNumber(usage.quantity)));
			}
		});
		addTextKnowledgeSection(card.name, "Ground spawns", sources.spawns);
		addKnowledgeSection(card.name, "Shops", safeSize(sources.shops), body ->
		{
			for (CardKnowledgeCatalog.Shop shop : sources.shops)
			{
				String price = shop.price == null ? "" : formatNumber(shop.price)
					+ (shop.currency == null ? "" : " " + shop.currency);
				body.add(plainKnowledgeRow(shop.seller, price));
			}
		});
		addTextKnowledgeSection(card.name, "Clue rewards", sources.clueTiers);
	}

	private void addTextKnowledgeSection(String cardName, String title, List<String> values)
	{
		addKnowledgeSection(cardName, title, safeSize(values), body ->
		{
			for (String value : values)
			{
				body.add(plainKnowledgeRow(value, ""));
			}
		});
	}

	private void addKnowledgeSection(String cardName, String title, int count,
		java.util.function.Consumer<JPanel> bodyBuilder)
	{
		if (count == 0)
		{
			return;
		}
		String key = cardName + "\0" + title;
		boolean expanded = expandedKnowledgeSections.contains(key);
		JPanel heading = hierarchyProgressRow(title, count, count, true, expanded, false);
		makeClickable(heading, () ->
		{
			if (!expandedKnowledgeSections.remove(key))
			{
				expandedKnowledgeSections.add(key);
			}
			renderCollectionCard(cardName, false);
		});
		collectionDetailPanel.add(Box.createVerticalStrut(5));
		collectionDetailPanel.add(heading);
		if (expanded)
		{
			JPanel body = sectionBody();
			body.setBorder(BorderFactory.createEmptyBorder(3, 5, 2, 0));
			bodyBuilder.accept(body);
			collectionDetailPanel.add(body);
		}
	}

	private JPanel linkedKnowledgeRow(String cardName, String detail)
	{
		JPanel item = plainKnowledgeRow(displayCardName(cardName), detail);
		makeClickable(item, () -> openCollectionCard(cardName));
		item.setToolTipText("View " + displayCardName(cardName));
		return item;
	}

	private static JPanel plainKnowledgeRow(String label, String detail)
	{
		JPanel item = row(new BorderLayout(5, 0));
		item.setBackground(Color.BLACK);
		item.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		JLabel name = new JLabel(label);
		name.setForeground(Color.WHITE);
		item.add(name, BorderLayout.CENTER);
		if (detail != null && !detail.isEmpty())
		{
			JLabel value = new JLabel(detail);
			value.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			value.setFont(value.getFont().deriveFont(10f));
			item.add(value, BorderLayout.EAST);
		}
		return item;
	}

	private static String levelLabel(String skill, Integer level)
	{
		String name = valueOrDash(skill);
		return level == null || level <= 0 ? name : name + " " + level;
	}

	private static String valueOrDash(String value)
	{
		return value == null || value.trim().isEmpty() ? "\u2014" : value;
	}

	private static String formatNumber(Number number)
	{
		if (number == null)
		{
			return "";
		}
		double value = number.doubleValue();
		return value == Math.rint(value)
			? String.valueOf(number.longValue()) : String.valueOf(value);
	}

	private void addCollectionPreview(CardKnowledgeCatalog.Card card)
	{
		if (card.isResource() && card.sources != null)
		{
			int monsterSources = safeSize(card.sources.monsters)
				+ safeSize(card.sources.rareDropTable);
			int locations = safeSize(card.sources.spawns);
			int clues = safeSize(card.sources.clueTiers);
			if (monsterSources + locations + clues > 0)
			{
				collectionDetailPanel.add(sectionHeader("Information available"));
				addDetailField(collectionDetailPanel, "Monster sources",
					String.valueOf(monsterSources));
				addDetailField(collectionDetailPanel, "Ground spawns",
					String.valueOf(locations));
				addDetailField(collectionDetailPanel, "Clue tiers",
					String.valueOf(clues));
			}
		}
		else if (!card.isResource() && card.drops != null && !card.drops.isEmpty())
		{
			collectionDetailPanel.add(sectionHeader("Information available"));
			addDetailField(collectionDetailPanel, "Card drops",
				String.valueOf(card.drops.size()));
		}
	}

	private static void addDetailField(JPanel target, String label, String value)
	{
		JPanel field = row(new BorderLayout(6, 0));
		field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		field.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		JLabel name = new JLabel(label);
		name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		field.add(name, BorderLayout.WEST);
		JLabel detail = new JLabel(value);
		detail.setForeground(Color.WHITE);
		field.add(detail, BorderLayout.EAST);
		target.add(field);
		target.add(Box.createVerticalStrut(2));
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
		String alternatives = requirement.displayCards.size() > 1
			? ": " + String.join(" / ", requirement.displayCards)
			: "";
		JPanel row = statusRow("  " + requirement.label + alternatives, have, null);
		return row;
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
					searchResults.add(statusRow(entry.displayName, unlocked,
						unlocked ? null : String.join(" / ", entry.cards)));
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
		sorted.sort(Comparator.comparing(entry -> entry.name, String.CASE_INSENSITIVE_ORDER));
		return Collections.unmodifiableList(sorted);
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

	private static JLabel sectionHeader(String title)
	{
		JLabel label = new JLabel(title);
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setBorder(BorderFactory.createEmptyBorder(10, 0, 4, 0));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private static JPanel statusRow(String name, boolean unlocked, String missingCards)
	{
		JPanel row = row(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

		JLabel nameLabel = new JLabel(name);
		nameLabel.setForeground(Color.WHITE);
		row.add(nameLabel, BorderLayout.CENTER);

		JLabel status = new JLabel(unlocked ? "✓" : "✗");
		status.setForeground(unlocked ? UNLOCKED : LOCKED);
		status.setFont(status.getFont().deriveFont(Font.BOLD));
		row.add(status, BorderLayout.EAST);

		if (missingCards != null && !missingCards.isEmpty())
		{
			JLabel needs = new JLabel("needs: " + missingCards);
			needs.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			needs.setFont(needs.getFont().deriveFont(11f));
			row.add(needs, BorderLayout.SOUTH);
		}
		return row;
	}

	private static JPanel recentUnlockRow(String name, long time, boolean shared)
	{
		JPanel row = row(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

		JLabel nameLabel = new JLabel(name);
		nameLabel.setForeground(Color.WHITE);
		row.add(nameLabel, BorderLayout.CENTER);

		JLabel status = new JLabel(shared ? "Shared" : "✓");
		status.setForeground(shared ? ColorScheme.LIGHT_GRAY_COLOR : UNLOCKED);
		status.setFont(shared
			? status.getFont().deriveFont(10f) : status.getFont().deriveFont(Font.BOLD));
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

		JPanel labels = new JPanel(new BorderLayout(6, 0));
		labels.setOpaque(false);
		JLabel text = new JLabel(label);
		text.setForeground(Color.WHITE);
		labels.add(text, BorderLayout.CENTER);
		JLabel count = new JLabel(done + "/" + total);
		count.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		labels.add(count, BorderLayout.EAST);
		row.add(labels, BorderLayout.NORTH);

		JProgressBar bar = new JProgressBar(0, Math.max(total, 1));
		bar.setValue(total == 0 && complete ? 1 : done);
		bar.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 6));
		bar.setForeground(complete ? UNLOCKED : ColorScheme.BRAND_ORANGE);
		bar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.add(bar, BorderLayout.SOUTH);
		return row;
	}

	private static JPanel hierarchyProgressRow(String label, int done, int total,
		boolean complete, boolean expanded, boolean nested)
	{
		JPanel row = row(new BorderLayout(6, 0));
		styleHierarchyRow(row, expanded, nested);

		JLabel text = new JLabel(label);
		text.setForeground(Color.WHITE);
		text.setToolTipText(label);
		row.add(text, BorderLayout.CENTER);

		JPanel progress = new JPanel(new BorderLayout(6, 0));
		progress.setOpaque(false);

		JProgressBar bar = new JProgressBar(0, Math.max(total, 1));
		bar.setValue(total == 0 && complete ? 1 : done);
		bar.setPreferredSize(new Dimension(nested ? 32 : 42, 6));
		bar.setForeground(complete ? UNLOCKED : ColorScheme.BRAND_ORANGE);
		bar.setBackground(new Color(82, 82, 82));
		progress.add(bar, BorderLayout.CENTER);

		JLabel count = new JLabel(done + "/" + total);
		count.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		count.setHorizontalAlignment(JLabel.RIGHT);
		count.setPreferredSize(new Dimension(46, 16));
		if (nested)
		{
			count.setFont(count.getFont().deriveFont(11f));
		}
		progress.add(count, BorderLayout.EAST);

		row.add(progress, BorderLayout.EAST);
		return row;
	}

	private static void styleHierarchyRow(JPanel row, boolean expanded, boolean nested)
	{
		Color bronze = nested ? new Color(138, 94, 52) : new Color(153, 102, 51);
		Color background = expanded ? new Color(62, 50, 40)
			: ColorScheme.DARKER_GRAY_COLOR;
		int verticalPadding = nested ? 3 : 5;
		int leftPadding = nested ? 10 : 7;
		row.setBackground(background);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(bronze),
			BorderFactory.createEmptyBorder(
				verticalPadding, leftPadding, verticalPadding, nested ? 5 : 7)));
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
			return Math.max(visibleRect.height - 32, 16);
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
		private final List<QuestCatalog.QuestEntry> quests;
		private final List<QuestCatalog.QuestEntry> contents;
		private final List<QuestCatalog.QuestEntry> areas;
		private final List<SlayerMasterEntry> slayer;
		private final List<QuestCatalog.Requirement> allSuperiors;
		private final List<QuestCatalog.QuestEntry> rumours;
		private final List<SearchEntry> searchEntries;

		private PreparedData(List<QuestCatalog.QuestEntry> quests,
			List<QuestCatalog.QuestEntry> contents,
			List<QuestCatalog.QuestEntry> areas,
			List<SlayerMasterEntry> slayer,
			List<QuestCatalog.Requirement> allSuperiors,
			List<QuestCatalog.QuestEntry> rumours,
			List<SearchEntry> searchEntries)
		{
			this.quests = quests;
			this.contents = contents;
			this.areas = areas;
			this.slayer = slayer;
			this.allSuperiors = allSuperiors;
			this.rumours = rumours;
			this.searchEntries = Collections.unmodifiableList(searchEntries);
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
		private final List<RecentUnlocksTracker.Unlock> recentUnlocks;
		private final List<RecentUnlocksTracker.Unlock> sharedRecentUnlocks;
		private final boolean includeSlayerSuperiors;
		private final Set<String> completedQuests;
		private final int unlockedMonsters;
		private final int unlockedItems;

		private PanelSnapshot(PreparedData data, Set<String> owned, Set<String> shared,
			List<RecentUnlocksTracker.Unlock> recentUnlocks,
			List<RecentUnlocksTracker.Unlock> sharedRecentUnlocks,
			boolean includeSlayerSuperiors, Set<String> completedQuests,
			int unlockedMonsters,
			int unlockedItems)
		{
			this.data = data;
			this.owned = owned;
			this.shared = shared;
			this.recentUnlocks = recentUnlocks;
			this.sharedRecentUnlocks = sharedRecentUnlocks;
			this.includeSlayerSuperiors = includeSlayerSuperiors;
			this.completedQuests = completedQuests;
			this.unlockedMonsters = unlockedMonsters;
			this.unlockedItems = unlockedItems;
		}
	}

	private static class SharedCategory
	{
		private final String name;
		private final List<String> items;
		private final Map<String, List<String>> subcategories;

		private SharedCategory(String name, List<String> items,
			Map<String, List<String>> subcategories)
		{
			this.name = name;
			this.items = items;
			this.subcategories = subcategories;
		}

		private int size()
		{
			int count = items.size();
			for (List<String> values : subcategories.values())
			{
				count += values.size();
			}
			return count;
		}
	}

	private static class SettingsCategory
	{
		private final String name;
		private final List<SettingsSection> sections;

		private SettingsCategory(String name, List<SettingsSection> sections)
		{
			this.name = name;
			this.sections = sections;
		}

	}

	private static class SettingsSection
	{
		private final String name;
		private final String key;
		private final List<Method> items;

		private SettingsSection(String name, String key, List<Method> items)
		{
			this.name = name;
			this.key = key;
			this.items = items;
		}
	}

	private static class SearchEntry
	{
		private final String searchName;
		private final String displayName;
		private final Set<String> cards;

		private SearchEntry(String searchName, String displayName, Set<String> cards)
		{
			this.searchName = searchName;
			this.displayName = displayName;
			this.cards = cards;
		}
	}

}
