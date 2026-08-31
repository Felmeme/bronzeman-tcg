package com.bronzemantcg.ownership;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Atomically switches identity readers between the reviewed bundle and one validated revision. */
@Singleton
public final class ActiveCardIdentityCatalog implements CardIdentityCatalog
{
	private final BundledCardIdentityCatalog bundledCatalog;
	private final AtomicLong revisionSequence = new AtomicLong();
	private final AtomicReference<View> active;

	@Inject
	public ActiveCardIdentityCatalog(BundledCardIdentityCatalog bundledCatalog)
	{
		if (bundledCatalog == null)
		{
			throw new IllegalArgumentException("bundledCatalog is required");
		}
		this.bundledCatalog = bundledCatalog;
		active = new AtomicReference<>(new View(0L, "beta-fallback", false,
			bundledCatalog, bundledCatalog.getEntries()));
	}

	@Override
	public List<CardIdentity> findById(CardEntityKind kind, int entityId)
	{
		return active.get().catalog.findById(kind, entityId);
	}

	@Override
	public List<CardIdentity> findByName(CardEntityKind kind, String entityName)
	{
		return active.get().catalog.findByName(kind, entityName);
	}

	@Override
	public List<CardIdentity> findByCardName(CardEntityKind kind, String cardName)
	{
		return active.get().catalog.findByCardName(kind, cardName);
	}

	public synchronized long activate(CardIdentityCatalog catalog,
		List<ImmutableCardIdentityCatalog.Entry> entries, String version)
	{
		if (catalog == null || entries == null)
		{
			throw new IllegalArgumentException("validated catalogue and entries are required");
		}
		String cleanVersion = cleanVersion(version);
		View current = active.get();
		if (current.catalog == catalog && current.version.equals(cleanVersion))
		{
			return current.revision;
		}
		long revision = revisionSequence.incrementAndGet();
		active.set(new View(revision, cleanVersion, true, catalog, entries));
		return revision;
	}

	public synchronized long useBundled()
	{
		View current = active.get();
		if (!current.remote)
		{
			return current.revision;
		}
		long revision = revisionSequence.incrementAndGet();
		active.set(new View(revision, "beta-fallback", false,
			bundledCatalog, bundledCatalog.getEntries()));
		return revision;
	}

	public long getRevision()
	{
		return active.get().revision;
	}

	public boolean isRemoteActive()
	{
		return active.get().remote;
	}

	public boolean isV1CatalogAvailable()
	{
		return active.get().v1CatalogAvailable;
	}

	public View getView()
	{
		return active.get();
	}

	public String findDisplayCardName(String cardName)
	{
		return active.get().findDisplayCardName(cardName);
	}

	private static String cleanVersion(String version)
	{
		return version == null || version.trim().isEmpty() ? "unknown" : version.trim();
	}

	private static Map<CardEntityKind, Map<String, Set<String>>> projectEntityNames(
		List<ImmutableCardIdentityCatalog.Entry> entries)
	{
		Map<CardEntityKind, Map<String, Set<String>>> mutable =
			new EnumMap<>(CardEntityKind.class);
		for (CardEntityKind kind : CardEntityKind.values())
		{
			mutable.put(kind, new LinkedHashMap<>());
		}
		for (ImmutableCardIdentityCatalog.Entry entry : entries)
		{
			if (entry == null)
			{
				continue;
			}
			CardIdentity identity = entry.getIdentity();
			String parent = normalize(identity.getCardName());
			for (String entityName : entry.getEntityNames())
			{
				String entity = normalize(entityName);
				if (!entity.isEmpty())
				{
					mutable.get(identity.getKind())
						.computeIfAbsent(entity, ignored -> new LinkedHashSet<>())
						.add(parent);
				}
			}
		}
		Map<CardEntityKind, Map<String, Set<String>>> frozen =
			new EnumMap<>(CardEntityKind.class);
		for (Map.Entry<CardEntityKind, Map<String, Set<String>>> kindEntry
			: mutable.entrySet())
		{
			Map<String, Set<String>> names = new LinkedHashMap<>();
			for (Map.Entry<String, Set<String>> nameEntry : kindEntry.getValue().entrySet())
			{
				names.put(nameEntry.getKey(), Collections.unmodifiableSet(
					new LinkedHashSet<>(nameEntry.getValue())));
			}
			frozen.put(kindEntry.getKey(), Collections.unmodifiableMap(names));
		}
		return Collections.unmodifiableMap(frozen);
	}

	private static String normalize(String value)
	{
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	/** One self-consistent revision and its entity-name projection for dependent caches. */
	public static final class View implements CardIdentityCatalog
	{
		private final long revision;
		private final String version;
		private final boolean remote;
		private final boolean v1CatalogAvailable;
		private final CardIdentityCatalog catalog;
		private final List<CardIdentity> cardIdentities;
		private final Map<CardEntityKind, Map<String, Set<String>>> entityToCardNames;
		private final List<ImmutableCardIdentityCatalog.Entry> entries;
		private final Map<String, String> displayCardNames;

		private View(long revision, String version, boolean remote,
			CardIdentityCatalog catalog, List<ImmutableCardIdentityCatalog.Entry> entries)
		{
			this.revision = revision;
			this.version = version;
			this.remote = remote;
			this.v1CatalogAvailable = remote;
			this.catalog = catalog;
			this.entries = List.copyOf(entries);
			List<CardIdentity> identities = new ArrayList<>();
			Map<String, String> displayNames = new LinkedHashMap<>();
			for (ImmutableCardIdentityCatalog.Entry entry : entries)
			{
				if (entry != null)
				{
					CardIdentity identity = entry.getIdentity();
					identities.add(identity);
					displayNames.putIfAbsent(normalize(identity.getCardName()),
						identity.getCardName());
					for (String legacyName : identity.getLegacyCardNames())
					{
						displayNames.putIfAbsent(normalize(legacyName), legacyName);
					}
				}
			}
			this.cardIdentities = List.copyOf(identities);
			this.displayCardNames = Collections.unmodifiableMap(displayNames);
			this.entityToCardNames = projectEntityNames(entries);
		}

		public long getRevision()
		{
			return revision;
		}

		public boolean isRemote()
		{
			return remote;
		}

		public boolean isV1CatalogAvailable()
		{
			return v1CatalogAvailable;
		}

		public List<CardIdentity> getCardIdentities()
		{
			return cardIdentities;
		}

		public List<ImmutableCardIdentityCatalog.Entry> getEntries()
		{
			return entries;
		}

		public String findDisplayCardName(String cardName)
		{
			return displayCardNames.get(normalize(cardName));
		}

		@Override
		public List<CardIdentity> findById(CardEntityKind kind, int entityId)
		{
			return catalog.findById(kind, entityId);
		}

		@Override
		public List<CardIdentity> findByName(CardEntityKind kind, String entityName)
		{
			return catalog.findByName(kind, entityName);
		}

		@Override
		public List<CardIdentity> findByCardName(CardEntityKind kind, String cardName)
		{
			return catalog.findByCardName(kind, cardName);
		}

		public Map<String, Set<String>> getEntityToCardNames(CardEntityKind kind)
		{
			return entityToCardNames.getOrDefault(kind, Collections.emptyMap());
		}
	}
}
