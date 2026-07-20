package com.bronzemantcg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.LayoutManager;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * Sidebar panel: card search, collection progress, and collapsible readiness checklists
 * for quests, slayer masters, PvM content and hunter rumour masters.
 *
 * Threading contract: everything here runs on the Swing EDT. The catalogs are immutable
 * after load and TcgCollectionReader is synchronized, so reading them from the EDT is
 * safe; live game state is never touched here. {@link #refresh()} is called periodically
 * from the plugin (via SwingUtilities.invokeLater) so unlocks show without reopening.
 */
class BronzemanTcgPanel extends PluginPanel
{
	private static final int MAX_SEARCH_RESULTS = 20;
	private static final Color UNLOCKED = ColorScheme.PROGRESS_COMPLETE_COLOR;
	private static final Color LOCKED = ColorScheme.PROGRESS_ERROR_COLOR;

	private final TrackedMonsterCatalog monsterCatalog;
	private final TrackedItemCatalog itemCatalog;
	private final ResourceNodeCatalog nodeCatalog;
	private final QuestCatalog questCatalog;
	private final ContentCatalog contentCatalog;
	private final TcgCollectionReader collectionReader;
	private final BronzemanTcgConfig config;

	private final IconTextField searchBar = new IconTextField();
	private final JPanel searchResults = sectionBody();
	private final JPanel progressList = sectionBody();

	// One list per tab. MaterialTabGroup swaps the selected list into tabDisplay, so the
	// old per-section collapse state is gone - a tab is either shown or it isn't.
	private final JPanel tabDisplay = new JPanel(new BorderLayout());
	private final MaterialTabGroup tabs = new MaterialTabGroup(tabDisplay);

	private final JPanel questList = sectionBody();
	private final Set<String> expandedQuests = new HashSet<>();

	private final JPanel slayerList = sectionBody();
	private final Set<String> expandedSlayer = new HashSet<>();

	private final JPanel contentList = sectionBody();
	private final Set<String> expandedContents = new HashSet<>();

	private final JPanel rumoursList = sectionBody();
	private final Set<String> expandedRumours = new HashSet<>();

	BronzemanTcgPanel(TrackedMonsterCatalog monsterCatalog, TrackedItemCatalog itemCatalog,
		ResourceNodeCatalog nodeCatalog, QuestCatalog questCatalog, ContentCatalog contentCatalog,
		TcgCollectionReader collectionReader, BronzemanTcgConfig config)
	{
		this.monsterCatalog = monsterCatalog;
		this.itemCatalog = itemCatalog;
		this.nodeCatalog = nodeCatalog;
		this.questCatalog = questCatalog;
		this.contentCatalog = contentCatalog;
		this.collectionReader = collectionReader;
		this.config = config;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

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
		tabs.setAlignmentX(Component.LEFT_ALIGNMENT);
		tabDisplay.setAlignmentX(Component.LEFT_ALIGNMENT);
		tabDisplay.setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(searchBar);
		add(Box.createVerticalStrut(4));
		add(searchResults);

		add(sectionHeader("Progress"));
		add(progressList);

		// Labels stay short: four tabs share the fixed 225px panel width.
		add(Box.createVerticalStrut(10));
		addTab("Quests", questList);
		addTab("Slayer", slayerList);
		addTab("PvM", contentList);
		addTab("Rumours", rumoursList);
		tabs.select(tabs.getTab(0));
		add(tabs);
		add(Box.createVerticalStrut(4));
		add(tabDisplay);

		refresh();
	}

	private void addTab(String title, JPanel content)
	{
		content.setAlignmentX(Component.LEFT_ALIGNMENT);
		tabs.addTab(new MaterialTab(title, tabs, content));
	}

	/** Periodic re-render so newly unlocked cards show in every section and count. */
	void refresh()
	{
		refreshProgress();
		refreshQuests();
		refreshSlayer();
		refreshContent();
		refreshRumours();
	}

	// ------------------------------------------------------------------ collapsible checklists

	private void refreshQuests()
	{
		refreshChecklist(questList, "quests completable",
			questCatalog.getQuests(), expandedQuests, this::refreshQuests, "No quest data bundled");
	}

	private void refreshContent()
	{
		refreshChecklist(contentList, "contents completable",
			contentCatalog.getContents(), expandedContents, this::refreshContent, "No content data bundled");
	}

	private void refreshSlayer()
	{
		refreshChecklist(slayerList, "masters ready",
			buildMasterEntries("slayer"), expandedSlayer, this::refreshSlayer, "No slayer data bundled");
	}

	private void refreshRumours()
	{
		refreshChecklist(rumoursList, "masters ready",
			buildMasterEntries("hunter-rumours"), expandedRumours, this::refreshRumours, "No rumour data bundled");
	}

	/**
	 * Adapts slayer / rumour master rules into the same QuestEntry shape the checklist
	 * renders. Slayer masters show their assignable-monster cards (plus superiors when the
	 * config stacks them on, mirroring the restriction); rumour masters show every creature.
	 */
	private List<QuestCatalog.QuestEntry> buildMasterEntries(String category)
	{
		boolean slayer = "slayer".equals(category);
		boolean countSuperiors = slayer
			&& config.restrictSlayerMonsters() && config.restrictSlayerSuperiors();
		List<QuestCatalog.QuestEntry> entries = new ArrayList<>();
		for (Map.Entry<String, ResourceNodeCatalog.Rule> e : distinctRules(category).entrySet())
		{
			List<QuestCatalog.Requirement> reqs = new ArrayList<>();
			for (ResourceNodeCatalog.CardGroup group : e.getValue().groups)
			{
				boolean include = slayer
					? "monsters".equals(group.role) || (countSuperiors && "superiors".equals(group.role))
					: true;
				if (include && !group.displayCards.isEmpty())
				{
					reqs.add(new QuestCatalog.Requirement(group.displayCards.get(0), group.displayCards));
				}
			}
			entries.add(new QuestCatalog.QuestEntry(display(e.getKey()), false, reqs, ""));
		}
		return entries;
	}

	private void refreshChecklist(JPanel container, String summaryNoun,
		List<QuestCatalog.QuestEntry> entries, Set<String> expandedNames, Runnable refresh, String emptyText)
	{
		container.removeAll();
		Set<String> owned = collectionReader.getOwnedCardNamesLowerCase();

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
		// Strictly alphabetical (owner ruling 2026-07-21). The previous ordering keyed on
		// how many requirements were still missing, which meant every card pulled silently
		// reshuffled the list; a name never moves.
		List<QuestCatalog.QuestEntry> sorted = new ArrayList<>(entries);
		sorted.sort(Comparator.comparing(q -> q.name, String.CASE_INSENSITIVE_ORDER));
		for (QuestCatalog.QuestEntry entry : sorted)
		{
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
		JPanel row = progressRow(entry.name + (entry.miniquest ? " (mini)" : ""), have, Math.max(total, 0));
		if (!entry.notes.isEmpty())
		{
			row.setToolTipText(entry.notes);
		}
		else if (total == 0)
		{
			row.setToolTipText("No card-backed requirements - always completable");
		}
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (!expandedNames.remove(entry.name))
				{
					expandedNames.add(entry.name);
				}
				refresh.run();
			}
		});
		return row;
	}

	private JPanel requirementRow(QuestCatalog.Requirement requirement, boolean have)
	{
		String alternatives = requirement.displayCards.size() > 1
			? ": " + String.join(" / ", requirement.displayCards)
			: "";
		JPanel row = statusRow("  " + requirement.label + alternatives, have, null);
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		return row;
	}

	// ------------------------------------------------------------------ search

	private void refreshSearch()
	{
		searchResults.removeAll();
		String query = searchBar.getText() == null ? "" : searchBar.getText().trim().toLowerCase(Locale.ROOT);
		if (query.length() >= 2)
		{
			Set<String> owned = collectionReader.getOwnedCardNamesLowerCase();
			int shown = 0;
			// NPCs first, then items; TreeMap for stable alphabetical results.
			Map<String, Set<String>> matches = new LinkedHashMap<>();
			for (Map.Entry<String, Set<String>> e : new TreeMap<>(monsterCatalog.getEntityToCards()).entrySet())
			{
				if (e.getKey().contains(query))
				{
					matches.put(e.getKey() + " (npc)", e.getValue());
				}
			}
			for (Map.Entry<String, Set<String>> e : new TreeMap<>(itemCatalog.getEntityToCards()).entrySet())
			{
				if (e.getKey().contains(query))
				{
					matches.put(e.getKey(), e.getValue());
				}
			}
			for (Map.Entry<String, Set<String>> e : matches.entrySet())
			{
				if (++shown > MAX_SEARCH_RESULTS)
				{
					searchResults.add(mutedRow("...and " + (matches.size() - MAX_SEARCH_RESULTS) + " more"));
					break;
				}
				boolean unlocked = ownsAny(owned, e.getValue());
				searchResults.add(statusRow(display(e.getKey()), unlocked,
					unlocked ? null : String.join(" / ", e.getValue())));
			}
			if (matches.isEmpty())
			{
				searchResults.add(mutedRow("No tracked NPC or item matches"));
			}
		}
		searchResults.revalidate();
		searchResults.repaint();
	}

	// ------------------------------------------------------------------ progress

	private void refreshProgress()
	{
		progressList.removeAll();
		Set<String> owned = collectionReader.getOwnedCardNamesLowerCase();

		progressList.add(progressRow("NPCs unlocked",
			countUnlocked(monsterCatalog.getEntityToCards(), owned), monsterCatalog.size()));
		progressList.add(progressRow("Items unlocked",
			countUnlocked(itemCatalog.getEntityToCards(), owned), itemCatalog.size()));

		List<String> seeds = nodeCatalog.getMasterFarmerSeedCards();
		if (!seeds.isEmpty())
		{
			int have = 0;
			for (String seed : seeds)
			{
				if (owned.contains(seed.toLowerCase(Locale.ROOT)))
				{
					have++;
				}
			}
			progressList.add(progressRow("Master Farmer seeds", have, seeds.size()));
		}

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

	private static JLabel mutedRow(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private static JPanel progressRow(String label, int done, int total)
	{
		JPanel row = row(new BorderLayout(0, 2));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));

		JLabel text = new JLabel(label + "  " + done + "/" + total);
		text.setForeground(Color.WHITE);
		row.add(text, BorderLayout.NORTH);

		JProgressBar bar = new JProgressBar(0, Math.max(total, 1));
		bar.setValue(done);
		bar.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 6));
		bar.setForeground(done >= total ? UNLOCKED : ColorScheme.BRAND_ORANGE);
		bar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.add(bar, BorderLayout.SOUTH);
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

}
