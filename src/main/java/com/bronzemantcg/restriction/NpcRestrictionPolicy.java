package com.bronzemantcg.restriction;


import java.util.Locale;
import java.util.Set;
import net.runelite.api.gameval.NpcID;

/** Permanent NPC exceptions to card-based interaction restrictions. */
public final class NpcRestrictionPolicy
{
	private static final Set<String> DEFAULT_EXEMPT_NAMES = Set.of(
		"ironman tutor",
		"veos"
	);

	private NpcRestrictionPolicy()
	{
	}

	public static boolean isCardRestrictionExempt(int npcId)
	{
		// Evil Bob's random-event servant is unrelated to Servant (Burthorpe).
		return npcId == NpcID.MACRO_EVIL_BOB_FEMALE_SERVANT;
	}

	public static boolean isCardRestrictionExempt(int npcId, String npcName)
	{
		return isCardRestrictionExempt(npcId)
			|| npcName != null && DEFAULT_EXEMPT_NAMES.contains(
				npcName.trim().toLowerCase(Locale.ROOT));
	}
}
