package com.bronzemantcg;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Processing-skill recipes (firemaking, smithing, crafting, enchanting, fletching, herblore)
 * loaded from resources/recipe_nodes.json. Each recipe declares input card groups (any one
 * card per group, all groups needed) and an output card; the per-skill config mode decides
 * whether inputs, output, or both are enforced at click time.
 *
 * Trigger kinds and their lookup keys:
 *  - item-on-item:  (used item name, target item name) e.g. ("Tinderbox", "oak logs")
 *  - item-on-object:(used item name, target object)    e.g. ("Iron ore", "furnace")
 *  - interface:     (product name, "*") - make-X interface clicks only carry the product
 *  - spell-on-item: keyed by the TARGET item alone ((item, "*")) since each enchantable
 *    item has exactly one enchant spell; spell-name strings vary by client widget.
 */
@Slf4j
@Singleton
public class RecipeCatalog
{
	public static final String KIND_ITEM_ON_ITEM = "item-on-item";
	public static final String KIND_ITEM_ON_OBJECT = "item-on-object";
	public static final String KIND_INTERFACE = "interface";
	public static final String KIND_SPELL_ON_ITEM = "spell-on-item";
	public static final String ANY_TARGET = "*";

	private static final Set<String> EVENT_LOGS = new HashSet<>(Arrays.asList(
		"blue logs", "green logs", "red logs", "purple logs", "white logs"));

	private Map<String, Recipe> recipes = Collections.emptyMap();

	@Inject
	public RecipeCatalog(Gson gson)
	{
		load(gson);
	}

	/** @return the recipe for this interaction, or null if unrestricted. */
	public Recipe find(String kind, String name, String target)
	{
		if (name == null)
		{
			return null;
		}
		String nameKey = CardNames.stripDoseSuffix(name.trim().toLowerCase(Locale.ROOT));
		String targetKey = target == null ? ANY_TARGET
			: CardNames.stripDoseSuffix(target.trim().toLowerCase(Locale.ROOT));
		Recipe recipe = recipes.get(key(kind, nameKey, targetKey));
		if (recipe == null && !ANY_TARGET.equals(targetKey))
		{
			recipe = recipes.get(key(kind, nameKey, ANY_TARGET));
		}
		return recipe;
	}

	public int size()
	{
		return recipes.size();
	}

	/**
	 * Unmodifiable view of all recipes keyed "kind|name|target" (lowercased), for the side
	 * panel's skills guide. The same Recipe instance appears under multiple lookup keys.
	 */
	public Map<String, Recipe> getRecipeEntries()
	{
		return recipes;
	}

	private static String key(String kind, String nameLower, String targetLower)
	{
		return kind + '|' + nameLower + '|' + targetLower;
	}

	private void load(Gson gson)
	{
		try (InputStream stream = getClass().getResourceAsStream("/recipe_nodes.json"))
		{
			if (stream == null)
			{
				log.warn("recipe_nodes.json missing from classpath; recipe restrictions disabled.");
				return;
			}
			Snapshot snapshot = gson.fromJson(
				new InputStreamReader(stream, StandardCharsets.UTF_8), Snapshot.class);
			if (snapshot == null || snapshot.recipes == null)
			{
				return;
			}
			Map<String, Recipe> map = new HashMap<>();
			for (RecipeDto dto : snapshot.recipes)
			{
				if (dto == null || dto.category == null || dto.trigger == null
					|| dto.trigger.kind == null || dto.trigger.name == null)
				{
					continue;
				}
				List<ResourceNodeCatalog.CardGroup> inputs = new ArrayList<>();
				if (dto.inputs != null)
				{
					for (List<String> group : dto.inputs)
					{
						ResourceNodeCatalog.CardGroup g = ResourceNodeCatalog.CardGroup.of(group, null);
						if (g != null)
						{
							inputs.add(g);
						}
					}
				}
				String kind = dto.trigger.kind.trim().toLowerCase(Locale.ROOT);
				String name = dto.trigger.name.trim().toLowerCase(Locale.ROOT);
				List<String> targets = dto.trigger.targets == null || dto.trigger.targets.isEmpty()
					? Collections.singletonList(ANY_TARGET) : dto.trigger.targets;

				for (String target : targets)
				{
					String targetKey = target == null ? ANY_TARGET : target.trim().toLowerCase(Locale.ROOT);
					boolean eventLog = "firemaking".equals(dto.category) && EVENT_LOGS.contains(targetKey);
					Recipe recipe = new Recipe(dto.category, inputs, dto.output, eventLog, dto.crushable);
					map.put(key(kind, name, targetKey), recipe);
					if (KIND_SPELL_ON_ITEM.equals(kind))
					{
						// Also key enchants by the target jewellery alone: each enchantable
						// item has exactly one enchant spell, and the clicked spell's menu
						// string is less stable than the item name.
						map.put(key(kind, targetKey, ANY_TARGET), recipe);
					}
					if (KIND_INTERFACE.equals(kind))
					{
						// Make-X clicks only expose the product name, not the station.
						map.put(key(kind, name, ANY_TARGET), recipe);
					}
				}
			}
			recipes = Collections.unmodifiableMap(map);
			log.info("Loaded {} recipe rules from snapshot", recipes.size());
		}
		catch (IOException ex)
		{
			log.warn("Failed to load recipe_nodes.json", ex);
		}
	}

	public static class Recipe
	{
		public final String category;
		public final List<ResourceNodeCatalog.CardGroup> inputGroups;
		/** Exact output card name, or null (e.g. firemaking produces no item). */
		public final String output;
		public final boolean eventLog;
		/** Gem-cutting recipes whose gem can shatter into a Crushed gem; config-gated extra. */
		public final boolean crushable;

		Recipe(String category, List<ResourceNodeCatalog.CardGroup> inputGroups, String output,
			boolean eventLog, boolean crushable)
		{
			this.category = category.trim().toLowerCase(Locale.ROOT);
			this.inputGroups = Collections.unmodifiableList(inputGroups);
			this.output = output;
			this.eventLog = eventLog;
			this.crushable = crushable;
		}

		/** Display strings for unmet requirements under the given enforcement, empty = allowed. */
		public List<String> missingRequirements(Set<String> owned, boolean enforceInputs,
			boolean enforceOutput)
		{
			List<String> missing = new ArrayList<>();
			if (enforceInputs)
			{
				for (ResourceNodeCatalog.CardGroup group : inputGroups)
				{
					if (!group.isSatisfied(owned))
					{
						missing.add(String.join(" / ", group.displayCards));
					}
				}
			}
			if (enforceOutput && output != null
				&& !owned.contains(output.toLowerCase(Locale.ROOT)))
			{
				missing.add(output);
			}
			return missing;
		}
	}

	private static class Snapshot
	{
		List<RecipeDto> recipes;
	}

	private static class RecipeDto
	{
		String category;
		List<List<String>> inputs;
		String output;
		boolean crushable;
		TriggerDto trigger;
	}

	private static class TriggerDto
	{
		String kind;
		String name;
		List<String> targets;
	}
}
