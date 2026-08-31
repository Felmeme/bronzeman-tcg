package com.bronzemantcg.panel;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RecentUnlocksTrackerTest
{
	@Test
	public void recentDiffIgnoresBaselineSeenAndReconnects()
	{
		Set<String> baseline = set("dragon axe");
		Set<String> seen = set("dragon axe", "rune axe");
		assertEquals(Collections.singletonList("abyssal whip"),
			RecentUnlocksTracker.newSharedNames(
				set("dragon axe", "rune axe", "abyssal whip"), baseline, seen));
		assertTrue(RecentUnlocksTracker.newSharedNames(
			set("dragon axe", "rune axe"), Collections.emptySet(), seen).isEmpty());
	}

	private static Set<String> set(String... values)
	{
		return new HashSet<>(Arrays.asList(values));
	}
}
