package com.bronzemantcg;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Food/potion classification for the Food Settings dropdown, loaded from
 * consumables.json ({"food": [...], "potions": [...]}, exact tracked item names,
 * wiki-derived and owner-curated - wines/beers/teas count as food by ruling).
 *
 * A missing or empty resource leaves both sets empty, which makes Food Settings
 * inert (nothing classifies, so nothing is granted) rather than wrong.
 */
@Slf4j
@Singleton
class ConsumablesCatalog
{
	private Set<String> foodLower = Collections.emptySet();
	private Set<String> potionsLower = Collections.emptySet();

	@Inject
	ConsumablesCatalog(Gson gson)
	{
		try (InputStream stream = getClass().getResourceAsStream("/consumables.json"))
		{
			if (stream == null)
			{
				log.info("consumables.json not present; Food Settings has no effect until it ships.");
				return;
			}
			Snapshot snapshot = gson.fromJson(
				new InputStreamReader(stream, StandardCharsets.UTF_8), Snapshot.class);
			if (snapshot == null)
			{
				return;
			}
			foodLower = toLowerSet(snapshot.food);
			potionsLower = toLowerSet(snapshot.potions);
			log.info("Loaded consumables classification: {} food, {} potions",
				foodLower.size(), potionsLower.size());
		}
		catch (IOException ex)
		{
			log.warn("Failed to load consumables.json", ex);
		}
	}

	Set<String> getFoodNamesLower()
	{
		return foodLower;
	}

	Set<String> getPotionNamesLower()
	{
		return potionsLower;
	}

	private static Set<String> toLowerSet(List<String> names)
	{
		if (names == null)
		{
			return Collections.emptySet();
		}
		Set<String> set = new HashSet<>();
		for (String name : names)
		{
			if (name != null && !name.trim().isEmpty())
			{
				set.add(name.trim().toLowerCase(Locale.ROOT));
			}
		}
		return Collections.unmodifiableSet(set);
	}

	private static class Snapshot
	{
		List<String> food;
		List<String> potions;
	}
}
