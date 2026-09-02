package com.bronzemantcg.catalog.remote;

import com.bronzemantcg.ownership.CardEntityKind;
import com.bronzemantcg.ownership.CardIdentity;
import com.bronzemantcg.ownership.ImmutableCardIdentityCatalog;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Strict parser for {@code /api/v1/catalog/cards/live}; it never returns a partial snapshot. */
@Singleton
public final class OsrsTcgCatalogParser
{
	private final Gson gson;

	@Inject
	public OsrsTcgCatalogParser(Gson gson)
	{
		this.gson = gson;
	}

	public OsrsTcgCatalogSnapshot parse(Reader reader) throws CatalogValidationException
	{
		if (reader == null)
		{
			throw new CatalogValidationException("catalogue reader is required");
		}
		RootDto root;
		try
		{
			root = gson.fromJson(reader, RootDto.class);
		}
		catch (JsonParseException | IllegalStateException ex)
		{
			throw new CatalogValidationException("catalogue JSON is malformed", ex);
		}
		if (root == null)
		{
			throw new CatalogValidationException("catalogue response is empty");
		}
		if (root.items == null || root.items.isEmpty())
		{
			throw new CatalogValidationException("catalogue items are missing or empty");
		}
		if (root.npcs == null || root.npcs.isEmpty())
		{
			throw new CatalogValidationException("catalogue NPCs are missing or empty");
		}

		List<ImmutableCardIdentityCatalog.Entry> entries = new ArrayList<>();
		Map<CardEntityKind, Map<Integer, String>> parentsById = new EnumMap<>(CardEntityKind.class);
		for (CardEntityKind kind : CardEntityKind.values())
		{
			parentsById.put(kind, new LinkedHashMap<>());
		}
		parseRows(CardEntityKind.ITEM, root.items, entries, parentsById.get(CardEntityKind.ITEM));
		parseRows(CardEntityKind.NPC, root.npcs, entries, parentsById.get(CardEntityKind.NPC));
		OsrsTcgCatalogSnapshot snapshot = new OsrsTcgCatalogSnapshot(entries);
		if (snapshot.getAmbiguousIdCount(CardEntityKind.ITEM) != 0
			|| snapshot.getAmbiguousIdCount(CardEntityKind.NPC) != 0)
		{
			throw new CatalogValidationException("catalogue contains ambiguous entity IDs");
		}
		return snapshot;
	}

	private static void parseRows(CardEntityKind kind, List<ParentDto> rows,
		List<ImmutableCardIdentityCatalog.Entry> entries, Map<Integer, String> parentsById)
		throws CatalogValidationException
	{
		for (int index = 0; index < rows.size(); index++)
		{
			ParentDto row = rows.get(index);
			String location = kind + " parent at index " + index;
			if (row == null)
			{
				throw new CatalogValidationException(location + " is null");
			}
			int parentId = requireId(row.id, location);
			String parentName = requireName(row.name, location);
			if (row.tcg == null)
			{
				throw new CatalogValidationException(location + " has no tcg data");
			}

			Set<Integer> entityIds = new LinkedHashSet<>();
			Set<Integer> ownedNameRequiredEntityIds = new LinkedHashSet<>();
			Set<String> entityNames = new LinkedHashSet<>();
			Set<String> legacyCardNames = new LinkedHashSet<>();
			entityIds.add(parentId);
			entityNames.add(parentName);
			claimId(parentsById, parentId, parentName, location);

			List<VariantDto> variants = row.tcg.variants == null
				? Collections.emptyList() : row.tcg.variants;
			for (int variantIndex = 0; variantIndex < variants.size(); variantIndex++)
			{
				VariantDto variant = variants.get(variantIndex);
				String variantLocation = location + " variant at index " + variantIndex;
				if (variant == null)
				{
					throw new CatalogValidationException(variantLocation + " is null");
				}
				int variantId = requireId(variant.id, variantLocation);
				String variantName = requireName(variant.name, variantLocation);
				claimId(parentsById, variantId, parentName, variantLocation);
				entityIds.add(variantId);
				entityNames.add(variantName);
				if (requiresParentOwnershipName(kind, parentName, variantName))
				{
					ownedNameRequiredEntityIds.add(variantId);
				}
				else if (!normalize(parentName).equals(normalize(variantName)))
				{
					legacyCardNames.add(variantName);
				}
			}

			CardIdentity identity = new CardIdentity(kind, parentName, legacyCardNames,
				entityIds, ownedNameRequiredEntityIds);
			entries.add(new ImmutableCardIdentityCatalog.Entry(identity, entityNames));
		}
	}

	private static boolean requiresParentOwnershipName(CardEntityKind kind,
		String parentName, String variantName)
	{
		return kind == CardEntityKind.ITEM
			&& "coins".equals(normalize(parentName))
			&& "coin pouch".equals(normalize(variantName));
	}

	private static void claimId(Map<Integer, String> parentsById, int entityId,
		String parentName, String location) throws CatalogValidationException
	{
		String existing = parentsById.putIfAbsent(entityId, parentName);
		if (existing != null && !normalize(existing).equals(normalize(parentName)))
		{
			throw new CatalogValidationException(location + " reuses entity ID " + entityId
				+ " already assigned to " + existing);
		}
	}

	private static int requireId(Integer id, String location) throws CatalogValidationException
	{
		if (id == null || id < 0)
		{
			throw new CatalogValidationException(location + " has an invalid ID");
		}
		return id;
	}

	private static String requireName(String name, String location)
		throws CatalogValidationException
	{
		if (name == null || name.trim().isEmpty())
		{
			throw new CatalogValidationException(location + " has a missing name");
		}
		return name.trim();
	}

	private static String normalize(String value)
	{
		return value.trim().toLowerCase(Locale.ROOT);
	}

	@SuppressWarnings("unused")
	private static final class RootDto
	{
		private List<ParentDto> items;
		private List<ParentDto> npcs;
	}

	@SuppressWarnings("unused")
	private static final class ParentDto
	{
		private Integer id;
		private String name;
		private TcgDto tcg;
	}

	@SuppressWarnings("unused")
	private static final class TcgDto
	{
		private List<VariantDto> variants;
	}

	@SuppressWarnings("unused")
	private static final class VariantDto
	{
		private Integer id;
		private String name;
	}
}
