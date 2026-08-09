package com.bronzemantcg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.gson.Gson;
import java.util.List;
import org.junit.Test;

public class AuditedOrdinaryFiremakingRecipeTest
{
	private static final List<String> AUDITED_LOGS = List.of(
		"Achey tree logs", "Arctic pine logs", "Blisterwood logs", "Camphor logs",
		"Ironwood logs", "Jatoba logs", "Rosewood logs");

	private final RecipeCatalog catalog = new RecipeCatalog(new Gson());

	@Test
	public void everyAuditedOrdinaryLogHasATinderboxRule()
	{
		for (String logs : AUDITED_LOGS)
		{
			RecipeCatalog.Recipe recipe = catalog.find(
				RecipeCatalog.KIND_ITEM_ON_ITEM, "Tinderbox", logs);
			assertNotNull(logs, recipe);
			assertEquals("firemaking", recipe.category);
		}
	}
}
