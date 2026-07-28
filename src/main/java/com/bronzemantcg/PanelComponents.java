package com.bronzemantcg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.LayoutManager;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Stateless Swing building blocks shared by the side-panel views.
 *
 * Keeping visual primitives here lets {@link BronzemanTcgPanel} coordinate
 * navigation and data without also owning every row's paint and sizing rules.
 */
final class PanelComponents
{
	private static final Color UNLOCKED = ColorScheme.PROGRESS_COMPLETE_COLOR;
	private static final Color LOCKED = ColorScheme.PROGRESS_ERROR_COLOR;
	private static final DateTimeFormatter UNLOCK_TIME_FORMAT = DateTimeFormatter
		.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault());

	private PanelComponents()
	{
	}

	/**
	 * Row container for a BoxLayout column: height tracks the live preferred height, so
	 * rows never stretch or jitter when a list is rebuilt.
	 */
	static JPanel row(LayoutManager layout)
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

	static JPanel sectionBody()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	static JLabel sectionHeader(String title)
	{
		JLabel label = new JLabel(title);
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setBorder(BorderFactory.createEmptyBorder(10, 0, 4, 0));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	static JPanel statusRow(String name, boolean unlocked, String missingCards)
	{
		JPanel row = row(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

		JLabel nameLabel = new JLabel(name);
		nameLabel.setForeground(Color.WHITE);
		row.add(nameLabel, BorderLayout.CENTER);

		JLabel status = new JLabel(unlocked ? "\u2713" : "\u2717");
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

	static JPanel recentUnlockRow(String name, long time, boolean shared)
	{
		JPanel row = row(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

		JLabel nameLabel = new JLabel(name);
		nameLabel.setForeground(Color.WHITE);
		row.add(nameLabel, BorderLayout.CENTER);

		JLabel status = new JLabel(shared ? "Shared" : "\u2713");
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

	static JLabel mutedRow(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	static JPanel listDivider()
	{
		JPanel divider = new JPanel();
		divider.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		divider.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 2));
		divider.setMinimumSize(new Dimension(0, 2));
		divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
		divider.setAlignmentX(Component.LEFT_ALIGNMENT);
		return divider;
	}

	static void addSpacedDivider(JPanel container)
	{
		container.add(Box.createVerticalStrut(2));
		container.add(listDivider());
		container.add(Box.createVerticalStrut(3));
	}

	static JPanel progressRow(String label, int done, int total)
	{
		return progressRow(label, done, total, done >= total);
	}

	static JPanel progressRow(String label, int done, int total, boolean complete)
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

	static JPanel hierarchyProgressRow(String label, int done, int total,
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

	static void styleHierarchyRow(JPanel row, boolean expanded, boolean nested)
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

	/**
	 * Swing mouse events do not bubble from child labels and progress bars.
	 */
	static void makeClickable(Component component, Runnable action)
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
}
