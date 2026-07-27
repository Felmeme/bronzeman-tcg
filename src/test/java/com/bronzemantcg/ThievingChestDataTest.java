package com.bronzemantcg;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ThievingChestDataTest
{
	private final ResourceNodeCatalog catalog = new ResourceNodeCatalog(new Gson());

	@Test
	public void objectIdsDistinguishChestsWithTheSameDisplayName()
	{
		ResourceNodeCatalog.Rule coins = catalog.find(
			"object", "Chest", "Search for traps", 11735);
		ResourceNodeCatalog.Rule nature = catalog.find(
			"object", "Chest", "Search for traps", 11736);

		assertNotNull(coins);
		assertNotNull(nature);
		assertEquals(List.of("Coins"), missing(coins, Collections.emptySet(), false));
		assertEquals(List.of("Nature rune / Coins"),
			missing(nature, Collections.emptySet(), false));
		assertNull(catalog.find("object", "Chest", "Search for traps", 999999));
	}

	@Test
	public void anyOfAndAllUseTheSameChestLootGroup()
	{
		ResourceNodeCatalog.Rule nature = catalog.find(
			"object", "Chest", "Search for traps", 11736);

		assertEquals(Collections.emptyList(), missing(nature, Set.of("coins"), false));
		assertEquals(List.of("Nature rune"), missing(nature, Set.of("coins"), true));
	}

	@Test
	public void supportsUnderwaterAndModernPicklockChests()
	{
		assertNotNull(catalog.find("object", "Chest", "Search", 30971));
		assertNotNull(catalog.find("object", "Chest", "Picklock", 54773));
		assertNotNull(catalog.find("object", "Chest", "Pick-lock", 22681));
		assertNotNull(catalog.find("object", "Reinforced chest", "Picklock", 60517));
	}

	private List<String> missing(ResourceNodeCatalog.Rule rule, Set<String> owned,
		boolean requireAll)
	{
		return rule.missingRequirements(owned, Collections.emptySet(), requireAll);
	}
}
