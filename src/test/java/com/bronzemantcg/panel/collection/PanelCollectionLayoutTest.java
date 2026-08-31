package com.bronzemantcg.panel.collection;

import com.bronzemantcg.ownership.CardEntityKind;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PanelCollectionLayoutTest
{
	@Test
	public void rejectsInvalidCategoryReferencesAsAnEmptyLayout()
	{
		PanelCollectionLayout layout = new PanelCollectionLayout(
			new Gson(), "/panel/malformed_collection_layout.json");

		assertTrue(layout.getSections().isEmpty());
		assertTrue(layout.getCollectionPlacements().isEmpty());
		assertTrue(layout.getBetaCollectionCards().isEmpty());
	}

	@Test
	public void rejectsNullJsonRowsAsAnEmptyLayout()
	{
		PanelCollectionLayout layout = new PanelCollectionLayout(
			new Gson(), "/panel/null_collection_placement_layout.json");

		assertTrue(layout.getSections().isEmpty());
		assertTrue(layout.getCollectionPlacements().isEmpty());
		assertTrue(layout.getBetaCollectionCards().isEmpty());
	}

	@Test
	public void loadsSlimGeneratedProductionLayout()
	{
		PanelCollectionLayout layout = new PanelCollectionLayout(new Gson());

		assertEquals("sha256:062cfd93a66d2b8268c45cd58ae68c63cad4b2dbe9049f8d9cb4626f6b677e77",
			layout.getOrganiserFingerprint());
		assertEquals("9D39936DC8C5C65C2D3E0141A7880C2E6C071DAB68D364EFBB6DDC6FFD929EF1",
			layout.getOrganiserProjectSha256());
		assertEquals(24, layout.getSections().size());
		assertEquals(4992, layout.getCollectionPlacements().size());
		assertEquals(3601, layout.getCollectionPlacements().stream()
			.filter(card -> card.getKind() == CardEntityKind.ITEM).count());
		assertEquals(1391, layout.getCollectionPlacements().stream()
			.filter(card -> card.getKind() == CardEntityKind.NPC).count());
		assertFalse(layout.getCollectionPlacements().stream()
			.anyMatch(card -> card.getCategoryIds().isEmpty()));
		assertEquals(5561, layout.getBetaCollectionCards().size());
		assertEquals(6361, layout.getBetaCollectionCards().stream()
			.mapToInt(card -> card.getVariants().size()).sum());
		assertEquals(1075, layout.getBetaCollectionCards().stream()
			.filter(PanelCollectionLayout.BetaCollectionCard::isBetaOnly).count());

		placement(layout, CardEntityKind.NPC, "Akkha");
		placement(layout, CardEntityKind.NPC, "The Wardens");
		placement(layout, CardEntityKind.ITEM, "Helm of Neitiznot");
		PanelCollectionLayout.BetaCollectionCard betaAkkha = betaParent(layout, "Akkha");
		variant(betaAkkha, "Akkha's Shadow");
		assertTrue(betaParent(layout, "Akkha's Phantom").isBetaOnly());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void collectionPlacementsContainNoBundledV1IdentityData()
	{
		Map<String, Object> raw = new Gson().fromJson(new InputStreamReader(
			Objects.requireNonNull(getClass().getResourceAsStream(
				"/panel/collection_layout.json")), StandardCharsets.UTF_8), Map.class);
		List<Map<String, Object>> placements =
			(List<Map<String, Object>>) raw.get("collectionPlacements");

		assertNotNull(placements);
		assertFalse(placements.isEmpty());
		assertTrue(placements.stream().noneMatch(row -> row.containsKey("entityIds")));
		assertTrue(placements.stream().noneMatch(row -> row.containsKey("acceptedNames")));
		assertFalse(raw.containsKey("collectionCards"));
	}

	@Test
	public void betaVariantUniquenessPreservesHistoricalIdentitySafety()
	{
		PanelCollectionLayout layout = fixture();
		PanelCollectionOwnership ownership = new PanelCollectionOwnership(layout);
		PanelCollectionLayout.BetaVariant item = betaVariant(
			layout, CardEntityKind.ITEM, "Manta ray", "Manta ray");
		PanelCollectionLayout.BetaVariant npc = betaVariant(
			layout, CardEntityKind.NPC, "Manta ray", "Manta ray");

		assertFalse(layout.isBetaVariantNameUnique("Manta ray"));
		assertFalse(ownership.isBetaVariantInSnapshot(item, Set.of("manta ray")));
		assertFalse(ownership.isBetaVariantInSnapshot(npc, Set.of("manta ray")));
		assertFalse(layout.isBetaEntityIdUnique(CardEntityKind.NPC, 14706));
	}

	private static PanelCollectionLayout fixture()
	{
		return new PanelCollectionLayout(new Gson(), "/panel/test_collection_layout.json");
	}

	private static PanelCollectionLayout.CollectionPlacement placement(
		PanelCollectionLayout layout, CardEntityKind kind, String name)
	{
		PanelCollectionLayout.CollectionPlacement result = layout.getCollectionPlacements().stream()
			.filter(card -> card.getKind() == kind && card.getCardName().equals(name))
			.findFirst().orElse(null);
		assertNotNull(result);
		return result;
	}

	private static PanelCollectionLayout.BetaCollectionCard betaParent(
		PanelCollectionLayout layout, String name)
	{
		PanelCollectionLayout.BetaCollectionCard result = layout.getBetaCollectionCards().stream()
			.filter(card -> card.getParentName().equals(name)).findFirst().orElse(null);
		assertNotNull(result);
		return result;
	}

	private static PanelCollectionLayout.BetaVariant variant(
		PanelCollectionLayout.BetaCollectionCard card, String name)
	{
		PanelCollectionLayout.BetaVariant result = card.getVariants().stream()
			.filter(value -> value.getName().equals(name)).findFirst().orElse(null);
		assertNotNull(result);
		return result;
	}

	private static PanelCollectionLayout.BetaVariant betaVariant(
		PanelCollectionLayout layout, CardEntityKind kind, String parentName, String variantName)
	{
		PanelCollectionLayout.BetaCollectionCard parent = layout.getBetaCollectionCards().stream()
			.filter(card -> card.getKind() == kind && card.getParentName().equals(parentName))
			.findFirst().orElse(null);
		assertNotNull(parent);
		return variant(parent, variantName);
	}
}
