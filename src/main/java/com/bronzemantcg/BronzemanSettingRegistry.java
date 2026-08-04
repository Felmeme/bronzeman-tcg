package com.bronzemantcg;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit, reviewable description of every player-facing setting used by the compact panel.
 * This registry supplies direct getters and value validation without Java reflection.
 * Side-panel labels, descriptions, sections and ranges live in
 * {@link SidePanelSettingMetadata}; RuneLite's normal config panel still uses the annotations
 * on {@link BronzemanTcgConfig}.
 */
final class BronzemanSettingRegistry
{
	private static final List<Definition> ALL;
	private static final Map<String, Definition> BY_KEY;

	static
	{
		List<Definition> definitions = Arrays.asList(
			enumSetting("npcVisibilityMode", BronzemanTcgConfig::npcVisibilityMode,
				NpcVisibilityMode.values()),
			enumSetting("groundItemsMode", BronzemanTcgConfig::groundItemsMode,
				LockState.values()),
			enumSetting("itemUsageMode", BronzemanTcgConfig::itemUsageMode,
				LockState.values()),
			enumSetting("foodSettingsMode", BronzemanTcgConfig::foodSettingsMode,
				FoodSettingsMode.values()),
			enumSetting("bankingMode", BronzemanTcgConfig::bankingMode,
				BankingMode.values()),
			enumSetting("grandExchangeMode", BronzemanTcgConfig::grandExchangeMode,
				LockState.values()),
			enumSetting("coinMode", BronzemanTcgConfig::coinMode, LockState.values()),
			booleanSetting("acceptSharedUnlocks", BronzemanTcgConfig::acceptSharedUnlocks),
			stringSetting("lootExemptNames", BronzemanTcgConfig::lootExemptNames),
			booleanSetting("showLockedMenuOptions", BronzemanTcgConfig::showLockedMenuOptions),

			enumSetting("woodcuttingMode", BronzemanTcgConfig::woodcuttingMode,
				WoodcuttingMode.values()),
			enumSetting("miningMode", BronzemanTcgConfig::miningMode, MiningMode.values()),
			enumSetting("fishingMode", BronzemanTcgConfig::fishingMode,
				FishingRestrictionMode.values()),
			enumSetting("cookingMode", BronzemanTcgConfig::cookingMode, CookingMode.values()),
			enumSetting("burntFoodMode", BronzemanTcgConfig::burntFoodMode,
				BurntFoodMode.values()),
			enumSetting("tinderboxMode", BronzemanTcgConfig::tinderboxMode,
				CardRequirement.values()),
			enumSetting("smeltingMode", BronzemanTcgConfig::smeltingMode, SmeltingMode.values()),
			enumSetting("smithingMode", BronzemanTcgConfig::smithingMode, SmithingMode.values()),
			enumSetting("craftingMode", BronzemanTcgConfig::craftingMode, CraftingMode.values()),
			booleanSetting("restrictEnchanting", BronzemanTcgConfig::restrictEnchanting),
			booleanSetting("requireCrushedGem", BronzemanTcgConfig::requireCrushedGem),
			enumSetting("fletchingMode", BronzemanTcgConfig::fletchingMode,
				FletchingMode.values()),
			enumSetting("herbloreMode", BronzemanTcgConfig::herbloreMode, HerbloreMode.values()),
			enumSetting("runecraftingMode", BronzemanTcgConfig::runecraftingMode,
				RunecraftingMode.values()),
			enumSetting("hunterMode", BronzemanTcgConfig::hunterMode, HunterMode.values()),
			booleanSetting("restrictHunterRumours", BronzemanTcgConfig::restrictHunterRumours),
			enumSetting("farmingRakeMode", BronzemanTcgConfig::farmingRakeMode,
				FarmingRakeMode.values()),
			enumSetting("compostMode", BronzemanTcgConfig::compostMode,
				CardRequirement.values()),
			enumSetting("slayerMode", BronzemanTcgConfig::slayerMode, SlayerMode.values()),
			booleanSetting("restrictSlayerSuperiors", BronzemanTcgConfig::restrictSlayerSuperiors),
			enumSetting("thievingMode", BronzemanTcgConfig::thievingMode, ThievingMode.values()),
			booleanSetting("hamFullLoot", BronzemanTcgConfig::hamFullLoot),
			booleanSetting("masterFarmerInsanity", BronzemanTcgConfig::masterFarmerInsanity),
			enumSetting("stallThievingMode", BronzemanTcgConfig::stallThievingMode,
				StallThievingMode.values()),
			enumSetting("sailingUpgradeMode", BronzemanTcgConfig::sailingUpgradeMode,
				SailingUpgradeMode.values()),
			booleanSetting("restrictSalvaging", BronzemanTcgConfig::restrictSalvaging),

			enumSetting("lockedItemMarkMode", BronzemanTcgConfig::lockedItemMarkMode,
				LockedItemMarkMode.values()),
			booleanSetting("tintLockedNpcs", BronzemanTcgConfig::tintLockedNpcs),
			booleanSetting("tintLockedGroundItems",
				BronzemanTcgConfig::tintLockedGroundItems),
			booleanSetting("duelistCityMode", BronzemanTcgConfig::duelistCityMode),
			colorSetting("lockedOutlineColor", BronzemanTcgConfig::lockedOutlineColor),
			integerSetting("lockedOutlineWidth", BronzemanTcgConfig::lockedOutlineWidth),
			integerSetting("lockedOutlineFeather", BronzemanTcgConfig::lockedOutlineFeather));

		Map<String, Definition> byKey = new LinkedHashMap<>();
		for (Definition definition : definitions)
		{
			if (byKey.put(definition.key, definition) != null)
			{
				throw new IllegalStateException("Duplicate setting definition: " + definition.key);
			}
		}
		ALL = Collections.unmodifiableList(new ArrayList<>(definitions));
		BY_KEY = Collections.unmodifiableMap(byKey);
	}

