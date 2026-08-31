package com.bronzemantcg.restriction;

import com.bronzemantcg.BronzemanTcgConfig;
import com.bronzemantcg.catalog.QuestNpcIndex;
import com.bronzemantcg.catalog.ResourceNodeCatalog;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.client.game.FishingSpot;
import net.runelite.client.util.Text;

/**
 * Owns NPC scene visibility, menu visibility and click-routing decisions. RuneLite scene/menu/
 * event effects and chat feedback remain at the plugin entry points.
 */
@Singleton
public final class NpcRestrictionService
{
	private static final String ATTACK_OPTION = "attack";
	private static final String PICKPOCKET_OPTION = "pickpocket";
	private static final String MASTER_FARMER_NAME = "master farmer";

	private final Sources sources;

	@Inject
	public NpcRestrictionService(BronzemanTcgConfig config,
		RestrictionDecisionService restrictionDecisionService,
		ResourceRestrictionService resourceRestrictionService,
		ResourceNodeCatalog nodeCatalog, QuestNpcIndex questNpcIndex)
	{
		this(new RuneLiteSources(config, restrictionDecisionService,
			resourceRestrictionService, nodeCatalog, questNpcIndex));
	}

	NpcRestrictionService(Sources sources)
	{
		this.sources = sources;
	}

	/** Allocation-free RenderCallback decision. */
	public boolean shouldRender(NPC npc)
	{
		if (npc == null || sources.npcVisibilityMode() != NpcVisibilityMode.HIDE
			|| sources.isEnforcementBypassed())
		{
			return true;
		}
		return shouldRenderResolved(npc.getId(), sources.resolveNpcName(npc));
	}

	boolean shouldRender(int npcId, String npcName)
	{
		if (sources.npcVisibilityMode() != NpcVisibilityMode.HIDE
			|| sources.isEnforcementBypassed())
		{
			return true;
		}
		return shouldRenderResolved(npcId, npcName);
	}

	private boolean shouldRenderResolved(int npcId, String npcName)
	{
		if (isBlank(npcName) || sources.isShownQuestNpc(npcName))
		{
			return true;
		}
		return !sources.isNpcLocked(npcId, npcName);
	}

	/** Allocation-free NPC menu decision; true means the plugin should remove the entry. */
	public boolean shouldHideMenuEntry(NPC npc, String option)
	{
		if (npc == null || sources.isEnforcementBypassed() || isBlank(option)
			|| sources.fishingSpotName(npc.getId()) != null)
		{
			return false;
		}
		return shouldHideMenuEntryResolved(npc.getId(), sources.resolveNpcName(npc), option);
	}

	boolean shouldHideMenuEntry(int npcId, String npcName, String option)
	{
		if (sources.isEnforcementBypassed() || isBlank(option)
			|| sources.fishingSpotName(npcId) != null || isBlank(npcName))
		{
			return false;
		}
		return shouldHideMenuEntryResolved(npcId, npcName, option);
	}

	private boolean shouldHideMenuEntryResolved(int npcId, String npcName, String option)
	{
		if (isBlank(npcName))
		{
			return false;
		}
		String cleanOption = clean(option);
		String optionLower = cleanOption.toLowerCase(Locale.ROOT);
		boolean locked = sources.isNpcLocked(npcId, npcName);
		NpcVisibilityMode mode = sources.npcVisibilityMode();
		if (mode != NpcVisibilityMode.OFF && ATTACK_OPTION.equals(optionLower) && locked)
		{
			return true;
		}
		if (mode.strictOptions() && locked && !isStrictOptionException(npcName))
		{
			return true;
		}
		if (sources.showLockedMenuOptions())
		{
			return false;
		}
		if (MASTER_FARMER_NAME.equalsIgnoreCase(npcName)
			&& PICKPOCKET_OPTION.equals(optionLower))
		{
			return hasMissing(sources.evaluateMasterFarmer());
		}
		return hasMissing(sources.evaluateResource(
			ResourceNodeCatalog.KIND_NPC, npcName, cleanOption));
	}

	/** Click-only routing result; the plugin applies consumption and the appropriate chat effect. */
	public InteractionDecision evaluateInteraction(NPC npc, MenuAction action, String option,
		boolean selectedWidgetSpell)
	{
		if (npc == null || sources.isEnforcementBypassed())
		{
			return InteractionDecision.allowed();
		}
		return evaluateInteraction(npc.getId(), sources.resolveNpcName(npc), action, option,
			selectedWidgetSpell);
	}

	InteractionDecision evaluateInteraction(int npcId, String npcName, MenuAction action,
		String option, boolean selectedWidgetSpell)
	{
		if (sources.isEnforcementBypassed() || isBlank(npcName))
		{
			return InteractionDecision.allowed();
		}
		String cleanOption = option == null ? "" : clean(option);
		String optionLower = cleanOption.toLowerCase(Locale.ROOT);

		// Master Farmer must remain seed-only and never fall through to general NPC requirements.
		if (MASTER_FARMER_NAME.equalsIgnoreCase(npcName)
			&& PICKPOCKET_OPTION.equals(optionLower))
		{
			return InteractionDecision.blockedCards(sources.evaluateMasterFarmer());
		}

		if (isRestrictedInteraction(action, optionLower, selectedWidgetSpell, npcName)
			&& sources.isNpcLocked(npcId, npcName))
		{
			return InteractionDecision.blockedNpc(npcName);
		}
		if (option == null)
		{
			return InteractionDecision.allowed();
		}

		String fishingSpot = sources.fishingSpotName(npcId);
		if (fishingSpot != null)
		{
			return InteractionDecision.blockedCards(sources.evaluateResource(
				ResourceNodeCatalog.KIND_FISHING_SPOT, fishingSpot, cleanOption));
		}
		return InteractionDecision.blockedCards(sources.evaluateResource(
			ResourceNodeCatalog.KIND_NPC, npcName, cleanOption));
	}

