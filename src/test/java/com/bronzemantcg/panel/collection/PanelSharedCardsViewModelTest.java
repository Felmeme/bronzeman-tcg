package com.bronzemantcg.panel.collection;

import com.bronzemantcg.ownership.ActiveCardIdentityCatalog;
import com.bronzemantcg.ownership.BundledCardIdentityCatalog;
import com.bronzemantcg.ownership.CardEntityKind;
import com.bronzemantcg.ownership.CardIdentity;
import com.bronzemantcg.ownership.ImmutableCardIdentityCatalog;
import com.bronzemantcg.ownership.TcgOwnershipSnapshot;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PanelSharedCardsViewModelTest
{
	@Test
	public void liveSharedCardsUseCollectionPlacementAndAppearOnlyOnce()
	{
		Fixture fixture = fixture();
		activate(fixture.active,
			identity(CardEntityKind.ITEM, "Water rune", Set.of("Water rune pack"), 900),
			identity(CardEntityKind.ITEM, "Brand new parent", Collections.emptySet(), 901));
		Set<String> shared = Set.of("water rune pack", "brand new parent", "mystery card");
		PanelCollectionViewModel.State collection = fixture.collection.prepare(
			emptyOwnership(), shared);

		PanelSharedCardsViewModel.State state = fixture.shared.prepare(shared, collection);

		assertEquals(List.of("Water rune"), cards(state, "Items", "Magic"));
		assertFalse(cards(state, "Items", "NPCs").contains("Water rune"));
		assertEquals(List.of("Brand new parent"),
			cards(state, "Unsorted v1", "Items"));
		assertEquals(List.of("Mystery card"), directCards(state, "Other Cards"));
		assertEquals(3, allCards(state).size());
	}

	@Test
	public void fallbackSharedCardsUseExactBetaVariantsAndKeepAmbiguityUnassigned()
	{
		Fixture fixture = fixture();
		Set<String> shared = Set.of("water rune pack", "beta-only item", "manta ray");
		PanelCollectionViewModel.State unavailable = fixture.collection.prepare(
			emptyOwnership(), shared);

		PanelSharedCardsViewModel.State state = fixture.shared.prepare(shared, unavailable);

		assertEquals(List.of("Water rune pack"), cards(state, "Items", "Magic"));
		assertEquals(List.of("Beta-only item"),
			cards(state, "Beta Only Items", "Beta-only NPCs"));
		assertEquals(List.of("Manta ray"), directCards(state, "Other Cards"));
		assertEquals(3, allCards(state).size());
	}

	@Test
	public void liveAmbiguousAliasIsNotGuessedIntoEitherNpcParent()
	{
		Fixture fixture = fixture();
		activate(fixture.active,
			identity(CardEntityKind.NPC, "Phantom Muspah", Set.of("Strange Creature"), 902),
			identity(CardEntityKind.NPC, "Juvenile custodian stalker",
				Set.of("Strange Creature"), 903));
		Set<String> shared = Set.of("strange creature");
		PanelCollectionViewModel.State collection = fixture.collection.prepare(
			emptyOwnership(), shared);

		PanelSharedCardsViewModel.State state = fixture.shared.prepare(shared, collection);

		assertEquals(List.of("Strange Creature"), directCards(state, "Other Cards"));
		assertEquals(1, allCards(state).size());
	}

	private static Fixture fixture()
	{
		PanelCollectionLayout catalog = new PanelCollectionLayout(
			new Gson(), "/panel/test_collection_layout.json");
		PanelCollectionOwnership ownership = new PanelCollectionOwnership(catalog);
		ActiveCardIdentityCatalog active = new ActiveCardIdentityCatalog(
			new BundledCardIdentityCatalog(new Gson()));
		return new Fixture(active,
			new PanelCollectionViewModel(catalog, ownership, active),
			new PanelSharedCardsViewModel(catalog, ownership, active));
	}

	private static CardIdentity identity(CardEntityKind kind, String name,
		Set<String> legacyNames, int id)
	{
		return new CardIdentity(kind, name, legacyNames, Set.of(id));
	}

	private static void activate(ActiveCardIdentityCatalog active,
		CardIdentity... identities)
	{
		List<ImmutableCardIdentityCatalog.Entry> entries = Arrays.stream(identities)
			.map(identity -> new ImmutableCardIdentityCatalog.Entry(
				identity, Set.of(identity.getCardName())))
			.collect(Collectors.toList());
		active.activate(new ImmutableCardIdentityCatalog(entries), entries, "test-v1");
	}

	private static TcgOwnershipSnapshot emptyOwnership()
	{
		return TcgOwnershipSnapshot.fromApi(Collections.emptyList(),
			Collections.emptyList(), Collections.emptyList(), null);
	}

	private static List<String> cards(PanelSharedCardsViewModel.State state,
		String section, String category)
	{
		return state.getCategories().stream()
			.filter(value -> value.getName().equals(section))
			.map(PanelSharedCardsViewModel.Category::getSubcategories)
			.map(values -> values.getOrDefault(category, Collections.emptyList()))
			.findFirst().orElse(Collections.emptyList());
	}

	private static List<String> directCards(PanelSharedCardsViewModel.State state,
		String category)
	{
		return state.getCategories().stream()
			.filter(value -> value.getName().equals(category))
			.map(PanelSharedCardsViewModel.Category::getItems)
			.findFirst().orElse(Collections.emptyList());
	}

	private static List<String> allCards(PanelSharedCardsViewModel.State state)
	{
		return state.getCategories().stream().flatMap(category ->
		{
			List<String> cards = new java.util.ArrayList<>(category.getItems());
			category.getSubcategories().values().forEach(cards::addAll);
			return cards.stream();
		}).collect(Collectors.toList());
	}

	private static final class Fixture
	{
		private final ActiveCardIdentityCatalog active;
		private final PanelCollectionViewModel collection;
		private final PanelSharedCardsViewModel shared;

		private Fixture(ActiveCardIdentityCatalog active,
			PanelCollectionViewModel collection, PanelSharedCardsViewModel shared)
		{
			this.active = active;
			this.collection = collection;
			this.shared = shared;
		}
	}
}
