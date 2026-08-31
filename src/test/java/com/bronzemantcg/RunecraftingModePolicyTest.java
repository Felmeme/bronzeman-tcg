package com.bronzemantcg;

import com.bronzemantcg.catalog.ResourceNodeCatalog;
import com.bronzemantcg.ownership.BundledCardIdentityCatalog;
import com.bronzemantcg.ownership.CardOwnershipService;
import com.bronzemantcg.ownership.CardResolver;
import com.bronzemantcg.ownership.TcgOwnershipSnapshot;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.function.Predicate;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class RunecraftingModePolicyTest
{
	private ResourceNodeCatalog.Rule waterAltar;
	private CardOwnershipService ownershipService;

	@Before
	public void setUp()
	{
		waterAltar = new ResourceNodeCatalog(new Gson()).find(
			ResourceNodeCatalog.KIND_OBJECT, "Altar", "Craft-rune", 34762);
		assertNotNull(waterAltar);
		ownershipService = new CardOwnershipService(
			new CardResolver(new BundledCardIdentityCatalog(new Gson())));
	}

	@Test
	public void genericAltarNameRequiresAnExactObjectId()
	{
		ResourceNodeCatalog catalog = new ResourceNodeCatalog(new Gson());
		assertNull(catalog.find(ResourceNodeCatalog.KIND_OBJECT,
			"Altar", "Craft-rune"));
		assertNotNull(catalog.find(ResourceNodeCatalog.KIND_OBJECT,
			"Altar", "Craft-rune", 29631));
	}

	@Test
	public void runicAltarIdsResolveTheirOwnOutputRune()
	{
		ResourceNodeCatalog catalog = new ResourceNodeCatalog(new Gson());
		assertOutputRune(catalog, 34760, "Air rune");
		assertOutputRune(catalog, 34761, "Mind rune");
		assertOutputRune(catalog, 34762, "Water rune");
		assertOutputRune(catalog, 34763, "Earth rune");
		assertOutputRune(catalog, 34764, "Fire rune");
		assertOutputRune(catalog, 34765, "Body rune");
		assertOutputRune(catalog, 34766, "Cosmic rune");
		assertOutputRune(catalog, 34767, "Law rune");
		assertOutputRune(catalog, 34768, "Nature rune");
		assertOutputRune(catalog, 34769, "Chaos rune");
		assertOutputRune(catalog, 34770, "Death rune");
		assertOutputRune(catalog, 34771, "Astral rune");
		assertOutputRune(catalog, 34772, "Wrath rune");
		assertOutputRune(catalog, 27978, "Blood rune");
		assertOutputRune(catalog, 27980, "Soul rune");
		assertOutputRune(catalog, 43479, "Blood rune");

		ResourceNodeCatalog.Rule ourania = catalog.find(
			ResourceNodeCatalog.KIND_OBJECT, "Altar", "Craft-rune", 29631);
		assertNotNull(ourania);
		assertEquals(Collections.emptyList(),
			ourania.missingRequirementsForRole(Collections.emptySet(), "rune"));
	}

	@Test
	public void waterAcceptsSpecificOrElementalAccessCards()
	{
		assertAccessAllowed(waterAltar, "Water talisman");
		assertAccessAllowed(waterAltar, "Water tiara");
		assertAccessAllowed(waterAltar, "Elemental talisman");
		assertAccessAllowed(waterAltar, "Elemental tiara");
	}

	@Test
	public void catalyticAccessCardsWorkAtCatalyticAltars()
	{
		ResourceNodeCatalog.Rule natureAltar = new ResourceNodeCatalog(new Gson()).find(
			ResourceNodeCatalog.KIND_OBJECT, "Altar", "Craft-rune", 34768);
		assertNotNull(natureAltar);
		assertAccessAllowed(natureAltar, "Nature talisman");
		assertAccessAllowed(natureAltar, "Nature tiara");
		assertAccessAllowed(natureAltar, "Catalytic talisman");
		assertAccessAllowed(natureAltar, "Catalytic tiara");
	}

	@Test
	public void specialAltarsKeepTheirDistinctAccessRules()
	{
		ResourceNodeCatalog catalog = new ResourceNodeCatalog(new Gson());
		for (int objectId : new int[]{29631, 34771, 27978, 27980})
		{
			ResourceNodeCatalog.Rule rule = catalog.find(ResourceNodeCatalog.KIND_OBJECT,
				"Altar", "Craft-rune", objectId);
			assertNotNull(rule);
			assertEquals(Collections.emptyList(),
				rule.missingRequirementsForRole(Collections.emptySet(), "talisman"));
		}

		ResourceNodeCatalog.Rule trueBloodAltar = catalog.find(
			ResourceNodeCatalog.KIND_OBJECT, "Altar", "Craft-rune", 43479);
		assertNotNull(trueBloodAltar);
		assertAccessAllowed(trueBloodAltar, "Blood talisman");
		assertAccessAllowed(trueBloodAltar, "Blood tiara");
		assertAccessAllowed(trueBloodAltar, "Catalytic talisman");
		assertAccessAllowed(trueBloodAltar, "Catalytic tiara");
	}

	@Test
	public void inputOnlyExcludesRuneButInputAndOutputRequiresIt()
	{
		Predicate<String> betaOwnership = ownership(
			TcgOwnershipSnapshot.namesOnly(Set.of(
				"rune essence", "water talisman")));
		assertEquals(Collections.emptyList(), waterAltar.missingRequirements(
			betaOwnership, Collections.singleton("rune"), false));
		assertEquals(Collections.singletonList("Water rune"),
			waterAltar.missingRequirements(betaOwnership, Collections.emptySet(), false));
	}

	@Test
	public void gotrExcludesSuppliedAccessButStillRequiresOutputRune()
	{
		Predicate<String> noOwnership = ownership(
			TcgOwnershipSnapshot.namesOnly(Collections.emptySet()));
		assertEquals(Collections.singletonList("Water rune"),
			waterAltar.missingRequirements(noOwnership,
				Set.of("essence", "talisman"), false));

		Predicate<String> ownsRune = ownership(
			TcgOwnershipSnapshot.namesOnly(Collections.singleton("water rune")));
		assertEquals(Collections.emptyList(), waterAltar.missingRequirements(
			ownsRune, Set.of("essence", "talisman"), false));
	}

	@Test
	public void betaWaterRunePackIdNowGrantsWaterRuneParent()
	{
		TcgOwnershipSnapshot betaPack = TcgOwnershipSnapshot.fromApi(
			Collections.singletonList("Water rune pack"),
			Collections.singletonList(12730), Collections.emptyList(), null);
		assertEquals(Collections.emptyList(),
			waterAltar.missingRequirements(ownership(betaPack),
				Set.of("essence", "talisman"), false));
	}

	private Predicate<String> ownership(TcgOwnershipSnapshot snapshot)
	{
		return card -> ownershipService.decideCard(
			card, snapshot, Collections.emptySet(), Collections.emptySet()).isAllowed();
	}

	private static void assertOutputRune(ResourceNodeCatalog catalog, int objectId,
		String rune)
	{
		ResourceNodeCatalog.Rule rule = catalog.find(ResourceNodeCatalog.KIND_OBJECT,
			"Altar", "Craft-rune", objectId);
		assertNotNull(rule);
		assertEquals(Collections.singletonList(rune),
			rule.missingRequirementsForRole(Collections.emptySet(), "rune"));
	}

	private static void assertAccessAllowed(ResourceNodeCatalog.Rule rule, String accessCard)
	{
		assertEquals(Collections.emptyList(), rule.missingRequirementsForRole(
			Collections.singleton(accessCard.toLowerCase()), "talisman"));
	}
}
