package com.bronzemantcg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure planning engine for the sidebar. It deliberately returns advice rather than
 * performing game actions: RuneLite remains the source of account state and the player
 * remains in control of every click.
 */
final class CardcorePlanner
{
	private static final Map<String, GoalQuest> BARROWS_GLOVE_QUESTS = buildBarrowsGloveQuests();
	private static final Map<String, Integer> PACK_QUEST_SCORES = buildPackQuestScores();
	private final Map<String, QuestCatalog.QuestEntry> questsByName;
	private final QuestCatalog.QuestEntry fightCaves;
	private final FauxCardcoreProfile fauxProfile;

	CardcorePlanner(QuestCatalog questCatalog, ContentCatalog contentCatalog,
		FauxCardcoreProfile fauxProfile)
	{
		this.fauxProfile = fauxProfile;
		Map<String, QuestCatalog.QuestEntry> quests = new HashMap<>();
		for (QuestCatalog.QuestEntry quest : questCatalog.getQuests())
		{
			quests.put(key(quest.name), quest);
		}
		questsByName = Collections.unmodifiableMap(quests);

		QuestCatalog.QuestEntry caves = null;
		for (QuestCatalog.QuestEntry content : contentCatalog.getContents())
		{
			if ("fight caves".equals(key(content.name)))
			{
				caves = content;
				break;
			}
		}
		fightCaves = caves;
	}

	Plan evaluate(Set<String> owned, Set<String> completed, Map<String, Integer> skills)
	{
		return evaluate(owned, completed, skills, 0L);
	}

	Plan evaluate(Set<String> owned, Set<String> completed, Map<String, Integer> skills,
		long credits)
	{
		return evaluate(owned, completed, skills, credits, "Unknown", Collections.emptyList());
	}

	Plan evaluate(Set<String> owned, Set<String> completed, Map<String, Integer> skills,
		long credits, String currentArea, List<String> nearbyUnlockedCombat)
	{
		return evaluate(owned, completed, skills, credits, currentArea,
			nearbyUnlockedCombat, owned);
	}

