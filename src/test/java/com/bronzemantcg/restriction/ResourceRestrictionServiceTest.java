package com.bronzemantcg.restriction;

import com.bronzemantcg.catalog.CardRequirement;
import com.bronzemantcg.catalog.ResourceNodeCatalog;
import com.bronzemantcg.restriction.ThievingPolicy.HamPickpocketingMode;
import com.bronzemantcg.restriction.ThievingPolicy.StallThievingMode;
import com.bronzemantcg.restriction.ThievingPolicy.ThievingMode;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ResourceRestrictionServiceTest
{
	@Test
	public void gatheringUsesOneCarriedStateSnapshotAndCurrentOwnership()
	{
		Harness harness = new Harness();
		harness.sources.miningMode = MiningMode.TOOL_ONLY;
		harness.service.updateCarriedState(Set.of("Rune pickaxe"), Set.of(), Set.of(), Set.of());

		List<String> missing = harness.service.evaluate(
			ResourceNodeCatalog.KIND_OBJECT, "Adamantite rocks", "Mine");
		assertTrue(missing.contains("Rune pickaxe"));

		harness.owned.add("rune pickaxe");
		assertNull(harness.service.evaluate(
			ResourceNodeCatalog.KIND_OBJECT, "Adamantite rocks", "Mine"));
	}

	@Test
	public void runecraftingChecksTheActualCarriedEssenceVariant()
	{
		Harness harness = new Harness();
		harness.sources.runecraftingMode = RunecraftingMode.TALISMAN;
		harness.owned.add("air talisman");
		harness.service.updateCarriedState(Set.of(), Set.of(), Set.of(), Set.of("pure essence"));

		List<String> missing = harness.service.evaluate(
			ResourceNodeCatalog.KIND_OBJECT, "Air altar", "Craft-rune", 34760);
		assertTrue(missing.contains("Pure essence"));

		harness.owned.add("pure essence");
		assertNull(harness.service.evaluate(
			ResourceNodeCatalog.KIND_OBJECT, "Air altar", "Craft-rune", 34760));
	}

	@Test
	public void gotrAndDisabledModesRemainFailOpenAtTheirExistingBoundaries()
	{
		Harness harness = new Harness();
		assertNull(harness.service.evaluate(
			ResourceNodeCatalog.KIND_OBJECT, "Air altar", "Craft-rune"));
		assertNull(harness.service.evaluate(
			ResourceNodeCatalog.KIND_OBJECT, "No such node", "Use"));

		harness.sources.runecraftingMode = RunecraftingMode.TALISMAN;
		harness.sources.gotr = true;
		assertNull(harness.service.evaluate(
			ResourceNodeCatalog.KIND_OBJECT, "Air altar", "Craft-rune", 34760));
	}

	@Test
	public void masterFarmerOnlyUsesItsDedicatedFullLootSetting()
	{
		Harness harness = new Harness();
		assertNull(harness.service.evaluateMasterFarmer());

		harness.sources.masterFarmerFullLoot = true;
		List<String> missing = harness.service.evaluateMasterFarmer();
		assertFalse(missing.isEmpty());
	}

	@Test
	public void fishingAndHunterFamiliesUseTheirCategoryModes()
	{
		Harness harness = new Harness();
		harness.sources.fishingMode = FishingRestrictionMode.TOOL_ONLY;
		assertFalse(harness.service.evaluate(ResourceNodeCatalog.KIND_FISHING_SPOT,
			"SHRIMP", "Net").isEmpty());

		harness.sources.hunterMode = HunterMode.TOOLS_ONLY;
		assertFalse(harness.service.evaluate(ResourceNodeCatalog.KIND_INVENTORY,
			"Bird snare", "Lay").isEmpty());
		assertFalse(harness.service.evaluate(ResourceNodeCatalog.KIND_NPC,
			"Black warlock", "Catch").isEmpty());
	}

	@Test
	public void barehandedHunterLevelsRemoveOnlyToolRequirements()
	{
		Harness harness = new Harness();
		harness.sources.hunterMode = HunterMode.TOOLS_ONLY;
		harness.sources.boostedHunterLevel = 54;
		assertFalse(harness.service.evaluate(ResourceNodeCatalog.KIND_NPC,
			"Black warlock", "Catch").isEmpty());

		harness.sources.boostedHunterLevel = 55;
		assertNull(harness.service.evaluate(ResourceNodeCatalog.KIND_NPC,
			"Black warlock", "Catch"));

		harness.sources.hunterMode = HunterMode.ALL_CARDS;
		harness.sources.boostedHunterLevel = 27;
		assertEquals(List.of("Baby impling"), harness.service.evaluate(
			ResourceNodeCatalog.KIND_NPC, "Baby impling", "Catch"));
	}

	@Test
	public void hamMembersUseTheirDedicatedModeWithoutCoinsOrPouch()
	{
		Harness harness = new Harness();
		harness.sources.thievingMode = ThievingMode.REQUIRE_ALL;
		assertNull(harness.service.evaluate(
			ResourceNodeCatalog.KIND_NPC, "H.A.M. Member", "Pickpocket"));

		harness.sources.hamPickpocketingMode = HamPickpocketingMode.MEMBER_CARD;
		assertEquals(List.of("H.A.M. Member"), harness.service.evaluate(
			ResourceNodeCatalog.KIND_NPC, "H.A.M. Member", "Pickpocket"));

		harness.owned.add("h.a.m. member");
		assertNull(harness.service.evaluate(
			ResourceNodeCatalog.KIND_NPC, "H.A.M. Member", "Pickpocket"));

		harness.sources.hamPickpocketingMode = HamPickpocketingMode.MEMBER_AND_OUTFIT;
		List<String> outfit = harness.service.evaluate(
			ResourceNodeCatalog.KIND_NPC, "H.A.M. Member", "Pickpocket");
		assertEquals(7, outfit.size());
		assertTrue(outfit.contains("Ham boots"));
		assertFalse(outfit.contains("Coins"));
		assertFalse(outfit.contains("Coin pouch"));

		harness.sources.hamPickpocketingMode = HamPickpocketingMode.FULL_LOOT;
		List<String> fullLoot = harness.service.evaluate(
			ResourceNodeCatalog.KIND_NPC, "H.A.M. Member", "Pickpocket");
		assertEquals(37, fullLoot.size());
		assertTrue(fullLoot.contains("Bronze arrow"));
		assertFalse(fullLoot.contains("Coins"));
		assertFalse(fullLoot.contains("Coin pouch"));
	}

	@Test
	public void shootingStarsRequireStardustOnlyInToolsAndOreMode()
	{
		Harness harness = new Harness();
		harness.sources.miningMode = MiningMode.TOOL_ONLY;
		assertNull(harness.service.evaluate(ResourceNodeCatalog.KIND_OBJECT,
			"Crashed Star", "Mine"));

		harness.sources.miningMode = MiningMode.CARD_REQUIRED;
		assertEquals(List.of("Stardust"), harness.service.evaluate(
			ResourceNodeCatalog.KIND_OBJECT, "Crashed Star", "Mine"));
		harness.owned.add("stardust");
		assertNull(harness.service.evaluate(ResourceNodeCatalog.KIND_OBJECT,
			"Crashed Star", "Mine"));
	}

	@Test
	public void thievingCookingAndFarmingModesSelectTheirExistingRoles()
	{
		Harness harness = new Harness();
		harness.sources.thievingMode = ThievingMode.NPC_CARD_ONLY;
		assertFalse(harness.service.evaluate(ResourceNodeCatalog.KIND_NPC,
			"Farmer", "Pickpocket").isEmpty());

		harness.sources.cookingMode = CookingMode.INPUT_ONLY;
		List<String> cooking = harness.service.evaluate(ResourceNodeCatalog.KIND_INTERFACE,
			"Anchovies", ResourceNodeCatalog.ANY_OPTION);
		assertTrue(cooking.contains("Raw anchovies"));
		assertFalse(cooking.contains("Anchovies"));

		harness.sources.cookingMode = CookingMode.INPUT_OUTPUT;
		List<String> inputOutput = harness.service.evaluate(ResourceNodeCatalog.KIND_INTERFACE,
			"Lobster", ResourceNodeCatalog.ANY_OPTION);
		assertEquals(List.of("Raw lobster", "Lobster"), inputOutput);

		harness.sources.farmingRakeMode = FarmingRakeMode.BOTH;
		assertFalse(harness.service.evaluate(ResourceNodeCatalog.KIND_ITEM_ON_OBJECT,
			"Asgarnian seed", "Hops patch").isEmpty());
	}

	@Test
	public void ordinaryPickpocketingUsesOneCoinsParentAndOptionalNpcParent()
	{
		Harness harness = new Harness();
		harness.sources.thievingMode = ThievingMode.COINS;
		assertEquals(List.of("Coins"), harness.service.evaluate(
			ResourceNodeCatalog.KIND_NPC, "Farmer", "Pickpocket"));

		harness.owned.add("coins");
		assertNull(harness.service.evaluate(
			ResourceNodeCatalog.KIND_NPC, "Farmer", "Pickpocket"));

		harness.sources.thievingMode = ThievingMode.COINS_NPC;
		assertEquals(List.of("Farmer"), harness.service.evaluate(
			ResourceNodeCatalog.KIND_NPC, "Farmer", "Pickpocket"));

		harness.sources.thievingMode = ThievingMode.REQUIRE_ALL;
		harness.owned.remove("coins");
		List<String> full = harness.service.evaluate(
			ResourceNodeCatalog.KIND_NPC, "Farmer", "Pickpocket");
		assertEquals(1, Collections.frequency(full, "Coins"));
		assertFalse(full.contains("Coin pouch"));
	}

	@Test
	public void optInActivitiesRemainOffUntilTheirDedicatedSwitches()
	{
		Harness harness = new Harness();
		assertNull(harness.service.evaluate(ResourceNodeCatalog.KIND_NPC,
			"Guild Hunter Aco", "Talk-to"));
		harness.sources.restrictHunterRumours = true;
		assertFalse(harness.service.evaluate(ResourceNodeCatalog.KIND_NPC,
			"Guild Hunter Aco", "Talk-to").isEmpty());

		assertFalse(harness.service.evaluate(ResourceNodeCatalog.KIND_NPC,
			"Guard", "Mark").isEmpty());
		harness.sources.cotsInProgress = true;
		assertNull(harness.service.evaluate(ResourceNodeCatalog.KIND_NPC,
			"Guard", "Mark"));
	}

	@Test
	public void sailingAndSlayerDialsRetainTheirIndependentBoundaries()
	{
		Harness harness = new Harness();
		harness.sources.sailingUpgradeMode = SailingUpgradeMode.PARTS;
		assertFalse(harness.service.evaluate(ResourceNodeCatalog.KIND_INTERFACE,
			"Adamant keel", ResourceNodeCatalog.ANY_OPTION).isEmpty());
		harness.sources.restrictSalvaging = true;
		assertFalse(harness.service.evaluate(ResourceNodeCatalog.KIND_OBJECT,
			"Barracuda shipwreck", "Salvage").isEmpty());

		harness.sources.slayerMode = SlayerMode.MASTER;
		assertFalse(harness.service.evaluate(ResourceNodeCatalog.KIND_NPC,
			"Aya", "Talk-to").isEmpty());
	}

	@Test
	public void slayerRequirementsUseNpcOwnershipNamespace()
	{
		FakeSources sources = new FakeSources();
		sources.slayerMode = SlayerMode.MASTER;
		ResourceRestrictionService service = new ResourceRestrictionService(
			new ResourceNodeCatalog(new Gson()), sources,
			new ResourceRestrictionService.Ownership()
			{
				public Predicate<String> generic() { return card -> true; }
				public Predicate<String> npc() { return card -> false; }
			});

		assertFalse(service.evaluate(ResourceNodeCatalog.KIND_NPC,
			"Aya", "Talk-to").isEmpty());
	}

	private static final class Harness
	{
		private final Set<String> owned = new HashSet<>();
		private final FakeSources sources = new FakeSources();
		private final ResourceRestrictionService service = new ResourceRestrictionService(
			new ResourceNodeCatalog(new Gson()), sources, new ResourceRestrictionService.Ownership()
			{
				public Predicate<String> generic()
				{
					return card -> owned.contains(card.toLowerCase());
				}
				public Predicate<String> npc()
				{
					return card -> owned.contains(card.toLowerCase());
				}
			});
	}

	private static final class FakeSources implements ResourceRestrictionService.Sources
	{
		private MiningMode miningMode = MiningMode.OFF;
		private WoodcuttingMode woodcuttingMode = WoodcuttingMode.OFF;
		private FishingRestrictionMode fishingMode = FishingRestrictionMode.OFF;
		private HunterMode hunterMode = HunterMode.OFF;
		private RunecraftingMode runecraftingMode = RunecraftingMode.OFF;
		private ThievingMode thievingMode = ThievingMode.OFF;
		private StallThievingMode stallThievingMode = StallThievingMode.OFF;
		private CookingMode cookingMode = CookingMode.OFF;
		private FarmingRakeMode farmingRakeMode = FarmingRakeMode.OFF;
		private CardRequirement compostMode = CardRequirement.NO_CARD;
		private SailingUpgradeMode sailingUpgradeMode = SailingUpgradeMode.OFF;
		private SlayerMode slayerMode = SlayerMode.OFF;
		private HamPickpocketingMode hamPickpocketingMode = HamPickpocketingMode.OFF;
		private boolean masterFarmerFullLoot;
		private int boostedHunterLevel;
		private boolean gotr;
		private boolean restrictHunterRumours;
		private boolean restrictSalvaging;
		private boolean cotsInProgress;

		public MiningMode miningMode() { return miningMode; }
		public WoodcuttingMode woodcuttingMode() { return woodcuttingMode; }
		public FishingRestrictionMode fishingMode() { return fishingMode; }
		public HunterMode hunterMode() { return hunterMode; }
		public RunecraftingMode runecraftingMode() { return runecraftingMode; }
		public ThievingMode thievingMode() { return thievingMode; }
		public StallThievingMode stallThievingMode() { return stallThievingMode; }
		public CookingMode cookingMode() { return cookingMode; }
		public FarmingRakeMode farmingRakeMode() { return farmingRakeMode; }
		public CardRequirement compostMode() { return compostMode; }
		public SailingUpgradeMode sailingUpgradeMode() { return sailingUpgradeMode; }
		public SlayerMode slayerMode() { return slayerMode; }
		public HamPickpocketingMode hamPickpocketingMode() { return hamPickpocketingMode; }
		public boolean masterFarmerFullLoot() { return masterFarmerFullLoot; }
		public boolean restrictHunterRumours() { return restrictHunterRumours; }
		public boolean restrictSalvaging() { return restrictSalvaging; }
		public boolean restrictSlayerSuperiors() { return false; }
		public boolean isGuardiansOfTheRift() { return gotr; }
		public boolean hasBareHandedPlanting() { return false; }
		public boolean isCotsInProgress() { return cotsInProgress; }
		public int currentRegionId() { return -1; }
		public int boostedHunterLevel() { return boostedHunterLevel; }
	}
}
