package com.bronzemantcg.panel;

import com.bronzemantcg.catalog.ContentCatalog;
import com.bronzemantcg.catalog.QuestCatalog;
import com.bronzemantcg.catalog.ResourceNodeCatalog;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Selects informational side-panel rows for old name-only and v1-capable OSRS TCG payloads.
 * The pre-v1 resource contains presentation overrides only; restriction catalogues always keep
 * using their current data.
 */
@Slf4j
@Singleton
public class PanelPresentationCatalog
{
	private static final String RESOURCE = "/panel/pre_v1_presentation_overrides.json";
	private static final int SCHEMA_VERSION = 1;
	private static final Set<String> PRE_V1_NODE_NAMES = Set.of(
		"guild hunter aco", "guild hunter cervus", "guild hunter teco",
		"guild hunter wolf", "huntmaster gilman", "achtryn");

	private final Data current;
	private final Data preV1;

	@Inject
	public PanelPresentationCatalog(Gson gson, ContentCatalog contentCatalog,
		ResourceNodeCatalog nodeCatalog)
	{
		current = currentData(contentCatalog, nodeCatalog);
		preV1 = loadPreV1(gson, current);
	}

	public Data select(boolean v1Capable)
	{
		return v1Capable ? current : preV1;
	}

	private Data loadPreV1(Gson gson, Data base)
	{
		try (InputStream stream = getClass().getResourceAsStream(RESOURCE))
		{
			if (stream == null)
			{
				throw new IllegalArgumentException("missing " + RESOURCE);
			}
			Snapshot snapshot = gson.fromJson(
				new InputStreamReader(stream, StandardCharsets.UTF_8), Snapshot.class);
			validate(snapshot);

			List<Checklist> contents = replace(base.contents,
				checklists(snapshot.contents));
			Map<String, Rule> slayer = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			slayer.putAll(base.slayerRules);
			Map<String, Rule> rumours = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			rumours.putAll(base.rumourRules);
			for (NodeDto node : snapshot.nodes)
			{
				Map<String, Rule> destination = "slayer".equals(node.category)
					? slayer : rumours;
				destination.put(node.name.trim(), rule(node));
			}
			log.info("Loaded pre-v1 panel presentation overrides from {}", snapshot.sourceCommit);
			return new Data(contents, slayer, rumours);
		}
		catch (IOException | RuntimeException ex)
		{
			log.warn("Could not load pre-v1 panel presentation; using current data", ex);
			return base;
		}
	}

	private static Data currentData(ContentCatalog contentCatalog,
		ResourceNodeCatalog nodeCatalog)
	{
		List<Checklist> contents = new ArrayList<>();
		for (QuestCatalog.QuestEntry entry : contentCatalog.getContents())
		{
			List<String> cards = new ArrayList<>();
			for (QuestCatalog.Requirement requirement : entry.requirements)
			{
				cards.add(requirement.label);
			}
			contents.add(new Checklist(entry.name, cards, entry.notes));
		}
		return new Data(contents, rules(nodeCatalog, "slayer"),
			rules(nodeCatalog, "hunter-rumours"));
	}

	private static Map<String, Rule> rules(ResourceNodeCatalog catalog, String category)
	{
		Map<String, Rule> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (Map.Entry<String, ResourceNodeCatalog.Rule> entry
			: catalog.getRuleEntries().entrySet())
		{
			if (!category.equals(entry.getValue().category))
			{
				continue;
			}
			String[] parts = entry.getKey().split("\\|", 3);
			String name = parts.length > 1 ? parts[1] : entry.getKey();
			List<Group> groups = new ArrayList<>();
			for (ResourceNodeCatalog.CardGroup group : entry.getValue().groups)
			{
				groups.add(new Group(group.displayCards, group.role, group.label));
			}
			result.put(name, new Rule(groups));
		}
		return result;
	}

	private static Rule rule(NodeDto node)
	{
		List<Group> groups = new ArrayList<>();
		for (int i = 0; i < node.requiredCardGroups.size(); i++)
		{
			List<String> cards = node.requiredCardGroups.get(i);
			String role = lowerValueAt(node.groupRoles, i);
			String label = valueAt(node.groupLabels, i);
			if (label == null && cards != null && !cards.isEmpty())
			{
				label = cards.get(0);
			}
			groups.add(new Group(cards, role, label));
		}
		return new Rule(groups);
	}

	private static String valueAt(List<String> values, int index)
	{
		if (values == null || index >= values.size() || values.get(index) == null
			|| values.get(index).trim().isEmpty())
		{
			return null;
		}
		return values.get(index).trim();
	}

