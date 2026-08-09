package com.bronzemantcg;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Non-combat quest NPC associations which cannot be derived from enemy requirements.
 * Kept separate from quest_cards.json so adding a conversation NPC never makes it appear
 * as an enemy in the quest checklist.
 */
@Slf4j
@Singleton
class QuestNpcAssociationCatalog
{
	private List<Association> associations = Collections.emptyList();

	@Inject
	QuestNpcAssociationCatalog(Gson gson)
	{
		try (InputStream stream = getClass().getResourceAsStream("/quest_npc_associations.json"))
		{
			if (stream == null)
			{
				log.warn("quest_npc_associations.json missing from classpath");
				return;
			}
			Snapshot snapshot = gson.fromJson(
				new InputStreamReader(stream, StandardCharsets.UTF_8), Snapshot.class);
			if (snapshot != null && snapshot.associations != null)
			{
				associations = Collections.unmodifiableList(snapshot.associations);
			}
		}
		catch (IOException ex)
		{
			log.warn("Failed to load quest_npc_associations.json", ex);
		}
	}

	List<Association> getAssociations()
	{
		return associations;
	}

	static class Association
	{
		String quest;
		String npc;
		boolean startsQuest;
	}

	private static class Snapshot
	{
		List<Association> associations;
	}
}
