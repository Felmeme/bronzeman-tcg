package com.bronzemantcg;

import net.runelite.api.gameval.NpcID;

/** Exact NPC exceptions where a shared display name would otherwise select the wrong card. */
final class NpcRestrictionPolicy
{
	private NpcRestrictionPolicy()
	{
	}

	static boolean isCardRestrictionExempt(int npcId)
	{
		// Evil Bob's random-event servant is unrelated to Servant (Burthorpe).
		return npcId == NpcID.MACRO_EVIL_BOB_FEMALE_SERVANT;
	}
}