	public static boolean isNpcMenuAction(MenuAction action)
	{
		if (action == null)
		{
			return false;
		}
		switch (action)
		{
			case NPC_FIRST_OPTION:
			case NPC_SECOND_OPTION:
			case NPC_THIRD_OPTION:
			case NPC_FOURTH_OPTION:
			case NPC_FIFTH_OPTION:
				return true;
			default:
				return false;
		}
	}

	private boolean isRestrictedInteraction(MenuAction action, String optionLower,
		boolean selectedWidgetSpell, String npcName)
	{
		NpcVisibilityMode mode = sources.npcVisibilityMode();
		if (action == null || mode == NpcVisibilityMode.OFF)
		{
			return false;
		}
		if (isNpcMenuAction(action))
		{
			return ATTACK_OPTION.equals(optionLower)
				|| (mode.strictOptions() && !isStrictOptionException(npcName));
		}
		if (action == MenuAction.WIDGET_TARGET_ON_NPC)
		{
			if (mode.strictOptions() && !isStrictOptionException(npcName))
			{
				return true;
			}
			return "cast".equals(optionLower) || selectedWidgetSpell;
		}
		return false;
	}

	private boolean isStrictOptionException(String npcName)
	{
		return sources.isSlayerNpc(npcName) || sources.isShownQuestNpc(npcName);
	}

	private static boolean hasMissing(List<String> missing)
	{
		return missing != null && !missing.isEmpty();
	}

	private static boolean isBlank(String value)
	{
		return value == null || value.trim().isEmpty();
	}

	private static String clean(String value)
	{
		return Text.removeTags(value).trim();
	}

	public static final class InteractionDecision
	{
		private static final InteractionDecision ALLOWED =
			new InteractionDecision(null, Collections.emptyList());

		private final String blockedNpcName;
		private final List<String> missingCards;

		private InteractionDecision(String blockedNpcName, List<String> missingCards)
		{
			this.blockedNpcName = blockedNpcName;
			this.missingCards = missingCards;
		}

		private static InteractionDecision allowed()
		{
			return ALLOWED;
		}

		private static InteractionDecision blockedNpc(String npcName)
		{
			return new InteractionDecision(npcName, Collections.emptyList());
		}

		private static InteractionDecision blockedCards(List<String> missingCards)
		{
			return hasMissing(missingCards)
				? new InteractionDecision(null, List.copyOf(missingCards)) : ALLOWED;
		}

		public boolean isBlocked()
		{
			return blockedNpcName != null || !missingCards.isEmpty();
		}

		public String getBlockedNpcName()
		{
			return blockedNpcName;
		}

		public List<String> getMissingCards()
		{
			return missingCards;
		}
	}

	interface Sources
	{
		NpcVisibilityMode npcVisibilityMode();
		boolean showLockedMenuOptions();
		boolean isEnforcementBypassed();
		String resolveNpcName(NPC npc);
		boolean isNpcLocked(int npcId, String npcName);
		boolean isSlayerNpc(String npcName);
		boolean isShownQuestNpc(String npcName);
		String fishingSpotName(int npcId);
		List<String> evaluateMasterFarmer();
		List<String> evaluateResource(String kind, String name, String option);
	}

	private static final class RuneLiteSources implements Sources
	{
		private final BronzemanTcgConfig config;
		private final RestrictionDecisionService restrictionDecisionService;
		private final ResourceRestrictionService resourceRestrictionService;
		private final ResourceNodeCatalog nodeCatalog;
		private final QuestNpcIndex questNpcIndex;

		private RuneLiteSources(BronzemanTcgConfig config,
			RestrictionDecisionService restrictionDecisionService,
			ResourceRestrictionService resourceRestrictionService,
			ResourceNodeCatalog nodeCatalog, QuestNpcIndex questNpcIndex)
		{
			this.config = config;
			this.restrictionDecisionService = restrictionDecisionService;
			this.resourceRestrictionService = resourceRestrictionService;
			this.nodeCatalog = nodeCatalog;
			this.questNpcIndex = questNpcIndex;
		}

		public NpcVisibilityMode npcVisibilityMode() { return config.npcVisibilityMode(); }
		public boolean showLockedMenuOptions() { return config.showLockedMenuOptions(); }
		public boolean isEnforcementBypassed()
		{
			return restrictionDecisionService.isEnforcementBypassed();
		}
		public String resolveNpcName(NPC npc)
		{
			return restrictionDecisionService.resolveNpcName(npc);
		}
		public boolean isNpcLocked(int npcId, String npcName)
		{
			return restrictionDecisionService.isNpcLocked(npcId, npcName);
		}
		public boolean isSlayerNpc(String npcName) { return nodeCatalog.isSlayerNpc(npcName); }
		public boolean isShownQuestNpc(String npcName)
		{
			return questNpcIndex.isShownQuestNpc(npcName);
		}
		public String fishingSpotName(int npcId)
		{
			FishingSpot spot = FishingSpot.findSpot(npcId);
			return spot == null ? null : spot.name();
		}
		public List<String> evaluateMasterFarmer()
		{
			return resourceRestrictionService.evaluateMasterFarmer();
		}
		public List<String> evaluateResource(String kind, String name, String option)
		{
			return resourceRestrictionService.evaluate(kind, name, option);
		}
	}
}
