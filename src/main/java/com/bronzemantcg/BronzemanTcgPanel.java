package com.bronzemantcg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
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

/**
 * Sidebar panel: card lookup, nearby NPC lock states, and collection progress meters.
 *
 * Threading contract: everything here runs on the Swing EDT. The catalogs are immutable
 * after load and TcgCollectionReader is synchronized, so reading them from the EDT is
 * safe; the one thing that must NOT happen here is touching live game state - the nearby
 * list is gathered on the client thread by the plugin and handed over as a snapshot.
 */
class BronzemanTcgPanel extends PluginPanel
{
	private static final int MAX_SEARCH_RESULTS = 20;
	private static final int MAX_NEARBY_ROWS = 15;
	private static final Color UNLOCKED = ColorScheme.PROGRESS_COMPLETE_COLOR;
	private static final Color LOCKED = ColorScheme.PROGRESS_ERROR_COLOR;

	private final TrackedMonsterCatalog monsterCatalog;
	private final TrackedItemCatalog itemCatalog;
	private final ResourceNodeCatalog nodeCatalog;
	private final QuestCatalog questCatalog;
	private final TcgCollectionReader collectionReader;

	private final IconTextField searchBar = new IconTextField();
	private final JPanel searchResults = sectionBody();
	private final JPanel nearbyList = sectionBody();
	private final JPanel progressList = sectionBody();
	private final JPanel questList = sectionBody();
	private final JLabel questsHeader = sectionHeader("Quests ▸");
	private boolean questsExpanded;
	private final Set<String> expandedQuests = new HashSet<>();

