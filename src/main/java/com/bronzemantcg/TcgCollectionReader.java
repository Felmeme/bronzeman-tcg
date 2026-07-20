package com.bronzemantcg;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Single source of truth for the player's osrs-tcg collection, with two ways in:
 *
 * 1. Preferred: osrs-tcg's PluginMessage API. The plugin forwards "owned-names" /
 *    "owned-names-changed" payloads here via {@link #onApiOwnedNames}, giving an
 *    already-decoded, push-updated list with no polling.
 * 2. Fallback: decoding osrs-tcg's persisted ConfigManager state, for hub versions
 *    that predate the API. No compile-time dependency either way - just published
 *    config group/key names (the standard pattern for unrelated Hub plugins).
 *
 * Once any API payload arrives, it wins until {@link #invalidate()} (profile switch),
 * after which we fall back to config until the next payload.
 *
 * The fallback is cached and refreshed lazily rather than decoded on every menu click,
 * since gzip decode on every single click would be wasteful. A short cache window is
 * fine here: worst case you can attack something you *just* unlocked for a few seconds
 * longer than necessary, which is a harmless direction to be stale in.
 */
@Slf4j
@Singleton
public class TcgCollectionReader
{
	private static final String TCG_CONFIG_GROUP = "osrstcg";
	private static final String TCG_STATE_KEY = "state";
	private static final long CACHE_MILLIS = 5_000L;

	private final ConfigManager configManager;
	private final Gson gson;

	private Set<String> cachedOwnedLowerCaseNames = Collections.emptySet();
	private boolean stateAvailable;
	private long lastRefreshMs = 0L;
	// Null until the first API payload lands; non-null means the API path is live.
	private Set<String> apiOwnedLowerCaseNames;

	@Inject
	public TcgCollectionReader(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	/**
	 * @return lower-cased set of every card name the player currently owns (foil or not,
	 *         collapsed - owning either counts as "collected" for restriction purposes).
	 */
	public synchronized Set<String> getOwnedCardNamesLowerCase()
	{
		if (apiOwnedLowerCaseNames != null)
		{
			return apiOwnedLowerCaseNames;
		}
		ensureFresh();
		return cachedOwnedLowerCaseNames;
	}

	/** Distinct card names owned (normal/foil folded), for the stats overlay. */
	public synchronized int getOwnedCardCount()
	{
		return getOwnedCardNamesLowerCase().size();
	}

	/** False when osrs-tcg has no readable state (not installed, no data yet, or decode failure). */
	public synchronized boolean isStateAvailable()
	{
		if (apiOwnedLowerCaseNames != null)
		{
			return true;
		}
		ensureFresh();
		return stateAvailable;
	}

	/** True once an API payload has arrived; the plugin stops re-querying at that point. */
	public synchronized boolean hasApiData()
	{
		return apiOwnedLowerCaseNames != null;
	}

	/**
	 * Feed in an "ownedNames" payload from osrs-tcg's PluginMessage API. Elements are
	 * validated individually rather than trusting the cast - the data map is untyped, and
	 * a malformed payload should degrade to the config fallback, not throw on a click.
	 */
	public synchronized void onApiOwnedNames(List<?> names)
	{
		if (names == null)
		{
			return;
		}
		Set<String> normalized = new HashSet<>();
		for (Object name : names)
		{
			if (name instanceof String && !((String) name).trim().isEmpty())
			{
				normalized.add(((String) name).trim().toLowerCase(Locale.ROOT));
			}
		}
		apiOwnedLowerCaseNames = Collections.unmodifiableSet(normalized);
	}

	private void ensureFresh()
	{
		if (System.currentTimeMillis() - lastRefreshMs >= CACHE_MILLIS)
		{
			refresh();
		}
	}

	/**
	 * Call after profile switches / logins so a stale cache from a different account never
	 * lingers. Drops API data too - it described the previous profile's collection - so we
	 * serve the config fallback until the re-query for the new profile is answered.
	 */
	public synchronized void invalidate()
	{
		lastRefreshMs = 0L;
		apiOwnedLowerCaseNames = null;
	}

	private void refresh()
	{
		lastRefreshMs = System.currentTimeMillis();
		try
		{
			String raw = configManager.getRSProfileConfiguration(TCG_CONFIG_GROUP, TCG_STATE_KEY);
			String json = TcgStateDecoder.decode(raw);
			if (json.isEmpty())
			{
				cachedOwnedLowerCaseNames = Collections.emptySet();
				stateAvailable = false;
				return;
			}

			TcgStateDto dto = gson.fromJson(json, TcgStateDto.class);
			if (dto == null || dto.cardInstances == null)
			{
				cachedOwnedLowerCaseNames = Collections.emptySet();
				stateAvailable = false;
				return;
			}
			stateAvailable = true;

			Set<String> names = new HashSet<>();
			for (TcgStateDto.OwnedCardInstanceDto instance : dto.cardInstances)
			{
				if (instance != null && instance.cardName != null && !instance.cardName.trim().isEmpty())
				{
					names.add(instance.cardName.trim().toLowerCase(Locale.ROOT));
				}
			}
			cachedOwnedLowerCaseNames = Collections.unmodifiableSet(names);
		}
		catch (Exception ex)
		{
			// osrs-tcg not installed, no data yet, or its storage format changed upstream.
			// Fail safe to "own nothing known" rather than crash the client.
			log.debug("Could not read osrs-tcg collection state", ex);
			cachedOwnedLowerCaseNames = Collections.emptySet();
			stateAvailable = false;
		}
	}
}
