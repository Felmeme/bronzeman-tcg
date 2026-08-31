package com.bronzemantcg.panel;

import com.bronzemantcg.catalog.ContentCatalog;
import com.bronzemantcg.catalog.ResourceNodeCatalog;
import com.google.gson.Gson;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PanelPresentationCatalogTest
{
	private PanelPresentationCatalog catalog;

	@Before
	public void setUp()
	{
		Gson gson = new Gson();
		catalog = new PanelPresentationCatalog(gson, new ContentCatalog(gson),
			new ResourceNodeCatalog(gson));
	}

	@Test
	public void preV1KeepsBetaTombsWhileV1UsesCanonicalParents()
	{
		List<String> beta = cards(catalog.select(false).getContents(), "Tombs of Amascut");
		List<String> v1 = cards(catalog.select(true).getContents(), "Tombs of Amascut");

		assertEquals(25, beta.size());
		assertTrue(beta.contains("Akkha's Phantom"));
		assertTrue(beta.contains("Tumeken's Warden"));
		assertFalse(beta.contains("The Wardens"));
		assertEquals(20, v1.size());
		assertTrue(v1.contains("The Wardens"));
		assertTrue(v1.contains("Scarab"));
		assertFalse(v1.contains("Akkha's Phantom"));
	}

	@Test
	public void onlyReviewedRumourRowsChange()
	{
		PanelPresentationCatalog.Data beta = catalog.select(false);
		PanelPresentationCatalog.Data v1 = catalog.select(true);

		assertEquals(6, beta.getRumourRules().size());
		assertEquals(6, v1.getRumourRules().size());
		List<String> betaAco = ruleCards(rule(beta.getRumourRules(), "Guild Hunter Aco"));
		List<String> v1Aco = ruleCards(rule(v1.getRumourRules(), "Guild Hunter Aco"));
		assertTrue(betaAco.contains("Dark kebbit"));
		assertTrue(betaAco.contains("Sunlight antelope"));
		assertFalse(betaAco.contains("Kebbity tuft"));
		assertTrue(v1Aco.contains("Kebbity tuft"));
		assertTrue(v1Aco.contains("Antelope hoof shard"));
		assertFalse(v1Aco.contains("Dark kebbit"));
	}

	@Test
	public void slayerKeepsCurrentRowsAndOverridesAchtrynOnly()
	{
		PanelPresentationCatalog.Data beta = catalog.select(false);
		PanelPresentationCatalog.Data v1 = catalog.select(true);

		assertEquals(13, beta.getSlayerRules().size());
		assertEquals(13, v1.getSlayerRules().size());
		assertTrue(ruleCards(rule(beta.getSlayerRules(), "Achtryn")).contains("Achtryn"));
		assertFalse(ruleCards(rule(v1.getSlayerRules(), "Achtryn")).contains("Achtryn"));
		assertEquals(ruleCards(rule(beta.getSlayerRules(), "Vannaka")),
			ruleCards(rule(v1.getSlayerRules(), "Vannaka")));
	}

	private static List<String> cards(List<PanelPresentationCatalog.Checklist> rows,
		String name)
	{
		return rows.stream().filter(row -> row.name.equals(name)).findFirst()
			.orElseThrow(AssertionError::new).cards;
	}

	private static List<String> ruleCards(PanelPresentationCatalog.Rule rule)
	{
		return rule.groups.stream().flatMap(group -> group.displayCards.stream())
			.collect(Collectors.toList());
	}

	private static PanelPresentationCatalog.Rule rule(
		java.util.Map<String, PanelPresentationCatalog.Rule> rules, String name)
	{
		return rules.entrySet().stream()
			.filter(entry -> entry.getKey().equalsIgnoreCase(name))
			.map(java.util.Map.Entry::getValue).findFirst()
			.orElseThrow(AssertionError::new);
	}
}
