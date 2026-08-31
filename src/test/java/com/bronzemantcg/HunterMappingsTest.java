package com.bronzemantcg;

import com.bronzemantcg.restriction.HunterAreaSpecies;
import com.bronzemantcg.restriction.HunterTrapType;
import java.util.List;
import net.runelite.api.gameval.ObjectID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
		assertTrapCard(ObjectID.HUNTING_SAPLING_UP_SWAMP, "Swamp lizard");
		assertTrapCard(ObjectID.HUNTING_SAPLING_UP_ORANGE, "Orange salamander");
		assertTrapCard(ObjectID.HUNTING_SAPLING_UP_RED, "Red salamander");
		assertTrapCard(ObjectID.HUNTING_SAPLING_UP_BLACK, "Black salamander");
		assertTrapCard(ObjectID.HUNTING_SAPLING_UP_MOUNTAIN, "Tecu salamander");

		assertNull(HunterTrapType.fromObjectId(-1));
	}

	private static void assertTrapCard(int objectId, String expectedCardName)
	{
		HunterTrapType trapType = HunterTrapType.fromObjectId(objectId);
		assertNotNull(trapType);
		assertEquals(expectedCardName, trapType.getCardName());
	}
}
