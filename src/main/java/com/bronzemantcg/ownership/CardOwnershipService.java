package com.bronzemantcg.ownership;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/** One fail-open ownership decision boundary shared by future enforcement and visuals. */
@Slf4j
@Singleton
public final class CardOwnershipService
{
	private final CardResolver resolver;
	private final Set<String> reportedFailOpenDecisions = ConcurrentHashMap.newKeySet();

	@Inject
	public CardOwnershipService(CardResolver resolver)
	{
		this.resolver = resolver;
	}

	public Decision decide(CardEntityKind kind, int entityId, String entityName,
		TcgOwnershipSnapshot personalOwnership, Set<String> sharedCardNames,
		Set<String> exemptCardNames)
	{
		CardResolver.Result resolved = resolver.resolve(kind, entityId, entityName);
		return decideResolved(resolved, personalOwnership, sharedCardNames, exemptCardNames,
			kind, entityId, entityName);
	}

	/**
	 * Decides one name-authored parent-card requirement across the separate item and NPC
	 * namespaces. Exactly one reviewed identity is required; missing or conflicting catalogue
	 * data fails open just like direct entity resolution.
	 */
	public Decision decideCard(String cardName, TcgOwnershipSnapshot personalOwnership,
		Set<String> sharedCardNames, Set<String> exemptCardNames)
	{
		CardResolver.Result item = resolver.resolveCardName(CardEntityKind.ITEM, cardName);
		CardResolver.Result npc = resolver.resolveCardName(CardEntityKind.NPC, cardName);
		if (item.getStatus() == CardResolver.Status.AMBIGUOUS
			|| npc.getStatus() == CardResolver.Status.AMBIGUOUS
			|| (item.isTracked() && npc.isTracked()))
		{
			logRequirementFailOpenOnce(cardName, Status.AMBIGUOUS);
			return new Decision(Status.AMBIGUOUS, null);
		}
		CardResolver.Result resolved = item.isTracked() ? item : npc;
		if (!resolved.isTracked())
		{
			logRequirementFailOpenOnce(cardName, Status.UNTRACKED);
			return new Decision(Status.UNTRACKED, null);
		}
		return decideIdentity(resolved.getIdentity(), personalOwnership,
			sharedCardNames, exemptCardNames);
	}

	/** Decides one parent-card requirement inside an explicit item or NPC namespace. */
	public Decision decideCard(CardEntityKind kind, String cardName,
		TcgOwnershipSnapshot personalOwnership, Set<String> sharedCardNames,
		Set<String> exemptCardNames)
	{
		CardResolver.Result resolved = resolver.resolveCardName(kind, cardName);
		if (!resolved.isTracked())
		{
			Status status = map(resolved.getStatus());
			logRequirementFailOpenOnce(kind, cardName, status);
			return new Decision(status, null);
		}
		return decideIdentity(resolved.getIdentity(), personalOwnership,
			sharedCardNames, exemptCardNames);
	}

	/**
	 * Additive ownership for collection and readiness displays. Unlike enforcement, unresolved
	 * catalogue data is not treated as collected; only an exact owned/shared name may satisfy an
	 * unresolved row.
	 */
	public boolean isCollectedCard(String cardName, TcgOwnershipSnapshot personalOwnership,
		Set<String> sharedCardNames)
	{
		String normalizedCardName = normalizeCardName(cardName);
		if (normalizedCardName.isEmpty())
		{
			return false;
		}
		if (personalOwnership != null
			&& personalOwnership.getOwnedCardNamesLowerCase().contains(normalizedCardName))
		{
			return true;
		}
		if (containsCardName(sharedCardNames, normalizedCardName))
		{
			return true;
		}

		CardResolver.Result item = resolver.resolveCardName(CardEntityKind.ITEM, cardName);
		CardResolver.Result npc = resolver.resolveCardName(CardEntityKind.NPC, cardName);
		if (item.getStatus() == CardResolver.Status.AMBIGUOUS
			|| npc.getStatus() == CardResolver.Status.AMBIGUOUS
			|| (item.isTracked() && npc.isTracked()))
		{
			return false;
		}
		CardResolver.Result resolved = item.isTracked() ? item : npc;
		if (!resolved.isTracked())
		{
			return false;
		}
		CardIdentity identity = resolved.getIdentity();
		return identity.isCollectedBy(personalOwnership)
			|| acceptsUnambiguousSharedName(identity, sharedCardNames);
	}

	/**
	 * Collection/readiness lookup for a row whose item or NPC namespace is known. IDs remain
	 * authoritative when that namespace is present; names are only the legacy fallback.
	 */
	public boolean isCollectedCard(CardEntityKind kind, String cardName,
		TcgOwnershipSnapshot personalOwnership, Set<String> sharedCardNames)
	{
		String normalizedCardName = normalizeCardName(cardName);
		if (normalizedCardName.isEmpty())
		{
			return false;
		}
		CardResolver.Result resolved = resolver.resolveCardName(kind, cardName);
		if (!resolved.isTracked())
		{
			return false;
		}
		CardIdentity identity = resolved.getIdentity();
		return identity.isOwnedBy(personalOwnership)
			|| acceptsUnambiguousSharedName(identity, sharedCardNames);
	}

