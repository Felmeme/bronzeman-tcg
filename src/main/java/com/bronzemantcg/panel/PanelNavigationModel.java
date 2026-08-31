package com.bronzemantcg.panel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure payload/config-driven navigation state shared by the Swing panel and its tests. */
public final class PanelNavigationModel
{
	private PanelNavigationModel()
	{
	}

	public static State resolve(boolean v1Capable, boolean sharedEnabled,
		boolean showBetaTab)
	{
		List<List<Tab>> rows = new ArrayList<>();
		rows.add(pair(Tab.ACTIVITIES, Tab.SLAYER_PVM));
		if (!v1Capable)
		{
			rows.add(showBetaTab
				? pair(Tab.RECENT, Tab.BETA)
				: Collections.singletonList(Tab.RECENT));
			if (sharedEnabled)
			{
				rows.add(Collections.singletonList(Tab.SHARED));
			}
		}
		else
		{
			if (showBetaTab && !sharedEnabled)
			{
				rows.add(pair(Tab.RECENT, Tab.BETA));
				rows.add(Collections.singletonList(Tab.COLLECTION));
			}
			else
			{
				rows.add(pair(Tab.COLLECTION, Tab.RECENT));
				if (showBetaTab)
				{
					rows.add(pair(Tab.BETA, Tab.SHARED));
				}
				else if (sharedEnabled)
				{
					rows.add(Collections.singletonList(Tab.SHARED));
				}
			}
		}

		List<Tab> visible = new ArrayList<>();
		for (List<Tab> row : rows)
		{
			visible.addAll(row);
		}
		return new State(v1Capable, visible, rows);
	}

	private static List<Tab> pair(Tab left, Tab right)
	{
		List<Tab> pair = new ArrayList<>();
		pair.add(left);
		pair.add(right);
		return pair;
	}

	public enum Tab
	{
		ACTIVITIES,
		SLAYER_PVM,
		RECENT,
		COLLECTION,
		SHARED,
		BETA
	}

	public static final class State
	{
		private final boolean v1Capable;
		private final List<Tab> visibleTabs;
		private final List<List<Tab>> rows;

		private State(boolean v1Capable, List<Tab> visibleTabs, List<List<Tab>> rows)
		{
			this.v1Capable = v1Capable;
			this.visibleTabs = Collections.unmodifiableList(new ArrayList<>(visibleTabs));
			List<List<Tab>> rowCopy = new ArrayList<>();
			for (List<Tab> row : rows)
			{
				rowCopy.add(Collections.unmodifiableList(new ArrayList<>(row)));
			}
			this.rows = Collections.unmodifiableList(rowCopy);
		}

		public boolean isVisible(Tab tab)
		{
			return visibleTabs.contains(tab);
		}

		public List<Tab> getVisibleTabs()
		{
			return visibleTabs;
		}

		/** Approved one- or two-button rows for the fixed-width side panel. */
		public List<List<Tab>> getRows()
		{
			return rows;
		}

		/** Returns the approved safe destination when a selected tab becomes hidden. */
		public Tab selectionAfterHiding(Tab hiddenTab)
		{
			if (hiddenTab == Tab.BETA && v1Capable && isVisible(Tab.COLLECTION))
			{
				return Tab.COLLECTION;
			}
			if (hiddenTab == Tab.COLLECTION && isVisible(Tab.BETA))
			{
				return Tab.BETA;
			}
			return Tab.ACTIVITIES;
		}
	}
}
