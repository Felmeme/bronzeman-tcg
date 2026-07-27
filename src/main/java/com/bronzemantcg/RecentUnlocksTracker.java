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
	private static final String SHARED_KEY = "recentSharedUnlocks";
	private static final int MAX_RECENT = 200;
	private static final Type LIST_TYPE = new TypeToken<List<Unlock>>()
	{
	}.getType();

	private final ConfigManager configManager;
	private final Gson gson;
	private List<Unlock> recent = new ArrayList<>();
	private List<Unlock> sharedRecent = new ArrayList<>();
	private Set<String> baseline;
	private Set<String> sharedBaseline;
	private Set<String> sharedSeen = new HashSet<>();

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
		sharedBaseline = null;
		sharedSeen = new HashSet<>();
		recent = new ArrayList<>();
		sharedRecent = new ArrayList<>();
		load(KEY, recent);
		load(SHARED_KEY, sharedRecent);
		for (Unlock unlock : sharedRecent)
		{
			sharedSeen.add(unlock.name);
		}
	}

	private void load(String key, List<Unlock> destination)
	{
		String raw = configManager.getRSProfileConfiguration(BronzemanTcgConfig.GROUP, key);
		if (raw == null || raw.isEmpty())
		{
			return;
		}
		try
		{
			List<Unlock> loaded = gson.fromJson(raw, LIST_TYPE);
			if (loaded != null)
			{
				destination.addAll(loaded);
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

	/**
	 * Observe the current effective shared set. The first payload is a silent baseline; later,
	 * genuinely unseen additions are persisted newest-first. Cards withdrawn and re-offered in
	 * the same session are not presented as new again.
	 */
	public synchronized boolean updateShared(Set<String> shared)
	{
		if (sharedBaseline == null)
		{
			sharedBaseline = new HashSet<>(shared);
			sharedSeen.addAll(shared);
			return false;
		}

		List<String> added = newSharedNames(shared, sharedBaseline, sharedSeen);
		sharedBaseline = new HashSet<>(shared);
		sharedSeen.addAll(shared);
		if (added.isEmpty())
		{
			return false;
		}

		long now = System.currentTimeMillis();
		for (int i = added.size() - 1; i >= 0; i--)
		{
			sharedRecent.add(0, new Unlock(added.get(i), now, true));
		}
		while (sharedRecent.size() > MAX_RECENT)
		{
			sharedRecent.remove(sharedRecent.size() - 1);
		}
		configManager.setRSProfileConfiguration(
			BronzemanTcgConfig.GROUP, SHARED_KEY, gson.toJson(sharedRecent));
		return true;
	}

	static List<String> newSharedNames(Set<String> shared, Set<String> baseline, Set<String> seen)
	{
		return shared.stream()
			.filter(name -> !baseline.contains(name) && !seen.contains(name))
			.sorted()
			.collect(Collectors.toList());
	}

	public synchronized List<Unlock> getSharedRecent()
	{
		return Collections.unmodifiableList(new ArrayList<>(sharedRecent));
	}

	/** One locally observed unlock. The name is normalized just like TcgCollectionReader. */
	public static class Unlock
	{
		final String name;
		final long time;
		final boolean shared;

		Unlock(String name, long time)
		{
			this(name, time, false);
		}

		Unlock(String name, long time, boolean shared)
		{
			this.name = name;
			this.time = time;
			this.shared = shared;
		}
	}
}
