package com.bronzemantcg.restriction;

import com.bronzemantcg.BronzemanTcgConfig;
import com.bronzemantcg.catalog.CardRequirement;
import com.bronzemantcg.catalog.QuestNpcIndex;
import com.bronzemantcg.catalog.ResourceNodeCatalog;
import com.bronzemantcg.ownership.CardEntityKind;
import com.bronzemantcg.restriction.ThievingPolicy.HamPickpocketingMode;
import com.bronzemantcg.restriction.ThievingPolicy.StallThievingMode;
import com.bronzemantcg.restriction.ThievingPolicy.ThievingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Predicate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.Player;

/** Owns all resource-node restriction decisions; event effects remain in the plugin. */
@Singleton
public final class ResourceRestrictionService
{
	private static final int BRUT_FARMING_PLANTING_VARBIT = 9609;

	private final ResourceNodeCatalog nodeCatalog;
	private final Sources sources;
	private final Ownership ownership;
	private volatile CarriedState carriedState = CarriedState.EMPTY;

	@Inject
	public ResourceRestrictionService(ResourceNodeCatalog nodeCatalog,
		RestrictionDecisionService restrictionDecisionService, Client client,
		BronzemanTcgConfig config, QuestNpcIndex questNpcIndex)
	{
		this(nodeCatalog, new RuneLiteSources(client, config, questNpcIndex),
			new Ownership()
			{
				@Override
				public Predicate<String> generic()
				{
					return restrictionDecisionService.requirementOwnership();
				}

				@Override
				public Predicate<String> npc()
				{
					return restrictionDecisionService.requirementOwnership(CardEntityKind.NPC);
				}
			});
	}

	ResourceRestrictionService(ResourceNodeCatalog nodeCatalog, Sources sources,
		Ownership ownership)
	{
		this.nodeCatalog = nodeCatalog;
		this.sources = sources;
		this.ownership = ownership;
	}

	/** Replaces all carried-name inputs atomically so one evaluation sees one snapshot. */
	public void updateCarriedState(Set<String> pickaxes, Set<String> axes,
		Set<String> fishingInputs, Set<String> inventoryNamesLower)
	{
		carriedState = new CarriedState(copyOf(pickaxes), copyOf(axes),
			copyOf(fishingInputs), copyOf(inventoryNamesLower));
	}

	public List<String> evaluate(String kind, String name, String option)
	{
		return evaluate(kind, name, option, -1);
	}

	/** @return missing cards, or null when the interaction is allowed. */
	public List<String> evaluate(String kind, String name, String option, int targetId)
	{
		ResourceNodeCatalog.Rule rule = nodeCatalog.find(kind, name, option, targetId);
		if (rule == null)
		{
			return null;
		}
		Predicate<String> ownsCard = "slayer".equals(rule.category)
			? ownership.npc() : ownership.generic();
		CarriedState carried = carriedState;

		switch (rule.category)
		{
			case "mining":
			case "woodcutting":
				return evaluateGatheringRule(rule, ownsCard, carried);
			case "fishing":
				return evaluateFishingRule(rule, ownsCard, carried);
			case "hunter-salamanders":
				return evaluateSalamanderRule(targetId, ownsCard);
			case "hunter-birds":
				return evaluateAreaTrapRule(rule, true, ownsCard);
			case "hunter-chins":
				return evaluateAreaTrapRule(rule, false, ownsCard);
			case "hunter-butterflies":
			case "hunter-implings":
				return evaluateHunterNetRule(rule, name, ownsCard);
			case "runecrafting":
				return evaluateRunecraftingRule(rule, ownsCard, carried);
			default:
				break;
		}

		if ("pickpocketing".equals(rule.category)
			&& "H.A.M. Member".equalsIgnoreCase(name))
		{
			return evaluateHamMemberRule(rule, ownsCard);
		}

		if ("pickpocketing".equals(rule.category)
			&& sources.thievingMode().isNpcOnly())
		{
			List<String> missing = rule.missingRequirementsForRole(ownsCard, "npc");
			return missing.isEmpty() ? null : missing;
		}

		boolean forceAllInGroups = false;
		Set<String> excludedRoles;
		if ("thieving-stalls".equals(rule.category)
			|| "thieving-chests".equals(rule.category))
		{
			StallThievingMode mode = sources.stallThievingMode();
			if (!mode.isEnabled())
			{
				return null;
			}
			forceAllInGroups = mode.requiresAll();
			excludedRoles = Collections.emptySet();
		}
		else
		{
			excludedRoles = excludedRolesFor(rule.category);
			if (excludedRoles == null)
			{
				return null;
			}
		}

		List<String> missing = rule.missingRequirements(
			ownsCard, excludedRoles, forceAllInGroups);
		return missing.isEmpty() ? null : missing;
	}

