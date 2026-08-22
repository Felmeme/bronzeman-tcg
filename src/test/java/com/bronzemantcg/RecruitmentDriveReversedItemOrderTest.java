package com.bronzemantcg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Set;
import org.junit.Test;

public class RecruitmentDriveReversedItemOrderTest
{
	@Test
	public void cakeTinIsCheckedInEitherItemOnItemOrder()
	{
		Set<String> locked = Set.of("Cake tin");
		assertEquals("Cake tin", BronzemanTcgPlugin.firstBlockedItem(
			"Cake tin", "Knife", locked::contains));
		assertEquals("Cake tin", BronzemanTcgPlugin.firstBlockedItem(
			"Knife", "Cake tin", locked::contains));
	}

	@Test
	public void unlockedPairRemainsAllowed()
	{
		assertNull(BronzemanTcgPlugin.firstBlockedItem(
			"Knife", "Cake tin", ignored -> false));
	}
}
