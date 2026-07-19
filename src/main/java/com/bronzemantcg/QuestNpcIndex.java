package com.bronzemantcg;

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
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;

/**
 * Quest-state awareness for NPC restriction: an NPC tied to a quest the player has
 * STARTED (in progress or finished) is "shown" - visible in Hide mode and granted
 * Prevent Combat treatment in the strict tiers, so no quest is ever bricked by
 * restriction settings. Attack always stays card-gated; quest progression is the
 * permit for everything else (owner's model, no toggle).
 *
 * Quest names from quest_cards.json are matched to RuneLite's {@link Quest} enum by
 * normalised name. Unmatched quests (renames, miniquests missing from the enum) FAIL
 * OPEN: their NPCs are always shown, because over-showing is recoverable while a
 * silently hidden quest NPC bricks a quest.
 *
 * {@link Quest#getState} reads varps, so it never runs on the render path; the
 * plugin calls {@link #refresh} on a slow game-tick cadence and everything else
 * reads the cached set.
 */
@Slf4j
@Singleton
class QuestNpcIndex
{
	// NPCs whose quest interaction lives in node rules rather than monsterCards;
	// currently just the CotS Guards (Mark option).
	private static final Map<String, Quest> INTERACTION_NPCS =
		Map.of("guard", Quest.CHILDREN_OF_THE_SUN);

	private final Map<String, List<Quest>> npcQuests = new HashMap<>();
	private final Set<String> alwaysShown = new HashSet<>();

	private volatile Set<String> shownNpcs = Collections.emptySet();
	private volatile boolean cotsInProgress;

	@Inject
	QuestNpcIndex(QuestCatalog questCatalog)
	{
		Map<String, Quest> questsByName = new HashMap<>();
		for (Quest quest : Quest.values())
		{
			questsByName.put(normalise(quest.getName()), quest);
		}

		int unmatched = 0;
		for (Map.Entry<String, List<String>> entry : questCatalog.getQuestMonsterCards().entrySet())
		{
			Quest quest = questsByName.get(normalise(entry.getKey()));
			for (String card : entry.getValue())
			{
				// Cards use wiki-style disambiguation ("Monkey (monster)"); the in-game
				// NPC name is the bare prefix, same convention as the monster snapshot.
				String npc = card.replaceAll("\\s*\\([^)]*\\)$", "").trim().toLowerCase(Locale.ROOT);
				if (npc.isEmpty())
				{
					continue;
				}
				if (quest == null)
				{
					alwaysShown.add(npc);
				}
				else
				{
					npcQuests.computeIfAbsent(npc, k -> new ArrayList<>()).add(quest);
				}
			}
			if (quest == null)
			{
				unmatched++;
			}
		}
		for (Map.Entry<String, Quest> entry : INTERACTION_NPCS.entrySet())
		{
			npcQuests.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue());
		}
		log.info("Quest NPC index: {} NPCs across matched quests, {} fail-open from {} unmatched quest names",
			npcQuests.size(), alwaysShown.size(), unmatched);
	}

	/** Recompute the shown set. Client thread only; called on a slow tick cadence. */
	void refresh(Client client)
	{
		Set<String> shown = new HashSet<>(alwaysShown);
		for (Map.Entry<String, List<Quest>> entry : npcQuests.entrySet())
		{
			for (Quest quest : entry.getValue())
			{
				if (quest.getState(client) != QuestState.NOT_STARTED)
				{
					shown.add(entry.getKey());
					break;
				}
			}
		}
		shownNpcs = shown;
		cotsInProgress = Quest.CHILDREN_OF_THE_SUN.getState(client) == QuestState.IN_PROGRESS;
	}

	/** True when this NPC belongs to a started (or unmatched) quest and must stay reachable. */
	boolean isShownQuestNpc(String npcName)
	{
		return npcName != null && shownNpcs.contains(npcName.trim().toLowerCase(Locale.ROOT));
	}

	/** Waives the quest-cots node rule (Guard marking) while the quest is actually running. */
	boolean isCotsInProgress()
	{
		return cotsInProgress;
	}

	private static String normalise(String name)
	{
		return name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}
}
