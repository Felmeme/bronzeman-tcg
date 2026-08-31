package com.bronzemantcg.restriction;


import net.runelite.api.gameval.NpcID;

/** Exact NPC exceptions where a shared display name would otherwise select the wrong card. */
public final class NpcRestrictionPolicy
{
	private NpcRestrictionPolicy()
	{
	}

	public static boolean isCardRestrictionExempt(int npcId)
	{
		// Evil Bob's random-event servant is unrelated to Servant (Burthorpe).
		return npcId == NpcID.MACRO_EVIL_BOB_FEMALE_SERVANT;
	}
}
