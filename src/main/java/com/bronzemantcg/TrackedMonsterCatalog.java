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
 * Bundled snapshot of every osrs-tcg card name tagged with the "Monster" category, i.e. the
 * full list of NPCs the TCG system tracks at all. This is a static resource (not read live from
 * osrs-tcg) because:
 *   - it changes rarely (only when the wiki-sourced Card.json catalog is updated upstream)
 *   - unlike ownership, "does a card exist for this NPC" doesn't need to be live
 *   - it lets this plugin work even before osrs-tcg has ever loaded in a given session
 *
 * Re-generate resources/tracked_monster_names.json whenever osrs-tcg ships a Card.json update
 * you want reflected (filter Card.json for category contains "Monster", dedupe names).
 */
@Slf4j
@Singleton
public class TrackedMonsterCatalog
{
	private Set<String> trackedLowerCaseNames = Collections.emptySet();
	private boolean loaded;

	@Inject
	public TrackedMonsterCatalog(Gson gson)
	{
		load(gson);
	}

	public synchronized boolean isTracked(String npcName)
	{
		if (npcName == null || npcName.trim().isEmpty())
		{
			return false;
		}
		return trackedLowerCaseNames.contains(npcName.trim().toLowerCase(Locale.ROOT));
	}

	public synchronized int size()
	{
		return trackedLowerCaseNames.size();
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
			if (snapshot == null || snapshot.names == null)
			{
				return;
			}
			Set<String> names = new HashSet<>();
			for (String name : snapshot.names)
			{
				if (name != null && !name.trim().isEmpty())
				{
					names.add(name.trim().toLowerCase(Locale.ROOT));
				}
			}
			trackedLowerCaseNames = Collections.unmodifiableSet(names);
			loaded = true;
			log.info("Loaded {} tracked monster names from osrs-tcg snapshot", trackedLowerCaseNames.size());
		}
		catch (IOException ex)
		{
			log.warn("Failed to load tracked_monster_names.json", ex);
		}
	}

	private static class Snapshot
	{
		List<String> names;
	}
}
