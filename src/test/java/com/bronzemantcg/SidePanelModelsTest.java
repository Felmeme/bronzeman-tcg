package com.bronzemantcg;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

public class SidePanelModelsTest
{
	@Test
	public void mergedSlayerRequirementsPreserveAlternativesWithoutDuplicates()
	{
		Map<String, QuestCatalog.Requirement> requirements = new LinkedHashMap<>();
		SidePanelModels.mergeSlayerRequirement(requirements,
			new QuestCatalog.Requirement("Dragons", Arrays.asList("Green dragon", "Blue dragon")));
		SidePanelModels.mergeSlayerRequirement(requirements,
			new QuestCatalog.Requirement("Dragons", Arrays.asList("blue dragon", "Red dragon")));

		List<String> cards = requirements.get("Dragons").displayCards;
		Assert.assertEquals(Arrays.asList("Green dragon", "Blue dragon", "Red dragon"), cards);
	}

	@Test
	public void satisfiedRequirementsCountGroupsRatherThanCards()
	{
		List<QuestCatalog.Requirement> requirements = Arrays.asList(
			new QuestCatalog.Requirement("Any axe", Arrays.asList("Bronze axe", "Iron axe")),
			new QuestCatalog.Requirement("Rope", Arrays.asList("Rope")));

		Assert.assertEquals(1, SidePanelModels.satisfiedRequirements(
			requirements, Set.of("iron axe")));
		Assert.assertEquals(2, SidePanelModels.satisfiedRequirements(
			requirements, Set.of("iron axe", "rope")));
	}
}
