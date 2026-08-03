package com.bronzemantcg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;

public class BronzemanPresetTest
{
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
	public void configDefaultsMatchTcgLockedPreset() throws Exception
	{
		BronzemanTcgConfig defaults = new BronzemanTcgConfig() { };
		Map<String, String> expected = BronzemanPreset.TCG_LOCKED.getSettings();
		Map<String, String> actual = new LinkedHashMap<>();
		for (Method method : BronzemanTcgConfig.class.getMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item == null || !expected.containsKey(item.keyName()))
			{
				continue;
			}
			Object value = method.invoke(defaults);
			actual.put(item.keyName(), value instanceof Enum
				? ((Enum<?>) value).name() : String.valueOf(value));
		}
		assertEquals(expected, actual);
	}

	@Test
	public void legacyDefaultMigrationOnlyContainsChangedDefaults()
	{
		Map<String, String> current = BronzemanPreset.TCG_LOCKED.getSettings();
		for (Map.Entry<String, String> old : BronzemanPreset.getPreTcgLockedDefaults().entrySet())
		{
			assertTrue(current.containsKey(old.getKey()));
			assertFalse(old.getValue().equals(current.get(old.getKey())));
		}
	}

	@Test
	public void maximumEnablesExtremePolicies()
	{
		Map<String, String> settings = BronzemanPreset.MAXIMUM.getSettings();
		assertEquals(BankingMode.OFF.name(), settings.get("bankingMode"));
		assertEquals(SlayerMode.FULL.name(), settings.get("slayerMode"));
		assertEquals(ThievingMode.NPC_AND_LOOT.name(), settings.get("thievingMode"));
		assertEquals("true", settings.get("restrictSlayerSuperiors"));
		assertEquals("true", settings.get("restrictHunterRumours"));
		assertEquals("true", settings.get("hamFullLoot"));
		assertEquals("true", settings.get("masterFarmerInsanity"));
	}

	@Test
	public void shareStringRoundTripsAndExcludesExemptions()
	{
		Map<String, String> settings = new LinkedHashMap<>(
			BronzemanPreset.TCG_LOCKED.getSettings());
		settings.put("lootExemptNames", "Coins, Rune*");

		String encoded = BronzemanSettingsManager.encodeSettings(settings);
		assertTrue(encoded.startsWith(BronzemanSettingsManager.EXPORT_PREFIX));
		String uncompressedJson = "{\"version\":1,\"settings\":"
			+ new com.google.gson.Gson().toJson(BronzemanPreset.TCG_LOCKED.getSettings()) + "}";
		String uncompressed = BronzemanSettingsManager.EXPORT_PREFIX
			+ Base64.getUrlEncoder().withoutPadding().encodeToString(
				uncompressedJson.getBytes(StandardCharsets.UTF_8));
		assertTrue(encoded.length() < uncompressed.length());
		Map<String, String> decoded = BronzemanSettingsManager.decodeSettings(encoded);
		assertEquals(BronzemanPreset.TCG_LOCKED.getSettings(), decoded);
		assertFalse(decoded.containsKey("lootExemptNames"));
	}

	@Test
	public void importIgnoresUnknownKeysButRejectsInvalidKnownValues()
	{
		Map<String, String> settings = new LinkedHashMap<>();
		settings.put("bankingMode", BankingMode.FULL.name());
		settings.put("futureSetting", "futureValue");
		String encoded = BronzemanSettingsManager.encodeSettings(settings);
		assertEquals(settings.get("bankingMode"),
			BronzemanSettingsManager.decodeSettings(encoded).get("bankingMode"));
		assertFalse(BronzemanSettingsManager.decodeSettings(encoded)
			.containsKey("futureSetting"));

		Map<String, String> invalid = new LinkedHashMap<>();
		invalid.put("bankingMode", "EVERYTHING");
		assertThrows(IllegalArgumentException.class,
			() -> BronzemanSettingsManager.encodeSettings(invalid));
	}
}