	private void logRequirementFailOpenOnce(String cardName, Status status)
	{
		String diagnosticKey = "CARD|" + cardName + "|" + status;
		if (reportedFailOpenDecisions.add(diagnosticKey))
		{
			log.debug("Failing open for parent-card requirement '{}': {}", cardName, status);
		}
	}

	private void logRequirementFailOpenOnce(CardEntityKind kind, String cardName, Status status)
	{
		String diagnosticKey = "CARD|" + kind + "|" + cardName + "|" + status;
		if (reportedFailOpenDecisions.add(diagnosticKey))
		{
			log.debug("Failing open for {} parent-card requirement '{}': {}",
				kind, cardName, status);
		}
	}

	private Decision decideResolved(CardResolver.Result resolved,
		TcgOwnershipSnapshot personalOwnership, Set<String> sharedCardNames,
		Set<String> exemptCardNames, CardEntityKind kind, int entityId, String entityName)
	{
		if (!resolved.isTracked())
		{
			if (resolved.getStatus() == CardResolver.Status.AMBIGUOUS
				|| resolved.getStatus() == CardResolver.Status.CATALOG_MISMATCH)
			{
				String diagnosticKey = kind + "|" + entityId + "|" + resolved.getStatus();
				if (reportedFailOpenDecisions.add(diagnosticKey))
				{
					log.debug("Failing open for {} id={} name='{}': {}",
						kind, entityId, entityName, resolved.getStatus());
				}
			}
			return new Decision(map(resolved.getStatus()), null);
		}
		return decideIdentity(resolved.getIdentity(), personalOwnership,
			sharedCardNames, exemptCardNames);
	}

	private Decision decideIdentity(CardIdentity identity,
		TcgOwnershipSnapshot personalOwnership, Set<String> sharedCardNames,
		Set<String> exemptCardNames)
	{
		if (identity.acceptsAnyCardName(exemptCardNames))
		{
			return new Decision(Status.EXEMPT, identity);
		}
		if (identity.isOwnedBy(personalOwnership))
		{
			return new Decision(Status.OWNED, identity);
		}
		if (acceptsUnambiguousSharedName(identity, sharedCardNames))
		{
			return new Decision(Status.SHARED, identity);
		}
		return new Decision(Status.LOCKED, identity);
	}

	/** Names-only shared ownership cannot safely choose between equal item and NPC card names. */
	private boolean acceptsUnambiguousSharedName(CardIdentity identity, Set<String> sharedCardNames)
	{
		if (!identity.acceptsAnyCardName(sharedCardNames))
		{
			return false;
		}
		CardEntityKind otherKind = identity.getKind() == CardEntityKind.ITEM
			? CardEntityKind.NPC : CardEntityKind.ITEM;
		if (containsCardName(sharedCardNames, normalizeCardName(identity.getCardName()))
			&& resolver.resolveCardName(otherKind, identity.getCardName()).getStatus()
				== CardResolver.Status.UNTRACKED)
		{
			return true;
		}
		for (String legacyCardName : identity.getLegacyCardNames())
		{
			if (containsCardName(sharedCardNames, normalizeCardName(legacyCardName))
				&& resolver.resolveCardName(otherKind, legacyCardName).getStatus()
					== CardResolver.Status.UNTRACKED)
			{
				return true;
			}
		}
		return false;
	}

	private static boolean containsCardName(Set<String> names, String lowerCaseCardName)
	{
		if (names == null || names.isEmpty())
		{
			return false;
		}
		// SharedUnlockStore and ExemptionList already provide lower-case sets, so the normal
		// render/menu path remains one hash lookup with no allocation.
		if (names.contains(lowerCaseCardName))
		{
			return true;
		}
		for (String name : names)
		{
			if (name != null && lowerCaseCardName.equals(name.trim().toLowerCase(Locale.ROOT)))
			{
				return true;
			}
		}
		return false;
	}

	private static String normalizeCardName(String cardName)
	{
		return cardName == null ? "" : cardName.trim().toLowerCase(Locale.ROOT);
	}

	private static Status map(CardResolver.Status status)
	{
		switch (status)
		{
			case AMBIGUOUS:
				return Status.AMBIGUOUS;
			case CATALOG_MISMATCH:
				return Status.CATALOG_MISMATCH;
			default:
				return Status.UNTRACKED;
		}
	}

	public enum Status
	{
		OWNED(true),
		SHARED(true),
		EXEMPT(true),
		LOCKED(false),
		UNTRACKED(true),
		AMBIGUOUS(true),
		CATALOG_MISMATCH(true);

		private final boolean allowed;

		Status(boolean allowed)
		{
			this.allowed = allowed;
		}

		public boolean isAllowed()
		{
			return allowed;
		}
	}

	public static final class Decision
	{
		private final Status status;
		private final CardIdentity identity;

		private Decision(Status status, CardIdentity identity)
		{
			this.status = status;
			this.identity = identity;
		}

		public Status getStatus()
		{
			return status;
		}

		public boolean isAllowed()
		{
			return status.isAllowed();
		}

		public CardIdentity getIdentity()
		{
			return identity;
		}
	}
}
