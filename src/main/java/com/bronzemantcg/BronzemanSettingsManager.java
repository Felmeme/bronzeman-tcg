package com.bronzemantcg;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigManager;

/** Applies built-in presets and encodes validated, plugin-only share strings. */
final class BronzemanSettingsManager
{
	static final String EXPORT_PREFIX = "BMTCG1:";
	private static final int FORMAT_VERSION = 1;
	private static final int MAX_IMPORT_LENGTH = 16_384;
	private static final int MAX_DECOMPRESSED_LENGTH = 65_536;
	private static final int MAX_TEXT_VALUE_LENGTH = 2_000;

	private static final Set<String> GAMEPLAY_KEYS;
	private static final Set<String> EXPORT_KEYS;
	private static final Map<String, Method> CONFIG_METHODS;

	static
	{
		LinkedHashSet<String> gameplay = new LinkedHashSet<>(
			BronzemanPreset.MAXIMUM.getSettings().keySet());
		GAMEPLAY_KEYS = Collections.unmodifiableSet(gameplay);

		// Personal exemptions are deliberately local and are never shared.
		EXPORT_KEYS = GAMEPLAY_KEYS;

		Map<String, Method> methods = new LinkedHashMap<>();
		for (Method method : BronzemanTcgConfig.class.getMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item != null && EXPORT_KEYS.contains(item.keyName()))
			{
				methods.put(item.keyName(), method);
			}
		}
		CONFIG_METHODS = Collections.unmodifiableMap(methods);
	}

	private final BronzemanTcgConfig config;
	private final ConfigManager configManager;
	private static final Gson GSON = new Gson();

	BronzemanSettingsManager(BronzemanTcgConfig config, ConfigManager configManager)
	{
		this.config = config;
		this.configManager = configManager;
	}

	void apply(BronzemanPreset preset)
	{
		apply(preset.getSettings());
	}

	void apply(Map<String, String> settings)
	{
		for (Map.Entry<String, String> entry : settings.entrySet())
		{
			if (EXPORT_KEYS.contains(entry.getKey()))
			{
				save(entry.getKey(), entry.getValue());
			}
		}
	}

	/** One write path for presets, imports and the compact side-panel controls. */
	void save(String key, Object value)
	{
		String serialized = serialize(value);
		configManager.setConfiguration(BronzemanTcgConfig.GROUP, key, serialized);
		String stored = configManager.getConfiguration(BronzemanTcgConfig.GROUP, key);
		if (!serialized.equals(stored))
		{
			throw new IllegalStateException("RuneLite did not save setting: " + key);
		}
	}

	/** Read the stored value directly; fall back to the config method only for defaults. */
	Object read(Method method)
	{
		ConfigItem item = method.getAnnotation(ConfigItem.class);
		String stored = configManager.getConfiguration(BronzemanTcgConfig.GROUP,
			item.keyName());
		if (stored != null)
		{
			return configManager.getConfiguration(BronzemanTcgConfig.GROUP,
				item.keyName(), method.getReturnType());
		}
		try
		{
			return method.invoke(config);
		}
		catch (IllegalAccessException | InvocationTargetException ex)
		{
			throw new IllegalStateException("Could not read setting " + item.keyName(), ex);
		}
	}

	String exportSettings()
	{
		Map<String, String> values = new LinkedHashMap<>();
		for (String key : EXPORT_KEYS)
		{
			Method method = CONFIG_METHODS.get(key);
			if (method == null)
			{
				continue;
			}
			values.put(key, serialize(read(method)));
		}
		return encodeSettings(values);
	}

	static String encodeSettings(Map<String, String> values)
	{
		Map<String, String> accepted = validateSettings(values);
		String json = GSON.toJson(new ExportData(FORMAT_VERSION, accepted));
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
		return decodeSettings(encoded);
	}

	static Map<String, String> decodeSettings(String encoded)
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
			data = GSON.fromJson(decompress(decoded), ExportData.class);
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
			Method method = CONFIG_METHODS.get(key);
			if (method == null || !isValid(method.getReturnType(), value))
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
			Method method = CONFIG_METHODS.get(entry.getKey());
			if (method == null)
			{
				continue;
			}
			String oldValue = serialize(read(method));
			if (!oldValue.equals(entry.getValue()))
			{
				ConfigItem item = method.getAnnotation(ConfigItem.class);
				changes.add(new Change(item.name(), oldValue, entry.getValue()));
			}
		}
		return Collections.unmodifiableList(changes);
	}

	static Set<String> gameplayKeys()
	{
		return GAMEPLAY_KEYS;
	}

	static Set<String> exportKeys()
	{
		return EXPORT_KEYS;
	}

	private static String serialize(Object value)
	{
		if (value instanceof Color)
		{
			return String.valueOf(((Color) value).getRGB());
		}
		return value instanceof Enum ? ((Enum<?>) value).name() : String.valueOf(value);
	}

	private static boolean isValid(Class<?> type, String value)
	{
		if (type == boolean.class || type == Boolean.class)
		{
			return "true".equals(value) || "false".equals(value);
		}
		if (type.isEnum())
		{
			for (Object constant : type.getEnumConstants())
			{
				if (((Enum<?>) constant).name().equals(value))
				{
					return true;
				}
			}
			return false;
		}
		return type == String.class && value.length() <= MAX_TEXT_VALUE_LENGTH;
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
