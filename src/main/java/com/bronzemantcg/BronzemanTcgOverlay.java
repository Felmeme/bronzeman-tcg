package com.bronzemantcg;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import net.runelite.client.util.Text;

/**
 * Marks NPCs whose card hasn't been collected with a grey model outline (the same
 * silhouette-hugging renderer NPC Indicators uses, in "disabled grey" rather than a
 * highlight colour). Runs on the client thread each frame, so the per-NPC work stays
 * to two map lookups.
 */
@Singleton
class BronzemanTcgOverlay extends Overlay
{
	private final Client client;
	private final BronzemanTcgConfig config;
	private final TrackedMonsterCatalog monsterCatalog;
	private final TcgCollectionReader collectionReader;
	private final ModelOutlineRenderer modelOutlineRenderer;

	@Inject
	BronzemanTcgOverlay(Client client, BronzemanTcgConfig config,
		TrackedMonsterCatalog monsterCatalog, TcgCollectionReader collectionReader,
		ModelOutlineRenderer modelOutlineRenderer)
	{
		this.client = client;
		this.config = config;
		this.monsterCatalog = monsterCatalog;
		this.collectionReader = collectionReader;
		this.modelOutlineRenderer = modelOutlineRenderer;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		// Hidden entities never render, so outlining them would just draw over scenery.
		if (!config.tintLockedNpcs() || config.npcVisibilityMode() == NpcVisibilityMode.HIDE)
		{
			return null;
		}

		Set<String> owned = collectionReader.getOwnedCardNamesLowerCase();
		Color color = config.lockedOutlineColor();
		int width = config.lockedOutlineWidth();
		int feather = config.lockedOutlineFeather();
		for (NPC npc : client.getNpcs())
		{
			if (npc == null || !isLocked(npc, owned))
			{
				continue;
			}
			modelOutlineRenderer.drawOutline(npc, width, color, feather);
		}
		return null;
	}

	private boolean isLocked(NPC npc, Set<String> owned)
	{
		NPCComposition composition = npc.getTransformedComposition();
		String name = composition != null ? composition.getName() : npc.getName();
		if (name == null)
		{
			return false;
		}
		Set<String> variants = monsterCatalog.getCardVariantsLowerCase(Text.removeTags(name).trim());
		if (variants.isEmpty())
		{
			return false; // untracked -> never restricted, never tinted
		}
		for (String card : variants)
		{
			if (owned.contains(card))
			{
				return false;
			}
		}
		return true;
	}
}
