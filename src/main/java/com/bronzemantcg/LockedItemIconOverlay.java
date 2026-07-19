package com.bronzemantcg;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Draws a small bank-filler badge centred on locked items when the mark mode asks
 * for it. The badge rides on top of the widget-opacity fade applied by the plugin
 * (see applyLockedItemMarks there) - this overlay draws, it never mutates widgets.
 */
@Singleton
class LockedItemIconOverlay extends WidgetItemOverlay
{
	// Owner's spec: centred, ~60% of the sprite so the faded item stays identifiable.
	private static final double ICON_SCALE = 0.6;

	private final BronzemanTcgConfig config;
	private final BronzemanTcgPlugin plugin;
	private final ItemManager itemManager;

	@Inject
	LockedItemIconOverlay(BronzemanTcgConfig config, BronzemanTcgPlugin plugin, ItemManager itemManager)
	{
		this.config = config;
		this.plugin = plugin;
		this.itemManager = itemManager;
		showOnInventory();
		showOnBank();
		showOnInterfaces(
			net.runelite.api.gameval.InterfaceID.SHOPMAIN,
			net.runelite.api.gameval.InterfaceID.SHOPSIDE);
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (config.lockedItemMarkMode() != LockedItemMarkMode.TRANSPARENT_ICON
			|| !plugin.shouldMarkLocked(itemId))
		{
			return;
		}
		// Item sprites carry their own transparency, so this drops straight on top.
		// AsyncBufferedImage draws blank until the sprite loads; it self-corrects
		// on a later frame, so no load callback is needed.
		BufferedImage icon = itemManager.getImage(ItemID.BANK_FILLER);
		if (icon == null)
		{
			return;
		}
		Rectangle bounds = widgetItem.getCanvasBounds();
		int w = (int) (bounds.width * ICON_SCALE);
		int h = (int) (bounds.height * ICON_SCALE);
		graphics.drawImage(icon,
			bounds.x + (bounds.width - w) / 2,
			bounds.y + (bounds.height - h) / 2,
			w, h, null);
	}
}
