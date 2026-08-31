package com.bronzemantcg;

import com.bronzemantcg.catalog.ResourceNodeCatalog;
import com.bronzemantcg.restriction.FishingRequirementPolicy;
import com.bronzemantcg.restriction.FishingRestrictionMode;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.function.Predicate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class FishingRequirementPolicyTest
{
	private final ResourceNodeCatalog catalog = new ResourceNodeCatalog(new Gson());

	@Test
	public void baitRequiresRodAndBaitEvenWhenNeitherIsCarried()
	{
		ResourceNodeCatalog.Rule rule = rule("SHRIMP", "Bait");
		assertEquals(List.of("Fishing rod / Pearl fishing rod", "Fishing bait"),
			missing(rule, FishingRestrictionMode.TOOL_ONLY,
				Collections.emptySet(), Collections.emptySet()));
	}

	@Test
	public void cageRequiresTheLobsterPotCardEvenWhenItIsAbsent()
	{
		ResourceNodeCatalog.Rule rule = rule("LOBSTER", "Cage");
		assertEquals(Collections.singletonList("Lobster pot"),
			missing(rule, FishingRestrictionMode.TOOL_ONLY,
				Collections.emptySet(), Collections.emptySet()));
		assertEquals(Collections.emptyList(),
			missing(rule, FishingRestrictionMode.TOOL_ONLY,
				Collections.singleton("lobster pot"), Collections.emptySet()));
	}

	@Test
	public void unlockedAlternativeDoesNotExcuseALockedCarriedTool()
	{
		ResourceNodeCatalog.Rule rule = rule("SHRIMP", "Bait");
		assertEquals(Collections.singletonList("Fishing rod"),
			missing(rule, FishingRestrictionMode.TOOL_ONLY,
				Set.of("pearl fishing rod", "fishing bait"),
				Collections.singleton("Fishing rod")));
	}

	@Test
	public void noCarriedHarpoonPreservesBareHandedFishing()
	{
		ResourceNodeCatalog.Rule rule = rule("LOBSTER", "Harpoon");
		assertEquals(Collections.emptyList(),
			missing(rule, FishingRestrictionMode.TOOL_ONLY,
				Collections.emptySet(), Collections.emptySet()));
		assertEquals(Collections.singletonList(
			"Harpoon / Dragon harpoon / Infernal harpoon / Crystal harpoon / "
				+ "Barb-tail harpoon"),
			missing(rule, FishingRestrictionMode.TOOL_ONLY,
				Collections.emptySet(), Collections.singleton("Harpoon")));
	}

	@Test
	public void catchModesRetainAnyOfAndAllSemantics()
	{
		ResourceNodeCatalog.Rule rule = rule("SHRIMP", "Bait");
		Set<String> tools = Set.of("fishing rod", "fishing bait");
		assertEquals(Collections.singletonList("Raw sardine / Raw herring"),
			missing(rule, FishingRestrictionMode.CARD_REQUIRED, tools,
				Set.of("Fishing rod", "Fishing bait")));
		assertEquals(Collections.singletonList("Raw herring"),
			missing(rule, FishingRestrictionMode.ALL_CATCHES,
				Set.of("fishing rod", "fishing bait", "raw sardine"),
				Set.of("Fishing rod", "Fishing bait")));
	}

	private ResourceNodeCatalog.Rule rule(String spot, String option)
	{
		ResourceNodeCatalog.Rule rule = catalog.find(
			ResourceNodeCatalog.KIND_FISHING_SPOT, spot, option);
		assertNotNull(rule);
		return rule;
	}

	private static List<String> missing(ResourceNodeCatalog.Rule rule,
		FishingRestrictionMode mode, Set<String> owned, Set<String> carried)
	{
		Predicate<String> ownsCard = card -> owned.contains(
			card.toLowerCase(Locale.ROOT));
		return FishingRequirementPolicy.missingRequirements(rule, mode, ownsCard, carried);
	}
}
