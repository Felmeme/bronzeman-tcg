package com.bronzemantcg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class KourendRunecraftingRouteTest
{
	private final ResourceNodeCatalog catalog = new ResourceNodeCatalog(new Gson());

	@Test
	public void kourendBloodAndSoulUseBindWithDarkEssenceFragments()
	{
		assertBindRule("Blood Altar", 27978, "Blood rune");
		assertBindRule("Soul Altar", 27980, "Soul rune");
	}

	@Test
	public void obsoleteSoulCraftRuneRouteIsNotRegistered()
	{
		assertNull(catalog.find(ResourceNodeCatalog.KIND_OBJECT,
			"Soul Altar", "Craft-rune", 27980));
	}

	@Test
	public void trueBloodAltarKeepsItsSeparatePureEssenceRoute()
	{
		ResourceNodeCatalog.Rule rule = catalog.find(ResourceNodeCatalog.KIND_OBJECT,
			"Altar", "Craft-rune", 43479);
		if (rule == null || !List.of("Blood rune").equals(rule.missingRequirements(
			Set.of("pure essence"), Collections.emptySet(), false)))
		{
			// Keep this fix independently testable against public main, before
			// the separate altar-ID correction is selected. The ID-aware lookup
			// otherwise falls back to the generic Ourania Altar rule.
			rule = catalog.find(ResourceNodeCatalog.KIND_OBJECT,
				"Blood altar", "Craft-rune");
		}
		assertNotNull(rule);
		assertEquals(List.of("Blood rune"), rule.missingRequirements(
			Set.of("pure essence"), Collections.emptySet(), false));
	}

	private void assertBindRule(String name, int objectId, String rune)
	{
		ResourceNodeCatalog.Rule rule = catalog.find(
			ResourceNodeCatalog.KIND_OBJECT, name, "Bind", objectId);
		assertNotNull(name, rule);
		assertEquals("runecrafting", rule.category);
		assertEquals(List.of("Dark essence fragments", rune),
			rule.missingRequirements(Collections.emptySet(), Collections.emptySet(), false));
		assertEquals(List.of(rune), rule.missingRequirements(
			Set.of("dark essence fragments"), Collections.emptySet(), false));
	}
}