	BronzemanTcgPanel(TrackedMonsterCatalog monsterCatalog, TrackedItemCatalog itemCatalog,
		ResourceNodeCatalog nodeCatalog, QuestCatalog questCatalog, TcgCollectionReader collectionReader)
	{
		this.monsterCatalog = monsterCatalog;
		this.itemCatalog = itemCatalog;
		this.nodeCatalog = nodeCatalog;
		this.questCatalog = questCatalog;
		this.collectionReader = collectionReader;

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

		add(searchBar);
		add(Box.createVerticalStrut(4));
		add(searchResults);
		add(sectionHeader("Nearby"));
		add(nearbyList);
		add(sectionHeader("Progress"));
		add(progressList);
		questsHeader.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		questsHeader.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				questsExpanded = !questsExpanded;
				refreshQuests();
			}
		});
		add(questsHeader);
		add(questList);

		refreshProgress();
		refreshQuests();
	}

	// ------------------------------------------------------------------ quests

	private void refreshQuests()
	{
		questList.removeAll();
		List<QuestCatalog.QuestEntry> quests = questCatalog.getQuests();
		Set<String> owned = collectionReader.getOwnedCardNamesLowerCase();

		int completable = 0;
		for (QuestCatalog.QuestEntry quest : quests)
		{
			if (quest.satisfiedCount(owned) == quest.requirements.size())
			{
				completable++;
			}
		}
		questsHeader.setText(String.format("Quests %s  %d/%d completable",
			questsExpanded ? "▾" : "▸", completable, quests.size()));

		if (questsExpanded)
		{
			if (quests.isEmpty())
			{
				questList.add(mutedRow("No quest data bundled"));
			}
			// Completable first, then fewest missing, then alphabetical.
			List<QuestCatalog.QuestEntry> sorted = new ArrayList<>(quests);
			sorted.sort(Comparator
				.comparingInt((QuestCatalog.QuestEntry q) -> q.requirements.size() - q.satisfiedCount(owned))
				.thenComparing(q -> q.name, String.CASE_INSENSITIVE_ORDER));
			for (QuestCatalog.QuestEntry quest : sorted)
			{
				questList.add(questRow(quest, owned));
				if (expandedQuests.contains(quest.name))
				{
					for (QuestCatalog.Requirement requirement : quest.requirements)
					{
						boolean have = requirement.isSatisfied(owned);
						questList.add(requirementRow(requirement, have));
					}
					if (quest.requirements.isEmpty())
					{
						questList.add(mutedRow("  No card-backed requirements"));
					}
				}
			}
		}
		questList.revalidate();
		questList.repaint();
	}

	private JPanel questRow(QuestCatalog.QuestEntry quest, Set<String> owned)
	{
		int have = quest.satisfiedCount(owned);
		int total = quest.requirements.size();
		JPanel row = progressRow(quest.name + (quest.miniquest ? " (mini)" : ""), have, Math.max(total, 0));
		if (total == 0)
		{
			row.setToolTipText("No card-backed requirements - always completable");
		}
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (!expandedQuests.remove(quest.name))
				{
					expandedQuests.add(quest.name);
				}
				refreshQuests();
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

	// ------------------------------------------------------------------ nearby

	static class NearbyEntry
	{
		final String name;
		final int distance;

		NearbyEntry(String name, int distance)
		{
			this.name = name;
			this.distance = distance;
		}
	}

	/** Called on the EDT with a snapshot the plugin gathered on the client thread. */
	void updateNearby(List<NearbyEntry> entries)
	{
		if (questsExpanded)
		{
			refreshQuests();
		}
		nearbyList.removeAll();
		Set<String> owned = collectionReader.getOwnedCardNamesLowerCase();
		int shown = 0;
		for (NearbyEntry entry : entries)
		{
			if (++shown > MAX_NEARBY_ROWS)
			{
				break;
			}
			Set<String> variants = monsterCatalog.getCardVariantsLowerCase(entry.name);
			boolean unlocked = ownsAny(owned, variants);
			nearbyList.add(statusRow(entry.name + "  (" + entry.distance + ")", unlocked,
				unlocked ? null : String.join(" / ", variants)));
		}
		if (entries.isEmpty())
		{
			nearbyList.add(mutedRow("No tracked NPCs loaded nearby"));
		}
		nearbyList.revalidate();
		nearbyList.repaint();
		refreshProgress();
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

		progressList.add(sectionHeader("Slayer masters"));
		for (Map.Entry<String, ResourceNodeCatalog.Rule> e : distinctRules("slayer").entrySet())
		{
			int total = 0;
			int have = 0;
			for (ResourceNodeCatalog.CardGroup group : e.getValue().groups)
			{
				if ("monsters".equals(group.role))
				{
					total++;
					if (group.isSatisfied(owned))
					{
						have++;
					}
				}
			}
			progressList.add(progressRow(display(e.getKey()), have, total));
		}

		progressList.add(sectionHeader("Rumour masters"));
		for (Map.Entry<String, ResourceNodeCatalog.Rule> e : distinctRules("hunter-rumours").entrySet())
		{
			int total = e.getValue().groups.size();
			int have = 0;
			for (ResourceNodeCatalog.CardGroup group : e.getValue().groups)
			{
				if (group.isSatisfied(owned))
				{
					have++;
				}
			}
			progressList.add(progressRow(display(e.getKey()), have, total));
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

	private static JPanel sectionBody()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		return panel;
	}

	private static JLabel sectionHeader(String title)
	{
		JLabel label = new JLabel(title);
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setBorder(BorderFactory.createEmptyBorder(10, 0, 4, 0));
		return label;
	}

	private static JPanel statusRow(String name, boolean unlocked, String missingCards)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));

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
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private static JLabel mutedRow(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
		return label;
	}

	private static JPanel progressRow(String label, int done, int total)
	{
		JPanel row = new JPanel(new BorderLayout(0, 2));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

		JLabel text = new JLabel(label + "  " + done + "/" + total);
		text.setForeground(Color.WHITE);
		row.add(text, BorderLayout.NORTH);

		JProgressBar bar = new JProgressBar(0, Math.max(total, 1));
		bar.setValue(done);
		bar.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 6));
		bar.setForeground(done >= total ? UNLOCKED : ColorScheme.BRAND_ORANGE);
		bar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.add(bar, BorderLayout.SOUTH);

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
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