	private BronzemanSettingRegistry()
	{
	}

	static List<Definition> all()
	{
		return ALL;
	}

	static Definition find(String key)
	{
		return BY_KEY.get(key);
	}

	static Definition require(String key)
	{
		Definition definition = find(key);
		if (definition == null)
		{
			throw new IllegalArgumentException("Unsupported setting: " + key);
		}
		return definition;
	}

	private static Definition booleanSetting(String key, Reader reader)
	{
		return new Definition(key, Kind.BOOLEAN, reader, Collections.emptyList());
	}

	private static Definition stringSetting(String key, Reader reader)
	{
		return new Definition(key, Kind.STRING, reader, Collections.emptyList());
	}

	private static Definition integerSetting(String key, Reader reader)
	{
		return new Definition(key, Kind.INTEGER, reader, Collections.emptyList());
	}

	private static Definition colorSetting(String key, Reader reader)
	{
		return new Definition(key, Kind.COLOR, reader, Collections.emptyList());
	}

	private static Definition enumSetting(String key, Reader reader, Enum<?>[] values)
	{
		return new Definition(key, Kind.ENUM, reader,
			Collections.unmodifiableList(Arrays.asList(values)));
	}

	interface Reader
	{
		Object read(BronzemanTcgConfig config);
	}

	enum Kind
	{
		BOOLEAN,
		ENUM,
		STRING,
		INTEGER,
		COLOR
	}

	static final class Definition
	{
		private final String key;
		private final Kind kind;
		private final Reader reader;
		private final List<Enum<?>> enumValues;

		private Definition(String key, Kind kind, Reader reader, List<Enum<?>> enumValues)
		{
			this.key = key;
			this.kind = kind;
			this.reader = reader;
			this.enumValues = enumValues;
		}

		String getKey()
		{
			return key;
		}

		Kind getKind()
		{
			return kind;
		}

		List<Enum<?>> getEnumValues()
		{
			return enumValues;
		}

		Object defaultValue(BronzemanTcgConfig config)
		{
			return reader.read(config);
		}

		Object parse(String value)
		{
			switch (kind)
			{
				case BOOLEAN:
					if (!"true".equals(value) && !"false".equals(value))
					{
						throw new IllegalArgumentException("Invalid boolean");
					}
					return Boolean.valueOf(value);
				case ENUM:
					for (Enum<?> choice : enumValues)
					{
						if (choice.name().equals(value))
						{
							return choice;
						}
					}
					throw new IllegalArgumentException("Invalid enum value");
				case INTEGER:
					return Integer.valueOf(value);
				case COLOR:
					return new Color(Integer.parseInt(value), true);
				case STRING:
				default:
					return value;
			}
		}

		boolean accepts(String value)
		{
			if (value == null || kind == Kind.STRING && value.length() > 2_000)
			{
				return false;
			}
			try
			{
				parse(value);
				return true;
			}
			catch (IllegalArgumentException ex)
			{
				return false;
			}
		}

		String serialize(Object value)
		{
			if (value instanceof Color)
			{
				return String.valueOf(((Color) value).getRGB());
			}
			return value instanceof Enum
				? ((Enum<?>) value).name() : String.valueOf(value);
		}

		String displaySerialized(String value)
		{
			Object parsed = parse(value);
			return parsed instanceof Color
				? String.format("#%08X", ((Color) parsed).getRGB())
				: String.valueOf(parsed);
		}
	}
}
