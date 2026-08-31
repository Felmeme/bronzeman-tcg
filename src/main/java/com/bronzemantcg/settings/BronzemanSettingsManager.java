package com.bronzemantcg.settings;

import com.bronzemantcg.BronzemanTcgConfig;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.runelite.client.config.ConfigManager;

/** Applies built-in presets and encodes validated, plugin-only share strings. */
final class BronzemanSettingsManager
{
	static final String EXPORT_PREFIX = "BMTCG1:";
	private static final int FORMAT_VERSION = 1;
	private static final int MAX_IMPORT_LENGTH = 16_384;
	private static final int MAX_DECOMPRESSED_LENGTH = 65_536;
	private static final String EXEMPT_LIST_KEY = "lootExemptNames";

	private static final Set<String> GAMEPLAY_KEYS;
	private static final Set<String> EXPORT_KEYS;

	static
	{
		GAMEPLAY_KEYS = Collections.unmodifiableSet(
			BronzemanPreset.MAXIMUM.getSettings().keySet());

		// Personal exemptions are deliberately local and are never shared.
		EXPORT_KEYS = GAMEPLAY_KEYS;
	}

	private final BronzemanTcgConfig config;
	private final ConfigManager configManager;
	private final Gson gson;

	BronzemanSettingsManager(Gson gson, BronzemanTcgConfig config, ConfigManager configManager)
	{
		this.gson = gson;
		this.config = config;
		this.configManager = configManager;
	}

	void apply(BronzemanPreset preset)
	{
		apply(preset.getSettings());
	}

	void apply(Map<String, String> settings)
	{
		String exemptList = configManager.getConfiguration(
			BronzemanTcgConfig.GROUP, EXEMPT_LIST_KEY);
		try
		{
			for (Map.Entry<String, String> entry : settings.entrySet())
			{
				if (EXPORT_KEYS.contains(entry.getKey()))
				{
					save(entry.getKey(), entry.getValue());
				}
			}
		}
		finally
		{
			restoreExemptList(exemptList);
		}
	}

	private void restoreExemptList(String expected)
	{
		String current = configManager.getConfiguration(
			BronzemanTcgConfig.GROUP, EXEMPT_LIST_KEY);
		if (Objects.equals(expected, current))
		{
			return;
		}
		if (expected == null)
		{
			configManager.unsetConfiguration(BronzemanTcgConfig.GROUP, EXEMPT_LIST_KEY);
		}
		else
		{
			configManager.setConfiguration(
				BronzemanTcgConfig.GROUP, EXEMPT_LIST_KEY, expected);
		}
	}

	/** One write path for presets, imports and the compact side-panel controls. */
	void save(String key, Object value)
	{
		BronzemanSettingRegistry.Definition definition =
			BronzemanSettingRegistry.require(key);
		String serialized = definition.serialize(value);
		if (!definition.accepts(serialized))
		{
			throw new IllegalArgumentException("Invalid value for setting: " + key);
		}
		configManager.setConfiguration(BronzemanTcgConfig.GROUP, key, serialized);
		String stored = configManager.getConfiguration(BronzemanTcgConfig.GROUP, key);
		if (!serialized.equals(stored))
		{
			throw new IllegalStateException("RuneLite did not save setting: " + key);
		}
	}

	/** Read the stored value directly; fall back to the direct config getter for defaults. */
	Object read(BronzemanSettingRegistry.Definition definition)
	{
		String stored = configManager.getConfiguration(BronzemanTcgConfig.GROUP,
			definition.getKey());
		if (stored != null)
		{
			return definition.parse(stored);
		}
		return definition.defaultValue(config);
	}

	String exportSettings()
	{
		Map<String, String> values = new LinkedHashMap<>();
		for (String key : EXPORT_KEYS)
		{
			BronzemanSettingRegistry.Definition definition =
				BronzemanSettingRegistry.require(key);
			values.put(key, definition.serialize(read(definition)));
		}
		return encodeSettings(gson, values);
	}

