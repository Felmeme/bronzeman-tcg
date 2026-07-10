package com.bronzemantcg;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
 * Rules for gating skill resource nodes (trees, rocks, fishing spots, pickpocket targets,
 * raw-food-on-fire) behind ownership of the cards of the items they yield. Loaded from
 * resources/resource_nodes.json, which is hand-curated (assisted by an audit of osrs-tcg's
 * Card.json for exact card names) and expected to grow over time.
 *
 * Lookup is by (kind, in-game name, menu option) — all lower-cased. For "item-on-object"
 * rules the roles shift: name = the used item, options = the target object names, because
 * that's what a "Use Raw shrimps -> Fire" click gives us.
 */
@Slf4j
@Singleton
public class ResourceNodeCatalog
{
	public static final String KIND_OBJECT = "object";
	public static final String KIND_NPC = "npc";
	public static final String KIND_ITEM_ON_OBJECT = "item-on-object";

	private Map<String, Rule> rules = Collections.emptyMap();
	private List<String> masterFarmerSeedCards = Collections.emptyList();

	@Inject
	public ResourceNodeCatalog(Gson gson)
	{
		load(gson);
	}

	/** Exact card names of every seed on Master Farmer's drop table (for Insanity mode). */
	public List<String> getMasterFarmerSeedCards()
	{
		return masterFarmerSeedCards;
	}

	/** @return the rule for this interaction, or null if the node is unrestricted. */
	public Rule find(String kind, String name, String option)
	{
		if (name == null || option == null)
		{
			return null;
		}
		return rules.get(key(kind, name.trim().toLowerCase(Locale.ROOT),
			option.trim().toLowerCase(Locale.ROOT)));
	}

	public int size()
	{
		return rules.size();
	}

	private static String key(String kind, String nameLower, String optionLower)
	{
		return kind + '|' + nameLower + '|' + optionLower;
	}

	private void load(Gson gson)
	{
		try (InputStream stream = getClass().getResourceAsStream("/resource_nodes.json"))
		{
			if (stream == null)
			{
				log.warn("resource_nodes.json missing from classpath; resource nodes will be unrestricted.");
				return;
			}
			Snapshot snapshot = gson.fromJson(
				new InputStreamReader(stream, StandardCharsets.UTF_8), Snapshot.class);
			if (snapshot == null || snapshot.nodes == null)
			{
				return;
			}
			Map<String, Rule> map = new HashMap<>();
			for (NodeDto node : snapshot.nodes)
			{
				if (node == null || node.kind == null || node.name == null
					|| node.options == null || node.requiredCards == null || node.requiredCards.isEmpty())
				{
					continue;
				}
				Rule rule = new Rule(node.category, node.requireAll, node.requiredCards);
				String nameLower = node.name.trim().toLowerCase(Locale.ROOT);
				for (String option : node.options)
				{
					if (option != null && !option.trim().isEmpty())
					{
						map.put(key(node.kind.trim().toLowerCase(Locale.ROOT), nameLower,
							option.trim().toLowerCase(Locale.ROOT)), rule);
					}
				}
			}
			rules = Collections.unmodifiableMap(map);
			if (snapshot.masterFarmerSeedCards != null)
			{
				masterFarmerSeedCards = Collections.unmodifiableList(snapshot.masterFarmerSeedCards);
			}
			log.info("Loaded {} resource node rules ({} Master Farmer seeds) from snapshot",
				rules.size(), masterFarmerSeedCards.size());
		}
		catch (IOException ex)
		{
			log.warn("Failed to load resource_nodes.json", ex);
		}
	}

	public static class Rule
	{
		public final String category;
		public final boolean requireAll;
		/** Original casing, for chat messages. */
		public final List<String> requiredCards;
		public final Set<String> requiredCardsLowerCase;

		Rule(String category, boolean requireAll, List<String> requiredCards)
		{
			this.category = category == null ? "" : category.toLowerCase(Locale.ROOT);
			this.requireAll = requireAll;
			this.requiredCards = Collections.unmodifiableList(requiredCards);
			Set<String> lower = new HashSet<>();
			for (String card : requiredCards)
			{
				if (card != null)
				{
					lower.add(card.trim().toLowerCase(Locale.ROOT));
				}
			}
			this.requiredCardsLowerCase = Collections.unmodifiableSet(lower);
		}
	}

	private static class Snapshot
	{
		List<NodeDto> nodes;
		List<String> masterFarmerSeedCards;
	}

	private static class NodeDto
	{
		String category;
		String kind;
		String name;
		List<String> options;
		List<String> requiredCards;
		boolean requireAll;
	}
}
