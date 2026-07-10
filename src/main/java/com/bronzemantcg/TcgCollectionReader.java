package com.bronzemantcg;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Reads osrs-tcg's persisted collection directly out of ConfigManager. No compile-time
 * dependency on the osrs-tcg plugin - just its published config group/key names, which are
 * effectively its public interop surface (this is the standard "Option 2" pattern for
 * unrelated RuneLite Hub plugins to interoperate).
 *
 * Cached and refreshed lazily rather than decoded on every menu click, since gzip decode
 * on every single click would be wasteful. A short cache window is fine here: worst case
 * you can attack something you *just* unlocked for a few seconds longer than necessary,
 * which is a harmless direction to be stale in.
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
	private long lastRefreshMs = 0L;

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
		long now = System.currentTimeMillis();
		if (now - lastRefreshMs < CACHE_MILLIS)
		{
			return cachedOwnedLowerCaseNames;
		}
		refresh();
		return cachedOwnedLowerCaseNames;
	}

	/** Call after profile switches / logins so a stale cache from a different account never lingers. */
	public synchronized void invalidate()
	{
		lastRefreshMs = 0L;
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
				return;
			}

			TcgStateDto dto = gson.fromJson(json, TcgStateDto.class);
			if (dto == null || dto.cardInstances == null)
			{
				cachedOwnedLowerCaseNames = Collections.emptySet();
				return;
			}

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
		}
	}
}
