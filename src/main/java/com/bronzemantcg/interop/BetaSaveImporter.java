package com.bronzemantcg.interop;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Reads only an explicitly selected save; never writes to OSRS TCG storage or contacts a server. */
public final class BetaSaveImporter
{
	private static final int MAX_FILE_BYTES = 16 * 1024 * 1024;
	private final Gson gson;

	public BetaSaveImporter(Gson gson)
	{
		this.gson = gson;
	}

	/** Run on a background worker, not the client thread or Swing event thread. */
	public Set<String> read(Path path) throws IOException
	{
		if (!Files.isRegularFile(path) || Files.size(path) > MAX_FILE_BYTES)
		{
			throw new IOException("Select a regular OSRS TCG save smaller than 16 MiB.");
		}
		try (InputStream input = Files.newInputStream(path))
		{
			byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
			if (bytes.length > MAX_FILE_BYTES)
			{
				throw new IOException("The selected save exceeds the size limit.");
			}
			return parse(new String(bytes, StandardCharsets.UTF_8).trim());
		}
	}

	Set<String> parse(String stored) throws IOException
	{
		String json = TcgStateDecoder.decode(stored);
		if (json.isEmpty())
		{
			throw new IOException("This is not a readable RLTCG_v2 or RLTCG_v3 save.");
		}
		try
		{
			validateJson(json);
			JsonObject root = gson.fromJson(json, JsonObject.class);
			JsonArray entries = array(root, "cardEntries");
			JsonArray instances = array(root, "cardInstances");
			if (entries != null && instances != null && entries.size() > 0 && instances.size() > 0)
			{
				throw new IllegalArgumentException("Conflicting collection formats.");
			}
			Set<String> names = new LinkedHashSet<>();
			if (entries != null && (entries.size() > 0 || instances == null))
			{
				for (JsonElement element : entries)
				{
					JsonObject entry = element.getAsJsonObject();
					String name = name(entry);
					JsonArray variants = array(entry, "variants");
					if (variants == null)
					{
						throw new IllegalArgumentException("Missing card variants.");
					}
					for (JsonElement value : variants)
					{
						JsonObject variant = value.getAsJsonObject();
						boolean beta = beta(variant, false);
						int quantity = 1;
						if (variant.has("quantity"))
						{
							JsonElement count = variant.get("quantity");
							if (!count.isJsonPrimitive() || !count.getAsJsonPrimitive().isNumber())
							{
								throw new IllegalArgumentException("Invalid quantity.");
							}
							quantity = count.getAsBigDecimal().intValueExact();
						}
						if (beta && quantity > 0)
						{
							names.add(name);
						}
					}
				}
			}
			else if (instances != null)
			{
				boolean metadata = false;
				for (JsonElement value : instances)
				{
					metadata |= value.getAsJsonObject().has("beta");
				}
				boolean legacy = stored.startsWith(TcgStateDecoder.STORAGE_PREFIX_V2) && !metadata;
				if (!legacy && !metadata && instances.size() > 0)
				{
					throw new IllegalArgumentException("This save has no Beta provenance.");
				}
				for (JsonElement value : instances)
				{
					JsonObject instance = value.getAsJsonObject();
					String name = name(instance);
					if (beta(instance, legacy))
					{
						names.add(name);
					}
				}
			}
			else
			{
				throw new IllegalArgumentException("No collection found.");
			}
			if (names.isEmpty() || names.size() > 20_000)
			{
				throw new IllegalArgumentException("No Beta cards found, or too many card names. "
					+ "To clear history, use Wipe saved Beta snapshot instead.");
			}
			return Collections.unmodifiableSet(names);
		}
		catch (RuntimeException ex)
		{
			throw new IOException("The save contains invalid or unsupported collection data. "
				+ "Nothing was imported.", ex);
		}
	}

	private static JsonArray array(JsonObject object, String key)
	{
		return !object.has(key) || object.get(key).isJsonNull()
			? null : object.getAsJsonArray(key);
	}

	private static String name(JsonObject object)
	{
		JsonElement value = object.get("cardName");
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
		{
			throw new IllegalArgumentException("Invalid card name.");
		}
		String name = value.getAsString().trim().toLowerCase(Locale.ROOT);
		if (name.isEmpty() || name.length() > 200 || name.chars().anyMatch(Character::isISOControl))
		{
			throw new IllegalArgumentException("Invalid card name.");
		}
		return name;
	}

	private static boolean beta(JsonObject object, boolean missingValue)
	{
		if (!object.has("beta"))
		{
			return missingValue;
		}
		JsonElement value = object.get("beta");
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
		{
			throw new IllegalArgumentException("Invalid Beta flag.");
		}
		return value.getAsBoolean();
	}

	/** Bound nesting before Gson constructs objects, and reject lenient/trailing JSON. */
	private static void validateJson(String json) throws IOException
	{
		try (JsonReader reader = new JsonReader(new StringReader(json)))
		{
			reader.setLenient(false);
			int depth = 0;
			int tokens = 0;
			java.util.Deque<Set<String>> objectKeys = new java.util.ArrayDeque<>();
			while (reader.peek() != JsonToken.END_DOCUMENT)
			{
				if (++tokens > 1_000_000 || depth > 32)
				{
					throw new IOException("Save structure exceeds safety limits.");
				}
				switch (reader.peek())
				{
					case BEGIN_OBJECT:
						reader.beginObject(); depth++;
						objectKeys.push(new java.util.HashSet<>());
						break;
					case END_OBJECT:
						reader.endObject(); depth--;
						objectKeys.pop();
						break;
					case BEGIN_ARRAY: reader.beginArray(); depth++; break;
					case END_ARRAY: reader.endArray(); depth--; break;
					case NAME:
						if (!objectKeys.element().add(reader.nextName()))
						{
							throw new IOException("Duplicate fields in save data.");
						}
						break;
					case BOOLEAN: reader.nextBoolean(); break;
					case NULL: reader.nextNull(); break;
					default: reader.nextString(); break;
				}
			}
		}
	}
}
