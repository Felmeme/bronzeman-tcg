package com.bronzemantcg.ownership;

import com.google.inject.ImplementedBy;
import java.util.List;

/**
 * Boundary between the restriction code and whichever reviewed catalogue source is active.
 * Implementations own entity-name aliases and ID indexes; the resolver never loads data itself.
 */
@ImplementedBy(ActiveCardIdentityCatalog.class)
public interface CardIdentityCatalog
{
	List<CardIdentity> findById(CardEntityKind kind, int entityId);

	List<CardIdentity> findByName(CardEntityKind kind, String entityName);

	List<CardIdentity> findByCardName(CardEntityKind kind, String cardName);
}
