package com.bronzemantcg;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/** Read-only, generated card information used by the side-panel browser. */
@Slf4j
@Singleton
class CardKnowledgeCatalog
{
	private static final String RESOURCE = "/card_knowledge.json.gz";
	private final Map<String, Card> cardsByLowerName;

	@Inject
	CardKnowledgeCatalog(Gson gson)
	{
		Map<String, Card> loaded = new HashMap<>();
		try (InputStream raw = CardKnowledgeCatalog.class.getResourceAsStream(RESOURCE))
		{
			if (raw == null)
			{
				throw new IOException("Missing resource " + RESOURCE);
			}
			try (GZIPInputStream gzip = new GZIPInputStream(raw);
				 InputStreamReader reader = new InputStreamReader(gzip, StandardCharsets.UTF_8))
			{
				Snapshot snapshot = gson.fromJson(reader, Snapshot.class);
				if (snapshot != null && snapshot.cards != null)
				{
					for (Card card : snapshot.cards)
					{
						if (card != null && card.name != null)
						{
							loaded.put(card.name.toLowerCase(Locale.ROOT), card);
						}
					}
				}
			}
		}
		catch (IOException | RuntimeException ex)
		{
			log.warn("Could not load card knowledge snapshot", ex);
		}
		cardsByLowerName = Collections.unmodifiableMap(loaded);
		log.debug("Loaded {} card knowledge entries", cardsByLowerName.size());
	}

	Card find(String cardName)
	{
		return cardName == null ? null
			: cardsByLowerName.get(cardName.toLowerCase(Locale.ROOT));
	}

	private static class Snapshot
	{
		private List<Card> cards;
	}

	static class Card
	{
		String name;
		String type;
		List<Integer> ids = Collections.emptyList();
		String examine;
		List<String> categories = Collections.emptyList();
		Integer combatLevel;
		Integer slayerLevel;
		List<Drop> drops = Collections.emptyList();
		Sources sources;

		boolean isResource()
		{
			return "resource".equals(type);
		}

		int primaryId()
		{
			return ids == null || ids.isEmpty() ? -1 : ids.get(0);
		}

		List<String> categoryLabels()
		{
			return categories == null ? new ArrayList<>() : categories;
		}
	}

	static class Drop
	{
		String card;
		String rarity;
		String fraction;
		boolean fromRdt;
	}

	static class Sources
	{
		List<MonsterSource> monsters = Collections.emptyList();
		List<MonsterSource> rareDropTable = Collections.emptyList();
		Gathering gathering;
		Production production;
		List<UsedIn> usedIn = Collections.emptyList();
		List<String> spawns = Collections.emptyList();
		List<Shop> shops = Collections.emptyList();
		List<String> clueTiers = Collections.emptyList();
	}

	static class MonsterSource
	{
		String card;
		String rarity;
		String fraction;
		boolean confirmedByMonsterDrops;
	}

	static class Gathering
	{
		String skill;
		Integer level;
		String method;
		String tool;
		Number xp;
	}

	static class Production
	{
		String skill;
		Integer level;
		Number xp;
		String facilities;
		List<String> tools = Collections.emptyList();
		List<Ingredient> ingredients = Collections.emptyList();
		Integer outputQuantity;
	}

	static class Ingredient
	{
		String item;
		Number quantity;
		boolean hasCard;
	}

	static class UsedIn
	{
		String card;
		Number quantity;
	}

	static class Shop
	{
		String seller;
		Number price;
		String currency;
	}
}
