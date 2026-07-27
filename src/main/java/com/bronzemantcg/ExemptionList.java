package com.bronzemantcg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Parses the user-editable exempt list and resolves its exact names and {@code *} wildcards
 * against the bundled item/NPC catalogs. Resolution happens only when the config text changes;
 * gameplay paths receive immutable sets and remain ordinary set lookups.
 */
@Singleton
final class ExemptionList
{
	private final TrackedItemCatalog itemCatalog;
	private final TrackedMonsterCatalog monsterCatalog;
	private volatile Snapshot snapshot = Snapshot.empty();

	@Inject
	ExemptionList(TrackedItemCatalog itemCatalog, TrackedMonsterCatalog monsterCatalog)
	{
		this.itemCatalog = itemCatalog;
		this.monsterCatalog = monsterCatalog;
	}

	Snapshot resolve(String raw)
	{
		String safeRaw = raw == null ? "" : raw;
		Snapshot current = snapshot;
		if (safeRaw.equals(current.raw))
		{
			return current;
		}
		synchronized (this)
		{
			current = snapshot;
			if (!safeRaw.equals(current.raw))
			{
				current = build(safeRaw);
				snapshot = current;
			}
			return current;
		}
	}

	private Snapshot build(String raw)
	{
		Set<String> exact = new HashSet<>();
		List<Pattern> wildcards = new ArrayList<>();
		for (String entry : raw.split(","))
		{
			String normalized = normalize(entry);
			if (normalized.isEmpty() || onlyWildcards(normalized))
			{
				// One or more bare wildcards would silently disable almost every restriction.
				continue;
			}
			if (normalized.indexOf('*') < 0)
			{
				exact.add(normalized);
			}
			else
			{
				wildcards.add(compileGlob(normalized));
			}
		}

		RuleSet rules = new RuleSet(exact, wildcards);
		Set<String> entityNames = new HashSet<>(exact);
		Set<String> cardNames = new HashSet<>(exact);
		resolveCatalog(itemCatalog.getEntityToCards(), rules, entityNames, cardNames);
		resolveCatalog(monsterCatalog.getEntityToCards(), rules, entityNames, cardNames);
		return new Snapshot(raw, entityNames, cardNames);
	}

	private static void resolveCatalog(Map<String, Set<String>> catalog, RuleSet rules,
		Set<String> entityNames, Set<String> cardNames)
	{
		for (Map.Entry<String, Set<String>> entry : catalog.entrySet())
		{
			boolean matched = rules.matches(entry.getKey());
			if (matched)
			{
				cardNames.addAll(entry.getValue());
			}
			else
			{
				for (String card : entry.getValue())
				{
					if (rules.matches(card))
					{
						cardNames.add(card);
						matched = true;
					}
				}
			}
			if (matched)
			{
				entityNames.add(entry.getKey());
			}
		}
	}

	private static Pattern compileGlob(String glob)
	{
		StringBuilder regex = new StringBuilder("^");
		int from = 0;
		int wildcard;
		while ((wildcard = glob.indexOf('*', from)) >= 0)
		{
			regex.append(Pattern.quote(glob.substring(from, wildcard))).append(".*");
			from = wildcard + 1;
		}
		regex.append(Pattern.quote(glob.substring(from))).append('$');
		return Pattern.compile(regex.toString());
	}

	private static boolean onlyWildcards(String value)
	{
		for (int i = 0; i < value.length(); i++)
		{
			if (value.charAt(i) != '*')
			{
				return false;
			}
		}
		return !value.isEmpty();
	}

	private static String normalize(String name)
	{
		return name == null ? ""
			: CardNames.stripDoseSuffix(name.trim().toLowerCase(Locale.ROOT));
	}

	static final class Snapshot
	{
		private final String raw;
		private final Set<String> entityNamesLowerCase;
		private final Set<String> cardNamesLowerCase;

		private Snapshot(String raw, Set<String> entityNames, Set<String> cardNames)
		{
			this.raw = raw;
			this.entityNamesLowerCase = Collections.unmodifiableSet(entityNames);
			this.cardNamesLowerCase = Collections.unmodifiableSet(cardNames);
		}

		private static Snapshot empty()
		{
			return new Snapshot("", Collections.emptySet(), Collections.emptySet());
		}

		boolean containsEntity(String name)
		{
			return entityNamesLowerCase.contains(normalize(name));
		}

		Set<String> getCardNamesLowerCase()
		{
			return cardNamesLowerCase;
		}
	}

	private static final class RuleSet
	{
		private final Set<String> exact;
		private final List<Pattern> wildcards;

		private RuleSet(Set<String> exact, List<Pattern> wildcards)
		{
			this.exact = exact;
			this.wildcards = wildcards;
		}

		private boolean matches(String name)
		{
			if (exact.contains(name))
			{
				return true;
			}
			for (Pattern wildcard : wildcards)
			{
				if (wildcard.matcher(name).matches())
				{
					return true;
				}
			}
			return false;
		}
	}
}
