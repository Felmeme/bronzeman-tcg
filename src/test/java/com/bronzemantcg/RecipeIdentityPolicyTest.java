package com.bronzemantcg;

import com.bronzemantcg.catalog.RecipeCatalog;
import com.bronzemantcg.ownership.BundledCardIdentityCatalog;
import com.bronzemantcg.ownership.CardOwnershipService;
import com.bronzemantcg.ownership.CardResolver;
import com.bronzemantcg.ownership.TcgOwnershipSnapshot;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.function.Predicate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RecipeIdentityPolicyTest
{
	private RecipeCatalog catalog;
	private CardOwnershipService ownershipService;
	private CardOwnershipService liveOwnershipService;

	@Before
	public void setUp()
	{
		Gson gson = new Gson();
		catalog = new RecipeCatalog(gson);
		ownershipService = new CardOwnershipService(
			new CardResolver(new BundledCardIdentityCatalog(gson)));
		liveOwnershipService = new CardOwnershipService(
			new CardResolver(LiveV1CatalogTestSupport.load()));
	}

	@Test
	public void recipesAcceptLegacyNamesAndAuthoritativeVariantIds()
	{
		RecipeCatalog.Recipe attackPotion = catalog.find(
			RecipeCatalog.KIND_INTERFACE, "Attack potion(3)", "Eye of newt");
		assertNotNull(attackPotion);

		Predicate<String> legacy = ownership(
			TcgOwnershipSnapshot.namesOnly(Collections.singleton("attack potion")),
			Collections.emptySet(), Collections.emptySet());
		assertTrue(attackPotion.missingRequirements(legacy, false, true).isEmpty());

		TcgOwnershipSnapshot doseId = TcgOwnershipSnapshot.fromApi(
			Collections.emptyList(), Collections.singletonList(123),
			Collections.emptyList(), null);
		assertTrue(attackPotion.missingRequirements(ownership(doseId,
			Collections.emptySet(), Collections.emptySet()), false, true).isEmpty());
	}

	@Test
	public void inputAndOutputEnforcementRemainIndependent()
	{
		RecipeCatalog.Recipe attackPotion = catalog.find(
			RecipeCatalog.KIND_INTERFACE, "Attack potion(3)", "Eye of newt");
		assertNotNull(attackPotion);
		Predicate<String> ownsInputs = ownership(TcgOwnershipSnapshot.namesOnly(
			Set.of("eye of newt", "guam potion")),
			Collections.emptySet(), Collections.emptySet());

		assertTrue(attackPotion.missingRequirements(ownsInputs, true, false).isEmpty());
		assertEquals(Collections.singletonList("Attack potion"),
			attackPotion.missingRequirements(ownsInputs, true, true));
	}

	@Test
	public void potteryBetaVariantsUnlockAndMissingFeedbackUsesV1Parents()
	{
		assertPotteryIdentity("Pot", "Unfired pot");
		assertPotteryIdentity("Bowl", "Unfired bowl");
		assertPotteryIdentity("Pie dish", "Unfired pie dish");
	}

	@Test
	public void recipesPreserveSharedExemptAndExactMissingMessages()
	{
		RecipeCatalog.Recipe recipe = catalog.find(
			RecipeCatalog.KIND_INTERFACE, "Leather vambraces", null);
		assertNotNull(recipe);
		TcgOwnershipSnapshot empty = TcgOwnershipSnapshot.namesOnly(Collections.emptySet());

		Set<String> shared = Set.of("Needle", "Thread");
		Set<String> exempt = Set.of("Leather", "Leather vambraces");
		assertTrue(recipe.missingRequirements(
			ownership(empty, shared, exempt), true, true).isEmpty());

		List<String> missing = recipe.missingRequirements(
			ownership(empty, Collections.emptySet(), Collections.emptySet()), true, true);
		assertEquals(List.of("Needle", "Thread", "Leather", "Leather vambraces"), missing);
	}

	@Test
	public void costumeNeedleReplacementWorksThroughIdentityPredicate()
	{
		RecipeCatalog.Recipe recipe = catalog.find(
			RecipeCatalog.KIND_INTERFACE, "Leather vambraces", null);
		assertNotNull(recipe);
		TcgOwnershipSnapshot owned = TcgOwnershipSnapshot.namesOnly(
			Set.of("costume needle", "leather", "leather vambraces"));
		assertTrue(recipe.missingRequirements(ownership(owned,
			Collections.emptySet(), Collections.emptySet()), true, true).isEmpty());
	}

	@Test
	public void crushedGemUsesTheSameIdentityAwareSources()
	{
		TcgOwnershipSnapshot empty = TcgOwnershipSnapshot.fromApi(
			Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null);
		assertTrue(ownership(empty, Collections.singleton("Crushed gem"),
			Collections.emptySet()).test("Crushed gem"));
		assertTrue(ownership(empty, Collections.emptySet(),
			Collections.singleton("Crushed gem")).test("Crushed gem"));
	}

	@Test
	public void everyShippedRecipeRequirementHasOneReviewedIdentity()
	{
		Set<String> requirements = new LinkedHashSet<>();
		InputStream resource = RecipeIdentityPolicyTest.class
			.getResourceAsStream("/nodes/recipe_nodes.json");
		assertNotNull(resource);
		JsonArray recipes = new JsonParser().parse(new InputStreamReader(resource,
			StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("recipes");
		for (JsonElement element : recipes)
		{
			JsonObject recipe = element.getAsJsonObject();
			for (JsonElement groupElement : recipe.getAsJsonArray("inputs"))
			{
				for (JsonElement card : groupElement.getAsJsonArray())
				{
					requirements.add(card.getAsString());
				}
			}
			if (!recipe.get("output").isJsonNull())
			{
				requirements.add(recipe.get("output").getAsString());
			}
		}
		TcgOwnershipSnapshot empty = TcgOwnershipSnapshot.fromApi(
			Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null);
		assertEquals(583, requirements.size());
		for (String requirement : requirements)
		{
			assertEquals(requirement, CardOwnershipService.Status.LOCKED,
				liveOwnershipService.decideCard(requirement, empty,
					Collections.emptySet(), Collections.emptySet()).getStatus());
		}
		assertEquals(CardOwnershipService.Status.UNTRACKED,
			liveOwnershipService.decideCard("Crushed gem", empty,
				Collections.emptySet(), Collections.emptySet()).getStatus());
	}

	private Predicate<String> ownership(TcgOwnershipSnapshot snapshot,
		Set<String> shared, Set<String> exempt)
	{
		return card -> ownershipService.decideCard(
			card, snapshot, shared, exempt).isAllowed();
	}

	private void assertPotteryIdentity(String parent, String betaVariant)
	{
		RecipeCatalog.Recipe recipe = catalog.find(
			RecipeCatalog.KIND_INTERFACE, parent, "potter's wheel");
		assertNotNull(recipe);

		Predicate<String> ownsVariant = ownership(TcgOwnershipSnapshot.namesOnly(
			Set.of("soft clay", betaVariant.toLowerCase())),
			Collections.emptySet(), Collections.emptySet());
		assertTrue(recipe.missingRequirements(ownsVariant, true, true).isEmpty());

		Predicate<String> missingOutput = ownership(TcgOwnershipSnapshot.namesOnly(
			Set.of("soft clay")), Collections.emptySet(), Collections.emptySet());
		assertEquals(List.of(parent), recipe.missingRequirements(missingOutput, true, true));
	}
}
