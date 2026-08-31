package com.bronzemantcg.panel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PanelNavigationModelTest
{
	@Test
	public void preV1KeepsCollectionHiddenAndOptionalTabsVisible()
	{
		PanelNavigationModel.State state = PanelNavigationModel.resolve(false, true, true);

		assertEquals(Arrays.asList(
			PanelNavigationModel.Tab.ACTIVITIES,
			PanelNavigationModel.Tab.SLAYER_PVM,
			PanelNavigationModel.Tab.RECENT,
			PanelNavigationModel.Tab.BETA,
			PanelNavigationModel.Tab.SHARED), state.getVisibleTabs());
		assertFalse(state.isVisible(PanelNavigationModel.Tab.COLLECTION));
	}

	@Test
	public void v1CapabilityAddsCollectionWithoutRemovingBetaHistory()
	{
		PanelNavigationModel.State state = PanelNavigationModel.resolve(true, true, true);

		assertEquals(Arrays.asList(
			PanelNavigationModel.Tab.ACTIVITIES,
			PanelNavigationModel.Tab.SLAYER_PVM,
			PanelNavigationModel.Tab.COLLECTION,
			PanelNavigationModel.Tab.RECENT,
			PanelNavigationModel.Tab.BETA,
			PanelNavigationModel.Tab.SHARED), state.getVisibleTabs());
	}

	@Test
	public void optionalTabsCanBeHiddenIndependently()
	{
		PanelNavigationModel.State state = PanelNavigationModel.resolve(false, false, false);

		assertFalse(state.isVisible(PanelNavigationModel.Tab.SHARED));
		assertFalse(state.isVisible(PanelNavigationModel.Tab.BETA));
		assertEquals(Arrays.asList(
			PanelNavigationModel.Tab.ACTIVITIES,
			PanelNavigationModel.Tab.SLAYER_PVM,
			PanelNavigationModel.Tab.RECENT), state.getVisibleTabs());
	}

	@Test
	public void hiddenBetaUsesCollectionOnlyWhenV1Capable()
	{
		PanelNavigationModel.State preV1 = PanelNavigationModel.resolve(false, true, false);
		PanelNavigationModel.State v1 = PanelNavigationModel.resolve(true, true, false);

		assertEquals(PanelNavigationModel.Tab.ACTIVITIES,
			preV1.selectionAfterHiding(PanelNavigationModel.Tab.BETA));
		assertEquals(PanelNavigationModel.Tab.COLLECTION,
			v1.selectionAfterHiding(PanelNavigationModel.Tab.BETA));
	}

	@Test
	public void capabilityLossUsesBetaWhenAvailableOtherwiseActivities()
	{
		PanelNavigationModel.State withBeta = PanelNavigationModel.resolve(false, true, true);
		PanelNavigationModel.State withoutBeta = PanelNavigationModel.resolve(false, true, false);

		assertEquals(PanelNavigationModel.Tab.BETA,
			withBeta.selectionAfterHiding(PanelNavigationModel.Tab.COLLECTION));
		assertEquals(PanelNavigationModel.Tab.ACTIVITIES,
			withoutBeta.selectionAfterHiding(PanelNavigationModel.Tab.COLLECTION));
		assertTrue(withBeta.isVisible(PanelNavigationModel.Tab.BETA));
	}

	@Test
	public void hiddenSharedAlwaysReturnsToActivities()
	{
		PanelNavigationModel.State state = PanelNavigationModel.resolve(true, false, true);

		assertEquals(PanelNavigationModel.Tab.ACTIVITIES,
			state.selectionAfterHiding(PanelNavigationModel.Tab.SHARED));
	}

	@Test
	public void preV1RowsKeepSharedFullWidth()
	{
		assertEquals(Arrays.asList(
			Arrays.asList(PanelNavigationModel.Tab.ACTIVITIES,
				PanelNavigationModel.Tab.SLAYER_PVM),
			Arrays.asList(PanelNavigationModel.Tab.RECENT,
				PanelNavigationModel.Tab.BETA),
			Arrays.asList(PanelNavigationModel.Tab.SHARED)),
			PanelNavigationModel.resolve(false, true, true).getRows());
		assertEquals(Arrays.asList(
			Arrays.asList(PanelNavigationModel.Tab.ACTIVITIES,
				PanelNavigationModel.Tab.SLAYER_PVM),
			Arrays.asList(PanelNavigationModel.Tab.RECENT),
			Arrays.asList(PanelNavigationModel.Tab.SHARED)),
			PanelNavigationModel.resolve(false, true, false).getRows());
	}

	@Test
	public void v1RowsUseApprovedPositionsForEveryOptionalCombination()
	{
		assertEquals(Arrays.asList(
			Arrays.asList(PanelNavigationModel.Tab.ACTIVITIES,
				PanelNavigationModel.Tab.SLAYER_PVM),
			Arrays.asList(PanelNavigationModel.Tab.COLLECTION,
				PanelNavigationModel.Tab.RECENT),
			Arrays.asList(PanelNavigationModel.Tab.BETA,
				PanelNavigationModel.Tab.SHARED)),
			PanelNavigationModel.resolve(true, true, true).getRows());
		assertEquals(Arrays.asList(
			Arrays.asList(PanelNavigationModel.Tab.ACTIVITIES,
				PanelNavigationModel.Tab.SLAYER_PVM),
			Arrays.asList(PanelNavigationModel.Tab.RECENT,
				PanelNavigationModel.Tab.BETA),
			Arrays.asList(PanelNavigationModel.Tab.COLLECTION)),
			PanelNavigationModel.resolve(true, false, true).getRows());
		assertEquals(Arrays.asList(
			Arrays.asList(PanelNavigationModel.Tab.ACTIVITIES,
				PanelNavigationModel.Tab.SLAYER_PVM),
			Arrays.asList(PanelNavigationModel.Tab.COLLECTION,
				PanelNavigationModel.Tab.RECENT),
			Arrays.asList(PanelNavigationModel.Tab.SHARED)),
			PanelNavigationModel.resolve(true, true, false).getRows());
		assertEquals(Arrays.asList(
			Arrays.asList(PanelNavigationModel.Tab.ACTIVITIES,
				PanelNavigationModel.Tab.SLAYER_PVM),
			Arrays.asList(PanelNavigationModel.Tab.COLLECTION,
				PanelNavigationModel.Tab.RECENT)),
			PanelNavigationModel.resolve(true, false, false).getRows());
	}

	@Test
	public void everyVisibilityCombinationPlacesEachVisibleTabOnce()
	{
		for (boolean v1Capable : Arrays.asList(false, true))
		{
			for (boolean sharedEnabled : Arrays.asList(false, true))
			{
				for (boolean showBeta : Arrays.asList(false, true))
				{
					PanelNavigationModel.State state = PanelNavigationModel.resolve(
						v1Capable, sharedEnabled, showBeta);
					List<PanelNavigationModel.Tab> flattened = new ArrayList<>();
					for (List<PanelNavigationModel.Tab> row : state.getRows())
					{
						assertTrue(row.size() == 1 || row.size() == 2);
						flattened.addAll(row);
					}
					assertEquals(state.getVisibleTabs().size(),
						new HashSet<>(flattened).size());
					assertEquals(new HashSet<>(state.getVisibleTabs()),
						new HashSet<>(flattened));
				}
			}
		}
	}
}
