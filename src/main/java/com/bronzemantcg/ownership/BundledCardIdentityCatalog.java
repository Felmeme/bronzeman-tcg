package com.bronzemantcg.ownership;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/** Reviewed Beta parent-card fallback and its RuneScape entity aliases/IDs. */
@Slf4j
@Singleton
public final class BundledCardIdentityCatalog implements CardIdentityCatalog
{
	private static final String DEFAULT_RESOURCE = "/beta/beta_card_identity_catalog.json";

	private final ImmutableCardIdentityCatalog catalog;

	@Inject
	public BundledCardIdentityCatalog(Gson gson)
	{
		this(gson, DEFAULT_RESOURCE);
	}

	BundledCardIdentityCatalog(Gson gson, String resourcePath)
	{
		catalog = new ImmutableCardIdentityCatalog(load(gson, resourcePath));
		log.info("Loaded {} reviewed Beta fallback identities "
			+ "({} ambiguous item IDs, {} ambiguous NPC IDs)",
			catalog.size(), catalog.getAmbiguousIdCount(CardEntityKind.ITEM),
			catalog.getAmbiguousIdCount(CardEntityKind.NPC));
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

	public int size()
	{
		return catalog.size();
	}

	public List<ImmutableCardIdentityCatalog.Entry> getEntries()
	{
		return catalog.getEntries();
	}

	public int getAmbiguousIdCount(CardEntityKind kind)
	{
		return catalog.getAmbiguousIdCount(kind);
	}

	private static List<ImmutableCardIdentityCatalog.Entry> load(Gson gson, String resourcePath)
	{
		List<ImmutableCardIdentityCatalog.Entry> entries = new ArrayList<>();
		if (gson == null || resourcePath == null)
		{
			return entries;
		}
		try (InputStream stream = BundledCardIdentityCatalog.class.getResourceAsStream(resourcePath))
		{
			if (stream == null)
			{
				log.warn("{} missing from classpath; ID-aware restrictions will fail open.", resourcePath);
				return entries;
			}
			Snapshot snapshot = gson.fromJson(
				new InputStreamReader(stream, StandardCharsets.UTF_8), Snapshot.class);
			if (snapshot == null || snapshot.identities == null)
			{
				return entries;
			}
			for (IdentityRow row : snapshot.identities)
			{
				ImmutableCardIdentityCatalog.Entry entry = parse(row);
				if (entry != null)
				{
					entries.add(entry);
				}
			}
		}
		catch (IOException | RuntimeException ex)
		{
			log.warn("Failed to load {}; ID-aware restrictions will fail open.", resourcePath, ex);
		}
		return entries;
	}

	private static ImmutableCardIdentityCatalog.Entry parse(IdentityRow row)
	{
		if (row == null || row.kind == null)
		{
			return null;
		}
		CardEntityKind kind;
		try
		{
			kind = CardEntityKind.valueOf(row.kind.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex)
		{
			return null;
		}
		try
		{
			CardIdentity identity = new CardIdentity(kind, row.cardName,
				row.legacyCardNames == null ? Collections.emptySet()
					: new LinkedHashSet<>(row.legacyCardNames),
				row.entityIds == null ? Collections.emptySet()
					: new LinkedHashSet<>(row.entityIds),
				row.ownedNameRequiredEntityIds == null ? Collections.emptySet()
					: new LinkedHashSet<>(row.ownedNameRequiredEntityIds));
			return new ImmutableCardIdentityCatalog.Entry(identity,
				row.entityNames == null ? Collections.emptyList() : row.entityNames);
		}
		catch (IllegalArgumentException ex)
		{
			return null;
		}
	}

	private static final class Snapshot
	{
		private List<IdentityRow> identities;
	}

	private static final class IdentityRow
	{
		private String kind;
		private String cardName;
		private List<String> legacyCardNames;
		private List<String> entityNames;
		private List<Integer> entityIds;
		private List<Integer> ownedNameRequiredEntityIds;
	}
}
