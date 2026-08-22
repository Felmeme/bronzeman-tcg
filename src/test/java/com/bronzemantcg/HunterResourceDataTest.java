package com.bronzemantcg;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HunterResourceDataTest
{
	private final ResourceNodeCatalog catalog = new ResourceNodeCatalog(new Gson());

	@Test
	public void toolsOnlyDataUsesTheApprovedHunterTools()
	{
		assertMissing("inventory", "Bird snare", "lay", Set.of("monster", "loot", "output"),
			List.of("Bird snare"));
		assertMissing("npc", "Black warlock", "catch", Set.of("output"),
			List.of("Butterfly net / Magic butterfly net", "Butterfly jar"));
		assertMissing("npc", "Baby impling", "catch", Set.of("output"),
			List.of("Magic butterfly net", "Impling jar"));
		assertMissing("npc", "Horned graahk", "tease", Set.of("monster", "loot"),
			List.of("Teasing stick"));
	}

	@Test
	public void groundBirdSnareLayReusesTheInventoryActivityRule()
	{
		assertTrue(BronzemanTcgPlugin.isGroundPlacementOption("Lay"));
		assertTrue(BronzemanTcgPlugin.isGroundPlacementOption("<col=ff9040>Lay</col>"));
		assertNotNull(catalog.find(ResourceNodeCatalog.KIND_INVENTORY,
			"Bird snare", "Lay"));
	}

	@Test
	public void allCardsDataAddsImplingAndGuaranteedPitfallOutputs()
	{
		assertMissing("npc", "Baby impling", "catch", Collections.emptySet(),
			List.of("Magic butterfly net", "Impling jar", "Baby impling jar"));
		assertMissing("npc", "Horned graahk", "tease", Collections.emptySet(),
			List.of("Teasing stick", "Horned graahk", "Big bones", "Raw graahk"));
		assertMissing("npc", "Spined larupia", "tease", Collections.emptySet(),
			List.of("Teasing stick", "Big bones", "Raw larupia"));
	}

	private void assertMissing(String kind, String name, String option, Set<String> excluded,
		List<String> expected)
	{
		ResourceNodeCatalog.Rule rule = catalog.find(kind, name, option);
		assertNotNull(rule);
		assertEquals(expected, rule.missingRequirements(Collections.emptySet(), excluded, false));
	}
}