	Plan evaluate(Set<String> owned, Set<String> completed, Map<String, Integer> skills,
		long credits, String currentArea, List<String> nearbyUnlockedCombat,
		Set<String> possessed)
	{
		List<Recommendation> recommendations = new ArrayList<>();
		if (credits >= 2_500L)
		{
			long packs = credits / 2_500L;
			recommendations.add(new Recommendation("Open " + packs + " pack"
				+ (packs == 1 ? "" : "s") + " now",
				"New cards expand the legal action space; spend available pack credits before committing to a long blocked grind.",
				Collections.singletonList((credits % 2_500L) + " credits remain afterward"), true));
		}
		if (level(skills, "hunter") < 9 || level(skills, "slayer") < 9)
		{
			recommendations.add(new Recommendation("Complete the Varrock Museum quiz",
				"Highest-value free opener: no requirements, 1,000 Hunter + 1,000 Slayer XP, and both skills jump from 1 to 9 for many level-up credit bonuses.",
				Arrays.asList("CARDLESS: Orlando Smith and the quiz displays have no card gate",
					"Complete all 14 display cases"), true));
		}
		int agility = level(skills, "agility");
		if (agility < 20)
		{
			recommendations.add(new Recommendation("Train Agility to 20",
				"Use the Draynor Village rooftop course. It is a cardless action and earns non-combat XP credits.",
				Arrays.asList("CARDLESS: no item, NPC, or resource card", (20 - agility) + " Agility levels"), true));
		}
		addOpeningRecommendations(recommendations, owned, possessed, completed, skills);

		QuestCatalog.QuestEntry packQuest = bestPackQuest(owned, completed);
		if (packQuest != null)
		{
			List<String> physical = missingPhysicalQuestItems(packQuest, owned, possessed);
			recommendations.add(new Recommendation((physical.isEmpty() ? "Prioritize " : "Prepare ")
				+ packQuest.name + " for XP levels",
				packQuestReason(packQuest.name) + " Travel: "
					+ travelHint(packQuest.name, currentArea, possessed, completed),
				physical.isEmpty() ? Collections.singletonList("Required carded items detected") : physical,
				physical.isEmpty()));
		}

		GoalQuest nextQuest = nextBarrowsQuest(owned, completed, skills, possessed);
		if (nextQuest != null)
		{
			QuestCatalog.QuestEntry quest = questsByName.get(key(nextQuest.name));
			List<String> blockers = blockers(nextQuest, quest, owned, completed, skills, possessed);
			recommendations.add(new Recommendation(
				(blockers.isEmpty() ? "Do " : "Prepare ") + nextQuest.name,
				blockers.isEmpty()
					? "This is the next card-, skill-, and prerequisite-ready step toward Barrows gloves. Travel: "
						+ travelHint(nextQuest.name, currentArea, possessed, completed)
					: "Closest outstanding Barrows-gloves dependency. Travel: "
						+ travelHint(nextQuest.name, currentArea, possessed, completed),
				blockers, blockers.isEmpty()));
		}

		List<QuestCatalog.QuestEntry> cardReady = new ArrayList<>();
		for (QuestCatalog.QuestEntry quest : questsByName.values())
		{
			if (!completed.contains(key(quest.name))
				&& quest.satisfiedCount(owned) == quest.requirements.size())
			{
				cardReady.add(quest);
			}
		}
		cardReady.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
		if (!cardReady.isEmpty())
		{
			List<String> names = new ArrayList<>();
			for (int i = 0; i < Math.min(5, cardReady.size()); i++)
			{
				names.add(cardReady.get(i).name);
			}
			recommendations.add(new Recommendation("Review card-ready quests",
				"These have all catalogued card requirements, but may still need normal quest prerequisites.",
				names, true));
		}

		int fireCardsHave = fightCaves == null ? 0 : fightCaves.satisfiedCount(owned);
		int fireCardsTotal = fightCaves == null ? 0 : fightCaves.requirements.size();
		List<String> fireBlockers = new ArrayList<>();
		if (fightCaves != null)
		{
			for (QuestCatalog.Requirement requirement : fightCaves.requirements)
			{
				if (!requirement.isSatisfied(owned))
				{
					fireBlockers.add(requirement.label);
				}
			}
		}
		addSkillBlocker(fireBlockers, skills, "ranged", 61);
		addSkillBlocker(fireBlockers, skills, "prayer", 43);
		addSkillBlocker(fireBlockers, skills, "hitpoints", 50);

		int barrowsDone = 0;
		for (GoalQuest quest : BARROWS_GLOVE_QUESTS.values())
		{
			if (completed.contains(key(quest.name)))
			{
				barrowsDone++;
			}
		}

		List<String> watchList = new ArrayList<>();
		for (String card : fauxProfile.getHighImpactCards())
		{
			if (!owned.contains(key(card)))
			{
				watchList.add(card);
			}
		}

		List<String> fireLoadout = new ArrayList<>();
		addBestPossessed(fireLoadout, owned, possessed, "Weapon", "Toxic blowpipe", "Rune crossbow",
			"Magic shortbow", "Dorgeshuun crossbow", "Shortbow");
		addBestPossessed(fireLoadout, owned, possessed, "Ammunition", "Amethyst dart", "Rune bolt",
			"Broad bolt", "Rune arrow", "Bronze arrow");
		addBestPossessed(fireLoadout, owned, possessed, "Body", "Armadyl chestplate", "Karil's leathertop",
			"Black d'hide body", "Red d'hide body", "Green d'hide body");
		addBestPossessed(fireLoadout, owned, possessed, "Legs", "Armadyl chainskirt", "Karil's leatherskirt",
			"Black d'hide chaps", "Red d'hide chaps", "Green d'hide chaps");
		addBestPossessed(fireLoadout, owned, possessed, "Food", "Saradomin brew", "Manta ray",
			"Shark", "Monkfish", "Lobster");
		addBestPossessed(fireLoadout, owned, possessed, "Prayer restore", "Super restore", "Prayer potion");
		List<String> opportunityIdeas = buildOpportunityIdeas(owned, possessed, completed, skills,
			nearbyUnlockedCombat);

		recommendations.sort((left, right) -> Integer.compare(
			recommendationPriority(right) + areaPriority(right, currentArea),
			recommendationPriority(left) + areaPriority(left, currentArea)));
		// A missing random card is not an actionable route step. Keep skill/quest preparation
		// when its cards are already legal, but do not fill the live plan with things the
		// player can only unlock by getting lucky from a future pack.
		recommendations.removeIf(recommendation -> !recommendation.ready
			&& hasRandomCardBlocker(recommendation.blockers));
		if (recommendations.size() > 10)
		{
			recommendations = new ArrayList<>(recommendations.subList(0, 10));
		}

		return new Plan(Collections.unmodifiableList(recommendations), fireCardsHave,
			fireCardsTotal, Collections.unmodifiableList(fireBlockers), barrowsDone,
			BARROWS_GLOVE_QUESTS.size(), Collections.unmodifiableList(watchList),
			Collections.unmodifiableList(fireLoadout), credits, currentArea,
			Collections.unmodifiableList(new ArrayList<>(nearbyUnlockedCombat)),
			Collections.unmodifiableList(opportunityIdeas));
	}

