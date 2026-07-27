package com.bronzemantcg;

import java.util.List;
import net.runelite.api.gameval.ObjectID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HunterMappingsTest
{
	@Test
	public void mapsReviewedHunterAreasToTheirExpectedCards()
	{
		assertEquals(
			List.of("Crimson swift", "Bones", "Raw bird meat", "Red feather"),
			HunterAreaSpecies.birdCards(12073));
		assertEquals(
			List.of("Golden warbler", "Bones", "Raw bird meat", "Yellow feather"),
			HunterAreaSpecies.birdCards(13616));
		assertEquals(
			List.of("Bones", "Raw bird meat", "Orange feather"),
			HunterAreaSpecies.birdCards(9272));
		assertEquals(
			List.of("Bones", "Raw bird meat", "Blue feather"),
			HunterAreaSpecies.birdCards(10811));
		assertEquals(
			List.of("Tropical wagtail", "Bones", "Raw bird meat", "Stripy feather"),
			HunterAreaSpecies.birdCards(10029));

		assertEquals(
			List.of("Chinchompa (Hunter)", "Chinchompa"),
			HunterAreaSpecies.chinchompaCards(9272));
		assertEquals(
			List.of("Carnivorous chinchompa", "Red chinchompa"),
			HunterAreaSpecies.chinchompaCards(7477));
		assertEquals(
			List.of("Black chinchompa (Hunter)", "Black chinchompa"),
			HunterAreaSpecies.chinchompaCards(12603));

		assertTrue(HunterAreaSpecies.birdCards(-1).isEmpty());
		assertTrue(HunterAreaSpecies.chinchompaCards(-1).isEmpty());
	}

	@Test
	public void mapsOfficialRuneLiteTrapObjectIdsToTheirExpectedCards()
	{
		assertEquals("Swamp lizard",
			HunterTrapType.fromObjectId(ObjectID.HUNTING_SAPLING_UP_SWAMP).getCardName());
		assertEquals("Orange salamander",
			HunterTrapType.fromObjectId(ObjectID.HUNTING_SAPLING_UP_ORANGE).getCardName());
		assertEquals("Red salamander",
			HunterTrapType.fromObjectId(ObjectID.HUNTING_SAPLING_UP_RED).getCardName());
		assertEquals("Black salamander",
			HunterTrapType.fromObjectId(ObjectID.HUNTING_SAPLING_UP_BLACK).getCardName());
		assertEquals("Tecu salamander",
			HunterTrapType.fromObjectId(ObjectID.HUNTING_SAPLING_UP_MOUNTAIN).getCardName());

		assertNull(HunterTrapType.fromObjectId(-1));
	}
}
