package com.bronzemantcg;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.text.NumberFormat;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

/**
 * Plain-text stats overlay: OSRS TCG credits and distinct cards collected, read from the
 * same decoded state the restrictions use (displayed with the TCG creator's blessing).
 * Renders nothing when osrs-tcg has no readable state, so it never shows misleading
 * zeros on accounts without the TCG plugin. Movable like any RuneLite overlay (alt+drag).
 */
@Singleton
class TcgStatsOverlay extends OverlayPanel
{
	private static final NumberFormat FORMAT = NumberFormat.getIntegerInstance(Locale.UK);
	// Total cards in the OSRS TCG catalog; matches the TCG plugin's own total.
	// Update by hand if osrs-tcg ever grows its Card.json.
	private static final int TOTAL_CARDS = 6376;
	private final BronzemanTcgConfig config;
	private final TcgCollectionReader collectionReader;

	@Inject
	TcgStatsOverlay(BronzemanTcgConfig config, TcgCollectionReader collectionReader)
	{
		this.config = config;
		this.collectionReader = collectionReader;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showTcgStatsOverlay() || !collectionReader.isStateAvailable())
		{
			return null;
		}
		panelComponent.getChildren().clear();
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Credits:")
			.right(FORMAT.format(collectionReader.getCredits()))
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Cards Collected:")
			.right(FORMAT.format(collectionReader.getOwnedCardCount()) + "/" + FORMAT.format(TOTAL_CARDS))
			.build());
		return super.render(graphics);
	}
}