	private static List<String> buildOpportunityIdeas(Set<String> owned, Set<String> possessed, Set<String> completed,
		Map<String, Integer> skills, List<String> nearbyCombat)
	{
		List<String> ideas = new ArrayList<>();
		if (!nearbyCombat.isEmpty())
		{
			ideas.add("Fight nearby " + nearbyCombat.get(0)
				+ " with legal gear for immediate kill credits.");
		}
		if (hasAny(owned, "Rabbit", "Bunny"))
		{
			ideas.add("Rabbit/Bunny is unlocked: punch or kick them for safe early combat levels and kill credits.");
		}
		if (possessed.contains(key("ring of dueling")))
		{
			ideas.add("Ring of dueling unlocks fast Castle Wars and Ferox routing; use it to cluster distant tasks.");
		}
		else if (owned.contains(key("ring of dueling")))
		{
			ideas.add("Ring of dueling card is unlocked, but no ring is detected; obtain one before using that travel route.");
		}
		if (possessed.contains(key("prayer potion")))
		{
			ideas.add("Prayer potion is a major Fire Cape supply unlock; preserve doses while building Ranged and 43 Prayer.");
		}
		else if (owned.contains(key("prayer potion")))
		{
			ideas.add("Prayer potion card is unlocked, but no potion is detected; treat it as an acquisition goal, not Fire Cape supplies yet.");
		}
		if (possessed.contains(key("dragon pickaxe")) && level(skills, "mining") < 61)
		{
			ideas.add(level(skills, "mining") < 6
				? "Dragon pickaxe is banked potential: use cardless specimen trays to 6 Mining first."
				: "Dragon pickaxe is unlocked for 61 Mining; prioritize legal Mining XP when it also earns pack credits.");
		}
		else if (owned.contains(key("dragon pickaxe")) && level(skills, "mining") < 61)
		{
			ideas.add("Dragon pickaxe card is unlocked, but the item is not detected; train Mining only when useful while seeking the actual pickaxe.");
		}
		if (possessed.contains(key("air tiara"))
			&& hasAny(possessed, "rune essence", "pure essence"))
		{
			ideas.add("Air tiara plus essence makes level-1 Runecraft legal and adds another non-combat credit engine.");
		}
		if (possessed.contains(key("glassblowing pipe")) && hasAny(possessed, "molten glass"))
		{
			ideas.add("Glassblowing pipe plus molten glass opens low-requirement Crafting XP and level bonuses.");
		}
		if (owned.contains(key("oak blackjack")) && level(skills, "thieving") < 30)
		{
			ideas.add("Oak blackjack becomes useful after The Feud and 30 Thieving; your cardless Thieving ladder works toward it.");
		}
		if (ideas.isEmpty())
		{
			ideas.add("Use the first legal Next Action for one pack, then reassess new pulls rather than over-grinding one skill.");
		}
		return ideas.size() > 6 ? new ArrayList<>(ideas.subList(0, 6)) : ideas;
	}

	private static int areaPriority(Recommendation recommendation, String area)
	{
		String title = key(recommendation.title);
		switch (key(area))
		{
			case "varrock": return title.contains("museum") || title.contains("varrock dummies") ? 180 : 0;
			case "draynor": return title.contains("agility") ? 180 : 0;
			case "ardougne": return title.contains("hazeel") || title.contains("thieving") ? 160 : 0;
			case "hosidius": return title.contains("fruit stall") || title.contains("thieving") ? 160 : 0;
			case "desert": return title.contains("cactus") || title.contains("panning") ? 160 : 0;
			case "falador": return title.contains("motherlode") ? 160 : 0;
			case "kourend": return title.contains("wintertodt") || title.contains("fruit stall") ? 150 : 0;
			default: return 0;
		}
	}

	private static boolean hasRandomCardBlocker(List<String> blockers)
	{
		for (String blocker : blockers)
		{
			String normalized = key(blocker);
			if (normalized.startsWith("card needed:") || normalized.startsWith("card:"))
			{
				return true;
			}
		}
		return false;
	}

