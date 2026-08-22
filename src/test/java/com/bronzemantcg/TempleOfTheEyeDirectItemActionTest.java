package com.bronzemantcg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class TempleOfTheEyeDirectItemActionTest
{
	private final ResourceNodeCatalog catalog = new ResourceNodeCatalog(new Gson());

	@Test
	public void placeCellRequiresWeakCell()
	{
		assertWeakCellRule("Inactive cell tile", "Place-cell");
	}

	@Test
	public void bothEssencePilesRequireWeakCellForAssemble()
	{
		assertWeakCellRule("Essence pile (elemental)", "Assemble");
		assertWeakCellRule("Essence pile (catalytic)", "Assemble");
	}

	private void assertWeakCellRule(String object, String option)
	{
		ResourceNodeCatalog.Rule rule = catalog.find(
			ResourceNodeCatalog.KIND_OBJECT, object, option);
		assertNotNull(rule);
		assertEquals("item-usage", rule.category);
		assertEquals(List.of("Weak cell"), rule.missingRequirements(
			Collections.emptySet(), Collections.emptySet(), false));
	}
}
