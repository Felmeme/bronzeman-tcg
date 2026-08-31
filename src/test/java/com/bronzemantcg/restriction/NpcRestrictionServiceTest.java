package com.bronzemantcg.restriction;

import com.bronzemantcg.catalog.ResourceNodeCatalog;
import java.util.Collections;
import java.util.List;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NpcRestrictionServiceTest
{
	@Test
	public void sceneVisibilityOnlyHidesLockedNpcsInHideMode()
	{
		Harness harness = new Harness();
		harness.sources.locked = true;
		assertTrue(harness.service.shouldRender(1, "Guard"));

		harness.sources.mode = NpcVisibilityMode.HIDE;
		assertFalse(harness.service.shouldRender(1, "Guard"));
		harness.sources.locked = false;
		assertTrue(harness.service.shouldRender(1, "Guard"));
	}

	@Test
	public void sceneVisibilityPreservesBypassQuestAndUnknownFailOpenCases()
	{
		Harness harness = new Harness();
		harness.sources.mode = NpcVisibilityMode.HIDE;
		harness.sources.locked = true;

		assertTrue(harness.service.shouldRender(1, null));
		assertTrue(harness.service.shouldRender(1, "  "));
		harness.sources.questNpc = true;
		assertTrue(harness.service.shouldRender(1, "Guard"));
		harness.sources.questNpc = false;
		harness.sources.bypassed = true;
		assertTrue(harness.service.shouldRender(1, "Guard"));
	}

	@Test
	public void preventCombatHidesLockedAttackButLeavesOrdinaryOptions()
	{
		Harness harness = new Harness();
		harness.sources.mode = NpcVisibilityMode.PREVENT_COMBAT;
		harness.sources.locked = true;

		assertTrue(harness.service.shouldHideMenuEntry(1, "Guard", "Attack"));
		assertFalse(harness.service.shouldHideMenuEntry(1, "Guard", "Talk-to"));
		harness.sources.locked = false;
		assertFalse(harness.service.shouldHideMenuEntry(1, "Guard", "Attack"));
	}

	@Test
	public void strictMenusPreserveSlayerAndStartedQuestExceptions()
	{
		Harness harness = new Harness();
		harness.sources.mode = NpcVisibilityMode.PREVENT_INTERACTION;
		harness.sources.locked = true;

		assertTrue(harness.service.shouldHideMenuEntry(1, "Guard", "Talk-to"));
		harness.sources.slayerNpc = true;
		assertFalse(harness.service.shouldHideMenuEntry(1, "Guard", "Talk-to"));
		harness.sources.slayerNpc = false;
		harness.sources.questNpc = true;
		assertFalse(harness.service.shouldHideMenuEntry(1, "Guard", "Talk-to"));
	}

	@Test
	public void fishingMenusStayVisibleAndDiscoverabilityControlsOtherResources()
	{
		Harness harness = new Harness();
		harness.sources.resourceMissing = List.of("Small fishing net");
		harness.sources.fishingSpot = "SHRIMP";
		assertFalse(harness.service.shouldHideMenuEntry(12, "Fishing spot", "Net"));

		harness.sources.fishingSpot = null;
		harness.sources.showLockedMenuOptions = true;
		assertFalse(harness.service.shouldHideMenuEntry(12, "Black warlock", "Catch"));
		harness.sources.showLockedMenuOptions = false;
		assertTrue(harness.service.shouldHideMenuEntry(12, "Black warlock", "Catch"));
	}

	@Test
	public void masterFarmerMenuUsesOnlyItsDedicatedSeedDecision()
	{
		Harness harness = new Harness();
		harness.sources.masterFarmerMissing = List.of("Ranarr seed");
		harness.sources.resourceMissing = List.of("Coins");

		assertTrue(harness.service.shouldHideMenuEntry(
			1, "Master farmer", "Pickpocket"));
		assertEquals(0, harness.sources.resourceCalls);
	}

	@Test
	public void ordinaryNpcClicksFollowCombatAndStrictActionModes()
	{
		Harness harness = new Harness();
		harness.sources.mode = NpcVisibilityMode.PREVENT_COMBAT;
		harness.sources.locked = true;

		NpcRestrictionService.InteractionDecision attack = harness.service.evaluateInteraction(
			1, "Goblin", MenuAction.NPC_SECOND_OPTION, "Attack", false);
		assertTrue(attack.isBlocked());
		assertEquals("Goblin", attack.getBlockedNpcName());

		assertFalse(harness.service.evaluateInteraction(1, "Goblin",
			MenuAction.NPC_FIRST_OPTION, "Talk-to", false).isBlocked());
		harness.sources.mode = NpcVisibilityMode.PREVENT_INTERACTION;
		assertTrue(harness.service.evaluateInteraction(1, "Goblin",
			MenuAction.NPC_FIRST_OPTION, "Talk-to", false).isBlocked());
	}

	@Test
	public void widgetNpcClicksDistinguishSpellsFromItemsAndStrictExceptions()
	{
		Harness harness = new Harness();
		harness.sources.mode = NpcVisibilityMode.PREVENT_COMBAT;
		harness.sources.locked = true;

		assertTrue(harness.service.evaluateInteraction(1, "Goblin",
			MenuAction.WIDGET_TARGET_ON_NPC, "Cast", false).isBlocked());
		assertTrue(harness.service.evaluateInteraction(1, "Goblin",
			MenuAction.WIDGET_TARGET_ON_NPC, "Use", true).isBlocked());
		assertFalse(harness.service.evaluateInteraction(1, "Goblin",
			MenuAction.WIDGET_TARGET_ON_NPC, "Use", false).isBlocked());

		harness.sources.mode = NpcVisibilityMode.PREVENT_INTERACTION;
		assertTrue(harness.service.evaluateInteraction(1, "Goblin",
			MenuAction.WIDGET_TARGET_ON_NPC, "Use", false).isBlocked());
		harness.sources.questNpc = true;
		assertFalse(harness.service.evaluateInteraction(1, "Goblin",
			MenuAction.WIDGET_TARGET_ON_NPC, "Use", false).isBlocked());
		harness.sources.questNpc = false;
		harness.sources.slayerNpc = true;
		assertFalse(harness.service.evaluateInteraction(1, "Goblin",
			MenuAction.WIDGET_TARGET_ON_NPC, "Use", false).isBlocked());
	}

	@Test
	public void masterFarmerAndFishingClicksReturnMissingCardDecisions()
	{
		Harness harness = new Harness();
		harness.sources.masterFarmerMissing = List.of("Ranarr seed");
		NpcRestrictionService.InteractionDecision farmer = harness.service.evaluateInteraction(
			1, "Master farmer", MenuAction.NPC_FIRST_OPTION, "Pickpocket", false);
		assertEquals(List.of("Ranarr seed"), farmer.getMissingCards());
		assertNull(farmer.getBlockedNpcName());
		assertEquals(0, harness.sources.resourceCalls);

		harness.sources.fishingSpot = "SHRIMP";
		harness.sources.resourceMissing = List.of("Small fishing net");
		NpcRestrictionService.InteractionDecision fishing = harness.service.evaluateInteraction(
			12, "Fishing spot", MenuAction.NPC_FIRST_OPTION, "Net", false);
		assertEquals(List.of("Small fishing net"), fishing.getMissingCards());
		assertEquals(ResourceNodeCatalog.KIND_FISHING_SPOT, harness.sources.lastKind);
		assertEquals("SHRIMP", harness.sources.lastName);
	}

	@Test
	public void genericNpcResourcesAndNullInputsFailOpenCorrectly()
	{
		Harness harness = new Harness();
		harness.sources.resourceMissing = List.of("Black warlock");
		NpcRestrictionService.InteractionDecision resource = harness.service.evaluateInteraction(
			1, "Black warlock", MenuAction.NPC_FIRST_OPTION, "<col=ff00ff>Catch</col>", false);
		assertEquals(List.of("Black warlock"), resource.getMissingCards());
		assertEquals(ResourceNodeCatalog.KIND_NPC, harness.sources.lastKind);
		assertEquals("Catch", harness.sources.lastOption);

		assertFalse(harness.service.evaluateInteraction(
			1, null, MenuAction.NPC_FIRST_OPTION, "Attack", false).isBlocked());
		assertFalse(harness.service.evaluateInteraction(
			1, "Goblin", null, null, false).isBlocked());

		harness.sources.bypassed = true;
		assertFalse(harness.service.shouldHideMenuEntry(1, "Goblin", "Attack"));
		assertFalse(harness.service.evaluateInteraction(1, "Goblin",
			MenuAction.NPC_SECOND_OPTION, "Attack", false).isBlocked());
	}

	@Test
	public void npcMenuActionClassifierOnlyIncludesOrdinaryNpcOptions()
	{
		assertTrue(NpcRestrictionService.isNpcMenuAction(MenuAction.NPC_FIRST_OPTION));
		assertTrue(NpcRestrictionService.isNpcMenuAction(MenuAction.NPC_FIFTH_OPTION));
		assertFalse(NpcRestrictionService.isNpcMenuAction(MenuAction.WIDGET_TARGET_ON_NPC));
		assertFalse(NpcRestrictionService.isNpcMenuAction(MenuAction.GAME_OBJECT_FIRST_OPTION));
		assertFalse(NpcRestrictionService.isNpcMenuAction(null));
	}

	private static final class Harness
	{
		private final FakeSources sources = new FakeSources();
		private final NpcRestrictionService service = new NpcRestrictionService(sources);
	}

	private static final class FakeSources implements NpcRestrictionService.Sources
	{
		private NpcVisibilityMode mode = NpcVisibilityMode.OFF;
		private boolean showLockedMenuOptions;
		private boolean bypassed;
		private boolean locked;
		private boolean slayerNpc;
		private boolean questNpc;
		private String fishingSpot;
		private List<String> masterFarmerMissing;
		private List<String> resourceMissing;
		private int resourceCalls;
		private String lastKind;
		private String lastName;
		private String lastOption;

		public NpcVisibilityMode npcVisibilityMode() { return mode; }
		public boolean showLockedMenuOptions() { return showLockedMenuOptions; }
		public boolean isEnforcementBypassed() { return bypassed; }
		public String resolveNpcName(NPC npc) { return null; }
		public boolean isNpcLocked(int npcId, String npcName) { return locked; }
		public boolean isSlayerNpc(String npcName) { return slayerNpc; }
		public boolean isShownQuestNpc(String npcName) { return questNpc; }
		public String fishingSpotName(int npcId) { return fishingSpot; }
		public List<String> evaluateMasterFarmer() { return masterFarmerMissing; }
		public List<String> evaluateResource(String kind, String name, String option)
		{
			resourceCalls++;
			lastKind = kind;
			lastName = name;
			lastOption = option;
			return resourceMissing == null ? Collections.emptyList() : resourceMissing;
		}
	}
}
