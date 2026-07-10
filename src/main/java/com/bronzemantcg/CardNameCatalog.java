package com.bronzemantcg;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Bundled snapshot of osrs-tcg card names keyed by the in-game entity (NPC or item) name each
 * card corresponds to. These are static resources (not read live from osrs-tcg) because:
 *   - they change rarely (only when the wiki-sourced Card.json catalog is updated upstream)
 *   - unlike ownership, "does a card exist for this entity" doesn't need to be live
 *   - it lets this plugin work even before osrs-tcg has ever loaded in a given session
 *
 * The snapshots are maps rather than flat name lists because 67 monster cards carry wiki-style
 * disambiguation suffixes the in-game NPC name never contains ("Monkey (monster)"), and several
 * distinct cards collapse to one NPC name (11 "Soldier (...)" cards). RuneLite only exposes the
 * plain entity name at interaction time, so owning ANY variant card unlocks the entity.
 *
 * Re-generate with scripts/generate_tracked_monsters.py whenever osrs-tcg ships a Card.json
 * update you want reflected.
 */
@Slf4j
abstract class CardNameCatalog
{
	private Map<String, Set<String>> entityToCardsLowerCase = Collections.emptyMap();

	protected CardNameCatalog(Gson gson, String resourcePath, String logLabel)
	{
		load(gson, resourcePath, logLabel);
	}

	public boolean isTracked(String entityName)
	{
		return !getCardVariantsLowerCase(entityName).isEmpty();
	}

	/**
	 * @return lower-cased names of every card variant that unlocks this entity (usually one;
	 *         up to 11 for instanced NPCs like Soldier), or empty set if untracked.
	 */
	public Set<String> getCardVariantsLowerCase(String entityName)
	{
		if (entityName == null || entityName.trim().isEmpty())
		{
			return Collections.emptySet();
		}
		return entityToCardsLowerCase.getOrDefault(
			entityName.trim().toLowerCase(Locale.ROOT), Collections.emptySet());
	}

	public int size()
	{
		return entityToCardsLowerCase.size();
	}

	private void load(Gson gson, String resourcePath, String logLabel)
	{
		try (InputStream stream = getClass().getResourceAsStream(resourcePath))
		{
			if (stream == null)
			{
				log.warn("{} missing from classpath; all {} will be unrestricted.", resourcePath, logLabel);
				return;
			}
			Snapshot snapshot = gson.fromJson(
				new InputStreamReader(stream, StandardCharsets.UTF_8), Snapshot.class);
			if (snapshot == null || snapshot.entityToCards == null)
			{
				return;
			}
			Map<String, Set<String>> map = new HashMap<>();
			for (Map.Entry<String, List<String>> entry : snapshot.entityToCards.entrySet())
			{
				if (entry.getKey() == null || entry.getValue() == null)
				{
					continue;
				}
				Set<String> cards = new HashSet<>();
				for (String cardName : entry.getValue())
				{
					if (cardName != null && !cardName.trim().isEmpty())
					{
						cards.add(cardName.trim().toLowerCase(Locale.ROOT));
					}
				}
				if (!cards.isEmpty())
				{
					map.put(entry.getKey().trim().toLowerCase(Locale.ROOT),
						Collections.unmodifiableSet(cards));
				}
			}
			entityToCardsLowerCase = Collections.unmodifiableMap(map);
			log.info("Loaded {} tracked {} from osrs-tcg snapshot", map.size(), logLabel);
		}
		catch (IOException ex)
		{
			log.warn("Failed to load {}", resourcePath, ex);
		}
	}

	private static class Snapshot
	{
		Map<String, List<String>> entityToCards;
	}
}
