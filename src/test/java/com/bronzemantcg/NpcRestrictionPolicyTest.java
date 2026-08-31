package com.bronzemantcg;

import com.bronzemantcg.restriction.NpcRestrictionPolicy;
import net.runelite.api.gameval.NpcID;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NpcRestrictionPolicyTest
{
	@Test
	public void evilBobServantIsExemptButBurthorpeServantIsNot()
	{
		assertTrue(NpcRestrictionPolicy.isCardRestrictionExempt(
			NpcID.MACRO_EVIL_BOB_FEMALE_SERVANT));
		assertFalse(NpcRestrictionPolicy.isCardRestrictionExempt(NpcID.DEATH_SERVANT1));
	}
}