	private static int recommendationPriority(Recommendation recommendation)
	{
		String title = key(recommendation.title);
		if (title.startsWith("open ")) return 1000;
		if (title.contains("varrock museum")) return 950;
		if (title.contains("agility to 20")) return 920;
		if (title.contains("restless ghost")) return 900;
		if (title.startsWith("prioritize ")) return 880;
		if (title.contains("attack to 8")) return 850;
		if (title.contains("hazeel cult")) return 830;
		if (title.startsWith("farm ")) return 810;
		if (title.contains("wintertodt")) return 800;
		if (title.contains("cardlessly")) return 790;
		if (title.contains("wealthy citizens") || title.contains("fruit stalls")
			|| title.contains("thieving toward")) return 780;
		if (title.startsWith("do ")) return recommendation.ready ? 760 : 620;
		if (title.contains("panning")) return 700;
		if (title.contains("big-net") || title.contains("motherlode")) return 690;
		if (title.contains("blast furnace pump")) return 680;
		if (title.contains("specimen") || title.contains("cactus")) return 650;
		if (title.startsWith("prepare ")) return 600;
		return 500;
	}

	private QuestCatalog.QuestEntry bestPackQuest(Set<String> owned, Set<String> completed)
	{
		QuestCatalog.QuestEntry best = null;
		int bestScore = Integer.MIN_VALUE;
		for (Map.Entry<String, Integer> candidate : PACK_QUEST_SCORES.entrySet())
		{
			QuestCatalog.QuestEntry quest = questsByName.get(candidate.getKey());
			if (quest == null || completed.contains(candidate.getKey())
				|| quest.satisfiedCount(owned) != quest.requirements.size())
			{
				continue;
			}
			if (candidate.getValue() > bestScore)
			{
				best = quest;
				bestScore = candidate.getValue();
			}
		}
		return best;
	}

	private static String packQuestReason(String quest)
	{
		switch (key(quest))
		{
			case "waterfall quest": return "13,750 Attack and Strength XP jumps both skills toward 30, creating many level bonuses and unlocking melee gear.";
			case "the knight's sword": return "12,725 Smithing XP can jump Smithing from 1 to 29 for a dense burst of level bonuses.";
			case "witch's house": return "6,325 Hitpoints XP can jump HP toward 24, adding level bonuses and directly improving Fire Cape survivability.";
			case "sea slug": return "7,175 Fishing XP can jump Fishing from 1 to 24, providing level bonuses and a new skilling credit engine.";
			case "the grand tree": return "Large Attack XP plus a Barrows Gloves prerequisite and gnome transport access gives three-way value.";
			case "tree gnome village": return "Large Attack XP, spirit-tree access and a Barrows Gloves prerequisite give strong combined value.";
			case "fight arena": return "12,175 Attack XP provides a large level-bonus burst and expands weapon access.";
			case "the restless ghost": return "Prayer XP advances Fire Cape readiness and the Barrows Gloves quest chain simultaneously.";
			case "x marks the spot": return "Its 300-XP lamp has no target-skill level requirement; the quest has no skill requirement, but its Spade card still gates completion.";
			default: return "High early XP or access value makes this an efficient way to generate level bonuses and expand future routes.";
		}
	}

	private static Map<String, Integer> buildPackQuestScores()
	{
		Map<String, Integer> scores = new LinkedHashMap<>();
		scores.put(key("Waterfall Quest"), 100);
		scores.put(key("The Knight's Sword"), 95);
		scores.put(key("Witch's House"), 92);
		scores.put(key("Sea Slug"), 90);
		scores.put(key("The Grand Tree"), 88);
		scores.put(key("Tree Gnome Village"), 86);
		scores.put(key("Fight Arena"), 84);
		scores.put(key("The Restless Ghost"), 82);
		scores.put(key("Hazeel Cult"), 78);
		scores.put(key("X Marks the Spot"), 77);
		scores.put(key("Doric's Quest"), 74);
		scores.put(key("Plague City"), 72);
		scores.put(key("Priest in Peril"), 70);
		return Collections.unmodifiableMap(scores);
	}

