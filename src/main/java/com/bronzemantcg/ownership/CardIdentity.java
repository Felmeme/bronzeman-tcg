package com.bronzemantcg.ownership;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reviewed identity for one parent TCG card. The IDs contain the parent RuneScape entity and
 * every variant which should count as that same card; this class carries no catalogue data by
 * itself.
 */
public final class CardIdentity
{
	private final CardEntityKind kind;
	private final String cardName;
	private final Set<String> legacyCardNames;
	private final Set<String> acceptedCardNamesLowerCase;
	private final Set<Integer> entityIds;
	private final Set<Integer> ownedNameRequiredEntityIds;

	public CardIdentity(CardEntityKind kind, String cardName, Set<Integer> entityIds)
	{
		this(kind, cardName, Collections.emptySet(), entityIds);
	}

	public CardIdentity(CardEntityKind kind, String cardName,
		Set<String> legacyCardNames, Set<Integer> entityIds)
	{
		this(kind, cardName, legacyCardNames, entityIds, Collections.emptySet());
	}

	public CardIdentity(CardEntityKind kind, String cardName,
		Set<String> legacyCardNames, Set<Integer> entityIds,
		Set<Integer> ownedNameRequiredEntityIds)
	{
		if (kind == null)
		{
			throw new IllegalArgumentException("kind is required");
		}
		if (cardName == null || cardName.trim().isEmpty())
		{
			throw new IllegalArgumentException("cardName is required");
		}
		this.kind = kind;
		this.cardName = cardName.trim();
		String cardNameLowerCase = this.cardName.toLowerCase(Locale.ROOT);
		Map<String, String> validLegacyNames = new LinkedHashMap<>();
		if (legacyCardNames != null)
		{
			for (String legacyName : legacyCardNames)
			{
				if (legacyName == null || legacyName.trim().isEmpty())
				{
					continue;
				}
				String trimmed = legacyName.trim();
				String lowerCase = trimmed.toLowerCase(Locale.ROOT);
				if (!cardNameLowerCase.equals(lowerCase))
				{
					validLegacyNames.putIfAbsent(lowerCase, trimmed);
				}
			}
		}
		this.legacyCardNames = Collections.unmodifiableSet(
			new LinkedHashSet<>(validLegacyNames.values()));
		LinkedHashSet<String> acceptedNames = new LinkedHashSet<>();
		acceptedNames.add(cardNameLowerCase);
		acceptedNames.addAll(validLegacyNames.keySet());
		this.acceptedCardNamesLowerCase = Collections.unmodifiableSet(acceptedNames);
		LinkedHashSet<Integer> validIds = new LinkedHashSet<>();
		if (entityIds != null)
		{
			for (Integer id : entityIds)
			{
				if (id != null && id >= 0)
				{
					validIds.add(id);
				}
			}
		}
		this.entityIds = Collections.unmodifiableSet(validIds);
		LinkedHashSet<Integer> guardedIds = new LinkedHashSet<>();
		if (ownedNameRequiredEntityIds != null)
		{
			for (Integer id : ownedNameRequiredEntityIds)
			{
				if (validIds.contains(id))
				{
					guardedIds.add(id);
				}
			}
		}
		this.ownedNameRequiredEntityIds = Collections.unmodifiableSet(guardedIds);
	}

	public CardEntityKind getKind()
	{
		return kind;
	}

	public String getCardName()
	{
		return cardName;
	}

	public Set<Integer> getEntityIds()
	{
		return entityIds;
	}

	public Set<String> getLegacyCardNames()
	{
		return legacyCardNames;
	}

	public Set<Integer> getOwnedNameRequiredEntityIds()
	{
		return ownedNameRequiredEntityIds;
	}

	/**
	 * IDs are authoritative when the installed OSRS TCG version supplies that namespace.
	 * Card-name fallback is reserved for the older name-only PluginMessage/config contract.
	 */
	public boolean isOwnedBy(TcgOwnershipSnapshot ownership)
	{
		if (ownership == null)
		{
			return false;
		}
		if (ownership.hasEntityIds(kind))
		{
			for (Integer id : entityIds)
			{
				if (ownsEntityId(ownership, id))
				{
					return true;
				}
			}
			return false;
		}
		return ownsAcceptedName(ownership);
	}

	/**
	 * Display/readiness ownership is additive. Exact current or legacy names still count when
	 * the API also supplies IDs, while a reviewed variant ID can collect the canonical parent.
	 */
	public boolean isCollectedBy(TcgOwnershipSnapshot ownership)
	{
		if (ownership == null)
		{
			return false;
		}
		for (Integer id : entityIds)
		{
			if (ownsEntityId(ownership, id))
			{
				return true;
			}
		}
		return ownsAcceptedName(ownership);
	}

	boolean acceptsAnyCardName(Set<String> names)
	{
		if (names == null || names.isEmpty())
		{
			return false;
		}
		for (String acceptedName : acceptedCardNamesLowerCase)
		{
			if (containsNormalized(names, acceptedName))
			{
				return true;
			}
		}
		return false;
	}

	private boolean ownsEntityId(TcgOwnershipSnapshot ownership, int entityId)
	{
		return ownership.ownsEntityId(kind, entityId)
			&& (!ownedNameRequiredEntityIds.contains(entityId)
				|| ownsAcceptedName(ownership));
	}

	private boolean ownsAcceptedName(TcgOwnershipSnapshot ownership)
	{
		Set<String> ownedNames = ownership.getOwnedCardNamesLowerCase();
		for (String acceptedName : acceptedCardNamesLowerCase)
		{
			if (ownedNames.contains(acceptedName))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean containsNormalized(Set<String> names, String lowerCaseName)
	{
		if (names.contains(lowerCaseName))
		{
			return true;
		}
		for (String name : names)
		{
			if (name != null && lowerCaseName.equals(name.trim().toLowerCase(Locale.ROOT)))
			{
				return true;
			}
		}
		return false;
	}
}