	/** Master Farmers answer only to their dedicated Full Loot switch. */
	public List<String> evaluateMasterFarmer()
	{
		List<String> missing = ThievingPolicy.missingMasterFarmerRequirements(
			sources.masterFarmerFullLoot(), nodeCatalog.getMasterFarmerSeedCards(),
			ownership.generic());
		return missing.isEmpty() ? null : missing;
	}

	private List<String> evaluateSalamanderRule(int objectId, Predicate<String> ownsCard)
	{
		HunterMode mode = sources.hunterMode();
		if (mode == HunterMode.OFF)
		{
			return null;
		}
		List<String> required = new ArrayList<>(List.of("Rope", "Small fishing net"));
		HunterTrapType trap = HunterTrapType.fromObjectId(objectId);
		if (mode == HunterMode.ALL_CARDS && trap != null)
		{
			required.add(trap.getCardName());
		}
		return missingOwnedCards(required, ownsCard);
	}

	private List<String> evaluateAreaTrapRule(ResourceNodeCatalog.Rule rule, boolean bird,
		Predicate<String> ownsCard)
	{
		HunterMode mode = sources.hunterMode();
		if (mode == HunterMode.OFF)
		{
			return null;
		}
		List<String> missing = rule.missingRequirements(ownsCard, Collections.emptySet(), false);
		if (mode == HunterMode.ALL_CARDS)
		{
			addMissingCards(missing, bird
				? HunterAreaSpecies.birdCards(sources.currentRegionId())
				: HunterAreaSpecies.chinchompaCards(sources.currentRegionId()), ownsCard);
		}
		return missing.isEmpty() ? null : missing;
	}

	private List<String> evaluateHunterNetRule(ResourceNodeCatalog.Rule rule, String creatureName,
		Predicate<String> ownsCard)
	{
		HunterMode mode = sources.hunterMode();
		if (mode == HunterMode.OFF)
		{
			return null;
		}
		Set<String> excluded = new HashSet<>();
		if (mode == HunterMode.TOOLS_ONLY)
		{
			excluded.add("output");
		}
		if (BarehandedHunterEligibilityPolicy.canCatch(
			creatureName, sources.boostedHunterLevel()))
		{
			excluded.add("tool");
		}
		List<String> missing = rule.missingRequirements(ownsCard, excluded, false);
		return missing.isEmpty() ? null : missing;
	}

	private List<String> evaluateHamMemberRule(ResourceNodeCatalog.Rule rule,
		Predicate<String> ownsCard)
	{
		HamPickpocketingMode mode = sources.hamPickpocketingMode();
		if (!mode.isEnabled())
		{
			return null;
		}
		Set<String> excluded = mode.excludedRoles();
		List<String> missing = rule.missingRequirements(ownsCard, excluded, false);
		return missing.isEmpty() ? null : missing;
	}

	private List<String> evaluateRunecraftingRule(ResourceNodeCatalog.Rule rule,
		Predicate<String> ownsCard, CarriedState carried)
	{
		RunecraftingMode mode = sources.runecraftingMode();
		if (mode == RunecraftingMode.OFF)
		{
			return null;
		}
		if (sources.isGuardiansOfTheRift())
		{
			if (mode == RunecraftingMode.TALISMAN)
			{
				return null;
			}
			List<String> missing = rule.missingRequirements(
				ownsCard, Set.of("essence", "talisman"), false);
			return missing.isEmpty() ? null : missing;
		}

		Set<String> excluded = new HashSet<>();
		excluded.add("essence");
		if (mode == RunecraftingMode.TALISMAN)
		{
			excluded.add("rune");
		}
		List<String> missing = rule.missingRequirements(ownsCard, excluded, false);
		ResourceNodeCatalog.CardGroup essenceGroup = null;
		for (ResourceNodeCatalog.CardGroup group : rule.groups)
		{
			if ("essence".equals(group.role))
			{
				essenceGroup = group;
				break;
			}
		}
		if (essenceGroup == null)
		{
			return missing.isEmpty() ? null : missing;
		}

		boolean foundCarriedEssence = false;
		for (int i = 0; i < essenceGroup.lowerCards.size(); i++)
		{
			if (carried.inventoryNamesLower.contains(essenceGroup.lowerCards.get(i)))
			{
				foundCarriedEssence = true;
				addMissingCard(missing, essenceGroup.displayCards.get(i), ownsCard);
			}
		}
		if (!foundCarriedEssence && !essenceGroup.isSatisfied(ownsCard))
		{
			missing.add(String.join(" / ", essenceGroup.displayCards));
		}
		return missing.isEmpty() ? null : missing;
	}