	private static void addOpeningRecommendations(List<Recommendation> recommendations,
		Set<String> owned, Set<String> possessed, Set<String> completed, Map<String, Integer> skills)
	{
		int attack = level(skills, "attack");
		if (attack < 8)
		{
			recommendations.add(new Recommendation("Train Attack to 8 on Varrock dummies",
				"Free, safe levels improve access to future weapon cards and both end goals.",
				Arrays.asList("CARDLESS: the dummies require no monster or weapon card",
					(8 - attack) + " Attack levels"), true));
		}
		if (!completed.contains(key("The Restless Ghost")))
		{
			recommendations.add(new Recommendation("Do The Restless Ghost",
				"Community starter route: free Prayer XP; also advances the Barrows Gloves chain and Fire Cape Prayer target.",
				Collections.singletonList("CARDLESS ROUTE: avoid the optional skeleton"), true));
		}
		if (!completed.contains(key("Hazeel Cult")))
		{
			recommendations.add(new Recommendation("Do Hazeel Cult",
				"Community starter route: jump-starts Thieving to 11 for a stronger pack-credit engine.",
				Collections.singletonList("CARDLESS ROUTE: side with Hazeel and avoid fighting Clivet"), true));
		}

		int thieving = level(skills, "thieving");
		if (thieving < 25)
		{
			recommendations.add(new Recommendation("Build Thieving toward 25 cardlessly",
				"H.A.M. members and stalls have no activity card under the community rules. Bank or destroy locked loot; do not use it until its card is owned.",
				Arrays.asList("CARDLESS: H.A.M. members from level 1", "Cake stalls from level 5",
					"Target 25 Thieving for fruit stalls"), true));
		}

		int mining = level(skills, "mining");
		if (mining < 6 && !hasAny(owned, "bronze pickaxe", "iron pickaxe"))
		{
			recommendations.add(new Recommendation("Search Digsite specimen trays to 6 Mining",
				"Zero-card fallback that activates a pulled steel-or-higher pickaxe and earns early level bonuses.",
				Arrays.asList("CARDLESS: no quest or item needed", (6 - mining) + " Mining levels"), true));
		}
		if (possessed.contains(key("cup of tea")) && (level(skills, "mining") < 10
			|| level(skills, "fishing") < 10))
		{
			recommendations.add(new Recommendation("Use Digsite panning for two skills",
				"Cup of tea unlocks a low-rate but double-purpose Mining and Fishing pack route.",
				Arrays.asList("Mining XP", "Fishing XP", "early level bonuses"), true));
		}
		if (level(skills, "woodcutting") < 6
			&& !hasAny(owned, "bronze axe", "iron axe"))
		{
			String slash = firstPossessedLegal(owned, possessed, "Knife", "Clan vexillum", "Bronze sword",
				"Iron sword", "Steel sword", "Bronze scimitar", "Iron scimitar", "Steel scimitar");
			List<String> blockers = slash == null
				? Collections.singletonList("CARD NEEDED: Knife or another owned slash weapon")
				: Arrays.asList("Card ready: " + slash, "Al Kharid or Ruins of Unkah cactus");
			recommendations.add(new Recommendation("Use cactus cutting to 6 Woodcutting",
				"The cactus is uncarded, but the knife or slash weapon used on it must be an owned card.",
				blockers, slash != null));
		}
		else if (thieving < 50)
		{
			recommendations.add(new Recommendation("Use Hosidius fruit stalls",
				"Steady non-combat XP credits with useful food outputs; target 50 Thieving.",
				Collections.singletonList((50 - thieving) + " Thieving levels"), true));
		}
		else
		{
			recommendations.add(new Recommendation("Use wealthy citizens for pack credits",
				"The community's scalable AFK Thieving route once its required cards and access are legal.",
				Collections.singletonList("Confirm target/loot cards before pickpocketing"), true));
		}

		String combatTarget = bestCombatCreditTarget(owned);
		if (combatTarget != null)
		{
			recommendations.add(new Recommendation("Farm " + combatTarget + " for kill credits",
				"Combat XP itself does not pay steady credits; prefer the highest safe, sustainable carded target.",
				Collections.singletonList("Use only legal gear and supplies"), true));
		}

		int firemaking = level(skills, "firemaking");
		if (firemaking >= 50 && hasAny(possessed, "bronze axe", "iron axe", "steel axe",
			"black axe", "mithril axe", "adamant axe", "rune axe", "dragon axe"))
		{
			String food = firstPossessedLegal(owned, possessed, "Cake", "Lobster", "Swordfish", "Monkfish",
				"Shark", "Manta ray", "Saradomin brew");
			recommendations.add(new Recommendation("Evaluate Wintertodt for credits",
				"High Firemaking XP can turn each group round into roughly a pack while banking useful rewards.",
				food == null
					? Collections.singletonList("CARD NEEDED: legally usable food healing 4+")
					: Arrays.asList("Card ready food: " + food, "Owned axe card required", "Prefer group worlds"),
				food != null));
		}

		addCardGatedFreeXp(recommendations, owned, possessed, completed, skills);
	}

