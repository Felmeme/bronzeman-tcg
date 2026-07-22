package com.bronzemantcg;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Records first-time card unlocks for the panel. The first readable collection per
 * profile is a silent baseline, so existing players do not receive a history flood.
 * Later additions are kept newest-first, independent of card pulls (duplicates do not
 * change the owned-name set), and persisted RSProfile-scoped.
 */
@Slf4j
@Singleton
public class RecentUnlocksTracker
{
	private static final String KEY = "recentUnlocks";
	private static final int MAX_RECENT = 200;
	private static final Type LIST_TYPE = new TypeToken<List<Unlock>>()
	{
	}.getType();

	private final ConfigManager configManager;
	private final Gson gson;
	private List<Unlock> recent = new ArrayList<>();
	private Set<String> baseline;

	@Inject
	RecentUnlocksTracker(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	/** Reload persisted history and await the current profile's first readable collection. */
	public synchronized void reload()
	{
		baseline = null;
		recent = new ArrayList<>();
		String raw = configManager.getRSProfileConfiguration(BronzemanTcgConfig.GROUP, KEY);
		if (raw == null || raw.isEmpty())
		{
			return;
		}
		try
		{
			List<Unlock> loaded = gson.fromJson(raw, LIST_TYPE);
			if (loaded != null)
			{
				recent.addAll(loaded);
			}
		}
		catch (Exception ex)
		{
			log.debug("Could not parse stored recent unlocks", ex);
		}
	}

	/**
	 * The API is authoritative over the config fallback. When it first arrives, take its
	 * complete collection as the baseline too, rather than mistaking a stale fallback
	 * snapshot for a batch of new unlocks.
	 */
	public synchronized void resetBaseline()
	{
		baseline = null;
	}

	/**
	 * @return true only when this observation records one or more genuinely new cards.
	 */
	public synchronized boolean update(Set<String> owned, boolean stateAvailable)
	{
		if (!stateAvailable)
		{
			return false;
		}
		if (baseline == null)
		{
			baseline = new HashSet<>(owned);
			return false;
		}

		List<String> added = owned.stream()
			.filter(name -> !baseline.contains(name))
			.sorted()
			.collect(Collectors.toList());
		baseline = new HashSet<>(owned);
		if (added.isEmpty())
		{
			return false;
		}

		long now = System.currentTimeMillis();
		for (int i = added.size() - 1; i >= 0; i--)
		{
			recent.add(0, new Unlock(added.get(i), now));
		}
		while (recent.size() > MAX_RECENT)
		{
			recent.remove(recent.size() - 1);
		}
		configManager.setRSProfileConfiguration(BronzemanTcgConfig.GROUP, KEY, gson.toJson(recent));
		return true;
	}

	public synchronized List<Unlock> getRecent()
	{
		return Collections.unmodifiableList(new ArrayList<>(recent));
	}

	/** One locally observed unlock. The name is normalized just like TcgCollectionReader. */
	public static class Unlock
	{
		final String name;
		final long time;

		Unlock(String name, long time)
		{
			this.name = name;
			this.time = time;
		}
	}
}
