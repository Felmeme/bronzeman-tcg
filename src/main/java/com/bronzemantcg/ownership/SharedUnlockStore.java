package com.bronzemantcg.ownership;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Singleton;

/**
 * Card names a sibling plugin has told us to treat as unlocked, on top of the player's own
 * collection. Group modes live outside this plugin - a party plugin can decide that a card owned by
 * any member counts for everyone - but the restriction engine is here, so without a way in, those
 * unlocks simply do not apply and the group mode silently does nothing.
 *
 * <p>Kept per source plugin rather than in one pile so several can contribute independently and a
 * source that goes quiet (or shuts down) can be dropped without disturbing the others. Each message
 * carries that source's complete set, so a payload replaces what it sent before rather than adding
 * to it - senders do not have to track deltas, and a stale set cannot accumulate.</p>
 *
 * <p>The union is rebuilt only when something changes and handed out as the same instance until it
 * does, because the effective-owned cache in the plugin compares by identity and the menu-hide path
 * asks for it many times per frame.</p>
 *
 * <p>Reads are lock-free for that reason: the union is replaced wholesale rather than mutated, so a
 * reader either sees the old set or the new one, never a half-built one. Writes arrive from
 * PluginMessage handlers and are rare, so they take the lock; the render and menu paths must not.</p>
 */
@Singleton
public class SharedUnlockStore
{
	private final Map<String, Set<String>> bySource = new HashMap<>();
	private volatile Set<String> union = Collections.emptySet();

	/**
	 * Replaces everything {@code source} previously contributed.
	 *
	 * @param source     identifier of the sending plugin; blank payloads are ignored
	 * @param cardNames  that plugin's full set of extra unlocked card names, validated per element
	 *                   because the payload map is untyped and a malformed one must not throw
	 *                   mid-click
	 * @return true if the effective set changed
	 */
	public synchronized boolean put(String source, List<?> cardNames)
	{
		String key = key(source);
		if (key.isEmpty())
		{
			return false;
		}
		Set<String> names = new HashSet<>();
		if (cardNames != null)
		{
			for (Object name : cardNames)
			{
				if (name instanceof String)
				{
					String trimmed = ((String) name).trim();
					if (!trimmed.isEmpty())
					{
						names.add(trimmed.toLowerCase(Locale.ROOT));
					}
				}
			}
		}
		if (names.isEmpty())
		{
			return remove(key);
		}
		if (names.equals(bySource.get(key)))
		{
			return false;
		}
		bySource.put(key, names);
		rebuild();
		return true;
	}

	/** Drops a source's contribution when it withdraws by sending an empty set. */
	private boolean remove(String source)
	{
		String key = key(source);
		if (key.isEmpty() || bySource.remove(key) == null)
		{
			return false;
		}
		rebuild();
		return true;
	}

	/** Drops every contribution; used on profile switches so one account's group never carries over. */
	public synchronized void clear()
	{
		if (bySource.isEmpty())
		{
			return;
		}
		bySource.clear();
		rebuild();
	}

	/**
	 * @return the union of every source's card names, lower-cased. The same instance is returned
	 * until the contents change, so callers may cache against it by identity. Called from the
	 * render and menu paths, so it takes no lock.
	 */
	public Set<String> getSharedCardNamesLowerCase()
	{
		return union;
	}

	private void rebuild()
	{
		if (bySource.isEmpty())
		{
			union = Collections.emptySet();
			return;
		}
		Set<String> combined = new HashSet<>();
		for (Set<String> names : bySource.values())
		{
			combined.addAll(names);
		}
		union = Collections.unmodifiableSet(combined);
	}

	private static String key(String source)
	{
		return source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
	}
}