	private static void addCardGatedFreeXp(List<Recommendation> recommendations,
		Set<String> owned, Set<String> possessed, Set<String> completed, Map<String, Integer> skills)
	{
		if (level(skills, "fishing") < 20 && possessed.contains(key("Big fishing net")))
		{
			recommendations.add(new Recommendation("Big-net fish at Civitas from level 1",
				"The spot has no skill requirement, but the Big fishing net card is mandatory. Keep catches locked unless their cards are owned.",
				Collections.singletonList("Card ready: Big fishing net"), true));
		}
		if (level(skills, "smithing") < 15)
		{
			String hammer = firstPossessedLegal(owned, possessed, "Hammer", "Imcando hammer");
			String pickaxe = firstPossessedLegal(owned, possessed, "Bronze pickaxe", "Iron pickaxe", "Steel pickaxe",
				"Black pickaxe", "Mithril pickaxe", "Adamant pickaxe", "Rune pickaxe", "Dragon pickaxe");
			if (hammer != null && pickaxe != null)
			{
				recommendations.add(new Recommendation("Repair Motherlode Mine struts for Smithing",
					"Repairs work from level 1; access still requires owned hammer and pickaxe cards.",
					Arrays.asList("Card ready: " + hammer, "Card ready: " + pickaxe), true));
			}
		}
		if (level(skills, "strength") >= 30 && !completed.contains(key("The Giant Dwarf")))
		{
			recommendations.add(new Recommendation("Start The Giant Dwarf and use the Blast Furnace pump",
				"The pump itself is cardless and trains Strength, but it gives no steady XP credits because Strength is combat XP.",
				Arrays.asList("CARDLESS METHOD", "Start the quest only far enough to reach Keldagrim"), true));
		}
		if (completed.contains(key("The Dig Site")))
		{
			recommendations.add(new Recommendation("Clean Varrock Museum finds for XP lamps",
				"The cleaning area is unlocked by The Dig Site. Its supplied cleaning tools are uncarded, and rare finds can award 500 XP in a skill already level 10+.",
				Arrays.asList("CARDLESS AFTER QUEST", "Choose a level 10+ non-combat skill for pack credits"), true));
		}
	}

	private static String bestCombatCreditTarget(Set<String> owned)
	{
		String[] targets = {"Gemstone crab", "King sand crab", "Ammonite crab",
			"Rock crab", "Sand crab", "Brutus", "Rabbit", "Bunny"};
		for (String target : targets)
		{
			if (owned.contains(key(target)))
			{
				return target;
			}
		}
		return null;
	}

	private static boolean hasAny(Set<String> owned, String... cards)
	{
		for (String card : cards)
		{
			if (owned.contains(key(card)))
			{
				return true;
			}
		}
		return false;
	}

	private static String firstPossessedLegal(Set<String> owned, Set<String> possessed,
		String... cards)
	{
		for (String card : cards)
		{
			if (owned.contains(key(card)) && possessed.contains(key(card)))
			{
				return card;
			}
		}
		return null;
	}

	private static void addBestPossessed(List<String> result, Set<String> owned,
		Set<String> possessed, String slot, String... candidates)
	{
		for (String candidate : candidates)
		{
			if (owned.contains(key(candidate)) && possessed.contains(key(candidate)))
			{
				result.add(slot + ": " + candidate);
				return;
			}
		}
		result.add(slot + ": no detected legal item yet");
	}

	private GoalQuest nextBarrowsQuest(Set<String> owned, Set<String> completed,
		Map<String, Integer> skills, Set<String> possessed)
	{
		GoalQuest best = null;
		int bestScore = Integer.MAX_VALUE;
		for (GoalQuest goal : BARROWS_GLOVE_QUESTS.values())
		{
			if (completed.contains(key(goal.name)))
			{
				continue;
			}
			boolean prerequisitesReady = true;
			for (String prerequisite : goal.prerequisites)
			{
				if (!completed.contains(key(prerequisite)))
				{
					prerequisitesReady = false;
					break;
				}
			}
			if (!prerequisitesReady)
			{
				continue;
			}
			QuestCatalog.QuestEntry quest = questsByName.get(key(goal.name));
			int score = blockers(goal, quest, owned, completed, skills, possessed).size() * 100 + goal.order;
			if (score < bestScore)
			{
				best = goal;
				bestScore = score;
			}
		}
		if (best == null)
		{
			for (GoalQuest goal : BARROWS_GLOVE_QUESTS.values())
			{
				if (!completed.contains(key(goal.name)))
				{
					return goal;
				}
			}
		}
		return best;
	}