	static String encodeSettings(Gson gson, Map<String, String> values)
	{
		Map<String, String> accepted = validateSettings(values);
		String json = gson.toJson(new ExportData(FORMAT_VERSION, accepted));
		try
		{
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (GZIPOutputStream gzip = new GZIPOutputStream(bytes))
			{
				gzip.write(json.getBytes(StandardCharsets.UTF_8));
			}
			return EXPORT_PREFIX + Base64.getUrlEncoder().withoutPadding()
				.encodeToString(bytes.toByteArray());
		}
		catch (IOException ex)
		{
			throw new IllegalStateException("Could not encode settings.", ex);
		}
	}

	Map<String, String> importSettings(String encoded)
	{
		return decodeSettings(gson, encoded);
	}

	static Map<String, String> decodeSettings(Gson gson, String encoded)
	{
		if (encoded == null)
		{
			throw new IllegalArgumentException("No settings string was provided.");
		}
		String clean = encoded.trim();
		if (clean.length() > MAX_IMPORT_LENGTH)
		{
			throw new IllegalArgumentException("The settings string is too long.");
		}
		if (!clean.startsWith(EXPORT_PREFIX))
		{
			throw new IllegalArgumentException("This is not a Bronzeman TCG settings string.");
		}

		ExportData data;
		try
		{
			byte[] decoded = Base64.getUrlDecoder().decode(
				clean.substring(EXPORT_PREFIX.length()));
			data = gson.fromJson(decompress(decoded), ExportData.class);
		}
		catch (IllegalArgumentException | JsonParseException | IOException ex)
		{
			throw new IllegalArgumentException("The settings string is damaged or invalid.", ex);
		}
		if (data == null || data.version != FORMAT_VERSION || data.settings == null)
		{
			throw new IllegalArgumentException("This settings string version is not supported.");
		}

		return validateSettings(data.settings);
	}

	private static String decompress(byte[] compressed) throws IOException
	{
		try (GZIPInputStream gzip = new GZIPInputStream(
			new ByteArrayInputStream(compressed));
			ByteArrayOutputStream output = new ByteArrayOutputStream())
		{
			byte[] buffer = new byte[1_024];
			int total = 0;
			int read;
			while ((read = gzip.read(buffer)) != -1)
			{
				total += read;
				if (total > MAX_DECOMPRESSED_LENGTH)
				{
					throw new IOException("Decompressed settings are too large.");
				}
				output.write(buffer, 0, read);
			}
			return new String(output.toByteArray(), StandardCharsets.UTF_8);
		}
	}

	private static Map<String, String> validateSettings(Map<String, String> settings)
	{
		Map<String, String> accepted = new LinkedHashMap<>();
		for (String key : EXPORT_KEYS)
		{
			String value = settings.get(key);
			if (value == null)
			{
				continue;
			}
			BronzemanSettingRegistry.Definition definition =
				BronzemanSettingRegistry.find(key);
			if (definition == null || !definition.accepts(value))
			{
				throw new IllegalArgumentException("Invalid value for setting: " + key);
			}
			accepted.put(key, value);
		}
		if (accepted.isEmpty())
		{
			throw new IllegalArgumentException("The string contains no supported settings.");
		}
		return Collections.unmodifiableMap(accepted);
	}

	List<Change> changes(Map<String, String> settings)
	{
		List<Change> changes = new ArrayList<>();
		for (Map.Entry<String, String> entry : settings.entrySet())
		{
			BronzemanSettingRegistry.Definition definition =
				BronzemanSettingRegistry.find(entry.getKey());
			if (definition == null)
			{
				continue;
			}
			String oldValue = definition.serialize(read(definition));
			if (!oldValue.equals(entry.getValue()))
			{
				changes.add(new Change(
					SidePanelSettingMetadata.require(entry.getKey()).name,
					definition.displaySerialized(oldValue),
					definition.displaySerialized(entry.getValue())));
			}
		}
		return Collections.unmodifiableList(changes);
	}

	static Set<String> gameplayKeys()
	{
		return GAMEPLAY_KEYS;
	}

	static final class Change
	{
		final String name;
		final String oldValue;
		final String newValue;

		private Change(String name, String oldValue, String newValue)
		{
			this.name = name;
			this.oldValue = oldValue;
			this.newValue = newValue;
		}
	}

	private static final class ExportData
	{
		private int version;
		private Map<String, String> settings;

		@SuppressWarnings("unused")
		private ExportData()
		{
		}

		private ExportData(int version, Map<String, String> settings)
		{
			this.version = version;
			this.settings = settings;
		}
	}
}
