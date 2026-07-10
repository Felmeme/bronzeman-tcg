package com.bronzemantcg;

import java.util.List;

/**
 * Deliberately minimal mirror of osrs-tcg's TcgState/CollectionState/OwnedCardInstance JSON
 * shape. Gson ignores JSON fields with no matching Java field, so we only declare what we
 * actually read. Field names must match osrs-tcg's plain (un-annotated) Gson output exactly:
 *   TcgState.collectionState.instances[].cardName
 */
public class TcgStateDto
{
	public CollectionStateDto collectionState;

	public static class CollectionStateDto
	{
		public List<OwnedCardInstanceDto> instances;
	}

	public static class OwnedCardInstanceDto
	{
		public String cardName;
		public boolean foil;
	}
}
