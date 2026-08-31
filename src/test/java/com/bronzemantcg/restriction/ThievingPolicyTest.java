package com.bronzemantcg.restriction;

import com.bronzemantcg.catalog.ResourceNodeCatalog;
import com.bronzemantcg.restriction.ThievingPolicy.HamPickpocketingMode;
import com.bronzemantcg.restriction.ThievingPolicy.StallThievingMode;
import com.bronzemantcg.restriction.ThievingPolicy.ThievingMode;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ThievingPolicyTest
{
	private final ResourceNodeCatalog catalog = new ResourceNodeCatalog(new Gson());

	@Test
	public void persistedEnumNamesAndLabelsRemainStable()
	{
		assertEquals(ThievingMode.COINS, ThievingMode.valueOf("COINS"));
		assertEquals("Coins + NPC", ThievingMode.COINS_NPC.toString());
		assertEquals(HamPickpocketingMode.MEMBER_CARD,
			HamPickpocketingMode.valueOf("MEMBER_CARD"));
		assertEquals("Member + Outfit", HamPickpocketingMode.MEMBER_AND_OUTFIT.toString());
		assertEquals(StallThievingMode.ANY_OF, StallThievingMode.valueOf("ANY_OF"));
		assertEquals("All", StallThievingMode.REQUIRE_ALL.toString());
	}

	@Test
	public void modesOwnTheirRoleAndRequirementBehavior()
	{
		assertFalse(ThievingMode.OFF.isEnabled());
		assertTrue(ThievingMode.NPC_CARD_ONLY.isNpcOnly());
		assertEquals(Set.of("npc", "loot", "loot-ham", "loot-elf"),
			ThievingMode.COINS.excludedRoles());
		assertEquals(Set.of("loot", "loot-ham"),
			HamPickpocketingMode.MEMBER_CARD.excludedRoles());
		assertFalse(StallThievingMode.OFF.isEnabled());
		assertTrue(StallThievingMode.REQUIRE_ALL.requiresAll());
	}

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
		assertTrue(ItemInteractionService.shouldHideWorldObjectCategory("woodcutting"));
		assertFalse(ItemInteractionService.shouldHideWorldObjectCategory("mining"));
		assertFalse(ItemInteractionService.shouldHideWorldObjectCategory("runecrafting"));
		assertFalse(ItemInteractionService.shouldHideWorldObjectCategory("thieving-stalls"));
		assertFalse(ItemInteractionService.shouldHideWorldObjectCategory("thieving-chests"));
	}

	@Test
	public void masterFarmerUsesOnlyItsDedicatedFullLootSetting()
	{
		List<String> seeds = catalog.getMasterFarmerSeedCards();
		assertEquals(45, seeds.size());
		assertEquals(Collections.emptyList(),
			ThievingPolicy.missingMasterFarmerRequirements(false, seeds, card -> false));
		assertEquals(seeds,
			ThievingPolicy.missingMasterFarmerRequirements(true, seeds, card -> false));
		assertEquals(Collections.emptyList(),
			ThievingPolicy.missingMasterFarmerRequirements(true, seeds, card -> true));
		assertEquals(Collections.emptyList(),
			ThievingPolicy.missingMasterFarmerRequirements(true,
				Collections.emptyList(), card -> false));
	}
}
