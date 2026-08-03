package com.bronzemantcg;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/** Small Swing primitives shared by the compact settings view. */
final class PanelComponents
{
	private PanelComponents()
	{
	}

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

	static void styleHierarchyRow(JPanel row, boolean expanded, boolean nested)
	{
		styleHierarchyRow(row, expanded, nested, nested ? 10 : 7);
	}

	/**
	 * Same look with an explicit left inset. The side-panel tabs nest four levels deep
	 * where the settings view only nests two, so they carry their own indentation while
	 * sharing these colours - keep this the single definition of the bronze row style.
	 */
	static void styleHierarchyRow(JPanel row, boolean expanded, boolean nested,
		int leftPadding)
	{
		Color bronze = nested ? new Color(138, 94, 52) : new Color(153, 102, 51);
		Color background = expanded ? new Color(62, 50, 40)
			: ColorScheme.DARKER_GRAY_COLOR;
		int verticalPadding = nested ? 3 : 5;
		row.setBackground(background);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(bronze),
			BorderFactory.createEmptyBorder(
				verticalPadding, leftPadding, verticalPadding, nested ? 5 : 7)));
	}

	/** Swing mouse events do not bubble from child labels. */
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
