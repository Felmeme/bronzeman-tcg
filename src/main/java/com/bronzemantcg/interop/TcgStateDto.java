package com.bronzemantcg.interop;

import java.util.List;

/**
 * Deliberately minimal mirror of the legacy and current osrs-tcg persisted
 * collection shapes. Gson ignores fields with no matching Java field, so only
 * ownership and beta-provenance fields are declared here.
 */
public class TcgStateDto
{
	public List<OwnedCardInstanceDto> cardInstances;
	public List<CardEntryDto> cardEntries;

	public static class OwnedCardInstanceDto
	{
		public String cardName;
		public boolean foil;
		public Boolean beta;
	}

	public static class CardEntryDto
	{
		public String cardName;
		public List<CardVariantDto> variants;
	}

	public static class CardVariantDto
	{
		public Boolean beta;
		public Integer quantity;
	}
}