	private static List<String> blockers(GoalQuest goal, QuestCatalog.QuestEntry quest,
		Set<String> owned, Set<String> completed, Map<String, Integer> skills,
		Set<String> possessed)
	{
		List<String> blockers = new ArrayList<>();
		for (String prerequisite : goal.prerequisites)
		{
			if (!completed.contains(key(prerequisite)))
			{
				blockers.add("Quest: " + prerequisite);
			}
		}
		for (Map.Entry<String, Integer> requirement : goal.skills.entrySet())
		{
			addSkillBlocker(blockers, skills, requirement.getKey(), requirement.getValue());
		}
		if (quest != null)
		{
			for (QuestCatalog.Requirement requirement : quest.requirements)
			{
				if (!requirement.isSatisfied(owned))
				{
					blockers.add("Card: " + requirement.label);
				}
				else if (!requirement.label.toLowerCase(Locale.ROOT).endsWith("(enemy)")
					&& !requirement.isSatisfied(possessed))
				{
					blockers.add("Acquire item: " + requirement.label);
				}
			}
		}
		return blockers;
	}

	private static List<String> missingPhysicalQuestItems(QuestCatalog.QuestEntry quest,
		Set<String> owned, Set<String> possessed)
	{
		List<String> missing = new ArrayList<>();
		for (QuestCatalog.Requirement requirement : quest.requirements)
		{
			if (requirement.isSatisfied(owned)
				&& !requirement.label.toLowerCase(Locale.ROOT).endsWith("(enemy)")
				&& !requirement.isSatisfied(possessed))
			{
				missing.add("Acquire item: " + requirement.label);
			}
		}
		return missing;
	}

	private static String travelHint(String quest, String currentArea,
		Set<String> possessed, Set<String> completed)
	{
		String destination = questArea(quest);
		if (key(destination).equals(key(currentArea)))
		{
			return "you are already in the " + destination + " region.";
		}
		if ("Morytania".equals(destination) && !completed.contains(key("Priest in Peril")))
		{
			return "Morytania is access-locked; complete Priest in Peril first.";
		}
		if ("Fossil Island".equals(destination) && !completed.contains(key("Bone Voyage")))
		{
			return "Fossil Island is access-locked; complete Bone Voyage first.";
		}
		if ("Karamja".equals(destination))
		{
			return "use the cardless TzHaar Fight Pit minigame teleport, or the Port Sarim boat when legal.";
		}
		if ("Kourend".equals(destination) || "Hosidius".equals(destination))
		{
			return "take Veos's free Port Sarim boat, then walk; no teleport item is required.";
		}
		if ("Ardougne".equals(destination) || "Gnome".equals(destination))
		{
			return possessed.contains(key("ring of dueling"))
				? "use your Ring of dueling to Castle Wars, then walk north/east."
				: "use the cardless Castle Wars minigame teleport, then walk.";
		}
		if ("Desert".equals(destination))
		{
			return "walk through the northern Al Kharid opening or use the free Tempoross ferry.";
		}
		if ("Lumbridge".equals(destination))
		{
			return "use the cardless Lumbridge Home Teleport when available, otherwise walk.";
		}
		if ("Varrock".equals(destination) && possessed.contains(key("chronicle")))
		{
			return "use your Chronicle and walk north.";
		}
		return "walk from " + currentArea + "; no mandatory transport card is known for this step.";
	}

	private static String questArea(String quest)
	{
		switch (key(quest))
		{
			case "cook's assistant": case "the restless ghost": case "recipe for disaster":
				return "Lumbridge";
			case "demon slayer": case "gertrude's cat": case "the dig site":
				return "Varrock";
			case "hazeel cult": case "sea slug": case "plague city": case "biohazard":
				return "Ardougne";
			case "waterfall quest": case "tree gnome village": case "the grand tree":
			case "monkey madness i": return "Gnome";
			case "the knight's sword": case "doric's quest": return "Falador";
			case "witch's house": return "Taverley";
			case "priest in peril": case "nature spirit": return "Morytania";
			case "the golem": case "shadow of the storm": return "Desert";
			case "bone voyage": return "Fossil Island";
			default: return "Other";
		}
	}

	private static void addSkillBlocker(List<String> blockers, Map<String, Integer> skills,
		String skill, int target)
	{
		int current = level(skills, skill);
		if (current < target)
		{
			blockers.add(target + " " + display(skill) + " (now " + current + ")");
		}
	}

	private static int level(Map<String, Integer> skills, String skill)
	{
		Integer value = skills.get(key(skill));
		return value == null ? 1 : value;
	}

