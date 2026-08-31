package com.bronzemantcg.panel.collection;

import com.bronzemantcg.ownership.CardEntityKind;
import com.bronzemantcg.ownership.TcgOwnershipSnapshot;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Ownership rules shared by the v1 Collection and Beta Collection views. */
@Singleton
public final class PanelCollectionOwnership
{
	private final PanelCollectionLayout layout;

	@Inject
	public PanelCollectionOwnership(PanelCollectionLayout layout)
	{
		this.layout = layout;
	}

	boolean isPersonallyCollected(PanelCollectionLayout.CollectionCard card,
		TcgOwnershipSnapshot personalOwnership, Set<String> frozenBetaNames,
		PanelCollectionProjection projection)
	{
		if (card == null)
		{
			return false;
		}
		if (containsUniqueCollectionName(card, frozenBetaNames, projection))
		{
			return true;
		}
		if (personalOwnership == null)
		{
			return false;
		}
		if (!personalOwnership.hasEntityIds(card.getKind()))
		{
			return containsUniqueCollectionName(
				card, personalOwnership.getOwnedCardNamesLowerCase(), projection);
		}
		for (Integer entityId : card.getEntityIds())
		{
			if (personalOwnership.ownsEntityId(card.getKind(), entityId)
				&& (isCollectionEntityIdUnique(projection, card.getKind(), entityId)
					|| containsUniqueCollectionName(
						card, personalOwnership.getOwnedCardNamesLowerCase(), projection)))
			{
				return true;
			}
		}
		return false;
	}

	public boolean isBetaVariantInSnapshot(PanelCollectionLayout.BetaVariant variant,
		Set<String> betaNames)
	{
		if (variant == null)
		{
			return false;
		}
		String name = normalize(variant.getName());
		return layout.isBetaVariantNameUnique(name) && containsName(betaNames, name);
	}

	boolean isSharedCollectionCard(PanelCollectionLayout.CollectionCard card,
		Set<String> sharedCardNames, PanelCollectionProjection projection)
	{
		return card != null && containsUniqueCollectionName(
			card, sharedCardNames, projection);
	}

	private static boolean containsName(Set<String> names, String normalized)
	{
		if (names == null)
		{
			return false;
		}
		if (names.contains(normalized))
		{
			return true;
		}
		for (String name : names)
		{
			if (name != null && normalize(name).equals(normalized))
			{
				return true;
			}
		}
		return false;
	}

	private boolean containsUniqueCollectionName(
		PanelCollectionLayout.CollectionCard card, Set<String> names,
		PanelCollectionProjection projection)
	{
		if (names == null || names.isEmpty())
		{
			return false;
		}
		for (String acceptedName : card.getAcceptedNamesLowerCase())
		{
			if (isCollectionAcceptedNameUnique(projection, acceptedName)
				&& containsName(names, acceptedName))
			{
				return true;
			}
		}
		return false;
	}

	private boolean isCollectionEntityIdUnique(PanelCollectionProjection projection,
		CardEntityKind kind, int entityId)
	{
		return projection.isEntityIdUnique(kind, entityId);
	}

	private boolean isCollectionAcceptedNameUnique(
		PanelCollectionProjection projection, String name)
	{
		return projection.isAcceptedNameUnique(name);
	}

	private static String normalize(String value)
	{
		return value.trim().toLowerCase(Locale.ROOT);
	}
}
