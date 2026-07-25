package com.bronzemantcg;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/** Owner-curated Monster-card groups for the PvM panel's Monsters by Area section. */
@Slf4j
@Singleton
public class MonsterAreaCatalog
{
	private List<Area> areas = Collections.emptyList();

	@Inject
	public MonsterAreaCatalog(Gson gson, TrackedMonsterCatalog monsterCatalog)
	{
		load(gson, monsterCatalog);
	}

	public List<Area> getAreas()
	{
		return areas;
	}

	private void load(Gson gson, TrackedMonsterCatalog monsterCatalog)
	{
		Set<String> validCards = new HashSet<>();
		for (Set<String> cards : monsterCatalog.getEntityToCards().values())
		{
			for (String card : cards)
			{
				validCards.add(card.toLowerCase(Locale.ROOT));
			}
		}

		try (InputStream stream = getClass().getResourceAsStream("/monster_areas.json"))
		{
			if (stream == null)
			{
				log.warn("monster_areas.json not present; Monsters by Area will be empty.");
				return;
			}
			Snapshot snapshot = gson.fromJson(
				new InputStreamReader(stream, StandardCharsets.UTF_8), Snapshot.class);
			if (snapshot == null || snapshot.areas == null)
			{
				return;
			}

			List<Area> loaded = new ArrayList<>();
			List<String> unknown = new ArrayList<>();
			Set<String> areaNames = new HashSet<>();
			for (AreaDto dto : snapshot.areas)
			{
				if (dto == null || dto.name == null || dto.name.trim().isEmpty()
					|| dto.monsterCards == null)
				{
					continue;
				}
				String areaName = dto.name.trim();
				if (!areaNames.add(areaName.toLowerCase(Locale.ROOT)))
				{
					log.warn("Monsters by Area contains duplicate area '{}'", areaName);
					continue;
				}

				List<String> cards = new ArrayList<>();
				Set<String> seen = new HashSet<>();
				for (String card : dto.monsterCards)
				{
					if (card == null || card.trim().isEmpty())
					{
						continue;
					}
					String clean = card.trim();
					String lower = clean.toLowerCase(Locale.ROOT);
					if (!seen.add(lower))
					{
						continue;
					}
					cards.add(clean);
					if (!validCards.contains(lower))
					{
						unknown.add(areaName + ": " + clean);
					}
				}
				loaded.add(new Area(areaName, cards));
			}
			areas = Collections.unmodifiableList(loaded);
			log.info("Loaded {} Monsters by Area groups", areas.size());
			if (!unknown.isEmpty())
			{
				log.warn("Monsters by Area: {} name(s) don't match a Monster card: {}",
					unknown.size(), unknown);
			}
		}
		catch (IOException ex)
		{
			log.warn("Failed to load monster_areas.json", ex);
		}
	}

	public static class Area
	{
		public final String name;
		public final List<String> monsterCards;

		Area(String name, List<String> monsterCards)
		{
			this.name = name;
			this.monsterCards = Collections.unmodifiableList(monsterCards);
		}
	}

	private static class Snapshot
	{
		List<AreaDto> areas;
	}

	private static class AreaDto
	{
		String name;
		List<String> monsterCards;
	}
}
