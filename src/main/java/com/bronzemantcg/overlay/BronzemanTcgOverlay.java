package com.bronzemantcg.overlay;

import com.bronzemantcg.BronzemanTcgConfig;
import com.bronzemantcg.feature.GroundItemLockTracker;
import com.bronzemantcg.restriction.RestrictionDecisionService;
import com.bronzemantcg.restriction.NpcVisibilityMode;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Marks NPCs whose card hasn't been collected with a grey model outline (the same
 * silhouette-hugging renderer NPC Indicators uses, in "disabled grey" rather than a
 * highlight colour). Runs on the client thread each frame and delegates the lock decision
 * to the same ID-aware boundary as NPC enforcement.
 */
@Singleton
public class BronzemanTcgOverlay extends Overlay
{
	private final Client client;
	private final BronzemanTcgConfig config;
	private final ModelOutlineRenderer modelOutlineRenderer;
	private final RestrictionDecisionService restrictionDecisionService;
	private final GroundItemLockTracker groundItemLockTracker;

	@Inject
	public BronzemanTcgOverlay(Client client, BronzemanTcgConfig config,
		ModelOutlineRenderer modelOutlineRenderer,
		RestrictionDecisionService restrictionDecisionService,
		GroundItemLockTracker groundItemLockTracker)
	{
		this.client = client;
		this.config = config;
		this.modelOutlineRenderer = modelOutlineRenderer;
		this.restrictionDecisionService = restrictionDecisionService;
		this.groundItemLockTracker = groundItemLockTracker;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		// Hidden entities never render, so outlining them would just draw over scenery.
		boolean outlineNpcs = config.tintLockedNpcs()
			&& config.npcVisibilityMode() != NpcVisibilityMode.HIDE
			&& !restrictionDecisionService.isEnforcementBypassed();
		// Deliberately not gated on groundItemsMode: the outline says "you do not own this
		// card", which stays true and useful even when pickup itself is allowed.
		boolean outlineGroundItems = config.tintLockedGroundItems()
			&& !restrictionDecisionService.isEnforcementBypassed();
		if (!outlineNpcs && !outlineGroundItems)
		{
			return null;
		}

		// NPCs live on the WorldView now (multi-world support); the old Client.getNpcs()
		// is a deprecated pass-through. Top level = the player's own world. Null before
		// the world exists (login screen), where there is nothing to outline anyway.
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return null;
		}
		Color color = config.lockedOutlineColor();
		int width = config.lockedOutlineWidth();
		int feather = config.lockedOutlineFeather();
		if (outlineNpcs)
		{
			for (NPC npc : worldView.npcs())
			{
				if (!restrictionDecisionService.isNpcLocked(npc))
				{
					continue;
				}
				modelOutlineRenderer.drawOutline(npc, width, color, feather);
			}
		}

		if (outlineGroundItems)
		{
			// blocked is precomputed, so this does no name lookups or allocation per frame.
			for (Map.Entry<TileItem, GroundItemLockTracker.TrackedGroundItem> entry
				: groundItemLockTracker.getTrackedItems().entrySet())
			{
				GroundItemLockTracker.TrackedGroundItem tracked = entry.getValue();
				if (!tracked.isBlocked() || tracked.getTile() == null
					|| tracked.getTile().getItemLayer() == null)
				{
					continue;
				}
				modelOutlineRenderer.drawOutline(
					tracked.getTile().getItemLayer(), entry.getKey(), width, color, feather);
			}
		}
		return null;
	}
}