	private List<String> evaluateGatheringRule(ResourceNodeCatalog.Rule rule,
		Predicate<String> ownsCard, CarriedState carried)
	{
		boolean mining = "mining".equals(rule.category);
		boolean enforceYield;
		if (mining)
		{
			MiningMode mode = sources.miningMode();
			if (mode == MiningMode.OFF)
			{
				return null;
			}
			enforceYield = mode == MiningMode.CARD_REQUIRED;
		}
		else
		{
			WoodcuttingMode mode = sources.woodcuttingMode();
			if (mode == WoodcuttingMode.OFF)
			{
				return null;
			}
			enforceYield = mode == WoodcuttingMode.LOGS_ONLY;
		}
		List<String> missing = enforceYield
			? rule.missingRequirements(ownsCard, Collections.emptySet(), false)
			: new ArrayList<>();
		for (String tool : mining ? carried.pickaxes : carried.axes)
		{
			if (!ownsCard.test(tool))
			{
				missing.add(tool);
			}
		}
		return missing.isEmpty() ? null : missing;
	}

	private List<String> evaluateFishingRule(ResourceNodeCatalog.Rule rule,
		Predicate<String> ownsCard, CarriedState carried)
	{
		FishingRestrictionMode mode = sources.fishingMode();
		if (mode == FishingRestrictionMode.OFF)
		{
			return null;
		}
		List<String> missing = FishingRequirementPolicy.missingRequirements(
			rule, mode, ownsCard, carried.fishingInputs);
		return missing.isEmpty() ? null : missing;
	}

	private Set<String> excludedRolesFor(String category)
	{
		switch (category)
		{
			case "pickpocketing":
				ThievingMode thievingMode = sources.thievingMode();
				if (!thievingMode.isEnabled() || thievingMode.isNpcOnly())
				{
					return null;
				}
				return thievingMode.excludedRoles();
			case "cooking":
			{
				CookingMode mode = sources.cookingMode();
				if (mode == CookingMode.OFF) return null;
				Set<String> excluded = new HashSet<>();
				if (mode == CookingMode.INPUT_ONLY) excluded.add("output");
				return excluded;
			}
			case "farming-plant":
			{
				if (sources.farmingRakeMode() == FarmingRakeMode.OFF) return null;
				Set<String> excluded = new HashSet<>();
				excluded.add("produce");
				if (sources.farmingRakeMode() == FarmingRakeMode.TOOLS) excluded.add("seed");
				if (sources.hasBareHandedPlanting()) excluded.add("tool");
				return excluded;
			}
			case "farming-compost":
				return sources.compostMode() == CardRequirement.CARD_REQUIRED
					? Collections.emptySet() : null;
			case "hunter-rumours":
				return sources.restrictHunterRumours() ? Collections.emptySet() : null;
			case "quest-cots":
				return sources.isCotsInProgress() ? null : Collections.emptySet();
			case "farming-rake":
				return sources.farmingRakeMode() == FarmingRakeMode.OFF
					? null : Collections.singleton("weeds");
			case "sailing-upgrades":
				switch (sources.sailingUpgradeMode())
				{
					case PARTS: return Set.of("material", "large");
					case PARTS_MATERIALS: return Set.of("large");
					case EVERYTHING: return Collections.emptySet();
					default: return null;
				}
			case "sailing-salvage":
				return sources.restrictSalvaging() ? Collections.emptySet() : null;
			case "slayer":
			{
				SlayerMode mode = sources.slayerMode();
				if (mode == SlayerMode.OFF) return null;
				Set<String> excluded = new HashSet<>();
				if (mode != SlayerMode.FULL) excluded.add("monsters");
				if (mode != SlayerMode.FULL || !sources.restrictSlayerSuperiors())
				{
					excluded.add("superiors");
				}
				return excluded;
			}
			case "hunter-pitfalls":
				switch (sources.hunterMode())
				{
					case TOOLS_ONLY: return Set.of("monster", "loot");
					case ALL_CARDS: return Collections.emptySet();
					default: return null;
				}
			default:
				return Collections.emptySet();
		}
	}

	private List<String> missingOwnedCards(List<String> cards, Predicate<String> ownsCard)
	{
		List<String> missing = new ArrayList<>();
		addMissingCards(missing, cards, ownsCard);
		return missing.isEmpty() ? null : missing;
	}