	private static String key(String value)
	{
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private static String display(String value)
	{
		return Character.toUpperCase(value.charAt(0)) + value.substring(1);
	}

	private static Map<String, GoalQuest> buildBarrowsGloveQuests()
	{
		Map<String, GoalQuest> quests = new LinkedHashMap<>();
		add(quests, "Cook's Assistant");
		add(quests, "The Restless Ghost");
		add(quests, "Goblin Diplomacy");
		add(quests, "Demon Slayer");
		add(quests, "Fishing Contest");
		add(quests, "Doric's Quest");
		add(quests, "Gertrude's Cat");
		add(quests, "Priest in Peril");
		add(quests, "Nature Spirit", req("Priest in Peril", "The Restless Ghost"));
		add(quests, "Big Chompy Bird Hunting", skills("ranged", 5, "cooking", 30));
		add(quests, "Biohazard", req("Plague City"));
		add(quests, "Murder Mystery");
		add(quests, "Witch's House");
		add(quests, "Lost City", skills("woodcutting", 36, "crafting", 31));
		add(quests, "The Golem", skills("crafting", 20, "thieving", 25));
		add(quests, "Shadow of the Storm", req("Demon Slayer", "The Golem"));
		add(quests, "Tree Gnome Village");
		add(quests, "The Grand Tree", skills("agility", 25));
		add(quests, "Monkey Madness I", req("Tree Gnome Village", "The Grand Tree"));
		add(quests, "Recipe for Disaster", req("Cook's Assistant"));
		return Collections.unmodifiableMap(quests);
	}

	private static void add(Map<String, GoalQuest> quests, String name)
	{
		add(quests, name, Collections.emptyList(), Collections.emptyMap());
	}

	private static void add(Map<String, GoalQuest> quests, String name, List<String> prerequisites)
	{
		add(quests, name, prerequisites, Collections.emptyMap());
	}

	private static void add(Map<String, GoalQuest> quests, String name, Map<String, Integer> skills)
	{
		add(quests, name, Collections.emptyList(), skills);
	}

	private static void add(Map<String, GoalQuest> quests, String name,
		List<String> prerequisites, Map<String, Integer> skills)
	{
		quests.put(key(name), new GoalQuest(name, quests.size(), prerequisites, skills));
	}

	private static List<String> req(String... names)
	{
		return Arrays.asList(names);
	}

	private static Map<String, Integer> skills(Object... pairs)
	{
		Map<String, Integer> result = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			result.put((String) pairs[i], (Integer) pairs[i + 1]);
		}
		return result;
	}

	static final class Plan
	{
		final List<Recommendation> recommendations;
		final int fireCardsHave;
		final int fireCardsTotal;
		final List<String> fireBlockers;
		final int barrowsQuestsDone;
		final int barrowsQuestsTotal;
		final List<String> highImpactWatchList;
		final List<String> fireLoadout;
		final long credits;
		final String currentArea;
		final List<String> nearbyUnlockedCombat;
		final List<String> opportunityIdeas;

		private Plan(List<Recommendation> recommendations, int fireCardsHave,
			int fireCardsTotal, List<String> fireBlockers, int barrowsQuestsDone,
			int barrowsQuestsTotal, List<String> highImpactWatchList,
			List<String> fireLoadout, long credits, String currentArea,
			List<String> nearbyUnlockedCombat, List<String> opportunityIdeas)
		{
			this.recommendations = recommendations;
			this.fireCardsHave = fireCardsHave;
			this.fireCardsTotal = fireCardsTotal;
			this.fireBlockers = fireBlockers;
			this.barrowsQuestsDone = barrowsQuestsDone;
			this.barrowsQuestsTotal = barrowsQuestsTotal;
			this.highImpactWatchList = highImpactWatchList;
			this.fireLoadout = fireLoadout;
			this.credits = credits;
			this.currentArea = currentArea;
			this.nearbyUnlockedCombat = nearbyUnlockedCombat;
			this.opportunityIdeas = opportunityIdeas;
		}
	}

	static final class Recommendation
	{
		final String title;
		final String reason;
		final List<String> blockers;
		final boolean ready;

		private Recommendation(String title, String reason, List<String> blockers, boolean ready)
		{
			this.title = title;
			this.reason = reason;
			this.blockers = Collections.unmodifiableList(new ArrayList<>(blockers));
			this.ready = ready;
		}
	}

	private static final class GoalQuest
	{
		private final String name;
		private final int order;
		private final List<String> prerequisites;
		private final Map<String, Integer> skills;

		private GoalQuest(String name, int order, List<String> prerequisites,
			Map<String, Integer> skills)
		{
			this.name = name;
			this.order = order;
			this.prerequisites = prerequisites;
			this.skills = skills;
		}
	}
}
