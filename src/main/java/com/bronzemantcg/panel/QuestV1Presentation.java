package com.bronzemantcg.panel;

import com.bronzemantcg.catalog.QuestCatalog;
import com.bronzemantcg.ownership.ActiveCardIdentityCatalog;
import com.bronzemantcg.ownership.CardEntityKind;
import com.bronzemantcg.ownership.CardIdentity;
import com.bronzemantcg.ownership.CardResolver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Projects the pinned pre-v1 quest checklist onto one active v1 identity revision. */
@Singleton
public final class QuestV1Presentation
{
	private final ActiveCardIdentityCatalog activeCatalog;

	@Inject
	public QuestV1Presentation(ActiveCardIdentityCatalog activeCatalog)
	{
		this.activeCatalog = activeCatalog;
	}

	public long getRevision()
	{
		return activeCatalog.getRevision();
	}

	public Data project(QuestCatalog source)
	{
		ActiveCardIdentityCatalog.View view = activeCatalog.getView();
		CardResolver resolver = new CardResolver(view);
		return new Data(view.getRevision(), project(source.getQuests(), resolver),
			project(source.getMiniquests(), resolver));
	}

	private static List<QuestCatalog.QuestEntry> project(
		List<QuestCatalog.QuestEntry> entries, CardResolver resolver)
	{
		List<QuestCatalog.QuestEntry> projected = new ArrayList<>();
		for (QuestCatalog.QuestEntry entry : entries)
		{
			List<QuestCatalog.Section> sections = new ArrayList<>();
			for (QuestCatalog.Section section : entry.sections)
			{
				List<QuestCatalog.Requirement> requirements = new ArrayList<>();
				for (QuestCatalog.Requirement requirement : section.requirements)
				{
					Projection result = project(requirement, resolver);
					if (!result.unrestricted)
					{
						requirements.add(result.requirement);
					}
				}
				sections.add(section.withRequirements(requirements));
			}
			projected.add(entry.withSections(sections));
		}
		return Collections.unmodifiableList(projected);
	}

	private static Projection project(QuestCatalog.Requirement source,
		CardResolver resolver)
	{
		List<QuestCatalog.Requirement> children = new ArrayList<>();
		boolean unrestrictedChild = false;
		boolean restrictedChild = false;
		boolean routed = !source.selector.isEmpty();
		for (QuestCatalog.Requirement child : source.children)
		{
			Projection projected = project(child, resolver);
			if (projected.unrestricted)
			{
				unrestrictedChild = true;
				if (routed)
				{
					children.add(child.withProjection(Collections.emptyList(),
						Collections.emptyList(), Collections.emptyList()));
				}
			}
			else
			{
				restrictedChild = true;
				children.add(projected.requirement);
			}
		}

		if (!source.children.isEmpty())
		{
			if (routed && unrestrictedChild && !restrictedChild)
			{
				return Projection.unrestricted();
			}
			if (!routed && ((source.logic == QuestCatalog.Logic.ANY && unrestrictedChild)
				|| (source.logic == QuestCatalog.Logic.ALL && children.isEmpty())))
			{
				return Projection.unrestricted();
			}
			MappedCards display = mapCards(source.displayCards, source.type, resolver);
			return Projection.restricted(source.withProjection(
				display.displayCards, display.acceptedCards, children));
		}

		MappedCards cards = mapCards(source.displayCards, source.type, resolver);
		if (cards.untrackedAlternative || cards.acceptedCards.isEmpty())
		{
			return Projection.unrestricted();
		}
		return Projection.restricted(source.withProjection(
			cards.displayCards, cards.acceptedCards, Collections.emptyList()));
	}

	private static MappedCards mapCards(List<String> sourceCards, String type,
		CardResolver resolver)
	{
		Map<String, String> display = new LinkedHashMap<>();
		Map<String, String> accepted = new LinkedHashMap<>();
		boolean untracked = false;
		for (String sourceCard : sourceCards)
		{
			CardIdentity identity = resolve(sourceCard, type, resolver);
			if (identity == null)
			{
				untracked = true;
				continue;
			}
			display.putIfAbsent(normalize(identity.getCardName()), identity.getCardName());
			accepted.putIfAbsent(normalize(identity.getCardName()), identity.getCardName());
			for (String legacyName : identity.getLegacyCardNames())
			{
				accepted.putIfAbsent(normalize(legacyName), legacyName);
			}
		}
		return new MappedCards(new ArrayList<>(display.values()),
			new ArrayList<>(accepted.values()), untracked);
	}

	private static CardIdentity resolve(String card, String type, CardResolver resolver)
	{
		switch (type) {
			case "item":
				return tracked(resolver.resolveCardName(CardEntityKind.ITEM, card));
			case "enemy":
			case "npc":
				return tracked(resolver.resolveCardName(CardEntityKind.NPC, card));
			default:
				CardIdentity internalCard = tracked(resolver.resolveCardName(CardEntityKind.ITEM, card));
				if (internalCard != null) {
					return internalCard;
				}

				internalCard = tracked(resolver.resolveCardName(CardEntityKind.NPC, card));
                return internalCard;
        }
	}

	private static CardIdentity tracked(CardResolver.Result result)
	{
		return result.isTracked() ? result.getIdentity() : null;
	}

	private static String normalize(String value)
	{
		return value.trim().toLowerCase(Locale.ROOT);
	}

	public static final class Data
	{
		private final long revision;
		private final List<QuestCatalog.QuestEntry> quests;
		private final List<QuestCatalog.QuestEntry> miniquests;

		private Data(long revision, List<QuestCatalog.QuestEntry> quests,
			List<QuestCatalog.QuestEntry> miniquests)
		{
			this.revision = revision;
			this.quests = quests;
			this.miniquests = miniquests;
		}

		public long getRevision()
		{
			return revision;
		}

		public List<QuestCatalog.QuestEntry> getQuests()
		{
			return quests;
		}

		public List<QuestCatalog.QuestEntry> getMiniquests()
		{
			return miniquests;
		}
	}

	private static final class Projection
	{
		private final QuestCatalog.Requirement requirement;
		private final boolean unrestricted;

		private Projection(QuestCatalog.Requirement requirement, boolean unrestricted)
		{
			this.requirement = requirement;
			this.unrestricted = unrestricted;
		}

		private static Projection restricted(QuestCatalog.Requirement requirement)
		{
			return new Projection(requirement, false);
		}

		private static Projection unrestricted()
		{
			return new Projection(null, true);
		}
	}

	private static final class MappedCards
	{
		private final List<String> displayCards;
		private final List<String> acceptedCards;
		private final boolean untrackedAlternative;

		private MappedCards(List<String> displayCards, List<String> acceptedCards,
			boolean untrackedAlternative)
		{
			this.displayCards = displayCards;
			this.acceptedCards = acceptedCards;
			this.untrackedAlternative = untrackedAlternative;
		}
	}
}
