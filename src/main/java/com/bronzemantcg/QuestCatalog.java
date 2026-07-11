package com.bronzemantcg;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Informational quest data for the side panel: which cards a player must own to complete
 * each quest (required items as any-of groups, plus resolvable quest enemies). Loaded from
 * resources/quest_cards.json; purely display - quests are never blocked by this plugin,
 * but under forced-drop a locked quest item genuinely can't be used, so "own the cards"
 * and "can physically complete it" coincide.
 */
@Slf4j
@Singleton
public class QuestCatalog
{
	private List<QuestEntry> quests = Collections.emptyList();

	@Inject
	public QuestCatalog(Gson gson)
	{
		load(gson);
	}

	public List<QuestEntry> getQuests()
	{
		return quests;
	}

	public int size()
	{
		return quests.size();
	}

	private void load(Gson gson)
	{
		try (InputStream stream = getClass().getResourceAsStream("/quest_cards.json"))
		{
			if (stream == null)
			{
				log.info("quest_cards.json not present; quest panel section will be empty.");
				return;
			}
			Snapshot snapshot = gson.fromJson(
				new InputStreamReader(stream, StandardCharsets.UTF_8), Snapshot.class);
			if (snapshot == null || snapshot.quests == null)
			{
				return;
			}
			List<QuestEntry> loaded = new ArrayList<>();
			for (QuestDto dto : snapshot.quests)
			{
				if (dto == null || dto.name == null || dto.name.trim().isEmpty())
				{
					continue;
				}
				List<Requirement> requirements = new ArrayList<>();
				if (dto.cardGroups != null)
				{
					for (int i = 0; i < dto.cardGroups.size(); i++)
					{
						List<String> cards = dto.cardGroups.get(i);
						if (cards == null || cards.isEmpty())
						{
							continue;
						}
						String label = dto.groupLabels != null && i < dto.groupLabels.size()
							&& dto.groupLabels.get(i) != null && !dto.groupLabels.get(i).trim().isEmpty()
							? dto.groupLabels.get(i).trim()
							: cards.get(0);
						requirements.add(new Requirement(label, cards));
					}
				}
				if (dto.monsterCards != null)
				{
					for (String monster : dto.monsterCards)
					{
						if (monster != null && !monster.trim().isEmpty())
						{
							requirements.add(new Requirement(monster.trim() + " (enemy)",
								Collections.singletonList(monster.trim())));
						}
					}
				}
				loaded.add(new QuestEntry(dto.name.trim(), dto.miniquest, requirements));
			}
			loaded.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
			quests = Collections.unmodifiableList(loaded);
			log.info("Loaded {} quests from card-requirement snapshot", quests.size());
		}
		catch (IOException ex)
		{
			log.warn("Failed to load quest_cards.json", ex);
		}
	}

	public static class QuestEntry
	{
		public final String name;
		public final boolean miniquest;
		public final List<Requirement> requirements;

		QuestEntry(String name, boolean miniquest, List<Requirement> requirements)
		{
			this.name = name;
			this.miniquest = miniquest;
			this.requirements = Collections.unmodifiableList(requirements);
		}

		public int satisfiedCount(Set<String> ownedLowerCase)
		{
			int count = 0;
			for (Requirement requirement : requirements)
			{
				if (requirement.isSatisfied(ownedLowerCase))
				{
					count++;
				}
			}
			return count;
		}
	}

	/** One any-of card group ("Any pickaxe") or a single required card / enemy. */
	public static class Requirement
	{
		public final String label;
		public final List<String> displayCards;
		public final List<String> lowerCards;

		Requirement(String label, List<String> cards)
		{
			this.label = label;
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
			this.displayCards = Collections.unmodifiableList(display);
			this.lowerCards = Collections.unmodifiableList(lower);
		}

		public boolean isSatisfied(Set<String> ownedLowerCase)
		{
			for (String card : lowerCards)
			{
				if (ownedLowerCase.contains(card))
				{
					return true;
				}
			}
			return false;
		}
	}

	private static class Snapshot
	{
		List<QuestDto> quests;
	}

	private static class QuestDto
	{
		String name;
		boolean miniquest;
		List<List<String>> cardGroups;
		List<String> groupLabels;
		List<String> monsterCards;
	}
}
