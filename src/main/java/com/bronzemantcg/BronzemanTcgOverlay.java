package com.bronzemantcg;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

/**
 * Greys out NPCs whose card hasn't been collected: a translucent dark fill over the
 * model's convex hull, the inverse of the usual highlight overlay. Runs on the client
 * thread each frame, so the per-NPC work stays to two map lookups.
 */
@Singleton
class BronzemanTcgOverlay extends Overlay
{
	private static final Color LOCKED_TINT = new Color(60, 60, 60, 140);

	private final Client client;
	private final BronzemanTcgConfig config;
	private final TrackedMonsterCatalog monsterCatalog;
	private final TcgCollectionReader collectionReader;

	@Inject
	BronzemanTcgOverlay(Client client, BronzemanTcgConfig config,
		TrackedMonsterCatalog monsterCatalog, TcgCollectionReader collectionReader)
	{
		this.client = client;
		this.config = config;
		this.monsterCatalog = monsterCatalog;
		this.collectionReader = collectionReader;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		// Hidden entities never render, so tinting them would just paint over scenery.
		if (!config.tintLockedNpcs() || config.hideLockedEntities())
		{
			return null;
		}

		Set<String> owned = collectionReader.getOwnedCardNamesLowerCase();
		graphics.setColor(LOCKED_TINT);
		for (NPC npc : client.getNpcs())
		{
			if (npc == null || !isLocked(npc, owned))
			{
				continue;
			}
			Shape hull = npc.getConvexHull();
			if (hull != null)
			{
				graphics.fill(hull);
			}
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
