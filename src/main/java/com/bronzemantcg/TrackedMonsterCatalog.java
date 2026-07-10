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
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Bundled snapshot of every osrs-tcg card name tagged with the "Monster" category, keyed by the
 * in-game NPC name the card corresponds to. This is a static resource (not read live from
 * osrs-tcg) because:
 *   - it changes rarely (only when the wiki-sourced Card.json catalog is updated upstream)
 *   - unlike ownership, "does a card exist for this NPC" doesn't need to be live
 *   - it lets this plugin work even before osrs-tcg has ever loaded in a given session
 *
 * The snapshot is a map rather than a flat name list because 67 monster cards carry wiki-style
 * disambiguation suffixes the in-game NPC name never contains ("Monkey (monster)"), and several
 * distinct cards collapse to one NPC name (11 "Soldier (...)" cards). RuneLite only exposes the
 * plain NPC name at attack time, so owning ANY variant card unlocks the NPC.
 *
 * Re-generate with scripts/generate_tracked_monsters.py whenever osrs-tcg ships a Card.json
 * update you want reflected.
 */
@Slf4j
@Singleton
public class TrackedMonsterCatalog
{
	private Map<String, Set<String>> npcToCardsLowerCase = Collections.emptyMap();
	private boolean loaded;

	@Inject
	public TrackedMonsterCatalog(Gson gson)
	{
		load(gson);
	}

	public synchronized boolean isTracked(String npcName)
	{
		return !getCardVariantsLowerCase(npcName).isEmpty();
	}

	/**
	 * @return lower-cased names of every card variant that unlocks this NPC (usually one;
	 *         up to 11 for instanced NPCs like Soldier), or empty set if untracked.
	 */
	public synchronized Set<String> getCardVariantsLowerCase(String npcName)
	{
		if (npcName == null || npcName.trim().isEmpty())
		{
			return Collections.emptySet();
		}
		return npcToCardsLowerCase.getOrDefault(
			npcName.trim().toLowerCase(Locale.ROOT), Collections.emptySet());
	}

	public synchronized int size()
	{
		return npcToCardsLowerCase.size();
	}

	private synchronized void load(Gson gson)
	{
		if (loaded)
		{
			return;
		}
		try (InputStream stream = getClass().getResourceAsStream("/tracked_monster_names.json"))
		{
			if (stream == null)
			{
				log.warn("tracked_monster_names.json missing from classpath; all NPCs will be unrestricted.");
				return;
			}
			Snapshot snapshot = gson.fromJson(
				new InputStreamReader(stream, StandardCharsets.UTF_8), Snapshot.class);
			if (snapshot == null || snapshot.npcToCards == null)
			{
				return;
			}
			Map<String, Set<String>> map = new HashMap<>();
			for (Map.Entry<String, List<String>> entry : snapshot.npcToCards.entrySet())
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
			npcToCardsLowerCase = Collections.unmodifiableMap(map);
			loaded = true;
			log.info("Loaded {} tracked NPCs from osrs-tcg snapshot", npcToCardsLowerCase.size());
		}
		catch (IOException ex)
		{
			log.warn("Failed to load tracked_monster_names.json", ex);
		}
	}

	private static class Snapshot
	{
		Map<String, List<String>> npcToCards;
	}
}
