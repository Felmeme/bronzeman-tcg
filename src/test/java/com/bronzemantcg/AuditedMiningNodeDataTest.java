package com.bronzemantcg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class AuditedMiningNodeDataTest
{
	private final ResourceNodeCatalog catalog = new ResourceNodeCatalog(new Gson());

	@Test
	public void cardBackedAuditedNodesRequireTheirExactYieldCards()
	{
		assertMiningRule("Dense runestone", "Chip", "Dense essence block");
		assertMiningRule("Calcified rocks", "Mine", "Blessed bone shards");
		assertMiningRule("Lead rocks", "Mine", "Lead ore");
		assertMiningRule("Nickel rocks", "Mine", "Nickel ore");
		assertMiningRule("Soft clay rocks", "Mine", "Soft clay");
		assertMiningRule("Rubium deposit", "Mine", "Rubium geode");
	}

	@Test
	public void distinctUncardedRubiumRockRouteRemainsUnrestricted()
	{
		assertNull(catalog.find(ResourceNodeCatalog.KIND_OBJECT,
			"Rubium rocks", "Mine"));
	}

	@Test
	public void denseRunestoneUsesItsActualChipOption()
	{
		assertNull(catalog.find(ResourceNodeCatalog.KIND_OBJECT,
			"Dense runestone", "Mine"));
	}

	private void assertMiningRule(String objectName, String option, String card)
	{
		ResourceNodeCatalog.Rule rule = catalog.find(
			ResourceNodeCatalog.KIND_OBJECT, objectName, option);
		assertNotNull(objectName, rule);
		assertEquals("mining", rule.category);
		assertEquals(List.of(card), rule.missingRequirements(
			Collections.emptySet(), Collections.emptySet(), false));
		assertEquals(Collections.emptyList(), rule.missingRequirements(
			Set.of(card.toLowerCase()), Collections.emptySet(), false));
	}
}
