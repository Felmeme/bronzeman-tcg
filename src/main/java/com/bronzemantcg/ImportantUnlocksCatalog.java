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

/** Owner-curated item-card groups for the Important Unlocks panel tab. */
@Slf4j
@Singleton
public class ImportantUnlocksCatalog
{
	private List<Category> categories = Collections.emptyList();

	@Inject
	public ImportantUnlocksCatalog(Gson gson, TrackedItemCatalog itemCatalog)
	{
		load(gson, itemCatalog);
	}

	public List<Category> getCategories()
	{
		return categories;
	}

	private void load(Gson gson, TrackedItemCatalog itemCatalog)
	{
		Set<String> validCards = new HashSet<>();
		for (Set<String> cards : itemCatalog.getEntityToCards().values())
		{
			validCards.addAll(cards);
		}
		try (InputStream stream = getClass().getResourceAsStream("/important_unlocks.json"))
		{
			if (stream == null)
			{
				log.warn("important_unlocks.json not present; Important Unlocks tab will be empty.");
				return;
			}
			Snapshot snapshot = gson.fromJson(
				new InputStreamReader(stream, StandardCharsets.UTF_8), Snapshot.class);
			if (snapshot == null || snapshot.categories == null)
			{
				return;
			}
			List<Category> loaded = new ArrayList<>();
			List<String> unknown = new ArrayList<>();
			for (CategoryDto dto : snapshot.categories)
			{
				if (dto == null || dto.name == null || dto.name.trim().isEmpty() || dto.items == null)
				{
					continue;
				}
				List<String> items = new ArrayList<>();
				for (String item : dto.items)
				{
					if (item == null || item.trim().isEmpty())
					{
						continue;
					}
					String name = item.trim();
					items.add(name);
					if (!validCards.contains(name.toLowerCase(Locale.ROOT)))
					{
						unknown.add(name);
					}
				}
				loaded.add(new Category(dto.name.trim(), items));
			}
			categories = Collections.unmodifiableList(loaded);
			log.info("Loaded {} Important Unlocks categories from snapshot", categories.size());
			if (!unknown.isEmpty())
			{
				log.warn("Important Unlocks: {} name(s) don't match an item card: {}",
					unknown.size(), unknown);
			}
		}
		catch (IOException ex)
		{
			log.warn("Failed to load important_unlocks.json", ex);
		}
	}

	public static class Category
	{
		public final String name;
		public final List<String> items;

		Category(String name, List<String> items)
		{
			this.name = name;
			this.items = Collections.unmodifiableList(items);
		}
	}

	private static class Snapshot
	{
		List<CategoryDto> categories;
	}

	private static class CategoryDto
	{
		String name;
		List<String> items;
	}
}
