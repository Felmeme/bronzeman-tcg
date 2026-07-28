package com.bronzemantcg;

import java.util.List;
import java.util.ArrayList;

/**
 * Deliberately minimal mirror of osrs-tcg's persisted TcgState JSON shape
 * (schemaVersion 3). Gson ignores JSON fields with no matching Java field, so
 * we only declare what we actually read. Field names must match osrs-tcg's
 * plain (un-annotated) Gson output exactly:
 *   TcgState.cardInstances[].cardName
 * Verified against a real decoded state blob captured from a live client
 * (2026-07-10); the collection lives at top level, not under a nested
 * collectionState object.
 */
public class TcgStateDto
{
	/** Legacy schema. */
	public List<OwnedCardInstanceDto> cardInstances;
	/** Current schema. */
	public CollectionStateDto collectionState;
	public EconomyStateDto economyState;
	/** Current v2 schema used by OSRS TCG 2026-07. */
	public List<CardEntryDto> cardEntries;
	public long credits;
	public long openedPacks;
	public double killCreditMultiplier = 1.0d;
	public double levelUpCreditMultiplier = 1.0d;
	public double xpCreditMultiplier = 1.0d;
	public SkillCreditBaselineDto skillCreditBaseline;

	public List<OwnedCardInstanceDto> instances()
	{
		if (collectionState != null && collectionState.instances != null)
		{
			return collectionState.instances;
		}
		if (cardInstances != null)
		{
			return cardInstances;
		}
		if (cardEntries == null)
		{
			return null;
		}
		List<OwnedCardInstanceDto> flattened = new ArrayList<>();
		for (CardEntryDto entry : cardEntries)
		{
			if (entry == null || entry.cardName == null || entry.variants == null) continue;
			for (CardVariantDto variant : entry.variants)
			{
				OwnedCardInstanceDto instance = new OwnedCardInstanceDto();
				instance.cardName = entry.cardName;
				instance.foil = variant != null && variant.foil;
				flattened.add(instance);
			}
		}
		return flattened;
	}

	public long credits()
	{
		return economyState != null ? economyState.credits : credits;
	}

	public static class CardEntryDto
	{
		public String cardName;
		public List<CardVariantDto> variants;
	}

	public static class CardVariantDto
	{
		public boolean foil;
	}

	public static class CollectionStateDto
	{
		public List<OwnedCardInstanceDto> instances;
	}

	public static class EconomyStateDto
	{
		public long credits;
		public long openedPacks;
	}

	public static class SkillCreditBaselineDto
	{
		public java.util.Map<String, Integer> skillXp;
		public long uncreditedXp;
	}

	public static class OwnedCardInstanceDto
	{
		public String cardName;
		public boolean foil;
	}
}
