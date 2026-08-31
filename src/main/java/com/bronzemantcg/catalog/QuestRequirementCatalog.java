package com.bronzemantcg.catalog;


import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Quest;
import net.runelite.api.Skill;

/**
 * Skill levels, prerequisite quests and quest-point gates per quest, generated from the
 * OSRS Wiki (scripts/build_quest_requirements.py). Purely a side-panel aid: it drives the
 * independent quest-panel filters and the quest hover, and never restricts anything in game.
 *
 * <p>Levels are compared strictly against real levels - boosts are deliberately not
 * honoured. Requirements the wiki states as free text (combat ability, area access) are
 * either omitted or carried as display-only {@link Requirements#other} notes that the
 * filter ignores.
 */
@Slf4j
@Singleton
public class QuestRequirementCatalog
{
	private Map<String, Requirements> byQuestName = Collections.emptyMap();

	@Inject
	public QuestRequirementCatalog(Gson gson)
	{
		load(gson);
	}

	/** Requirements for a quest, or null when it has none worth checking. */
	public Requirements get(String questName)
	{
		if (questName == null)
		{
			return null;
		}
		return byQuestName.get(questName.toLowerCase(Locale.ROOT));
	}

	public int size()
	{
		return byQuestName.size();
	}

	private void load(Gson gson)
	{
		try (InputStream stream = getClass().getResourceAsStream(
			"/quest/quest_requirements.json"))
		{
			if (stream == null)
			{
				log.info("quest_requirements.json not present; the quest requirement "
					+ "filter will treat every quest as unrestricted.");
				return;
			}
			Snapshot snapshot = gson.fromJson(
				new InputStreamReader(stream, StandardCharsets.UTF_8), Snapshot.class);
			if (snapshot == null || snapshot.quests == null)
			{
				return;
			}
			Map<String, Requirements> loaded = new HashMap<>();
			for (QuestDto dto : snapshot.quests)
			{
				if (dto == null || dto.name == null || dto.name.trim().isEmpty())
				{
					continue;
				}
				Requirements requirements = toRequirements(dto);
				if (requirements != null)
				{
					loaded.put(dto.name.trim().toLowerCase(Locale.ROOT), requirements);
				}
			}
			byQuestName = Collections.unmodifiableMap(loaded);
			log.info("Loaded requirements for {} quests", byQuestName.size());
		}
		catch (IOException ex)
		{
			log.warn("Failed to load quest_requirements.json", ex);
		}
	}

	private static Requirements toRequirements(QuestDto dto)
	{
		List<SkillRequirement> skills = new ArrayList<>();
		if (dto.skills != null)
		{
			for (SkillDto skill : dto.skills)
			{
				if (skill == null || skill.skill == null || skill.level < 1)
				{
					continue;
				}
				Skill parsed = parseSkill(skill.skill);
				// An unknown skill name means the generator and this client disagree.
				// Skip it rather than block the whole quest on data we cannot evaluate.
				if (parsed != null)
				{
					skills.add(new SkillRequirement(parsed, skill.level));
				}
			}
		}

		List<QuestRequirement> quests = new ArrayList<>();
		if (dto.quests != null)
		{
			for (QuestDto prerequisite : dto.quests)
			{
				if (prerequisite == null || prerequisite.runeliteQuest == null)
				{
					continue;
				}
				Quest quest = parseQuest(prerequisite.runeliteQuest);
				if (quest != null)
				{
					quests.add(new QuestRequirement(
						prerequisite.name == null ? quest.getName() : prerequisite.name.trim(),
						quest));
				}
			}
		}

		List<String> other = new ArrayList<>();
		if (dto.other != null)
		{
			for (String note : dto.other)
			{
				if (note != null && !note.trim().isEmpty())
				{
					other.add(note.trim());
				}
			}
		}

		int questPoints = Math.max(dto.questPoints, 0);
		if (skills.isEmpty() && quests.isEmpty() && questPoints == 0 && other.isEmpty())
		{
			return null;
		}
		return new Requirements(skills, quests, questPoints, other);
	}

	private static Skill parseSkill(String name)
	{
		for (Skill skill : Skill.values())
		{
			if (skill.name().equalsIgnoreCase(name.trim()))
			{
				return skill;
			}
		}
		log.warn("Unknown skill '{}' in quest_requirements.json", name);
		return null;
	}

	private static Quest parseQuest(String constant)
	{
		for (Quest quest : Quest.values())
		{
			if (quest.name().equals(constant.trim()))
			{
				return quest;
			}
		}
		log.warn("Unknown quest constant '{}' in quest_requirements.json", constant);
		return null;
	}

	/** Everything gating one quest. Only skills, prerequisites and quest points count. */
	public static class Requirements
	{
		public final List<SkillRequirement> skills;
		public final List<QuestRequirement> quests;
		public final int questPoints;
		/** Display-only notes; real gates we cannot evaluate, never used for filtering. */
		public final List<String> other;

		private Requirements(List<SkillRequirement> skills, List<QuestRequirement> quests,
			int questPoints, List<String> other)
		{
			this.skills = Collections.unmodifiableList(skills);
			this.quests = Collections.unmodifiableList(quests);
			this.questPoints = questPoints;
			this.other = Collections.unmodifiableList(other);
		}

		/**
		 * @param levels real skill levels indexed by {@link Skill#ordinal()}, or null when
		 *               unknown (logged out) - unknown means "cannot judge", so it passes
		 * @param completedQuests lower-case names of finished quests
		 */
		public boolean isMet(int[] levels, int questPointCount, java.util.Set<String> completedQuests)
		{
			if (levels == null)
			{
				return true;
			}
			for (SkillRequirement requirement : skills)
			{
				int ordinal = requirement.skill.ordinal();
				if (ordinal >= levels.length || levels[ordinal] < requirement.level)
				{
					return false;
				}
			}
			if (questPointCount < questPoints)
			{
				return false;
			}
			for (QuestRequirement requirement : quests)
			{
				if (!completedQuests.contains(
					requirement.quest.getName().toLowerCase(Locale.ROOT)))
				{
					return false;
				}
			}
			return true;
		}
	}

	public static class SkillRequirement
	{
		public final Skill skill;
		public final int level;

		private SkillRequirement(Skill skill, int level)
		{
			this.skill = skill;
			this.level = level;
		}
	}

	public static class QuestRequirement
	{
		public final String name;
		public final Quest quest;

		private QuestRequirement(String name, Quest quest)
		{
			this.name = name;
			this.quest = quest;
		}
	}

	private static class Snapshot
	{
		List<QuestDto> quests;
	}

	private static class QuestDto
	{
		String name;
		String runeliteQuest;
		List<SkillDto> skills;
		List<QuestDto> quests;
		int questPoints;
		List<String> other;
	}

	private static class SkillDto
	{
		String skill;
		int level;
	}
}
