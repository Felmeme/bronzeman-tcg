package com.bronzemantcg;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Informational PvM content readiness data for the side panel: the monster cards needed
 * to fight everything in each piece of instanced content (Fight Caves, Inferno, raids...).
 * Loaded from resources/content_cards.json; reuses the quest checklist model since the
 * panel renders both identically.
 */
@Slf4j
@Singleton
public class ContentCatalog
{
	private List<QuestCatalog.QuestEntry> contents = Collections.emptyList();

	@Inject
	public ContentCatalog(Gson gson)
	{
		load(gson);
	}

	public List<QuestCatalog.QuestEntry> getContents()
	{
		return contents;
	}

	public int size()
	{
		return contents.size();
	}

	private void load(Gson gson)
	{
		try (InputStream stream = getClass().getResourceAsStream("/content_cards.json"))
		{
			if (stream == null)
			{
				log.info("content_cards.json not present; PvM content panel section will be empty.");
				return;
			}
			Snapshot snapshot = gson.fromJson(
				new InputStreamReader(stream, StandardCharsets.UTF_8), Snapshot.class);
			if (snapshot == null || snapshot.contents == null)
			{
				return;
			}
			List<QuestCatalog.QuestEntry> loaded = new ArrayList<>();
			for (ContentDto dto : snapshot.contents)
			{
				if (dto == null || dto.name == null || dto.name.trim().isEmpty())
				{
					continue;
				}
				List<QuestCatalog.Requirement> requirements = new ArrayList<>();
				if (dto.monsterCards != null)
				{
					for (String card : dto.monsterCards)
					{
						if (card != null && !card.trim().isEmpty())
						{
							requirements.add(new QuestCatalog.Requirement(card.trim(),
								Collections.singletonList(card.trim())));
						}
					}
				}
				loaded.add(new QuestCatalog.QuestEntry(dto.name.trim(), false, requirements,
					dto.notes == null ? "" : dto.notes.trim()));
			}
			contents = Collections.unmodifiableList(loaded);
			log.info("Loaded {} PvM content rosters from snapshot", contents.size());
		}
		catch (IOException ex)
		{
			log.warn("Failed to load content_cards.json", ex);
		}
	}

	private static class Snapshot
	{
		List<ContentDto> contents;
	}

	private static class ContentDto
	{
		String name;
		List<String> monsterCards;
		String notes;
	}
}
