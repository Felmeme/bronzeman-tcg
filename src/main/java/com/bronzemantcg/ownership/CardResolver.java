package com.bronzemantcg.ownership;

import com.bronzemantcg.catalog.CardNames;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Resolves one RuneScape entity to a reviewed parent-card identity. */
@Singleton
public final class CardResolver
{
	private final CardIdentityCatalog catalog;

	@Inject
	public CardResolver(CardIdentityCatalog catalog)
	{
		if (catalog == null)
		{
			throw new IllegalArgumentException("catalog is required");
		}
		this.catalog = catalog;
	}

	/** Resolves a reviewed parent-card name without treating it as an entity alias. */
	public Result resolveCardName(CardEntityKind kind, String cardName)
	{
		if (kind == null)
		{
			return Result.of(Status.AMBIGUOUS);
		}
		List<CardIdentity> matches = unique(catalog.findByCardName(kind, cardName));
		if (matches.isEmpty())
		{
			return Result.of(Status.UNTRACKED);
		}
		return matches.size() == 1
			? Result.tracked(matches.get(0)) : Result.of(Status.AMBIGUOUS);
	}

	/**
	 * A supplied ID is authoritative. If the ID is unknown but its name is known, the catalogue
	 * and RuneScape disagree and the result is a mismatch rather than a silent name guess. Name
	 * fallback is used only when the interaction genuinely exposes no usable ID.
	 */
	public Result resolve(CardEntityKind kind, int entityId, String entityName)
	{
		if (kind == null)
		{
			return Result.of(Status.AMBIGUOUS);
		}
		if (entityId >= 0)
		{
			List<CardIdentity> idMatches = unique(catalog.findById(kind, entityId));
			if (idMatches.size() > 1)
			{
				return Result.of(Status.AMBIGUOUS);
			}
			if (idMatches.size() == 1)
			{
				return Result.tracked(idMatches.get(0));
			}
			return unique(catalog.findByName(kind, normalizeEntityName(kind, entityName))).isEmpty()
				? Result.of(Status.UNTRACKED) : Result.of(Status.CATALOG_MISMATCH);
		}

		List<CardIdentity> nameMatches = unique(
			catalog.findByName(kind, normalizeEntityName(kind, entityName)));
		if (nameMatches.isEmpty())
		{
			return Result.of(Status.UNTRACKED);
		}
		return nameMatches.size() == 1
			? Result.tracked(nameMatches.get(0)) : Result.of(Status.AMBIGUOUS);
	}

	private static String normalizeEntityName(CardEntityKind kind, String entityName)
	{
		return kind == CardEntityKind.ITEM && entityName != null
			? CardNames.stripDoseSuffix(entityName) : entityName;
	}

	private static List<CardIdentity> unique(List<CardIdentity> matches)
	{
		if (matches == null || matches.isEmpty())
		{
			return Collections.emptyList();
		}
		Map<String, CardIdentity> unique = new LinkedHashMap<>();
		for (CardIdentity identity : matches)
		{
			if (identity != null)
			{
				String key = identity.getKind().name() + '\u0000'
					+ identity.getCardName().toLowerCase(Locale.ROOT);
				unique.putIfAbsent(key, identity);
			}
		}
		return List.copyOf(unique.values());
	}

	public enum Status
	{
		TRACKED,
		UNTRACKED,
		AMBIGUOUS,
		CATALOG_MISMATCH
	}

	public static final class Result
	{
		private final Status status;
		private final CardIdentity identity;

		private Result(Status status, CardIdentity identity)
		{
			this.status = status;
			this.identity = identity;
		}

		private static Result tracked(CardIdentity identity)
		{
			return new Result(Status.TRACKED, identity);
		}

		private static Result of(Status status)
		{
			return new Result(status, null);
		}

		public Status getStatus()
		{
			return status;
		}

		public boolean isTracked()
		{
			return status == Status.TRACKED;
		}

		public CardIdentity getIdentity()
		{
			return identity;
		}
	}
}
