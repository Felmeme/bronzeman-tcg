package com.bronzemantcg.settings;

import com.bronzemantcg.BronzemanTcgConfig;
import com.bronzemantcg.restriction.BankingMode;
import com.bronzemantcg.restriction.CookingMode;
import com.bronzemantcg.restriction.HunterMode;
import com.bronzemantcg.restriction.LockState;
import com.bronzemantcg.restriction.SlayerMode;
import com.bronzemantcg.restriction.ThievingPolicy.HamPickpocketingMode;
import com.bronzemantcg.restriction.ThievingPolicy.ThievingMode;
import com.bronzemantcg.restriction.WoodcuttingMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BronzemanPresetTest
{
	private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

	@Test
	public void presetsCoverTheSameGameplaySettings()
	{
		for (BronzemanPreset preset : BronzemanPreset.values())
		{
			assertEquals(BronzemanPreset.TCG_LOCKED.getSettings().keySet(),
				preset.getSettings().keySet());
		}
		assertEquals(BronzemanSettingsManager.gameplayKeys(),
			BronzemanPreset.MAXIMUM.getSettings().keySet());
	}

	@Test
	public void presetsDoNotOverwriteProtectedPreferences()
	{
		for (BronzemanPreset preset : BronzemanPreset.values())
		{
			Map<String, String> settings = preset.getSettings();
			assertFalse(settings.containsKey("lootExemptNames"));
			assertFalse(settings.containsKey("acceptSharedUnlocks"));
			assertFalse(settings.containsKey("showLockedMenuOptions"));
			assertFalse(settings.containsKey("lockedItemMarkMode"));
			assertFalse(settings.containsKey("duelistCityMode"));
		}
	}

	@Test
	public void tcgLockedUsesTheAgreedAcquisitionAndSkillPolicy()
	{
		Map<String, String> settings = BronzemanPreset.TCG_LOCKED.getSettings();
		assertEquals(LockState.UNLOCKED.name(), settings.get("groundItemsMode"));
		assertEquals(BankingMode.FULL.name(), settings.get("bankingMode"));
		assertEquals(LockState.LOCKED.name(), settings.get("itemUsageMode"));
		assertEquals(LockState.LOCKED.name(), settings.get("grandExchangeMode"));
		assertEquals(CookingMode.INPUT_ONLY.name(), settings.get("cookingMode"));
		assertEquals(WoodcuttingMode.TOOL_ONLY.name(), settings.get("woodcuttingMode"));
		assertEquals(HunterMode.TOOLS_ONLY.name(), settings.get("hunterMode"));
	}

	@Test
	public void configDefaultsMatchTcgLockedPreset()
	{
		BronzemanTcgConfig defaults = new BronzemanTcgConfig() { };
		Map<String, String> expected = BronzemanPreset.TCG_LOCKED.getSettings();
		Map<String, String> actual = new LinkedHashMap<>();
		for (String key : expected.keySet())
		{
			BronzemanSettingRegistry.Definition definition =
				BronzemanSettingRegistry.require(key);
			actual.put(key, definition.serialize(definition.defaultValue(defaults)));
		}
		assertEquals(expected, actual);
	}

	@Test
	public void registryValuesRoundTripWithoutReflection()
	{
		BronzemanTcgConfig defaults = new BronzemanTcgConfig() { };
		for (BronzemanSettingRegistry.Definition definition
			: BronzemanSettingRegistry.all())
		{
			String serialized = definition.serialize(definition.defaultValue(defaults));
			assertTrue(definition.getKey(), definition.accepts(serialized));
			assertEquals(definition.getKey(), serialized,
				definition.serialize(definition.parse(serialized)));
			for (Enum<?> choice : definition.getEnumValues())
			{
				assertTrue(definition.getKey() + ":" + choice.name(),
					definition.accepts(choice.name()));
				assertEquals(choice, definition.parse(choice.name()));
			}
		}
	}

	@Test
	public void registryCoversEveryVisibleSidePanelSetting()
	{
		Set<String> expected = Set.of(
			"npcVisibilityMode", "groundItemsMode", "itemUsageMode", "foodSettingsMode",
			"bankingMode", "grandExchangeMode", "coinMode", "acceptSharedUnlocks",
			"lootExemptNames", "showLockedMenuOptions", "showBetaCollectionTab",
			"woodcuttingMode", "miningMode",
			"fishingMode", "cookingMode", "tinderboxMode",
			"smeltingMode", "smithingMode", "craftingMode", "restrictEnchanting",
			"requireCrushedGem", "fletchingMode", "herbloreMode", "runecraftingMode",
			"hunterMode", "restrictHunterRumours", "farmingRakeMode", "compostMode",
			"slayerMode", "restrictSlayerSuperiors", "thievingMode", "hamPickpocketingMode",
			"masterFarmerInsanity", "stallThievingMode", "sailingUpgradeMode",
			"restrictSalvaging", "lockedItemMarkMode", "tintLockedNpcs", "tintLockedGroundItems",
			"duelistCityMode", "lockedOutlineColor", "lockedOutlineWidth",
			"lockedOutlineFeather");
		Set<String> actual = new HashSet<>();
		for (BronzemanSettingRegistry.Definition definition
			: BronzemanSettingRegistry.all())
		{
			actual.add(definition.getKey());
		}
		assertEquals(expected, actual);
	}

	@Test
	public void sidePanelMetadataCoversTheExplicitRegistry()
	{
		Set<String> registryKeys = new HashSet<>();
		for (BronzemanSettingRegistry.Definition definition
			: BronzemanSettingRegistry.all())
		{
			registryKeys.add(definition.getKey());
		}

		Set<String> metadataKeys = new HashSet<>();
		for (SidePanelSettingMetadata.Entry entry : SidePanelSettingMetadata.all())
		{
			assertTrue(entry.key, metadataKeys.add(entry.key));
			assertFalse(entry.key, entry.name.trim().isEmpty());
			assertFalse(entry.key, entry.description.trim().isEmpty());
			assertTrue(entry.key, entry.min <= entry.max);
		}
		assertEquals(registryKeys, metadataKeys);
	}

	@Test
	public void importPreviewUsesDropdownLabelsInsteadOfEnumConstants()
	{
		BronzemanSettingRegistry.Definition herblore =
			BronzemanSettingRegistry.require("herbloreMode");
		assertEquals("Input Only", herblore.displaySerialized("INPUT_ONLY"));
		assertEquals("Input + Pots", herblore.displaySerialized("REQUIRE_UNFINISHED"));

		BronzemanSettingRegistry.Definition itemUsage =
			BronzemanSettingRegistry.require("itemUsageMode");
		assertEquals("Require Card", itemUsage.displaySerialized("LOCKED"));
		assertEquals("No Card Needed", itemUsage.displaySerialized("UNLOCKED"));
	}

	@Test
	public void everyPresetValueIsAcceptedByTheRegistry()
	{
		for (BronzemanPreset preset : BronzemanPreset.values())
		{
			for (Map.Entry<String, String> setting : preset.getSettings().entrySet())
			{
				assertTrue(setting.getKey(),
					BronzemanSettingRegistry.require(setting.getKey())
						.accepts(setting.getValue()));
			}
		}
	}

	@Test
	public void maximumEnablesExtremePolicies()
	{
		Map<String, String> settings = BronzemanPreset.MAXIMUM.getSettings();
		assertEquals(BankingMode.OFF.name(), settings.get("bankingMode"));
		assertEquals(SlayerMode.FULL.name(), settings.get("slayerMode"));
		assertEquals(ThievingMode.REQUIRE_ALL.name(), settings.get("thievingMode"));
		assertEquals("true", settings.get("restrictSlayerSuperiors"));
		assertEquals("true", settings.get("restrictHunterRumours"));
		assertEquals(HamPickpocketingMode.FULL_LOOT.name(),
			settings.get("hamPickpocketingMode"));
		assertEquals("true", settings.get("masterFarmerInsanity"));
	}

	@Test
	public void shareStringRoundTripsAndExcludesExemptions()
	{
		Map<String, String> settings = new LinkedHashMap<>(
			BronzemanPreset.TCG_LOCKED.getSettings());
		settings.put("lootExemptNames", "Coins, Rune*");

		String encoded = BronzemanSettingsManager.encodeSettings(GSON, settings);
		assertTrue(encoded.startsWith(BronzemanSettingsManager.EXPORT_PREFIX));
		String uncompressedJson = "{\"version\":1,\"settings\":"
			+ GSON.toJson(BronzemanPreset.TCG_LOCKED.getSettings()) + "}";
		String uncompressed = BronzemanSettingsManager.EXPORT_PREFIX
			+ Base64.getUrlEncoder().withoutPadding().encodeToString(
				uncompressedJson.getBytes(StandardCharsets.UTF_8));
		assertTrue(encoded.length() < uncompressed.length());
		Map<String, String> decoded = BronzemanSettingsManager.decodeSettings(GSON, encoded);
		assertEquals(BronzemanPreset.TCG_LOCKED.getSettings(), decoded);
		assertFalse(decoded.containsKey("lootExemptNames"));
	}

	@Test
	public void importIgnoresUnknownKeysButRejectsInvalidKnownValues()
	{
		Map<String, String> settings = new LinkedHashMap<>();
		settings.put("bankingMode", BankingMode.FULL.name());
		settings.put("futureSetting", "futureValue");
		String encoded = BronzemanSettingsManager.encodeSettings(GSON, settings);
		assertEquals(settings.get("bankingMode"),
			BronzemanSettingsManager.decodeSettings(GSON, encoded).get("bankingMode"));
		assertFalse(BronzemanSettingsManager.decodeSettings(GSON, encoded)
			.containsKey("futureSetting"));

		Map<String, String> invalid = new LinkedHashMap<>();
		invalid.put("bankingMode", "EVERYTHING");
		assertThrows(IllegalArgumentException.class,
			() -> BronzemanSettingsManager.encodeSettings(GSON, invalid));
	}
}
