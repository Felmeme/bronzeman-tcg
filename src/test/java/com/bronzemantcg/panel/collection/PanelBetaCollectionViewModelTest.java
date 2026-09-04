package com.bronzemantcg.panel.collection;

import com.bronzemantcg.ownership.CardEntityKind;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PanelBetaCollectionViewModelTest
{
	@Test
	public void unmatchedImportedNamesAreVisibleButDoNotInflateParentTotals()
	{
		PanelBetaCollectionViewModel view = fixtureView();
		PanelBetaCollectionViewModel.State state = view.prepare(
			Set.of("water rune", "water rune pack", "fish chunks"),
			BetaCollectionSnapshotService.Status.IMPORTED);
		assertEquals(1, state.getOwnedParents());
		assertEquals(Set.of("fish chunks"), state.getUnmatchedNames());
		assertFalse(state.equals(view.prepare(Set.of("water rune", "water rune pack"),
			BetaCollectionSnapshotService.Status.IMPORTED)));
	}
	@Test
	public void productionViewContainsEveryVisibleBetaParentAndVariant()
	{
		PanelBetaCollectionViewModel view = productionView();
		Set<PanelCollectionLayout.BetaCollectionCard> displayed = new HashSet<>();
		view.getSections().forEach(section -> displayed.addAll(section.getCards()));

		assertEquals(5562, view.getParentTotal());
		assertEquals(6362, view.getVariantTotal());
		assertEquals(5562, displayed.size());
		assertTrue(view.getSections().stream()
			.anyMatch(section -> section.getId().equals(
				PanelCollectionViewModel.BETA_ONLY_SECTION_ID)));
	}

	@Test
	public void exactOwnedVariantCollectsParentButNotItsSibling()
	{
		PanelBetaCollectionViewModel view = fixtureView();
		PanelCollectionLayout.BetaCollectionCard water = parent(
			view, CardEntityKind.ITEM, "Water rune");
		PanelCollectionLayout.BetaVariant rune = variant(water, "Water rune");
		PanelCollectionLayout.BetaVariant pack = variant(water, "Water rune pack");
		PanelBetaCollectionViewModel.State state = view.prepare(
			Collections.singleton("water rune pack"),
			BetaCollectionSnapshotService.Status.FROZEN_CAPTURED);

		assertEquals(1, state.getOwnedParents());
		assertEquals(BetaCollectionSnapshotService.Status.FROZEN_CAPTURED,
			state.getSnapshotStatus());
		assertEquals(PanelCollectionViewModel.Status.OWNED, state.getParentStatus(water));
		assertEquals(PanelCollectionViewModel.Status.LOCKED, state.getVariantStatus(rune));
		assertEquals(PanelCollectionViewModel.Status.OWNED, state.getVariantStatus(pack));
	}

	@Test
	public void betaCollectionUsesOnlyPersonalSnapshotNames()
	{
		PanelBetaCollectionViewModel view = fixtureView();
		PanelCollectionLayout.BetaCollectionCard betaOnly = parent(
			view, CardEntityKind.ITEM, "Beta-only item");
		PanelBetaCollectionViewModel.State state = view.prepare(
			Collections.emptySet(), BetaCollectionSnapshotService.Status.PROVISIONAL);

		assertEquals(0, state.getOwnedParents());
		assertEquals(PanelCollectionViewModel.Status.LOCKED,
			state.getParentStatus(betaOnly));
		assertEquals(PanelCollectionViewModel.Status.LOCKED,
			state.getVariantStatus(betaOnly.getVariants().get(0)));
	}

	@Test
	public void searchFindsVariantsAndQualifiesOnlyParentNameCollisions()
	{
		PanelBetaCollectionViewModel view = fixtureView();
		PanelCollectionLayout.BetaCollectionCard water = parent(
			view, CardEntityKind.ITEM, "Water rune");
		PanelCollectionLayout.BetaCollectionCard mantaItem = parent(
			view, CardEntityKind.ITEM, "Manta ray");
		PanelCollectionLayout.BetaCollectionCard mantaNpc = parent(
			view, CardEntityKind.NPC, "Manta ray");

		assertEquals("Water rune", view.getDisplayName(water));
		assertEquals("Manta ray (item)", view.getDisplayName(mantaItem));
		assertEquals("Manta ray (npc)", view.getDisplayName(mantaNpc));
		assertEquals(1, view.matchingVariants(water, "rune pack").size());
		assertTrue(view.parentNameMatches(water, "water"));
	}

	@Test
	public void expandsOnlyWhenVariantsAddUsefulBetaDetail()
	{
		PanelBetaCollectionViewModel view = fixtureView();
		PanelCollectionLayout.BetaCollectionCard water = parent(
			view, CardEntityKind.ITEM, "Water rune");
		PanelCollectionLayout.BetaCollectionCard betaOnly = parent(
			view, CardEntityKind.ITEM, "Beta-only item");
		PanelCollectionLayout.BetaCollectionCard renamed = parent(
			view, CardEntityKind.NPC, "Juvenile custodian stalker");

		assertTrue(view.hasVariantBreakdown(water));
		assertTrue(view.hasVariantBreakdown(renamed));
		assertEquals(1, view.matchingVariants(water, "rune pack").size());
		assertTrue(view.matchingVariants(betaOnly, "beta-only item").isEmpty());
		assertFalse(view.hasVariantBreakdown(betaOnly));
	}

	private static PanelBetaCollectionViewModel productionView()
	{
		PanelCollectionLayout catalog = new PanelCollectionLayout(new Gson());
		return new PanelBetaCollectionViewModel(catalog,
			new PanelCollectionOwnership(catalog));
	}

	private static PanelBetaCollectionViewModel fixtureView()
	{
		PanelCollectionLayout catalog = new PanelCollectionLayout(
			new Gson(), "/panel/test_collection_layout.json");
		return new PanelBetaCollectionViewModel(catalog,
			new PanelCollectionOwnership(catalog));
	}

	private static PanelCollectionLayout.BetaCollectionCard parent(
		PanelBetaCollectionViewModel view, CardEntityKind kind, String name)
	{
		return view.getParents().stream()
			.filter(card -> card.getKind() == kind && card.getParentName().equals(name))
			.findFirst().orElseThrow(AssertionError::new);
	}

	private static PanelCollectionLayout.BetaVariant variant(
		PanelCollectionLayout.BetaCollectionCard parent, String name)
	{
		return parent.getVariants().stream()
			.filter(card -> card.getName().equals(name))
			.findFirst().orElseThrow(AssertionError::new);
	}
}