	private static String lowerValueAt(List<String> values, int index)
	{
		String value = valueAt(values, index);
		return value == null ? null : value.toLowerCase(Locale.ROOT);
	}

	private static List<Checklist> checklists(List<ChecklistDto> source)
	{
		List<Checklist> result = new ArrayList<>();
		for (ChecklistDto entry : source)
		{
			result.add(new Checklist(entry.name, entry.monsterCards, entry.notes));
		}
		return result;
	}

	private static List<Checklist> replace(List<Checklist> base,
		List<Checklist> overrides)
	{
		Map<String, Checklist> replacements = new LinkedHashMap<>();
		for (Checklist entry : overrides)
		{
			replacements.put(entry.name.toLowerCase(Locale.ROOT), entry);
		}
		List<Checklist> result = new ArrayList<>();
		for (Checklist entry : base)
		{
			result.add(replacements.getOrDefault(
				entry.name.toLowerCase(Locale.ROOT), entry));
		}
		if (!replacements.keySet().stream().allMatch(key -> result.stream()
			.anyMatch(entry -> entry.name.equalsIgnoreCase(key))))
		{
			throw new IllegalArgumentException("pre-v1 override does not match current row");
		}
		return result;
	}

	private static void validate(Snapshot snapshot)
	{
		if (snapshot == null || snapshot.schemaVersion != SCHEMA_VERSION
			|| snapshot.sourceCommit == null || snapshot.sourceCommit.trim().isEmpty()
			|| snapshot.contents == null || snapshot.contents.size() != 1
			|| snapshot.nodes == null || snapshot.nodes.size() != 6)
		{
			throw new IllegalArgumentException("invalid pre-v1 presentation snapshot");
		}
		Set<String> names = new java.util.HashSet<>();
		for (NodeDto node : snapshot.nodes)
		{
			if (node == null || node.name == null || node.category == null
				|| node.requiredCardGroups == null
				|| !("slayer".equals(node.category)
					|| "hunter-rumours".equals(node.category))
				|| !names.add(node.name.trim().toLowerCase(Locale.ROOT)))
			{
				throw new IllegalArgumentException("invalid pre-v1 presentation node");
			}
		}
		if (!names.equals(PRE_V1_NODE_NAMES))
		{
			throw new IllegalArgumentException("unexpected pre-v1 presentation nodes");
		}
	}

	public static final class Data
	{
		private final List<Checklist> contents;
		private final Map<String, Rule> slayerRules;
		private final Map<String, Rule> rumourRules;

		private Data(List<Checklist> contents, Map<String, Rule> slayerRules,
			Map<String, Rule> rumourRules)
		{
			this.contents = List.copyOf(contents);
			Map<String, Rule> slayerCopy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			slayerCopy.putAll(slayerRules);
			this.slayerRules = Collections.unmodifiableMap(slayerCopy);
			Map<String, Rule> rumourCopy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			rumourCopy.putAll(rumourRules);
			this.rumourRules = Collections.unmodifiableMap(rumourCopy);
		}

		public List<Checklist> getContents()
		{
			return contents;
		}

		public Map<String, Rule> getSlayerRules()
		{
			return slayerRules;
		}

		public Map<String, Rule> getRumourRules()
		{
			return rumourRules;
		}
	}

	public static final class Checklist
	{
		public final String name;
		public final List<String> cards;
		public final String notes;

		private Checklist(String name, List<String> cards, String notes)
		{
			this.name = name.trim();
			this.cards = List.copyOf(cards);
			this.notes = notes == null ? "" : notes.trim();
		}
	}

	public static final class Rule
	{
		public final List<Group> groups;

		private Rule(List<Group> groups)
		{
			this.groups = List.copyOf(groups);
		}
	}

	public static final class Group
	{
		public final List<String> displayCards;
		public final String role;
		public final String label;

		private Group(List<String> displayCards, String role, String label)
		{
			this.displayCards = List.copyOf(displayCards);
			this.role = role;
			this.label = label;
		}
	}

	private static final class Snapshot
	{
		private int schemaVersion;
		private String sourceCommit;
		private List<ChecklistDto> contents;
		private List<NodeDto> nodes;
	}

	private static final class ChecklistDto
	{
		private String name;
		private List<String> monsterCards;
		private String notes;
	}

	private static final class NodeDto
	{
		private String category;
		private String name;
		private List<List<String>> requiredCardGroups;
		private List<String> groupRoles;
		private List<String> groupLabels;
	}
}
