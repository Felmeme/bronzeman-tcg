package com.bronzemantcg.ownership;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SharedUnlockStoreTest
{
	@Test
	public void sourcesReplaceAndUnionWithoutDuplicates()
	{
		SharedUnlockStore store = new SharedUnlockStore();
		assertTrue(store.put("TCG Locked", Arrays.asList("Dragon axe", "Rune axe")));
		assertTrue(store.put("Other", Arrays.asList("Rune axe", "Abyssal whip")));
		assertEquals(set("dragon axe", "rune axe", "abyssal whip"),
			store.getSharedCardNamesLowerCase());

		assertTrue(store.put("TCG Locked", Collections.singletonList("Dragon pickaxe")));
		assertEquals(set("dragon pickaxe", "rune axe", "abyssal whip"),
			store.getSharedCardNamesLowerCase());
		assertFalse(store.put("TCG Locked", Collections.singletonList("Dragon pickaxe")));
	}

	@Test
	public void emptyPayloadWithdrawsOnlyItsSource()
	{
		SharedUnlockStore store = new SharedUnlockStore();
		store.put("TCG Locked", Collections.singletonList("Dragon axe"));
		store.put("Other", Collections.singletonList("Rune axe"));
		assertTrue(store.put("TCG Locked", Collections.emptyList()));
		assertEquals(Collections.singleton("rune axe"), store.getSharedCardNamesLowerCase());
	}

	@Test
	public void clearDropsEverySourceAndCanBeRepeated()
	{
		SharedUnlockStore store = new SharedUnlockStore();
		store.put("TCG Locked", Collections.singletonList("Dragon axe"));
		store.put("Other", Collections.singletonList("Rune axe"));

		store.clear();
		assertTrue(store.getSharedCardNamesLowerCase().isEmpty());

		store.clear();
		assertTrue(store.getSharedCardNamesLowerCase().isEmpty());
	}

	private static Set<String> set(String... values)
	{
		return new HashSet<>(Arrays.asList(values));
	}
}
