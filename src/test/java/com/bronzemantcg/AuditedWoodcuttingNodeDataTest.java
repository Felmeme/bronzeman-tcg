package com.bronzemantcg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class AuditedWoodcuttingNodeDataTest
{
	private final ResourceNodeCatalog catalog = new ResourceNodeCatalog(new Gson());

	@Test
	public void exactCardBackedTreeRoutesRequireTheirLogCards()
	{
		assertTree("Mature juniper tree", 27499, "Juniper logs");
		assertTree("Camphor tree", 58557, "Camphor logs");
		assertTree("Ironwood tree", 58559, "Ironwood logs");
		assertTree("Rosewood tree", 58561, "Rosewood logs");
	}

	@Test
	public void uncardedJatobaRouteRemainsUnrestricted()
	{
		assertNull(catalog.find(ResourceNodeCatalog.KIND_OBJECT,
			"Jatoba tree", "Chop down", 58555));
	}

	private void assertTree(String objectName, int objectId, String card)
	{
		ResourceNodeCatalog.Rule rule = catalog.find(ResourceNodeCatalog.KIND_OBJECT,
			objectName, "Chop down", objectId);
		assertNotNull(objectName, rule);
		assertEquals("woodcutting", rule.category);
		assertEquals(List.of(card), rule.missingRequirements(
			Collections.emptySet(), Collections.emptySet(), false));
		assertEquals(Collections.emptyList(), rule.missingRequirements(
			Set.of(card.toLowerCase()), Collections.emptySet(), false));
	}
}
