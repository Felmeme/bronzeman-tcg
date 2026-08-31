package com.bronzemantcg.feature;

import com.bronzemantcg.BronzemanTcgConfig;
import com.bronzemantcg.ownership.ActiveCardIdentityCatalog;
import com.bronzemantcg.restriction.LockState;
import com.bronzemantcg.restriction.RestrictionDecisionService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.ScriptID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;

/**
 * Coordinates locked-item opacity and the shared decision used by the item-icon overlay.
 * RuneLite event subscribers stay in the plugin; this class owns visual marking policy,
 * refresh coalescing and widget mutation.
 */
@Singleton
public final class LockedItemMarkController
{
	// Widget opacity runs 0 (solid) to 255 (invisible). This value is also our
	// signature when restoring, so another plugin's opacity is never cleared.
	static final int LOCKED_ITEM_OPACITY = 140;
	private static final int PERIODIC_REFRESH_TICKS = 5;
	private static final int[] MARK_CONTAINERS = {
		InterfaceID.Inventory.ITEMS, InterfaceID.Bankmain.ITEMS, InterfaceID.Bankside.ITEMS,
		InterfaceID.Shopmain.ITEMS, InterfaceID.Shopside.ITEMS};

	private final Client client;
	private final ClientThread clientThread;
	private final BronzemanTcgConfig config;
	private final RestrictionDecisionService restrictionDecisionService;
	private final ActiveCardIdentityCatalog activeCatalog;
	private final AtomicBoolean refreshQueued = new AtomicBoolean();
	private final AtomicLong generation = new AtomicLong();

	private volatile boolean running;
	private int tickCounter;
	private long catalogRevision;

	@Inject
	public LockedItemMarkController(Client client, ClientThread clientThread,
		BronzemanTcgConfig config, RestrictionDecisionService restrictionDecisionService,
		ActiveCardIdentityCatalog activeCatalog)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
		this.restrictionDecisionService = restrictionDecisionService;
		this.activeCatalog = activeCatalog;
	}

	public void startUp()
	{
		generation.incrementAndGet();
		refreshQueued.set(false);
		tickCounter = 0;
		catalogRevision = activeCatalog.getRevision();
		running = true;
	}

	public void shutDown()
	{
		running = false;
		long stoppedGeneration = generation.incrementAndGet();
		refreshQueued.set(false);
		clientThread.invoke(() ->
		{
			if (!running && generation.get() == stoppedGeneration)
			{
				clearMarks();
			}
		});
	}

	public void onGameTick()
	{
		if (!running)
		{
			return;
		}
		long currentRevision = activeCatalog.getRevision();
		boolean catalogChanged = isCatalogRevisionChanged(
			catalogRevision, currentRevision);
		catalogRevision = currentRevision;
		int tick = ++tickCounter;
		if (catalogChanged || isPeriodicRefreshTick(tick))
		{
			scheduleRefresh();
		}
	}

	public void onScriptPostFired(int scriptId)
	{
		if (isMarkRedrawScript(scriptId))
		{
			scheduleRefresh();
		}
	}

	public void onWidgetLoaded(int groupId)
	{
		if (isMarkContainerGroup(groupId))
		{
			scheduleRefresh();
		}
	}

	public void onConfigChanged(String group, String key)
	{
		if (shouldRefreshForConfig(group, key))
		{
			scheduleRefresh();
		}
	}

	/** Shared by opacity marking and the drawing-only icon overlay. */
	public boolean shouldMarkItem(int itemId)
	{
		return running && isMarkingActive(config.itemUsageMode(), config.lockedItemMarkMode(),
			restrictionDecisionService.isEnforcementBypassed())
			&& itemId > 0 && restrictionDecisionService.isItemLocked(itemId);
	}

	private void scheduleRefresh()
	{
		if (!running || !refreshQueued.compareAndSet(false, true))
		{
			return;
		}
		long queuedGeneration = generation.get();
		clientThread.invokeAtTickEnd(() ->
		{
			if (generation.get() != queuedGeneration)
			{
				return;
			}
			refreshQueued.set(false);
			if (running)
			{
				applyMarks();
			}
		});
	}

	private void applyMarks()
	{
		boolean marking = isMarkingActive(config.itemUsageMode(), config.lockedItemMarkMode(),
			restrictionDecisionService.isEnforcementBypassed());
		for (int componentId : MARK_CONTAINERS)
		{
			Widget container = client.getWidget(componentId);
			Widget[] children = container == null ? null : container.getChildren();
			if (children == null)
			{
				continue;
			}
			for (Widget child : children)
			{
				if (child == null || child.getItemId() <= 0)
				{
					continue;
				}
				boolean markItem = marking && restrictionDecisionService.isItemLocked(child.getItemId());
				int opacity = resolveOpacity(markItem, child.getOpacity());
				if (opacity != child.getOpacity())
				{
					child.setOpacity(opacity);
				}
			}
		}
	}

	private void clearMarks()
	{
		for (int componentId : MARK_CONTAINERS)
		{
			Widget container = client.getWidget(componentId);
			Widget[] children = container == null ? null : container.getChildren();
			if (children == null)
			{
				continue;
			}
			for (Widget child : children)
			{
				if (child != null && child.getOpacity() == LOCKED_ITEM_OPACITY)
				{
					child.setOpacity(0);
				}
			}
		}
	}

	public static boolean isMarkingActive(LockState itemUsageMode,
		LockedItemMarkMode markMode, boolean enforcementBypassed)
	{
		return itemUsageMode == LockState.LOCKED
			&& markMode != LockedItemMarkMode.OFF
			&& !enforcementBypassed;
	}

	static int resolveOpacity(boolean markItem, int currentOpacity)
	{
		if (markItem)
		{
			return LOCKED_ITEM_OPACITY;
		}
		return currentOpacity == LOCKED_ITEM_OPACITY ? 0 : currentOpacity;
	}

	static boolean isPeriodicRefreshTick(int tick)
	{
		return tick > 0 && tick % PERIODIC_REFRESH_TICKS == 0;
	}

	static boolean isCatalogRevisionChanged(long previous, long current)
	{
		return previous != current;
	}

	static boolean isMarkRedrawScript(int scriptId)
	{
		return scriptId == ScriptID.INVENTORY_DRAWITEM || scriptId == ScriptID.BANKMAIN_BUILD;
	}

	static boolean isMarkContainerGroup(int groupId)
	{
		return groupId == InterfaceID.SHOPMAIN || groupId == InterfaceID.SHOPSIDE;
	}

	static boolean shouldRefreshForConfig(String group, String key)
	{
		return BronzemanTcgConfig.GROUP.equals(group)
			&& ("itemUsageMode".equals(key) || "lockedItemMarkMode".equals(key));
	}
}
