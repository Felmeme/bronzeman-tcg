package com.bronzemantcg;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CardcorePlannerTest
{
	private final QuestCatalog quests = new QuestCatalog(new Gson());
	private final ContentCatalog content = new ContentCatalog(new Gson());
	private final FauxCardcoreProfile profile = new FauxCardcoreProfile(new Gson());
	private final CardcorePlanner planner = new CardcorePlanner(quests, content, profile);

	@Test
	public void newAccountGetsAgilityAndGoalAdvice()
	{
		Map<String, Integer> skills = new HashMap<>();
		skills.put("agility", 12);
		CardcorePlanner.Plan plan = planner.evaluate(Collections.emptySet(),
			Collections.emptySet(), skills);

		assertEquals("Complete the Varrock Museum quiz", plan.recommendations.get(0).title);
		assertTrue(plan.recommendations.stream().anyMatch(r -> r.title.equals("Train Agility to 20")));
		assertTrue(plan.recommendations.get(0).estimate.creditsPerHour > 0);
		assertEquals(0, plan.barrowsQuestsDone);
		assertFalse(plan.fireBlockers.isEmpty());
		assertFalse(profile.getRules().isEmpty());
		assertTrue(plan.highImpactWatchList.stream()
			.anyMatch(card -> card.startsWith("Coins")));
	}

	@Test
	public void ownedFightCaveCardsAdvanceTheMilestone()
	{
		Set<String> cards = new HashSet<>();
		Collections.addAll(cards, "ket-zek", "tok-xil", "tz-kek", "tz-kih",
			"tztok-jad", "yt-hurkot", "yt-mejkot");
		Map<String, Integer> skills = new HashMap<>();
		skills.put("ranged", 61);
		skills.put("prayer", 43);
		skills.put("hitpoints", 50);
		skills.put("agility", 30);

		CardcorePlanner.Plan plan = planner.evaluate(cards, Collections.emptySet(), skills);
		assertEquals(plan.fireCardsTotal, plan.fireCardsHave);
		assertTrue(plan.fireBlockers.isEmpty());
	}

	@Test
	public void foilTorvaHelmUnlocksEveryHeadSlotCard()
	{
		FoilUnlockCatalog foils = new FoilUnlockCatalog(new Gson());
		assertTrue(foils.isUnlockedByFoil("Bronze full helm",
			Collections.singleton("torva full helm")));
		assertTrue(foils.isUnlockedByFoil("Armadyl helmet",
			Collections.singleton("torva full helm")));
		assertFalse(foils.isUnlockedByFoil("Torva platebody",
			Collections.singleton("torva full helm")));
		assertTrue(foils.inheritedItemNames(Collections.singleton("torva full helm"))
			.contains("bronze full helm"));
	}

	@Test
	public void availablePackAlwaysBecomesFirstAction()
	{
		Map<String, Integer> skills = new HashMap<>();
		skills.put("agility", 20);
		CardcorePlanner.Plan plan = planner.evaluate(Collections.emptySet(),
			Collections.emptySet(), skills, 5_200L);
		assertEquals("Open 2 packs now", plan.recommendations.get(0).title);
		assertEquals(5_200L, plan.credits);
	}

	@Test
	public void museumStopsBeingSuggestedAfterBothSkillsReachNine()
	{
		Map<String, Integer> skills = new HashMap<>();
		skills.put("agility", 12);
		skills.put("hunter", 9);
		skills.put("slayer", 9);
		CardcorePlanner.Plan plan = planner.evaluate(Collections.emptySet(),
			Collections.emptySet(), skills);
		assertFalse(plan.recommendations.stream()
			.anyMatch(r -> r.title.contains("Varrock Museum quiz")));
		assertTrue(plan.recommendations.stream()
			.anyMatch(r -> r.title.equals("Train Agility to 20")));
	}

	@Test
	public void currentTcgSchemaExposesNestedFoilsAndCredits()
	{
		String json = "{\"economyState\":{\"credits\":2600},"
			+ "\"collectionState\":{\"instances\":[{\"cardName\":\"Torva full helm\",\"foil\":true}]}}";
		TcgStateDto state = new Gson().fromJson(json, TcgStateDto.class);
		assertEquals(2_600L, state.economyState.credits);
		assertEquals("Torva full helm", state.instances().get(0).cardName);
		assertTrue(state.instances().get(0).foil);
	}

	@Test
	public void latestTcgSchemaExposesCardEntriesFoilsAndCredits()
	{
		String json = "{\"credits\":641,\"openedPacks\":31,"
			+ "\"killCreditMultiplier\":1.0,\"levelUpCreditMultiplier\":1.0,"
			+ "\"xpCreditMultiplier\":1.0,\"skillCreditBaseline\":{\"uncreditedXp\":328},"
			+ "\"cardEntries\":["
			+ "{\"cardName\":\"Torva full helm\",\"variants\":[{\"foil\":true}]}]}";
		TcgStateDto state = new Gson().fromJson(json, TcgStateDto.class);
		assertEquals(641L, state.credits());
		assertEquals("Torva full helm", state.instances().get(0).cardName);
		assertTrue(state.instances().get(0).foil);
		assertEquals(328L, state.skillCreditBaseline.uncreditedXp);
	}

	@Test
	public void cactusIsHiddenWithoutASlashItemCard()
	{
		Map<String, Integer> skills = new HashMap<>();
		skills.put("agility", 20);
		skills.put("hunter", 9);
		skills.put("slayer", 9);
		CardcorePlanner.Plan plan = planner.evaluate(Collections.emptySet(),
			Collections.emptySet(), skills);
		assertFalse(plan.recommendations.stream()
			.anyMatch(r -> r.title.contains("cactus")));
	}

	@Test
	public void ownedKnifeMakesCactusCardReady()
	{
		Map<String, Integer> skills = new HashMap<>();
		skills.put("agility", 20);
		skills.put("hunter", 9);
		skills.put("slayer", 9);
		CardcorePlanner.Plan plan = planner.evaluate(Collections.singleton("knife"),
			Collections.emptySet(), skills);
		assertTrue(plan.recommendations.stream()
			.anyMatch(r -> r.title.contains("cactus") && r.ready));
	}

	@Test
	public void bigNetRouteOnlyAppearsWithBigNetCard()
	{
		Map<String, Integer> skills = new HashMap<>();
		skills.put("agility", 20);
		skills.put("hunter", 9);
		skills.put("slayer", 9);
		CardcorePlanner.Plan locked = planner.evaluate(Collections.emptySet(),
			Collections.emptySet(), skills);
		assertFalse(locked.recommendations.stream()
			.anyMatch(r -> r.title.contains("Big-net")));
		CardcorePlanner.Plan unlocked = planner.evaluate(Collections.singleton("big fishing net"),
			Collections.emptySet(), skills);
		assertTrue(unlocked.recommendations.stream()
			.anyMatch(r -> r.title.contains("Big-net") && r.ready));
	}

	@Test
	public void currentAreaBoostsLocalLegalRouteAndCarriesNearbyCombat()
	{
		Map<String, Integer> skills = new HashMap<>();
		skills.put("agility", 12);
		CardcorePlanner.Plan plan = planner.evaluate(Collections.emptySet(),
			Collections.emptySet(), skills, 0L, "Draynor",
			Collections.singletonList("Goblin (level 2)"));
		assertTrue(plan.recommendations.stream()
			.anyMatch(r -> r.title.equals("Train Agility to 20")));
		assertEquals("Draynor", plan.currentArea);
		assertTrue(plan.nearbyUnlockedCombat.get(0).startsWith("Goblin (level 2) — reasonable"));
		assertTrue(plan.nearbyUnlockedCombat.get(0).contains("credits/hr"));
	}

	@Test
	public void unlockedCardWithoutPhysicalItemDoesNotEnableMethod()
	{
		Map<String, Integer> skills = new HashMap<>();
		skills.put("agility", 20);
		skills.put("hunter", 9);
		skills.put("slayer", 9);
		CardcorePlanner.Plan plan = planner.evaluate(Collections.singleton("cup of tea"),
			Collections.emptySet(), skills, 0L, "Varrock", Collections.emptyList(),
			Collections.emptySet());
		assertFalse(plan.recommendations.stream()
			.anyMatch(r -> r.title.contains("panning")));
	}

	@Test
	public void questWithLegalButMissingItemBecomesPreparationStep()
	{
		Map<String, Integer> skills = new HashMap<>();
		skills.put("agility", 20);
		skills.put("hunter", 9);
		skills.put("slayer", 9);
		CardcorePlanner.Plan plan = planner.evaluate(Collections.singleton("spade"),
			Collections.emptySet(), skills, 0L, "Lumbridge", Collections.emptyList(),
			Collections.emptySet());
		assertTrue(plan.recommendations.stream().anyMatch(r ->
			r.title.equals("Prepare X Marks the Spot for XP levels")
				&& r.blockers.stream().anyMatch(b -> b.startsWith("Acquire item: Spade"))));
	}

	@Test
	public void currentEarlyAccountSimulationStaysLegalAndPackFocused()
	{
		Set<String> cards = new HashSet<>();
		Collections.addAll(cards, "rabbit", "bunny", "ring of dueling", "prayer potion",
			"dragon pickaxe", "air tiara", "glassblowing pipe", "oak blackjack");
		Map<String, Integer> skills = new HashMap<>();
		skills.put("attack", 8);
		skills.put("hitpoints", 10);
		skills.put("prayer", 9);
		skills.put("agility", 30);
		skills.put("slayer", 9);
		skills.put("hunter", 9);

		CardcorePlanner.Plan opening = planner.evaluate(cards, Collections.emptySet(), skills,
			641L, "Varrock", Collections.singletonList("Rabbit (level 2)"),
			Collections.emptySet(), Collections.emptyMap(), false,
			Collections.emptySet(), 3210, 3424);
		assertTrue(opening.recommendations.stream().anyMatch(r -> r.title.equals("Do Hazeel Cult")));
		assertTrue(opening.opportunityIdeas.stream().anyMatch(i -> i.startsWith("Rabbit/Bunny")));
		assertFalse(opening.recommendations.stream().anyMatch(r ->
			r.title.contains("Jungle Potion") || r.title.contains("The Lost Tribe")
				|| r.title.contains("Tribal Totem") || r.title.contains("Ethically Acquired")));
		assertFalse(opening.bankSnapshotFresh);

		Map<String, Integer> afterThieving = new HashMap<>(skills);
		afterThieving.put("thieving", 25);
		CardcorePlanner.Plan packEarned = planner.evaluate(cards,
			Collections.singleton("hazeel cult"), afterThieving, 2_500L);
		assertEquals("Open 1 pack now", packEarned.recommendations.get(0).title);
	}

	@Test
	public void liveXpOptimizerEstimatesCurrentThievingTimeToPack()
	{
		Map<String, Integer> skills = new HashMap<>();
		skills.put("agility", 31);
		skills.put("thieving", 11);
		skills.put("hunter", 9);
		skills.put("slayer", 9);
		Map<String, Integer> xp = new HashMap<>();
		xp.put("thieving", 1_500);
		Set<String> completed = new HashSet<>();
		Collections.addAll(completed, "the restless ghost", "hazeel cult");

		CardcorePlanner.Plan plan = planner.evaluate(Collections.emptySet(), completed,
			skills, 1_584L, "Ardougne", Collections.emptyList(), Collections.emptySet(),
			Collections.emptyMap(), true, Collections.emptySet(), 2662, 3305, xp,
			TcgCollectionReader.RewardRates.DEFAULT);
		CardcorePlanner.Recommendation thieving = plan.recommendations.stream()
			.filter(r -> r.title.contains("Thieving toward 25"))
			.findFirst().orElseThrow(AssertionError::new);
		assertTrue(thieving.estimate.creditsPerHour > 0);
		assertTrue(thieving.estimate.minutesToPack > 0);
		assertTrue(thieving.estimate.minutesToPack <= 10);
	}

	@Test
	public void nearbyCombatRanksSafeEfficientTargetAndDetectedWeaponFirst()
	{
		Map<String, Integer> skills = new HashMap<>();
		skills.put("attack", 8);
		skills.put("strength", 5);
		skills.put("hitpoints", 10);
		CardcorePlanner.Plan plan = planner.evaluate(Collections.singleton("iron scimitar"),
			Collections.emptySet(), skills, 0L, "Lumbridge",
			java.util.Arrays.asList("Dark wizard (level 20) [4 tiles]",
				"Goblin (level 2) [8 tiles]"), Collections.singleton("iron scimitar"));
		assertTrue(plan.nearbyUnlockedCombat.get(0).startsWith("Goblin (level 2)"));
		assertTrue(plan.nearbyUnlockedCombat.get(0).contains("Iron scimitar"));
		assertTrue(plan.nearbyUnlockedCombat.get(1).contains("risky"));
	}
}
