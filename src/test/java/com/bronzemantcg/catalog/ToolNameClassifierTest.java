package com.bronzemantcg.catalog;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ToolNameClassifierTest
{
	@Test
	public void recognisesEverySupportedWoodcuttingAxe()
	{
		String[] axes = {
			"bronze axe", "iron axe", "steel axe", "black axe", "mithril axe",
			"adamant axe", "rune axe", "dragon axe", "crystal axe", "infernal axe",
			"gilded axe", "3rd age axe", "corrupted axe",
			"bronze felling axe", "iron felling axe", "steel felling axe",
			"black felling axe", "mithril felling axe", "adamant felling axe",
			"rune felling axe", "dragon felling axe", "crystal felling axe"
		};
		for (String axe : axes)
		{
			assertTrue(axe, ToolNameClassifier.isWoodcuttingAxe(axe));
		}
	}

	@Test
	public void rejectsCardedCombatAxes()
	{
		assertFalse(ToolNameClassifier.isWoodcuttingAxe("Zombie axe"));
		assertFalse(ToolNameClassifier.isWoodcuttingAxe("Soulreaper axe"));
		assertFalse(ToolNameClassifier.isWoodcuttingAxe("Morrigan's throwing axe"));
	}

	@Test
	public void classificationIsCaseInsensitiveAndNullSafe()
	{
		assertTrue(ToolNameClassifier.isWoodcuttingAxe("Dragon Axe"));
		assertTrue(ToolNameClassifier.isMiningPickaxe("Rune Pickaxe"));
		assertFalse(ToolNameClassifier.isWoodcuttingAxe(null));
		assertFalse(ToolNameClassifier.isMiningPickaxe(null));
		assertFalse(ToolNameClassifier.isMiningPickaxe("Zombie axe"));
	}
}
