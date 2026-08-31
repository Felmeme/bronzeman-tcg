package com.bronzemantcg.restriction;

import com.bronzemantcg.catalog.CardNames;
import com.bronzemantcg.ownership.ActiveCardIdentityCatalog;
import com.bronzemantcg.ownership.CardIdentity;
import com.bronzemantcg.ownership.ImmutableCardIdentityCatalog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Parses the user-editable exempt list and resolves its exact names and {@code *} wildcards
 * against the active item/NPC catalogue. Resolution happens only when the config text or active
 * catalogue revision changes; gameplay paths receive immutable sets and remain ordinary set
 * lookups.
 */
@Singleton
public final class ExemptionList
{
	private final ActiveCardIdentityCatalog activeCatalog;
	private volatile Snapshot snapshot = Snapshot.empty();

	@Inject
	ExemptionList(ActiveCardIdentityCatalog activeCatalog)
	{
		this.activeCatalog = activeCatalog;
	}

	public Snapshot resolve(String raw)
	{
		String safeRaw = raw == null ? "" : raw;
		ActiveCardIdentityCatalog.View catalogView = activeCatalog.getView();
		long catalogRevision = catalogView.getRevision();
		Snapshot current = snapshot;
		if (safeRaw.equals(current.raw) && catalogRevision == current.catalogRevision)
		{
			return current;
		}
		synchronized (this)
		{
			current = snapshot;
			if (!safeRaw.equals(current.raw) || catalogRevision != current.catalogRevision)
			{
				current = build(safeRaw, catalogView, catalogRevision);
				snapshot = current;
			}
			return current;
		}
	}

	private Snapshot build(String raw, ActiveCardIdentityCatalog.View catalogView,
		long catalogRevision)
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
		resolveCatalog(catalogView.getEntries(), rules, entityNames, cardNames);
		return new Snapshot(raw, catalogRevision, entityNames, cardNames);
	}

	private static void resolveCatalog(List<ImmutableCardIdentityCatalog.Entry> entries,
		RuleSet rules,
		Set<String> entityNames, Set<String> cardNames)
	{
		for (ImmutableCardIdentityCatalog.Entry entry : entries)
		{
			CardIdentity identity = entry.getIdentity();
			Set<String> matchedCardNames = new HashSet<>();
			String canonicalName = normalize(identity.getCardName());
			if (rules.matches(canonicalName))
			{
				matchedCardNames.add(canonicalName);
			}
			for (String legacyName : identity.getLegacyCardNames())
			{
				String normalized = normalize(legacyName);
				if (rules.matches(normalized))
				{
					matchedCardNames.add(normalized);
				}
			}

			boolean entityMatched = false;
			Set<String> identityEntityNames = new HashSet<>();
			for (String entityName : entry.getEntityNames())
			{
				String normalized = normalize(entityName);
				identityEntityNames.add(normalized);
				if (rules.matches(normalized))
				{
					entityMatched = true;
				}
			}
			if (entityMatched || !matchedCardNames.isEmpty())
			{
				entityNames.addAll(identityEntityNames);
			}
			if (entityMatched)
			{
				cardNames.add(canonicalName);
				for (String legacyName : identity.getLegacyCardNames())
				{
					cardNames.add(normalize(legacyName));
				}
			}
			else if (!matchedCardNames.isEmpty())
			{
				cardNames.add(canonicalName);
				cardNames.addAll(matchedCardNames);
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

	public static final class Snapshot
	{
		private final String raw;
		private final long catalogRevision;
		private final Set<String> entityNamesLowerCase;
		private final Set<String> cardNamesLowerCase;

		private Snapshot(String raw, long catalogRevision,
			Set<String> entityNames, Set<String> cardNames)
		{
			this.raw = raw;
			this.catalogRevision = catalogRevision;
			this.entityNamesLowerCase = Collections.unmodifiableSet(entityNames);
			this.cardNamesLowerCase = Collections.unmodifiableSet(cardNames);
		}

		private static Snapshot empty()
		{
			return new Snapshot("", 0L, Collections.emptySet(), Collections.emptySet());
		}

		boolean containsEntity(String name)
		{
			return entityNamesLowerCase.contains(normalize(name));
		}

		public Set<String> getCardNamesLowerCase()
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
