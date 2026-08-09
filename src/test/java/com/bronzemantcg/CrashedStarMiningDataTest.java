package com.bronzemantcg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class CrashedStarMiningDataTest
{
	private static final int[] CRASHED_STAR_IDS =
	{
		41020, 41021, 41223, 41224, 41225, 41226, 41227, 41228, 41229
	};

	private final ResourceNodeCatalog catalog = new ResourceNodeCatalog(new Gson());

	@Test
	public void everyCrashedStarTierRequiresStardustWhenMining()
	{
		ResourceNodeCatalog.Rule rule = catalog.find(
			ResourceNodeCatalog.KIND_OBJECT, "Crashed Star", "Mine");
		assertNotNull(rule);
		assertEquals("mining", rule.category);
		assertEquals(List.of("Stardust"),
			rule.missingRequirements(Collections.emptySet(), Collections.emptySet(), false));
		assertEquals(Collections.emptyList(),
			rule.missingRequirements(Set.of("stardust"), Collections.emptySet(), false));

		for (int objectId : CRASHED_STAR_IDS)
		{
			assertSame(rule, catalog.find(ResourceNodeCatalog.KIND_OBJECT,
				"Crashed Star", "Mine", objectId));
		}
	}

	@Test
	public void prospectRemainsUnrestricted()
	{
		assertNull(catalog.find(ResourceNodeCatalog.KIND_OBJECT,
			"Crashed Star", "Prospect"));
	}
}
