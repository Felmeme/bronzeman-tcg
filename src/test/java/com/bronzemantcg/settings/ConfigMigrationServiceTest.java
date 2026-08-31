package com.bronzemantcg.settings;

import com.bronzemantcg.catalog.CardRequirement;
import com.bronzemantcg.restriction.NpcVisibilityMode;
import com.bronzemantcg.restriction.BankingMode;
import com.bronzemantcg.restriction.CookingMode;
import com.bronzemantcg.restriction.CraftingMode;
import com.bronzemantcg.restriction.FishingRestrictionMode;
import com.bronzemantcg.restriction.FletchingMode;
import com.bronzemantcg.restriction.HerbloreMode;
import com.bronzemantcg.restriction.HunterMode;
import com.bronzemantcg.restriction.LockState;
import com.bronzemantcg.restriction.MiningMode;
import com.bronzemantcg.restriction.SlayerMode;
import com.bronzemantcg.restriction.SmeltingMode;
import com.bronzemantcg.restriction.SmithingMode;
import com.bronzemantcg.restriction.ThievingPolicy.HamPickpocketingMode;
import com.bronzemantcg.restriction.ThievingPolicy.ThievingMode;
import com.bronzemantcg.restriction.WoodcuttingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ConfigMigrationServiceTest
{
	@Test
	public void freshInstallIsMarkedPendingBeforeMigrationsExist()
	{
		MemoryConfig config = new MemoryConfig();
		ConfigMigrationService service = new ConfigMigrationService(config);

		assertTrue(service.preparePresetOnboarding());
		assertEquals("true", config.get("presetOnboardingPending"));
		assertEquals(List.of("set:presetOnboardingPending=true"), config.writes);
	}

	@Test
	public void existingMigrationMarkerSkipsPresetOnboarding()
	{
		for (String marker : List.of("npcVisibilityMigrated", "exemptListMigrated",
			"fishingModeMigrated", "hunterModeMigrated"))
		{
			MemoryConfig config = new MemoryConfig().with(marker, "false");
			assertFalse(new ConfigMigrationService(config).preparePresetOnboarding());
			assertEquals("true", config.get("presetOnboardingComplete"));
			assertNull(config.get("presetOnboardingPending"));
		}
	}

	@Test
	public void completedAndPendingOnboardingStatesAreStable()
	{
		MemoryConfig complete = new MemoryConfig().with("presetOnboardingComplete", true);
		assertFalse(new ConfigMigrationService(complete).preparePresetOnboarding());
		assertTrue(complete.writes.isEmpty());

		MemoryConfig pending = new MemoryConfig().with("presetOnboardingPending", true);
		assertTrue(new ConfigMigrationService(pending).preparePresetOnboarding());
		assertTrue(pending.writes.isEmpty());
	}

	@Test
	public void legacyDefaultsAreExactAndImmutable()
	{
		Map<String, String> expected = new LinkedHashMap<>();
		expected.put("groundItemsMode", "LOCKED");
		expected.put("bankingMode", "DEPOSIT_ONLY");
		expected.put("woodcuttingMode", "LOGS_ONLY");
		expected.put("miningMode", "CARD_REQUIRED");
		expected.put("fishingMode", "CARD_REQUIRED");
		expected.put("cookingMode", "INPUT_OUTPUT");
		expected.put("smeltingMode", "BOTH");
		expected.put("smithingMode", "BOTH");
		expected.put("craftingMode", "BOTH");
		expected.put("restrictEnchanting", "true");
		expected.put("requireCrushedGem", "true");
		expected.put("fletchingMode", "PRODUCT_AND_MATERIALS");
		expected.put("herbloreMode", "REQUIRE_ALL");
		expected.put("runecraftingMode", "TALISMAN_RUNES");
		expected.put("farmingRakeMode", "BOTH");
		expected.put("compostMode", "CARD_REQUIRED");
		expected.put("slayerMode", "OFF");

		assertEquals(expected, ConfigMigrationService.preTcgLockedDefaults());
		try
		{
			ConfigMigrationService.preTcgLockedDefaults().put("extra", "value");
		}
		catch (UnsupportedOperationException expectedException)
		{
			return;
		}
		throw new AssertionError("Legacy defaults must be immutable");
	}

	@Test
	public void upgradeWritesOnlyAbsentLegacyDefaults()
	{
		MemoryConfig config = allOneShotMarkers().with("bankingMode", BankingMode.OFF.name());
		new ConfigMigrationService(config).migrateLegacySettings(false);

		assertEquals(BankingMode.OFF.name(), config.get("bankingMode"));
		assertEquals(LockState.LOCKED.name(), config.get("groundItemsMode"));
		assertEquals("true", config.get("tcgLockedDefaultsMigrated"));
	}

	@Test
	public void freshInstallOnlyMarksLegacyDefaultsComplete()
	{
		MemoryConfig config = allOneShotMarkers();
		new ConfigMigrationService(config).migrateLegacySettings(true);

		assertEquals("true", config.get("tcgLockedDefaultsMigrated"));
		assertNull(config.get("groundItemsMode"));
		assertNull(config.get("bankingMode"));
	}

	@Test
	public void hamModesMigrateFromTheFormerSharedControls()
	{
		assertHamMigration("OFF", false, HamPickpocketingMode.OFF);
		assertHamMigration("NPC_CARD_ONLY", false,
			HamPickpocketingMode.MEMBER_CARD);
		assertHamMigration("COINS_POUCH", false,
			HamPickpocketingMode.MEMBER_CARD);
		assertHamMigration("NPC_ONLY", false,
			HamPickpocketingMode.MEMBER_CARD);
		assertHamMigration("NPC_AND_LOOT", false,
			HamPickpocketingMode.MEMBER_AND_OUTFIT);
		assertHamMigration("NPC_AND_LOOT", true,
			HamPickpocketingMode.FULL_LOOT);
	}

	@Test
	public void betaCoinPouchModesMigrateToTheirV1CoinsEquivalents()
	{
		assertThievingMigration("COINS_POUCH", ThievingMode.COINS);
		assertThievingMigration("NPC_ONLY", ThievingMode.COINS_NPC);
		assertThievingMigration("NPC_AND_LOOT", ThievingMode.REQUIRE_ALL);
		assertThievingMigration(ThievingMode.OFF.name(), ThievingMode.OFF);
		assertThievingMigration(ThievingMode.NPC_CARD_ONLY.name(),
			ThievingMode.NPC_CARD_ONLY);
	}

	@Test
	public void npcAndGeneralSettingsKeepTheirEstablishedMappings()
	{
		MemoryConfig config = new MemoryConfig()
			.with("exemptListMigrated", true)
			.with("fishingModeMigrated", true)
			.with("hunterModeMigrated", true)
			.with("tcgLockedDefaultsMigrated", true)
			.with("hideLockedEntities", true)
			.with("restrictAttacks", false)
			.with("masterFarmerMode", "INSANITY")
			.with("restrictLoot", false)
			.with("restrictPotionDrinking", false)
			.with("restrictBuying", false)
			.with("exemptCoins", false)
			.with("forcedDropMode", "DROP")
			.with("allowCotsGuards", true);

		new ConfigMigrationService(config).migrateLegacySettings(false);

		assertEquals(NpcVisibilityMode.HIDE.name(), config.get("npcVisibilityMode"));
		assertEquals("true", config.get("masterFarmerInsanity"));
		assertEquals(LockState.UNLOCKED.name(), config.get("groundItemsMode"));
		assertEquals(LockState.UNLOCKED.name(), config.get("itemUsageMode"));
		assertEquals(LockState.UNLOCKED.name(), config.get("grandExchangeMode"));
		assertEquals(LockState.LOCKED.name(), config.get("coinMode"));
		assertEquals(BankingMode.OFF.name(), config.get("bankingMode"));
		for (String retired : List.of("restrictAttacks", "restrictSpellCasts",
			"hideLockedEntities", "masterFarmerMode", "restrictLoot",
			"restrictEquipping", "restrictPotionDrinking", "restrictItemUsage",
			"restrictBuying", "forcedDropMode", "exemptCoins", "hideLockedOptions",
			"allowCotsGuards"))
		{
			assertNull(retired, config.get(retired));
		}
	}

	@Test
	public void npcVisibilityUsesAttackOptOutAndCurrentDefault()
	{
		MemoryConfig attackOptOut = oneShotExceptNpc().with("restrictAttacks", false);
		new ConfigMigrationService(attackOptOut).migrateLegacySettings(false);
		assertEquals(NpcVisibilityMode.OFF.name(), attackOptOut.get("npcVisibilityMode"));

		MemoryConfig normal = oneShotExceptNpc();
		new ConfigMigrationService(normal).migrateLegacySettings(false);
		assertEquals(NpcVisibilityMode.PREVENT_COMBAT.name(), normal.get("npcVisibilityMode"));
	}

	@Test
	public void retiredSkillValuesMapWithoutEscalatingLenientChoices()
	{
		MemoryConfig config = allOneShotMarkers()
			.with("restrictFletching", false)
			.with("restrictMining", false)
			.with("restrictWoodcutting", false)
			.with("restrictCrafting", false)
			.with("restrictCompost", false)
			.with("restrictCooking", false)
			.with("restrictBurntFood", true)
			.with("burntFoodMode", "REQUIRE_CARD")
			.with("restrictHerblore", false)
			.with("firemakingMode", "JUST_LOGS");

		new ConfigMigrationService(config).migrateLegacySettings(false);

		assertEquals(FletchingMode.OFF.name(), config.get("fletchingMode"));
		assertEquals(MiningMode.OFF.name(), config.get("miningMode"));
		assertEquals(WoodcuttingMode.OFF.name(), config.get("woodcuttingMode"));
		assertEquals(CraftingMode.OFF.name(), config.get("craftingMode"));
		assertEquals(CardRequirement.NO_CARD.name(), config.get("compostMode"));
		assertEquals(CookingMode.OFF.name(), config.get("cookingMode"));
		assertNull(config.get("restrictBurntFood"));
		assertNull(config.get("burntFoodMode"));
		assertEquals(HerbloreMode.OFF.name(), config.get("herbloreMode"));
		assertEquals(CardRequirement.NO_CARD.name(), config.get("tinderboxMode"));
	}

	@Test
	public void retiredEnumValuesMapToTheirReplacementModes()
	{
		MemoryConfig config = allOneShotMarkers()
			.with("fletchingMode", "PRODUCT")
			.with("craftingMode", "OUTPUT_ONLY")
			.with("smeltingMode", "BARS")
			.with("smithingMode", "ITEMS")
			.with("woodcuttingMode", "CARD_REQUIRED")
			.with("miningMode", "ORE_ONLY")
			.with("firemakingMode", "BOTH")
			.with("restrictSlayerMasters", true)
			.with("restrictSlayerMonsters", true);

		new ConfigMigrationService(config).migrateLegacySettings(false);

		assertEquals(FletchingMode.INPUT_ONLY.name(), config.get("fletchingMode"));
		assertEquals(CraftingMode.INPUT_ONLY.name(), config.get("craftingMode"));
		assertEquals(SmeltingMode.ORE.name(), config.get("smeltingMode"));
		assertEquals(SmithingMode.BARS.name(), config.get("smithingMode"));
		assertEquals(WoodcuttingMode.LOGS_ONLY.name(), config.get("woodcuttingMode"));
		assertEquals(MiningMode.CARD_REQUIRED.name(), config.get("miningMode"));
		assertEquals(CardRequirement.CARD_REQUIRED.name(), config.get("tinderboxMode"));
		assertEquals(SlayerMode.FULL.name(), config.get("slayerMode"));
	}

	@Test
	public void fishingAndExemptListMarkBeforeTransforming()
	{
		MemoryConfig config = new MemoryConfig()
			.with("npcVisibilityMigrated", true)
			.with("hunterModeMigrated", true)
			.with("tcgLockedDefaultsMigrated", true)
			.with("lootExemptNames", " Coins, Rune*, coins , Lobster ")
			.with("fishingMode", "REQUIRE_ALL");

		new ConfigMigrationService(config).migrateLegacySettings(false);

		assertEquals("set:exemptListMigrated=true", config.writes.get(0));
		assertTrue(config.writes.indexOf("set:fishingModeMigrated=true")
			< config.writes.indexOf("set:fishingMode=ALL_CATCHES"));
		assertEquals("true", config.get("exemptCoins"));
		assertEquals("Rune*, Lobster", config.get("lootExemptNames"));
		assertEquals(FishingRestrictionMode.ALL_CATCHES.name(), config.get("fishingMode"));
	}

	@Test
	public void fishingAnyOfAndHunterExtremesRetainTheirMeaning()
	{
		MemoryConfig anyOf = new MemoryConfig()
			.with("exemptListMigrated", true)
			.with("npcVisibilityMigrated", true)
			.with("tcgLockedDefaultsMigrated", true)
			.with("fishingMode", "ANY_OF")
			.with("hunterBirdsMode", "OFF")
			.with("butterflyMode", "OFF")
			.with("implingMode", "OFF")
			.with("restrictChins", false)
			.with("salamanderMode", "OFF")
			.with("pitfallMode", "OFF");

		new ConfigMigrationService(anyOf).migrateLegacySettings(false);
		assertEquals(FishingRestrictionMode.CARD_REQUIRED.name(), anyOf.get("fishingMode"));
		assertEquals(HunterMode.OFF.name(), anyOf.get("hunterMode"));

		MemoryConfig strict = allOneShotMarkers();
		strict.values.remove("hunterModeMigrated");
		strict.with("salamanderMode", "ITEMS_SALLY");
		new ConfigMigrationService(strict).migrateLegacySettings(false);
		assertEquals(HunterMode.ALL_CARDS.name(), strict.get("hunterMode"));
	}

	@Test
	public void markersMakeGuardedMigrationsIdempotentButRetiredKeysAreAlwaysRemoved()
	{
		MemoryConfig config = new MemoryConfig()
			.with("exemptListMigrated", true)
			.with("npcVisibilityMigrated", true)
			.with("fishingModeMigrated", true)
			.with("hunterModeMigrated", true)
			.with("tcgLockedDefaultsMigrated", true)
			.with("lootExemptNames", "Coins, Lobster")
			.with("fishingMode", "ANY_OF")
			.with("chatFeedback", false)
			.with("allowInLms", false);

		new ConfigMigrationService(config).migrateLegacySettings(false);

		assertEquals("Coins, Lobster", config.get("lootExemptNames"));
		assertEquals("ANY_OF", config.get("fishingMode"));
		assertNull(config.get("chatFeedback"));
		assertNull(config.get("allowInLms"));
		assertFalse(config.writes.contains("set:exemptListMigrated=true"));
		assertFalse(config.writes.contains("set:fishingModeMigrated=true"));
	}

	private static MemoryConfig allOneShotMarkers()
	{
		return new MemoryConfig()
			.with("exemptListMigrated", true)
			.with("npcVisibilityMigrated", true)
			.with("fishingModeMigrated", true)
			.with("hunterModeMigrated", true)
			.with("hamPickpocketingModeMigrated", true)
			.with("thievingModeV1Migrated", true);
	}

	private static MemoryConfig oneShotExceptNpc()
	{
		return new MemoryConfig()
			.with("exemptListMigrated", true)
			.with("fishingModeMigrated", true)
			.with("hunterModeMigrated", true)
			.with("hamPickpocketingModeMigrated", true)
			.with("thievingModeV1Migrated", true)
			.with("tcgLockedDefaultsMigrated", true);
	}

	private static void assertHamMigration(String oldMode, boolean oldFullLoot,
		HamPickpocketingMode expected)
	{
		MemoryConfig config = allOneShotMarkers()
			.with("tcgLockedDefaultsMigrated", true)
			.with("thievingMode", oldMode)
			.with("hamFullLoot", oldFullLoot);
		config.values.remove("hamPickpocketingModeMigrated");

		new ConfigMigrationService(config).migrateLegacySettings(false);

		assertEquals(expected.name(), config.get("hamPickpocketingMode"));
		assertEquals("true", config.get("hamPickpocketingModeMigrated"));
		assertNull(config.get("hamFullLoot"));
	}

	private static void assertThievingMigration(String storedMode, ThievingMode expected)
	{
		MemoryConfig config = allOneShotMarkers()
			.with("tcgLockedDefaultsMigrated", true)
			.with("thievingMode", storedMode);
		config.values.remove("thievingModeV1Migrated");

		new ConfigMigrationService(config).migrateLegacySettings(false);

		assertEquals(expected.name(), config.get("thievingMode"));
		assertEquals("true", config.get("thievingModeV1Migrated"));
	}

	private static final class MemoryConfig implements ConfigMigrationService.ConfigAccess
	{
		private final Map<String, String> values = new LinkedHashMap<>();
		private final List<String> writes = new ArrayList<>();

		private MemoryConfig with(String key, Object value)
		{
			values.put(key, String.valueOf(value));
			return this;
		}

		@Override
		public String get(String key)
		{
			return values.get(key);
		}

		@Override
		public void set(String key, Object value)
		{
			String stored = String.valueOf(value);
			values.put(key, stored);
			writes.add("set:" + key + "=" + stored);
		}

		@Override
		public void unset(String key)
		{
			values.remove(key);
			writes.add("unset:" + key);
		}
	}
}
