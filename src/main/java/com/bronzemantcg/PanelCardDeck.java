package com.bronzemantcg;

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.JPanel;
import javax.swing.Scrollable;

/** CardLayout which sizes itself to the currently visible side-panel tab. */
final class PanelCardDeck<K extends Enum<K>> extends JPanel implements Scrollable
{
	private final Map<K, Component> cards;
	private Component selected;

	PanelCardDeck(Class<K> keyType)
	{
		super(new CardLayout());
		cards = new EnumMap<>(keyType);
	}

	void addCard(K key, Component component)
	{
		cards.put(key, component);
		add(component, key.name());
		if (selected == null)
		{
			selected = component;
		}
	}

	void showCard(K key)
	{
		Component component = cards.get(key);
		if (component == null)
		{
			return;
		}
		selected = component;
		((CardLayout) getLayout()).show(this, key.name());
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
