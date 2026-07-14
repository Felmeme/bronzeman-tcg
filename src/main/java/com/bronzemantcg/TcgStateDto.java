package com.bronzemantcg;

import java.util.List;

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
	public List<OwnedCardInstanceDto> cardInstances;
	/** TCG pack currency; displayed (with the creator's blessing) in the stats overlay. */
	public long credits;

	public static class OwnedCardInstanceDto
	{
		public String cardName;
		public boolean foil;
	}
}
