package com.bronzemantcg.panel.collection;

import com.bronzemantcg.LiveV1CatalogTestSupport;
import com.bronzemantcg.ownership.ActiveCardIdentityCatalog;
import com.bronzemantcg.ownership.BundledCardIdentityCatalog;
import com.bronzemantcg.ownership.CardEntityKind;
import com.bronzemantcg.ownership.CardIdentity;
import com.bronzemantcg.ownership.ImmutableCardIdentityCatalog;
import com.bronzemantcg.ownership.TcgOwnershipSnapshot;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PanelCollectionViewModelTest
{
	@Test
	public void productionViewContainsEveryV1ParentAndTheApprovedFallback()
	{
		PanelCollectionLayout catalog = new PanelCollectionLayout(new Gson());
		ActiveCardIdentityCatalog active = activeCatalog();
		LiveV1CatalogTestSupport.activate(active);
		PanelCollectionViewModel view = new PanelCollectionViewModel(
			catalog, new PanelCollectionOwnership(catalog), active);
		Set<PanelCollectionLayout.CollectionCard> displayed = new HashSet<>();
		view.getSections().forEach(section -> displayed.addAll(section.getCards()));
		PanelCollectionViewModel.Section unsorted = view.getSections().stream()
			.filter(section -> section.getId().equals(
				PanelCollectionViewModel.UNSORTED_SECTION_ID))
			.findFirst().orElseThrow(AssertionError::new);

		assertEquals(3804, view.getItemTotal());
		assertEquals(1391, view.getNpcTotal());
		assertEquals(5195, view.getSearchCards().size());
		assertEquals(5195, displayed.size());
		assertEquals(204, unsorted.getCards().size());
		assertTrue(unsorted.getCards().stream()
			.anyMatch(card -> card.getCardName().equals("Adamant heraldic helm")));
		assertTrue(unsorted.getCards().stream()
			.anyMatch(card -> card.getCardName().equals("House keys")));
	}

	@Test
	public void buildsNormalSectionsAndMovesUnassignedAndPromotedCardsToUnsorted()
	{
		PanelCollectionViewModel view = view();

		assertFalse(view.getSections().stream()
			.anyMatch(section -> section.getId().equals(
				PanelCollectionViewModel.BETA_ONLY_SECTION_ID)));
		PanelCollectionViewModel.Section unsorted = view.getSections().stream()
			.filter(section -> section.getId().equals(
				PanelCollectionViewModel.UNSORTED_SECTION_ID))
			.findFirst().orElseThrow(AssertionError::new);
		assertEquals(Arrays.asList("Items", "NPCs"), unsorted.getCategories().stream()
			.map(PanelCollectionViewModel.Category::getName).collect(Collectors.toList()));
		assertTrue(unsorted.getCards().stream()
			.anyMatch(card -> card.getCardName().equals("New v1 item")));
		assertTrue(unsorted.getCards().stream()
			.anyMatch(card -> card.getCardName().equals("Promoted NPC")));
	}

	@Test
	public void searchQualifiesOnlySameNameItemAndNpcParents()
	{
		PanelCollectionViewModel view = view();

		assertTrue(view.getSearchCards().stream()
			.anyMatch(card -> card.getDisplayName().equals("Manta ray (item)")));
		assertTrue(view.getSearchCards().stream()
			.anyMatch(card -> card.getDisplayName().equals("Manta ray (npc)")));
		assertTrue(view.getSearchCards().stream()
			.anyMatch(card -> card.getDisplayName().equals("Water rune")));
	}

	@Test
	public void progressCountsPersonalParentsButNotSharedParents()
	{
		PanelCollectionViewModel view = view();
		TcgOwnershipSnapshot personal = TcgOwnershipSnapshot.fromApi(
			Collections.singletonList("Water rune pack"),
			Collections.singletonList(12730), Collections.emptyList(), null);
		PanelCollectionViewModel.State state = view.prepare(
			personal, Set.of("Promoted NPC"));

		assertEquals(1, state.getOwnedItems());
		assertEquals(0, state.getOwnedNpcs());
		assertEquals(PanelCollectionViewModel.Status.OWNED,
			state.getStatus(card(view, CardEntityKind.ITEM, "Water rune")));
		assertEquals(PanelCollectionViewModel.Status.SHARED,
			state.getStatus(card(view, CardEntityKind.NPC, "Promoted NPC")));
	}

	@Test
	public void findsCanonicalCardsWithinTheirEntityNamespace()
	{
		PanelCollectionViewModel view = view();

		assertEquals("Water rune", view.findCard(CardEntityKind.ITEM, " water RUNE ")
			.orElseThrow(AssertionError::new).getCardName());
		assertFalse(view.findCard(CardEntityKind.NPC, "Water rune").isPresent());
		assertFalse(view.findCard(CardEntityKind.ITEM, null).isPresent());
	}

	@Test
	public void frozenBetaVariantCollectsItsMappedV1Parent()
	{
		PanelCollectionViewModel view = view();
		TcgOwnershipSnapshot v1WithoutMappedIds = TcgOwnershipSnapshot.fromApi(
			Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null);
		PanelCollectionViewModel.State state = view.prepare(
			v1WithoutMappedIds, Collections.emptySet(), Set.of("water rune pack"));

		assertEquals(1, state.getOwnedItems());
		assertEquals(PanelCollectionViewModel.Status.OWNED,
			state.getStatus(card(view, CardEntityKind.ITEM, "Water rune")));
	}

	@Test
	public void hiddenBetaProgressDoesNotCountSnapshotParentsOrMaskV1OnlyProgress()
	{
		PanelCollectionViewModel view = view();
		TcgOwnershipSnapshot personal = TcgOwnershipSnapshot.fromApi(
			Arrays.asList("Water rune", "New v1 item"),
			Arrays.asList(555, 99), Collections.emptyList(), null);

		PanelCollectionViewModel.State state = view.prepare(personal,
			Collections.emptySet(), Set.of("water rune pack"), true);

		assertEquals(1, state.getOwnedItems());
		assertEquals(PanelCollectionViewModel.Status.LOCKED,
			state.getStatus(card(view, CardEntityKind.ITEM, "Water rune")));
		assertEquals(PanelCollectionViewModel.Status.OWNED,
			state.getStatus(card(view, CardEntityKind.ITEM, "New v1 item")));
	}

	@Test
	public void hiddenBetaProgressStillAllowsSharedPresentation()
	{
		PanelCollectionViewModel view = view();
		TcgOwnershipSnapshot personal = TcgOwnershipSnapshot.fromApi(
			Collections.singletonList("Water rune"),
			Collections.singletonList(555), Collections.emptyList(), null);

		PanelCollectionViewModel.State state = view.prepare(personal,
			Set.of("water rune"), Set.of("water rune pack"), true);

		assertEquals(0, state.getOwnedItems());
		assertEquals(PanelCollectionViewModel.Status.SHARED,
			state.getStatus(card(view, CardEntityKind.ITEM, "Water rune")));
	}

	@Test
	public void remoteParentsInheritUniqueLocalCategoriesAndLeaveNewParentsUnsorted()
	{
		PanelCollectionLayout catalog = fixtureCatalog();
		ActiveCardIdentityCatalog active = activeCatalog();
		PanelCollectionViewModel view = new PanelCollectionViewModel(
			catalog, new PanelCollectionOwnership(catalog), active);
		activate(active,
			identity(CardEntityKind.ITEM, "Manta ray",
				Collections.emptySet(), Set.of(899)),
			identity(CardEntityKind.ITEM, "Renamed water rune",
				Set.of("Water rune pack"), Set.of(900)),
			identity(CardEntityKind.ITEM, "Brand new parent",
				Collections.emptySet(), Set.of(901)),
			identity(CardEntityKind.NPC, "Manta ray",
				Collections.emptySet(), Set.of(902)));

		PanelCollectionViewModel.State state = view.prepare(
			TcgOwnershipSnapshot.fromApi(Collections.emptyList(),
				Collections.emptyList(), Collections.emptyList(), null),
			Collections.emptySet());

		assertTrue(state.isRemoteCatalog());
		assertEquals(4, state.getSearchCards().size());
		assertEquals(Arrays.asList("Renamed water rune", "Manta ray"),
			category(state, "magic").getCards().stream()
				.map(PanelCollectionLayout.CollectionCard::getCardName)
				.collect(Collectors.toList()));
		PanelCollectionViewModel.Section unsorted = state.getSections().stream()
			.filter(section -> section.getId().equals(
				PanelCollectionViewModel.UNSORTED_SECTION_ID))
			.findFirst().orElseThrow(AssertionError::new);
		assertTrue(unsorted.getCards().stream()
			.anyMatch(card -> card.getCardName().equals("Brand new parent")));
		assertFalse(state.getSearchCards().stream()
			.anyMatch(card -> card.getDisplayName().equals("Phantom Muspah")));
	}

	@Test
	public void ambiguousLegacyLayoutMatchIsNotGuessed()
	{
		PanelCollectionLayout catalog = fixtureCatalog();
		ActiveCardIdentityCatalog active = activeCatalog();
		PanelCollectionViewModel view = new PanelCollectionViewModel(
			catalog, new PanelCollectionOwnership(catalog), active);
		activate(active, identity(CardEntityKind.NPC, "Renamed encounter",
			Set.of("Phantom Muspah", "Juvenile custodian stalker"), Set.of(903)));

		PanelCollectionViewModel.State state = view.prepare(
			TcgOwnershipSnapshot.fromApi(Collections.emptyList(),
				Collections.emptyList(), Collections.emptyList(), null),
			Collections.emptySet());
		PanelCollectionLayout.CollectionCard renamed = card(
			state, CardEntityKind.NPC, "Renamed encounter");

		assertTrue(renamed.getCategoryIds().isEmpty());
		assertTrue(state.getSections().stream()
			.filter(section -> section.getId().equals(
				PanelCollectionViewModel.UNSORTED_SECTION_ID))
			.flatMap(section -> section.getCards().stream())
			.anyMatch(card -> card == renamed));
	}

	@Test
	public void activeRevisionAtomicallyChangesStructureOwnershipAndTotals()
	{
		PanelCollectionLayout catalog = fixtureCatalog();
		ActiveCardIdentityCatalog active = activeCatalog();
		PanelCollectionViewModel view = new PanelCollectionViewModel(
			catalog, new PanelCollectionOwnership(catalog), active);
		PanelCollectionViewModel.State bundled = view.prepare(
			emptyOwnership(), Collections.emptySet());
		activate(active, identity(CardEntityKind.ITEM, "Renamed water rune",
			Set.of("Water rune pack"), Set.of(900)));
		TcgOwnershipSnapshot remoteOwnership = TcgOwnershipSnapshot.fromApi(
			Collections.emptyList(), Collections.singletonList(900),
			Collections.emptyList(), null);
		PanelCollectionViewModel.State remote = view.prepare(
			remoteOwnership, Collections.emptySet());

		assertFalse(bundled.isRemoteCatalog());
		assertFalse(bundled.isCatalogAvailable());
		assertEquals(0, bundled.getSearchCards().size());
		assertTrue(remote.isRemoteCatalog());
		assertTrue(remote.isCatalogAvailable());
		assertFalse(bundled.equals(remote));
		assertEquals(1, remote.getItemTotal());
		assertEquals(0, remote.getNpcTotal());
		assertEquals(1, remote.getOwnedItems());
		assertEquals(PanelCollectionViewModel.Status.OWNED,
			remote.getStatus(card(remote, CardEntityKind.ITEM, "Renamed water rune")));

		active.useBundled();
		PanelCollectionViewModel.State restored = view.prepare(
			emptyOwnership(), Collections.emptySet());
		assertFalse(restored.isRemoteCatalog());
		assertFalse(restored.isCatalogAvailable());
		assertEquals(0, restored.getSearchCards().size());
		assertTrue(restored.getCatalogRevision() > remote.getCatalogRevision());
	}

	@Test
	public void remoteNameCollisionsRequireAUniqueParentName()
	{
		PanelCollectionLayout catalog = fixtureCatalog();
		ActiveCardIdentityCatalog active = activeCatalog();
		PanelCollectionViewModel view = new PanelCollectionViewModel(
			catalog, new PanelCollectionOwnership(catalog), active);
		activate(active,
			identity(CardEntityKind.NPC, "Phantom Muspah",
				Set.of("Strange Creature"), Set.of(904)),
			identity(CardEntityKind.NPC, "Juvenile custodian stalker",
				Set.of("Strange Creature"), Set.of(905)));

		PanelCollectionViewModel.State collision = view.prepare(
			emptyOwnership(), Set.of("strange creature"), Set.of("strange creature"));
		assertEquals(PanelCollectionViewModel.Status.LOCKED,
			collision.getStatus(card(collision, CardEntityKind.NPC, "Phantom Muspah")));
		assertEquals(PanelCollectionViewModel.Status.LOCKED,
			collision.getStatus(card(collision, CardEntityKind.NPC,
				"Juvenile custodian stalker")));

		PanelCollectionViewModel.State canonical = view.prepare(
			emptyOwnership(), Set.of("Phantom Muspah"));
		assertEquals(PanelCollectionViewModel.Status.SHARED,
			canonical.getStatus(card(canonical, CardEntityKind.NPC, "Phantom Muspah")));
		assertEquals(PanelCollectionViewModel.Status.LOCKED,
			canonical.getStatus(card(canonical, CardEntityKind.NPC,
				"Juvenile custodian stalker")));
	}

	@Test
	public void sameNameRemoteParentsRemainDistinctByEntityId()
	{
		PanelCollectionLayout catalog = fixtureCatalog();
		ActiveCardIdentityCatalog active = activeCatalog();
		PanelCollectionViewModel view = new PanelCollectionViewModel(
			catalog, new PanelCollectionOwnership(catalog), active);
		activate(active,
			identity(CardEntityKind.NPC, "Strange Creature",
				Set.of("Phantom Muspah"), Set.of(907)),
			identity(CardEntityKind.NPC, "Strange Creature",
				Set.of("Juvenile custodian stalker"), Set.of(908)));
		TcgOwnershipSnapshot ownsSecond = TcgOwnershipSnapshot.fromApi(
			Collections.emptyList(), Collections.emptyList(),
			Collections.singletonList(908), null);

		PanelCollectionViewModel.State state = view.prepare(
			ownsSecond, Collections.emptySet());
		PanelCollectionLayout.CollectionCard first = cardById(state, 907);
		PanelCollectionLayout.CollectionCard second = cardById(state, 908);

		assertEquals(PanelCollectionViewModel.Status.LOCKED, state.getStatus(first));
		assertEquals(PanelCollectionViewModel.Status.OWNED, state.getStatus(second));
	}

	@Test
	public void frozenBetaVariantCollectsRenamedRemoteParentWithoutAnOwnedId()
	{
		PanelCollectionLayout catalog = fixtureCatalog();
		ActiveCardIdentityCatalog active = activeCatalog();
		PanelCollectionViewModel view = new PanelCollectionViewModel(
			catalog, new PanelCollectionOwnership(catalog), active);
		activate(active, identity(CardEntityKind.ITEM, "Renamed water rune",
			Set.of("Water rune pack"), Set.of(906)));

		PanelCollectionViewModel.State state = view.prepare(
			emptyOwnership(), Collections.emptySet(), Set.of("water rune pack"));

		assertEquals(1, state.getOwnedItems());
		assertEquals(PanelCollectionViewModel.Status.OWNED,
			state.getStatus(card(state, CardEntityKind.ITEM, "Renamed water rune")));
	}

	private static PanelCollectionViewModel view()
	{
		PanelCollectionLayout catalog = fixtureCatalog();
		ActiveCardIdentityCatalog active = activeCatalog();
		activate(active,
			identity(CardEntityKind.ITEM, "Water rune",
				Set.of("Water rune pack"), Set.of(555, 12730)),
			identity(CardEntityKind.ITEM, "Manta ray", Collections.emptySet(), Set.of(391)),
			identity(CardEntityKind.NPC, "Manta ray", Collections.emptySet(), Set.of(15220)),
			identity(CardEntityKind.NPC, "Phantom Muspah",
				Set.of("Strange Creature"), Set.of(12073, 14706)),
			identity(CardEntityKind.NPC, "Juvenile custodian stalker",
				Set.of("Strange creature (Shadows of Custodia)"), Set.of(14702, 14706)),
			identity(CardEntityKind.ITEM, "New v1 item", Collections.emptySet(), Set.of(99)),
			identity(CardEntityKind.NPC, "Promoted NPC", Collections.emptySet(), Set.of(100)));
		return new PanelCollectionViewModel(
			catalog, new PanelCollectionOwnership(catalog), active);
	}

	private static PanelCollectionLayout fixtureCatalog()
	{
		return new PanelCollectionLayout(new Gson(), "/panel/test_collection_layout.json");
	}

	private static ActiveCardIdentityCatalog activeCatalog()
	{
		return new ActiveCardIdentityCatalog(new BundledCardIdentityCatalog(new Gson()));
	}

	private static TcgOwnershipSnapshot emptyOwnership()
	{
		return TcgOwnershipSnapshot.fromApi(Collections.emptyList(),
			Collections.emptyList(), Collections.emptyList(), null);
	}

	private static void activate(ActiveCardIdentityCatalog active, CardIdentity... identities)
	{
		List<ImmutableCardIdentityCatalog.Entry> entries = Arrays.stream(identities)
			.map(identity -> new ImmutableCardIdentityCatalog.Entry(
				identity, Set.of(identity.getCardName())))
			.collect(Collectors.toList());
		active.activate(new ImmutableCardIdentityCatalog(entries), entries, "test-v1");
	}

	private static CardIdentity identity(CardEntityKind kind, String name,
		Set<String> legacyNames, Set<Integer> ids)
	{
		return new CardIdentity(kind, name, legacyNames, ids);
	}

	private static PanelCollectionViewModel.Category category(
		PanelCollectionViewModel.State state, String id)
	{
		return state.getSections().stream()
			.flatMap(section -> section.getCategories().stream())
			.filter(category -> category.getId().equals(id))
			.findFirst().orElseThrow(AssertionError::new);
	}

	private static PanelCollectionLayout.CollectionCard card(
		PanelCollectionViewModel view, CardEntityKind kind, String name)
	{
		return view.getSearchCards().stream()
			.map(PanelCollectionViewModel.SearchCard::getCard)
			.filter(card -> card.getKind() == kind && card.getCardName().equals(name))
			.findFirst().orElseThrow(AssertionError::new);
	}

	private static PanelCollectionLayout.CollectionCard card(
		PanelCollectionViewModel.State state, CardEntityKind kind, String name)
	{
		return state.getSearchCards().stream()
			.map(PanelCollectionViewModel.SearchCard::getCard)
			.filter(card -> card.getKind() == kind && card.getCardName().equals(name))
			.findFirst().orElseThrow(AssertionError::new);
	}

	private static PanelCollectionLayout.CollectionCard cardById(
		PanelCollectionViewModel.State state, int entityId)
	{
		return state.getSearchCards().stream()
			.map(PanelCollectionViewModel.SearchCard::getCard)
			.filter(card -> card.getEntityIds().contains(entityId))
			.findFirst().orElseThrow(AssertionError::new);
	}
}
