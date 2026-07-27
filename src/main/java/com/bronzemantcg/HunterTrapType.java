package com.bronzemantcg;

import net.runelite.api.gameval.ObjectID;

/** Exact unset net-trap trees maintained by RuneLite's generated object IDs. */
enum HunterTrapType
{
	SWAMP(ObjectID.HUNTING_SAPLING_UP_SWAMP, "Swamp lizard"),
	ORANGE(ObjectID.HUNTING_SAPLING_UP_ORANGE, "Orange salamander"),
	RED(ObjectID.HUNTING_SAPLING_UP_RED, "Red salamander"),
	BLACK(ObjectID.HUNTING_SAPLING_UP_BLACK, "Black salamander"),
	TECU(ObjectID.HUNTING_SAPLING_UP_MOUNTAIN, "Tecu salamander");

	private final int objectId;
	private final String cardName;

	HunterTrapType(int objectId, String cardName)
	{
		this.objectId = objectId;
		this.cardName = cardName;
	}

	String getCardName()
	{
		return cardName;
	}

	static HunterTrapType fromObjectId(int objectId)
	{
		for (HunterTrapType type : values())
		{
			if (type.objectId == objectId)
			{
				return type;
			}
		}
		return null;
	}
}
