package com.bronzemantcg;

import com.bronzemantcg.catalog.QuestCatalog;
import com.bronzemantcg.ownership.CardEntityKind;
import com.bronzemantcg.panel.collection.PanelCollectionLayout;
import com.bronzemantcg.panel.collection.PanelCollectionOwnership;
import com.bronzemantcg.panel.collection.PanelCollectionViewModel;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PvmV1CatalogTest
{
	private final Gson gson = new Gson();
	private final PanelCollectionLayout collectionCatalog =
		new PanelCollectionLayout(gson);
	private final PanelCollectionViewModel collectionViewModel;

	public PvmV1CatalogTest()
	{
		com.bronzemantcg.ownership.ActiveCardIdentityCatalog active =
			new com.bronzemantcg.ownership.ActiveCardIdentityCatalog(
				new com.bronzemantcg.ownership.BundledCardIdentityCatalog(gson));
		LiveV1CatalogTestSupport.activate(active);
		collectionViewModel = new PanelCollectionViewModel(collectionCatalog,
			new PanelCollectionOwnership(collectionCatalog), active);
	}

	@Test
	public void pvmContentContainsOnlyUniqueCanonicalV1NpcParents()
	{
		assertCanonical(contentResource().contents);
	}

	@Test
	public void tombsOfAmascutUsesTheTwentyReviewedV1Parents()
	{
		Group tombs = contentResource().contents.stream()
			.filter(group -> group.name.equals("Tombs of Amascut"))
			.findFirst().orElseThrow(AssertionError::new);

		assertEquals(20, tombs.monsterCards.size());
		assertTrue(tombs.monsterCards.containsAll(
			Arrays.asList("Akkha", "Scarab", "The Wardens")));
		assertFalse(tombs.monsterCards.contains("Akkha's Shadow"));
		assertFalse(tombs.monsterCards.contains("Akkha's Phantom"));
		assertFalse(tombs.monsterCards.contains("Ba-Ba's Phantom"));
		assertFalse(tombs.monsterCards.contains("Kephri's Phantom"));
		assertFalse(tombs.monsterCards.contains("Elidinis' Warden"));
		assertFalse(tombs.monsterCards.contains("Tumeken's Warden"));
		assertFalse(tombs.monsterCards.contains("Scarab (Tombs of Amascut)"));
	}

	@Test
	public void reviewedBetaNamesSatisfyTheirCanonicalPvmParent()
	{
		assertAliasSatisfies("Akkha", "Akkha's Shadow");
		assertAliasSatisfies("The Wardens", "Elidinis' Warden");
		assertAliasSatisfies("The Wardens", "Tumeken's Warden");
		assertAliasSatisfies("Scarab", "Scarab (Tombs of Amascut)");
	}

	private void assertCanonical(List<Group> groups)
	{
		for (Group group : groups)
		{
			Set<String> unique = new LinkedHashSet<>(group.monsterCards);
			assertEquals(group.name + " contains a duplicate parent",
				group.monsterCards.size(), unique.size());
			for (String name : group.monsterCards)
			{
				PanelCollectionLayout.CollectionCard card = collectionViewModel
					.findCard(CardEntityKind.NPC, name)
					.orElseThrow(() -> new AssertionError(
						group.name + " does not use a v1 NPC parent: " + name));
				assertEquals(card.getCardName(), name);
			}
		}
	}

	private void assertAliasSatisfies(String parentName, String ownedName)
	{
		PanelCollectionLayout.CollectionCard card = collectionViewModel
			.findCard(CardEntityKind.NPC, parentName)
			.orElseThrow(AssertionError::new);
		QuestCatalog.Requirement requirement = new QuestCatalog.Requirement(
			card.getCardName(), new ArrayList<>(card.getAcceptedNamesLowerCase()));

		assertTrue(requirement.isSatisfied(
			Collections.singleton(ownedName.toLowerCase(java.util.Locale.ROOT))));
		assertTrue(requirement.isSatisfied(
			Collections.singleton(parentName.toLowerCase(java.util.Locale.ROOT))));
	}

	private ContentSnapshot contentResource()
	{
		try (InputStream stream = Objects.requireNonNull(
			PvmV1CatalogTest.class.getResourceAsStream("/panel/content_cards.json"),
			"Missing test resource: /panel/content_cards.json"))
		{
			return gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8),
				ContentSnapshot.class);
		}
		catch (IOException ex)
		{
			throw new AssertionError("Unable to close content resource", ex);
		}
	}

	@SuppressWarnings({"unused", "MismatchedQueryAndUpdateOfCollection"})
	private static final class ContentSnapshot
	{
		private List<Group> contents;
	}

	@SuppressWarnings({"unused", "MismatchedQueryAndUpdateOfCollection"})
	private static final class Group
	{
		private String name;
		private List<String> monsterCards;
	}
}
