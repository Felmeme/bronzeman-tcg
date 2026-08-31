package com.bronzemantcg.feature;

import com.bronzemantcg.ownership.TcgOwnershipSnapshot;
import com.bronzemantcg.restriction.RestrictionDecisionTestSupport;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Set;
import net.runelite.api.TileItem;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GroundItemLockTrackerTest
{
	private GroundItemLockTracker tracker;
	private RestrictionDecisionTestSupport.Harness decisions;

	@Before
	public void setUp()
	{
		decisions = RestrictionDecisionTestSupport.harness();
		tracker = new GroundItemLockTracker(decisions.getService());
	}

	@Test
	public void tracksUntracksAndClearsGroundItems()
	{
		TileItem item = tileItem(6739);
		tracker.track(item, null, "Dragon axe");
		assertTrue(tracker.getTrackedItems().containsKey(item));
		assertTrue(tracker.getTrackedItems().get(item).isBlocked());

		tracker.untrack(item);
		assertTrue(tracker.getTrackedItems().isEmpty());

		tracker.track(item, null, "Dragon axe");
		tracker.clear();
		assertTrue(tracker.getTrackedItems().isEmpty());
	}

	@Test
	public void authoritativeVariantIdUnlocksParentCard()
	{
		TileItem potion = tileItem(121);
		TcgOwnershipSnapshot ownedDose = TcgOwnershipSnapshot.fromApi(
			Collections.emptyList(), Collections.singletonList(123),
			Collections.emptyList(), null);
		decisions.ownership(ownedDose);
		tracker.track(potion, null, "Attack potion(3)");
		assertFalse(tracker.getTrackedItems().get(potion).isBlocked());
	}

	@Test
	public void refreshAppliesSharedAndExemptParentCards()
	{
		TileItem item = tileItem(6739);
		TcgOwnershipSnapshot emptyIds = TcgOwnershipSnapshot.fromApi(
			Collections.emptyList(), Collections.emptyList(),
			Collections.emptyList(), null);
		decisions.ownership(emptyIds);
		tracker.track(item, null, "Dragon axe");
		assertTrue(tracker.getTrackedItems().get(item).isBlocked());

		decisions.shared(Set.of("Dragon axe"));
		tracker.refresh();
		assertFalse(tracker.getTrackedItems().get(item).isBlocked());

		decisions.shared(none()).configuredExempt(Set.of("Dragon axe"));
		tracker.refresh();
		assertFalse(tracker.getTrackedItems().get(item).isBlocked());
	}

	@Test
	public void unreviewedAndNamelessItemsFailOpen()
	{
		TileItem unknown = tileItem(999999);
		TileItem nameless = tileItem(6739);
		tracker.track(unknown, null, "Unknown item");
		tracker.track(nameless, null, "");
		assertFalse(tracker.getTrackedItems().get(unknown).isBlocked());
		assertFalse(tracker.getTrackedItems().get(nameless).isBlocked());
	}

	private static Set<String> none()
	{
		return Collections.emptySet();
	}

	private static TileItem tileItem(int id)
	{
		return (TileItem) Proxy.newProxyInstance(TileItem.class.getClassLoader(),
			new Class<?>[]{TileItem.class}, (proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getId":
						return id;
					case "hashCode":
						return System.identityHashCode(proxy);
					case "equals":
						return proxy == args[0];
					default:
						Class<?> type = method.getReturnType();
						if (type == boolean.class)
						{
							return false;
						}
						if (type == int.class)
						{
							return 0;
						}
						return null;
				}
			});
	}
}
