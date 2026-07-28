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
		assertEquals("Train Agility to 20", plan.recommendations.get(1).title);
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
		assertEquals("Train Agility to 20", plan.recommendations.get(0).title);
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
}
