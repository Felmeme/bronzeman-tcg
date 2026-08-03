package com.bronzemantcg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class ThievingModePolicyTest
{
	private final ResourceNodeCatalog catalog = new ResourceNodeCatalog(new Gson());

	@Test
	public void npcOnlyReadsOnlyTheNpcRole()
	{
		ResourceNodeCatalog.Rule farmer = catalog.find("npc", "Farmer", "Pickpocket");
		assertNotNull(farmer);
		assertEquals(List.of("Farmer"),
			farmer.missingRequirementsForRole(Collections.emptySet(), "npc"));
		assertEquals(Collections.emptyList(),
			farmer.missingRequirementsForRole(Set.of("farmer"), "npc"));

		ResourceNodeCatalog.Rule anaire = catalog.find("npc", "Anaire", "Pickpocket");
		assertNotNull(anaire);
		assertEquals(Collections.emptyList(),
			anaire.missingRequirementsForRole(Collections.emptySet(), "npc"));
	}

	@Test
	public void onlyWoodcuttingHidesWorldObjectOptions()
	{
		assertTrue(BronzemanTcgPlugin.shouldHideWorldObjectCategory("woodcutting"));
		assertFalse(BronzemanTcgPlugin.shouldHideWorldObjectCategory("mining"));
		assertFalse(BronzemanTcgPlugin.shouldHideWorldObjectCategory("runecrafting"));
		assertFalse(BronzemanTcgPlugin.shouldHideWorldObjectCategory("thieving-stalls"));
		assertFalse(BronzemanTcgPlugin.shouldHideWorldObjectCategory("thieving-chests"));
	}
}
