package com.bronzemantcg;

import java.util.List;
import java.util.Map;

/**
 * Exact card requirements for birds/chinchompas in their reviewed hunting regions.
 *
 * Trap placement does not target an NPC, so the region is the only reliable
 * pre-action discriminator. Unknown regions deliberately return an empty list:
 * requiring the trap card is safe, but guessing output cards would create a false block.
 */
final class HunterAreaSpecies
{
	private static final List<String> CRIMSON_SWIFT = List.of(
		"Crimson swift", "Bones", "Raw bird meat", "Red feather");
	private static final List<String> GOLDEN_WARBLER = List.of(
		"Golden warbler", "Bones", "Raw bird meat", "Yellow feather");
	private static final List<String> COPPER_LONGTAIL = List.of(
		"Bones", "Raw bird meat", "Orange feather");
	private static final List<String> CERULEAN_TWITCH = List.of(
		"Bones", "Raw bird meat", "Blue feather");
	private static final List<String> TROPICAL_WAGTAIL = List.of(
		"Tropical wagtail", "Bones", "Raw bird meat", "Stripy feather");

	private static final Map<Integer, List<String>> BIRD_CARDS = Map.ofEntries(
		Map.entry(12073, CRIMSON_SWIFT), // Dognose Island
		Map.entry(10285, CRIMSON_SWIFT), // eastern Feldip Hunter area
		Map.entry(4664, CRIMSON_SWIFT),  // south-west Kebos Lowlands
		Map.entry(8492, CRIMSON_SWIFT),  // Isle of Souls
		Map.entry(13611, CRIMSON_SWIFT), // Ruins of Ullek
		Map.entry(5424, CRIMSON_SWIFT),  // Tlati Rainforest
		Map.entry(13616, GOLDEN_WARBLER), // Uzer Hunter area
		Map.entry(9272, COPPER_LONGTAIL), // Piscatoris Hunter area
		Map.entry(6197, COPPER_LONGTAIL), // Kourend Woodland
		Map.entry(8750, COPPER_LONGTAIL), // Isle of Souls
		Map.entry(5422, COPPER_LONGTAIL), // Aldarin
		Map.entry(5428, COPPER_LONGTAIL), // Auburnvale
		Map.entry(9770, COPPER_LONGTAIL), // Anglers' Retreat
		Map.entry(10811, CERULEAN_TWITCH), // upper Rellekka Hunter area
		Map.entry(10810, CERULEAN_TWITCH), // lower Rellekka Hunter area
		Map.entry(5682, CERULEAN_TWITCH), // Mons Gratia
		Map.entry(10029, TROPICAL_WAGTAIL), // Feldip Hunter area
		Map.entry(12582, TROPICAL_WAGTAIL),
		Map.entry(4912, TROPICAL_WAGTAIL),
		Map.entry(6187, TROPICAL_WAGTAIL),
		Map.entry(7728, TROPICAL_WAGTAIL),
		Map.entry(7205, TROPICAL_WAGTAIL)
	);

	private static final List<String> GREY_CHINCHOMPA = List.of(
		"Chinchompa (Hunter)", "Chinchompa");
	private static final List<String> RED_CHINCHOMPA = List.of(
		"Carnivorous chinchompa", "Red chinchompa");
	private static final List<String> BLACK_CHINCHOMPA = List.of(
		"Black chinchompa (Hunter)", "Black chinchompa");

	private static final Map<Integer, List<String>> CHINCHOMPA_CARDS = Map.ofEntries(
		Map.entry(9272, GREY_CHINCHOMPA), // Piscatoris Hunter area
		Map.entry(5942, GREY_CHINCHOMPA), // Isle of Souls
		Map.entry(8494, GREY_CHINCHOMPA), // Kourend Woodland
		Map.entry(7477, RED_CHINCHOMPA), // Gwenith Hunter area
		Map.entry(10029, RED_CHINCHOMPA), // Feldip Hunter area
		Map.entry(12837, RED_CHINCHOMPA),
		Map.entry(9013, RED_CHINCHOMPA),
		Map.entry(10129, RED_CHINCHOMPA), // private hunting ground
		Map.entry(5169, RED_CHINCHOMPA),
		Map.entry(12603, BLACK_CHINCHOMPA) // Wilderness Hunter area
	);

	private HunterAreaSpecies()
	{
	}

	static List<String> birdCards(int regionId)
	{
		return BIRD_CARDS.getOrDefault(regionId, List.of());
	}

	static List<String> chinchompaCards(int regionId)
	{
		return CHINCHOMPA_CARDS.getOrDefault(regionId, List.of());
	}
}
