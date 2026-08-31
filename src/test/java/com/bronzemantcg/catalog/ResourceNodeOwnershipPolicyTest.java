package com.bronzemantcg.catalog;


import com.bronzemantcg.LiveV1CatalogTestSupport;
import com.bronzemantcg.catalog.remote.OsrsTcgCatalogSnapshot;
import com.bronzemantcg.ownership.CardEntityKind;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ResourceNodeOwnershipPolicyTest
{
	private final ResourceNodeCatalog.Rule rule = new ResourceNodeCatalog.Rule("test", List.of(
		ResourceNodeCatalog.CardGroup.of(List.of("A", "B"), "output", null),
		ResourceNodeCatalog.CardGroup.of(Collections.singletonList("C"), "tool", null)));

	@Test
	public void predicateOwnershipPreservesAnyOfAndRoleExclusion()
	{
		Predicate<String> ownsB = card -> "b".equals(card);
		assertEquals(Collections.singletonList("C"),
			rule.missingRequirements(ownsB, Collections.emptySet(), false));
		assertEquals(Collections.emptyList(),
			rule.missingRequirements(ownsB, Collections.singleton("tool"), false));
	}

	@Test
	public void predicateOwnershipPreservesForcedAllAndRoleOnlyEvaluation()
	{
		Predicate<String> ownsB = card -> "b".equals(card);
		assertEquals(List.of("A", "C"),
			rule.missingRequirements(ownsB, Collections.emptySet(), true));
		assertEquals(Collections.singletonList("C"),
			rule.missingRequirementsForRole(ownsB, "tool"));
		assertEquals(Collections.emptyList(),
			rule.missingRequirementsForRole(ownsB, "missing"));
	}

	@Test
	public void everyShippedNodeRequirementHasOneReviewedIdentity()
	{
		ResourceNodeCatalog nodes = new ResourceNodeCatalog(new com.google.gson.Gson());
		OsrsTcgCatalogSnapshot identities = LiveV1CatalogTestSupport.load();
		Set<String> missing = new HashSet<>();
		Set<String> ambiguous = new HashSet<>();
		for (ResourceNodeCatalog.Rule node : new HashSet<>(nodes.getRuleEntries().values()))
		{
			for (ResourceNodeCatalog.CardGroup group : node.groups)
			{
				for (String card : group.displayCards)
				{
					int matches = identities.findByCardName(CardEntityKind.ITEM, card).size()
						+ identities.findByCardName(CardEntityKind.NPC, card).size();
					if (matches == 0)
					{
						missing.add(card);
					}
					else if (matches > 1)
					{
						ambiguous.add(card);
					}
				}
			}
		}
		assertTrue(missing.isEmpty());
		assertEquals(Collections.singleton("Crawling Hand"), ambiguous);
	}
}