	private static void addMissingCards(List<String> missing, List<String> cards,
		Predicate<String> ownsCard)
	{
		for (String card : cards) addMissingCard(missing, card, ownsCard);
	}

	private static void addMissingCard(List<String> missing, String card,
		Predicate<String> ownsCard)
	{
		if (card != null && !ownsCard.test(card) && !missing.contains(card)) missing.add(card);
	}

	private static Set<String> copyOf(Set<String> values)
	{
		return values == null || values.isEmpty() ? Collections.emptySet() : Set.copyOf(values);
	}

	interface Ownership
	{
		Predicate<String> generic();
		Predicate<String> npc();
	}

	interface Sources
	{
		MiningMode miningMode();
		WoodcuttingMode woodcuttingMode();
		FishingRestrictionMode fishingMode();
		HunterMode hunterMode();
		RunecraftingMode runecraftingMode();
		ThievingMode thievingMode();
		StallThievingMode stallThievingMode();
		CookingMode cookingMode();
		FarmingRakeMode farmingRakeMode();
		CardRequirement compostMode();
		SailingUpgradeMode sailingUpgradeMode();
		SlayerMode slayerMode();
		HamPickpocketingMode hamPickpocketingMode();
		boolean masterFarmerFullLoot();
		boolean restrictHunterRumours();
		boolean restrictSalvaging();
		boolean restrictSlayerSuperiors();
		boolean isGuardiansOfTheRift();
		boolean hasBareHandedPlanting();
		boolean isCotsInProgress();
		int currentRegionId();
		int boostedHunterLevel();
	}

	private static final class RuneLiteSources implements Sources
	{
		private final Client client;
		private final BronzemanTcgConfig config;
		private final QuestNpcIndex questNpcIndex;

		private RuneLiteSources(Client client, BronzemanTcgConfig config,
			QuestNpcIndex questNpcIndex)
		{
			this.client = client;
			this.config = config;
			this.questNpcIndex = questNpcIndex;
		}

		public MiningMode miningMode() { return config.miningMode(); }
		public WoodcuttingMode woodcuttingMode() { return config.woodcuttingMode(); }
		public FishingRestrictionMode fishingMode() { return config.fishingMode(); }
		public HunterMode hunterMode() { return config.hunterMode(); }
		public RunecraftingMode runecraftingMode() { return config.runecraftingMode(); }
		public ThievingMode thievingMode() { return config.thievingMode(); }
		public StallThievingMode stallThievingMode() { return config.stallThievingMode(); }
		public CookingMode cookingMode() { return config.cookingMode(); }
		public FarmingRakeMode farmingRakeMode() { return config.farmingRakeMode(); }
		public CardRequirement compostMode() { return config.compostMode(); }
		public SailingUpgradeMode sailingUpgradeMode() { return config.sailingUpgradeMode(); }
		public SlayerMode slayerMode() { return config.slayerMode(); }
		public HamPickpocketingMode hamPickpocketingMode()
		{
			return config.hamPickpocketingMode();
		}
		public boolean masterFarmerFullLoot() { return config.masterFarmerInsanity(); }
		public boolean restrictHunterRumours() { return config.restrictHunterRumours(); }
		public boolean restrictSalvaging() { return config.restrictSalvaging(); }
		public boolean restrictSlayerSuperiors() { return config.restrictSlayerSuperiors(); }
		public boolean isGuardiansOfTheRift()
		{
			return client.getVarbitValue(VarbitID.GOTR_IS_PLAYING) != 0;
		}
		public boolean hasBareHandedPlanting()
		{
			return client.getVarbitValue(BRUT_FARMING_PLANTING_VARBIT) >= 1;
		}
		public boolean isCotsInProgress() { return questNpcIndex.isCotsInProgress(); }
		public int currentRegionId()
		{
			Player local = client.getLocalPlayer();
			return local == null ? -1 : local.getWorldLocation().getRegionID();
		}
		public int boostedHunterLevel() { return client.getBoostedSkillLevel(Skill.HUNTER); }
	}

	private static final class CarriedState
	{
		private static final CarriedState EMPTY = new CarriedState(
			Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
			Collections.emptySet());
		private final Set<String> pickaxes;
		private final Set<String> axes;
		private final Set<String> fishingInputs;
		private final Set<String> inventoryNamesLower;

		private CarriedState(Set<String> pickaxes, Set<String> axes,
			Set<String> fishingInputs, Set<String> inventoryNamesLower)
		{
			this.pickaxes = pickaxes;
			this.axes = axes;
			this.fishingInputs = fishingInputs;
			this.inventoryNamesLower = inventoryNamesLower;
		}
	}
}
