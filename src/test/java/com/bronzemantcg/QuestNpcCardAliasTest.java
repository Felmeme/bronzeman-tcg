package com.bronzemantcg;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import java.util.Set;
import org.junit.Test;

public class QuestNpcCardAliasTest
{
	@Test
	public void avanUsesTheAuditedManCard()
	{
		assertEquals(Set.of("man"),
			new TrackedMonsterCatalog(new Gson()).getCardVariantsLowerCase("Avan"));
	}

	@Test
	public void ulsquireUsesTheAuditedAfflictedCard()
	{
		TrackedMonsterCatalog catalog = new TrackedMonsterCatalog(new Gson());
		assertEquals(Set.of("afflicted"),
			catalog.getCardVariantsLowerCase("Ulsquire Shauncy"));
		assertEquals(Set.of("afflicted"),
			catalog.getCardVariantsLowerCase("Afflicted(Ulsquire)"));
	}
}
