package com.bronzemantcg.feature;

import com.bronzemantcg.restriction.RestrictionDecisionService;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;

/**
 * Tracks visible ground items and caches their central ownership decision for the scene overlay.
 * RuneLite events remain subscribed in the plugin; this collaborator owns only lifecycle state.
 */
@Singleton
public final class GroundItemLockTracker
{
	private final RestrictionDecisionService restrictionDecisionService;
	private final Map<TileItem, TrackedGroundItem> trackedItems = new HashMap<>();
	private final Map<TileItem, TrackedGroundItem> trackedItemsView =
		Collections.unmodifiableMap(trackedItems);
	private RestrictionDecisionService.ItemContext itemContext;

	@Inject
	public GroundItemLockTracker(RestrictionDecisionService restrictionDecisionService)
	{
		this.restrictionDecisionService = restrictionDecisionService;
	}

	public void track(TileItem item, Tile tile, String name)
	{
		if (item == null)
		{
			return;
		}
		TrackedGroundItem tracked = new TrackedGroundItem(item.getId(), tile, name);
		RestrictionDecisionService.ItemContext current =
			restrictionDecisionService.getItemContext();
		tracked.blocked = isLocked(tracked, current);
		trackedItems.put(item, tracked);
	}

	public void untrack(TileItem item)
	{
		if (item != null)
		{
			trackedItems.remove(item);
		}
	}

	public void clear()
	{
		trackedItems.clear();
		itemContext = null;
	}

	/** Re-evaluate only when one of the immutable/identity-stable decision sources changes. */
	public void refresh()
	{
		RestrictionDecisionService.ItemContext current =
			restrictionDecisionService.getItemContext();
		if (current == itemContext)
		{
			return;
		}
		itemContext = current;
		for (TrackedGroundItem tracked : trackedItems.values())
		{
			tracked.blocked = isLocked(tracked, current);
		}
	}

	public Map<TileItem, TrackedGroundItem> getTrackedItems()
	{
		return trackedItemsView;
	}

	private boolean isLocked(TrackedGroundItem tracked,
		RestrictionDecisionService.ItemContext context)
	{
		if (tracked.name == null || tracked.name.isEmpty())
		{
			return false;
		}
		return restrictionDecisionService.isItemLocked(
			tracked.itemId, tracked.name, context);
	}

	public static final class TrackedGroundItem
	{
		private final int itemId;
		private final Tile tile;
		private final String name;
		private boolean blocked;

		private TrackedGroundItem(int itemId, Tile tile, String name)
		{
			this.itemId = itemId;
			this.tile = tile;
			this.name = name;
		}

		public Tile getTile()
		{
			return tile;
		}

		public boolean isBlocked()
		{
			return blocked;
		}
	}
}
