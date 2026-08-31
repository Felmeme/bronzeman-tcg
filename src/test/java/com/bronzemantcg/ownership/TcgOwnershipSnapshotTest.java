package com.bronzemantcg.ownership;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TcgOwnershipSnapshotTest
{
	@Test
	public void normalizesConfirmedApiPayload()
	{
		TcgOwnershipSnapshot snapshot = TcgOwnershipSnapshot.fromApi(
			Arrays.asList(" Strength potion ", "STRENGTH POTION", null),
			Arrays.asList(113L, 119.0, -1, 2.5, "117"),
			Arrays.asList(2, 2L), " party-one ");

		assertEquals(Collections.singleton("strength potion"), snapshot.getOwnedCardNamesLowerCase());
		assertEquals(new LinkedHashSet<>(Arrays.asList(113, 119)), snapshot.getOwnedItemIds());
		assertEquals(Collections.singleton(2), snapshot.getOwnedNpcIds());
		assertEquals("party-one", snapshot.getGroupKey());
	}

	@Test
	public void parentOrVariantIdOwnsOneParentCard()
	{
		CardIdentity potion = new CardIdentity(CardEntityKind.ITEM, "Strength potion",
			new LinkedHashSet<>(Arrays.asList(113, 115, 117, 119)));
		assertTrue(potion.isOwnedBy(TcgOwnershipSnapshot.fromApi(
			Collections.emptyList(), Collections.singletonList(113), Collections.emptyList(), null)));
		assertTrue(potion.isOwnedBy(TcgOwnershipSnapshot.fromApi(
			Collections.emptyList(), Collections.singletonList(119), Collections.emptyList(), null)));
	}

	@Test
	public void itemAndNpcIdsStayInSeparateNamespaces()
	{
		TcgOwnershipSnapshot snapshot = TcgOwnershipSnapshot.fromApi(
			Collections.emptyList(), Collections.singletonList(2), Collections.emptyList(), null);
		assertTrue(snapshot.ownsEntityId(CardEntityKind.ITEM, 2));
		assertFalse(snapshot.ownsEntityId(CardEntityKind.NPC, 2));
	}

	@Test
	public void missingIdFieldsFallBackToNames()
	{
		CardIdentity potion = new CardIdentity(CardEntityKind.ITEM, "Strength potion",
			Collections.singleton(113));
		TcgOwnershipSnapshot oldApi = TcgOwnershipSnapshot.fromApi(
			Collections.singletonList("Strength potion"), null, null, null);
		assertFalse(oldApi.hasEntityIds(CardEntityKind.ITEM));
		assertTrue(potion.isOwnedBy(oldApi));
		assertNull(oldApi.getGroupKey());
	}

	@Test
	public void presentIdFieldIsAuthoritativeEvenWhenEmpty()
	{
		CardIdentity potion = new CardIdentity(CardEntityKind.ITEM, "Strength potion",
			Collections.singleton(113));
		TcgOwnershipSnapshot currentApi = TcgOwnershipSnapshot.fromApi(
			Collections.singletonList("Strength potion"), Collections.emptyList(),
			Collections.emptyList(), null);
		assertTrue(currentApi.hasEntityIds(CardEntityKind.ITEM));
		assertFalse(potion.isOwnedBy(currentApi));
	}

	@Test
	public void guardedIdRequiresSupportingAcceptedOwnedName()
	{
		CardIdentity juvenile = new CardIdentity(CardEntityKind.NPC,
			"Juvenile custodian stalker", Collections.emptySet(),
			new LinkedHashSet<>(Arrays.asList(14702, 14706)),
			Collections.singleton(14706));
		TcgOwnershipSnapshot betaStrangeCreature = TcgOwnershipSnapshot.fromApi(
			Collections.singletonList("Strange Creature"), Collections.emptyList(),
			Collections.singletonList(14706), null);
		assertFalse(juvenile.isOwnedBy(betaStrangeCreature));
		assertFalse(juvenile.isCollectedBy(betaStrangeCreature));

		TcgOwnershipSnapshot juvenileVariant = TcgOwnershipSnapshot.fromApi(
			Collections.singletonList("Juvenile custodian stalker"), Collections.emptyList(),
			Collections.singletonList(14706), null);
		assertTrue(juvenile.isOwnedBy(juvenileVariant));
		assertTrue(juvenile.isCollectedBy(juvenileVariant));

		TcgOwnershipSnapshot juvenileParent = TcgOwnershipSnapshot.fromApi(
			Collections.emptyList(), Collections.emptyList(),
			Collections.singletonList(14702), null);
		assertTrue(juvenile.isOwnedBy(juvenileParent));
	}
}
