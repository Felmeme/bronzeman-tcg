package com.bronzemantcg.panel;

import com.bronzemantcg.LiveV1CatalogTestSupport;
import com.bronzemantcg.catalog.QuestCatalog;
import com.bronzemantcg.ownership.ActiveCardIdentityCatalog;
import com.bronzemantcg.ownership.BundledCardIdentityCatalog;
import com.bronzemantcg.ownership.CardEntityKind;
import com.bronzemantcg.ownership.CardIdentity;
import com.bronzemantcg.ownership.ImmutableCardIdentityCatalog;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QuestV1PresentationTest
{
	private final Gson gson = new Gson();
	private final QuestCatalog source = new QuestCatalog(gson);
	private final ActiveCardIdentityCatalog active = new ActiveCardIdentityCatalog(
		new BundledCardIdentityCatalog(gson));
	private final QuestV1Presentation presentation = new QuestV1Presentation(active);

	@Before
	public void activateMaintainedV1Capture()
	{
		LiveV1CatalogTestSupport.activate(active);
	}

	@Test
	public void canonicalizesDisplayAndAcceptsBothParentAndLegacyNames()
	{
		QuestV1Presentation.Data projected = presentation.project(source);
		QuestCatalog.Requirement zombie = requirement(
			quest(projected.getQuests(), "Defender of Varrock"),
			"Armoured zombie");

		assertEquals(List.of("Armoured zombie"), zombie.displayCards);
		assertTrue(zombie.isSatisfied(Set.of("armoured zombie")));
		assertTrue(zombie.isSatisfied(
			Set.of("armoured zombie (defender of varrock)")));
		assertEquals("enemy", zombie.type);
	}

	@Test
	public void canonicalSourceNeedsNoRetiredAlternativeProjection()
	{
		QuestV1Presentation.Data projected = presentation.project(source);
		QuestCatalog.QuestEntry digSite = quest(projected.getQuests(), "The Dig Site");
		QuestCatalog.QuestEntry wartface = quest(projected.getQuests(),
			"RFD - Wartface & Bentnoze");

		assertFalse(hasRequirement(digSite, "Cup of tea"));
		assertEquals(List.of("Blue dye", "Green dye", "Purple dye"),
			requirement(wartface, "Dye").displayCards);
		assertFalse(hasRequirement(quest(source.getQuests(), "The Dig Site"),
			"Cup of tea"));
		assertEquals(List.of("Armoured zombie"),
			requirement(quest(source.getQuests(), "Defender of Varrock"),
				"Armoured zombie").displayCards);
	}

	@Test
	public void preservesNestedTypesAndQuestRouteSelection()
	{
		QuestV1Presentation.Data projected = presentation.project(source);
		QuestCatalog.Requirement nestedAxe = requirement(
			quest(projected.getQuests(), "RFD - Sir Amik Varze"), "Any axe");
		assertEquals("item", nestedAxe.type);

		QuestCatalog.Requirement shieldRoute = requirement(
			quest(projected.getQuests(), "Shield of Arrav"),
			"Your Shield of Arrav gang route");
		assertTrue(shieldRoute.isSatisfied(Set.of("coins", "jonny the beard"),
			QuestCatalog.RouteSelection.PHOENIX));
		assertFalse(shieldRoute.isSatisfied(Set.of("coins", "jonny the beard"),
			QuestCatalog.RouteSelection.BLACK_ARM));
	}

	@Test
	public void ambiguousUntypedIdentityFailsOpenAtNewCatalogueRevision()
	{
		List<ImmutableCardIdentityCatalog.Entry> entries = Arrays.asList(
			entry(CardEntityKind.ITEM, "Guard item", "Guard", 1),
			entry(CardEntityKind.NPC, "Guard npc", "Guard", 2));
		ImmutableCardIdentityCatalog ambiguous = new ImmutableCardIdentityCatalog(entries);
		long revision = active.activate(ambiguous, entries, "ambiguous-test");

		QuestV1Presentation.Data projected = presentation.project(source);
		assertEquals(revision, projected.getRevision());
		assertFalse(hasRequirement(quest(projected.getQuests(), "Children of the Sun"),
			"Guard"));
	}

	private static ImmutableCardIdentityCatalog.Entry entry(CardEntityKind kind,
		String parent, String legacyName, int id)
	{
		CardIdentity identity = new CardIdentity(kind, parent,
			Set.of(legacyName), Set.of(id));
		return new ImmutableCardIdentityCatalog.Entry(identity, Set.of(parent));
	}

	private static QuestCatalog.QuestEntry quest(
		List<QuestCatalog.QuestEntry> entries, String name)
	{
		return entries.stream().filter(entry -> name.equals(entry.name))
			.findFirst().orElseThrow(() -> new AssertionError("Missing quest " + name));
	}

	private static boolean hasRequirement(QuestCatalog.QuestEntry entry, String label)
	{
		for (QuestCatalog.Requirement requirement : entry.requirements)
		{
			if (find(requirement, label) != null)
			{
				return true;
			}
		}
		return false;
	}

	private static QuestCatalog.Requirement requirement(
		QuestCatalog.QuestEntry entry, String label)
	{
		for (QuestCatalog.Requirement requirement : entry.requirements)
		{
			QuestCatalog.Requirement match = find(requirement, label);
			if (match != null)
			{
				return match;
			}
		}
		throw new AssertionError("Missing requirement " + label + " in " + entry.name);
	}

	private static QuestCatalog.Requirement find(
		QuestCatalog.Requirement requirement, String label)
	{
		if (label.equals(requirement.label))
		{
			return requirement;
		}
		for (QuestCatalog.Requirement child : requirement.children)
		{
			QuestCatalog.Requirement match = find(child, label);
			if (match != null)
			{
				return match;
			}
		}
		return null;
	}
}
