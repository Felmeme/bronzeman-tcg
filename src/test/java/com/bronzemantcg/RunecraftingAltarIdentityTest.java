package com.bronzemantcg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class RunecraftingAltarIdentityTest
{
	private static final Map<Integer, String> STANDARD_ALTARS = standardAltars();

	private final ResourceNodeCatalog catalog = new ResourceNodeCatalog(new Gson());

	@Test
	public void publicStandardAltarIdsReachTheirExistingRules()
	{
		for (Map.Entry<Integer, String> altar : STANDARD_ALTARS.entrySet())
		{
			ResourceNodeCatalog.Rule rule = catalog.find(ResourceNodeCatalog.KIND_OBJECT,
				"Altar", "Craft-rune", altar.getKey());
			assertNotNull(altar.getValue(), rule);
			assertEquals("runecrafting", rule.category);
		}
	}

	@Test
	public void airAltarAcceptsRuneEssenceInsteadOfFallingIntoZmiRule()
	{
		ResourceNodeCatalog.Rule air = catalog.find(ResourceNodeCatalog.KIND_OBJECT,
			"Altar", "Craft-rune", 34760);
		assertNotNull(air);
		assertEquals(List.of("Air talisman / Air tiara", "Air rune"),
			air.missingRequirements(Set.of("rune essence"), Collections.emptySet(), false));

		ResourceNodeCatalog.Rule zmi = catalog.find(ResourceNodeCatalog.KIND_OBJECT,
			"Altar", "Craft-rune", 29631);
		assertNotNull(zmi);
		assertEquals(List.of("Pure essence / Daeyalt essence"),
			zmi.missingRequirements(Collections.emptySet(), Collections.emptySet(), false));
	}

	@Test
	public void genericAltarNameNoLongerAliasesEveryAltarToZmi()
	{
		assertNull(catalog.find(ResourceNodeCatalog.KIND_OBJECT,
			"Altar", "Craft-rune"));
		assertSame(catalog.find(ResourceNodeCatalog.KIND_OBJECT,
			"Air altar", "Craft-rune", 34760), catalog.find(ResourceNodeCatalog.KIND_OBJECT,
			"Altar", "Craft-rune", 34760));
	}

	private static Map<Integer, String> standardAltars()
	{
		Map<Integer, String> altars = new LinkedHashMap<>();
		altars.put(34760, "Air");
		altars.put(34761, "Mind");
		altars.put(34762, "Water");
		altars.put(34763, "Earth");
		altars.put(34764, "Fire");
		altars.put(34765, "Body");
		altars.put(34766, "Cosmic");
		altars.put(34767, "Law");
		altars.put(34768, "Nature");
		altars.put(34769, "Chaos");
		altars.put(34770, "Death");
		altars.put(34771, "Astral");
		altars.put(34772, "Wrath");
		altars.put(43479, "True Blood");
		return altars;
	}
}
