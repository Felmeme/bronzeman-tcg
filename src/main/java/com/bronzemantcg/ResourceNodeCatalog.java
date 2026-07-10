package com.bronzemantcg;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
 * raw-food-on-fire, ...) behind ownership of the cards of the items they yield or consume.
 * Loaded from resources/resource_nodes.json, which is hand-curated (assisted by audits of
 * osrs-tcg's Card.json for exact card names) and expected to grow over time.
 *
 * Lookup is by (kind, in-game name, menu option) — all lower-cased. For "item-on-object"
 * rules the roles shift: name = the used item, options = the target object names, because
 * that's what a "Use Raw shrimps -> Fire" click gives us.
 *
 * Requirements come in two data shapes, unified internally into card GROUPS:
 *  - requiredCards + requireAll (legacy): requireAll=true -> each card its own group
 *    (all needed); requireAll=false -> one group (any one card suffices).
 *  - requiredCardGroups (+ optional parallel groupRoles): every group must be satisfied,
 *    a group is satisfied by owning ANY one of its cards. Roles let config modes drop
 *    groups (e.g. runecrafting's "rune" group in Talisman-only mode).
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
		String nameKey = CardNames.stripDoseSuffix(name.trim().toLowerCase(Locale.ROOT));
		return rules.get(key(kind, nameKey, option.trim().toLowerCase(Locale.ROOT)));
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
				if (node == null || node.kind == null || node.name == null || node.options == null)
				{
					continue;
				}
				List<CardGroup> groups = buildGroups(node);
				if (groups.isEmpty())
				{
					continue;
				}
				Rule rule = new Rule(node.category, groups);
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

	private static List<CardGroup> buildGroups(NodeDto node)
	{
		List<CardGroup> groups = new ArrayList<>();
		if (node.requiredCardGroups != null)
		{
			for (int i = 0; i < node.requiredCardGroups.size(); i++)
			{
				List<String> cards = node.requiredCardGroups.get(i);
				String role = node.groupRoles != null && i < node.groupRoles.size()
					? node.groupRoles.get(i) : null;
				CardGroup group = CardGroup.of(cards, role);
				if (group != null)
				{
					groups.add(group);
				}
			}
		}
		else if (node.requiredCards != null)
		{
			if (node.requireAll)
			{
				for (String card : node.requiredCards)
				{
					CardGroup group = CardGroup.of(Collections.singletonList(card), null);
					if (group != null)
					{
						groups.add(group);
					}
				}
			}
			else
			{
				CardGroup group = CardGroup.of(node.requiredCards, null);
				if (group != null)
				{
					groups.add(group);
				}
			}
		}
		return groups;
	}

	public static class Rule
	{
		public final String category;
		public final List<CardGroup> groups;

		Rule(String category, List<CardGroup> groups)
		{
			this.category = category == null ? "" : category.toLowerCase(Locale.ROOT);
			this.groups = Collections.unmodifiableList(groups);
		}

		/**
		 * @param owned         lower-cased owned card names
		 * @param excludedRoles group roles a config mode has switched off (never null)
		 * @param forceAllInGroups treat any-of groups as all-required (fishing "Require ALL")
		 * @return display strings for each unsatisfied requirement, empty when allowed.
		 *         Any-of groups render as "A / B" so the player sees the alternatives.
		 */
		public List<String> missingRequirements(Set<String> owned, Set<String> excludedRoles,
			boolean forceAllInGroups)
		{
			List<String> missing = new ArrayList<>();
			for (CardGroup group : groups)
			{
				if (group.role != null && excludedRoles.contains(group.role))
				{
					continue;
				}
				if (forceAllInGroups)
				{
					for (int i = 0; i < group.displayCards.size(); i++)
					{
						if (!owned.contains(group.lowerCards.get(i)))
						{
							missing.add(group.displayCards.get(i));
						}
					}
				}
				else if (!group.isSatisfied(owned))
				{
					missing.add(String.join(" / ", group.displayCards));
				}
			}
			return missing;
		}
	}

	public static class CardGroup
	{
		/** Original casing, for chat messages; lowerCards is index-aligned. */
		public final List<String> displayCards;
		public final List<String> lowerCards;
		public final String role;

		private CardGroup(List<String> displayCards, List<String> lowerCards, String role)
		{
			this.displayCards = Collections.unmodifiableList(displayCards);
			this.lowerCards = Collections.unmodifiableList(lowerCards);
			this.role = role;
		}

		static CardGroup of(List<String> cards, String role)
		{
			if (cards == null)
			{
				return null;
			}
			List<String> display = new ArrayList<>();
			List<String> lower = new ArrayList<>();
			for (String card : cards)
			{
				if (card != null && !card.trim().isEmpty())
				{
					display.add(card.trim());
					lower.add(card.trim().toLowerCase(Locale.ROOT));
				}
			}
			if (display.isEmpty())
			{
				return null;
			}
			String cleanRole = role == null || role.trim().isEmpty()
				? null : role.trim().toLowerCase(Locale.ROOT);
			return new CardGroup(display, lower, cleanRole);
		}

		boolean isSatisfied(Set<String> owned)
		{
			for (String card : lowerCards)
			{
				if (owned.contains(card))
				{
					return true;
				}
			}
			return false;
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
		List<List<String>> requiredCardGroups;
		List<String> groupRoles;
		boolean requireAll;
	}
}
