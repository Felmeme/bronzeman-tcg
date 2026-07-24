package com.bronzemantcg;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
			List<String> duplicates = new ArrayList<>();
			List<String> emptySubcategories = new ArrayList<>();
			Set<String> categoryNames = new HashSet<>();
			for (CategoryDto dto : snapshot.categories)
			{
				if (dto == null || dto.name == null || dto.name.trim().isEmpty())
				{
					continue;
				}
				String categoryName = dto.name.trim();
				if (!categoryNames.add(categoryName.toLowerCase(Locale.ROOT)))
				{
					duplicates.add("top-level category: " + categoryName);
				}

				List<String> items = loadItems(dto.items, validCards, unknown, duplicates,
					categoryName);
				List<Subcategory> subcategories = new ArrayList<>();
				Set<String> subcategoryNames = new HashSet<>();
				if (dto.subcategories != null)
				{
					for (SubcategoryDto subcategoryDto : dto.subcategories)
					{
						if (subcategoryDto == null || subcategoryDto.name == null
							|| subcategoryDto.name.trim().isEmpty())
						{
							continue;
						}
						String subcategoryName = subcategoryDto.name.trim();
						if (!subcategoryNames.add(subcategoryName.toLowerCase(Locale.ROOT)))
						{
							duplicates.add(categoryName + " subcategory: " + subcategoryName);
						}
						List<String> subcategoryItems = loadItems(subcategoryDto.items,
							validCards, unknown, duplicates,
							categoryName + " / " + subcategoryName);
						if (subcategoryItems.isEmpty())
						{
							emptySubcategories.add(categoryName + " / " + subcategoryName);
						}
						subcategories.add(new Subcategory(subcategoryName, subcategoryItems));
					}
				}
				loaded.add(new Category(categoryName, items, subcategories));
			}
			categories = Collections.unmodifiableList(loaded);
			log.info("Loaded {} Important Unlocks categories from snapshot", categories.size());
			if (!unknown.isEmpty())
			{
				log.warn("Important Unlocks: {} name(s) don't match an item card: {}",
					unknown.size(), unknown);
			}
			if (!duplicates.isEmpty())
			{
				log.warn("Important Unlocks: {} duplicate name/assignment(s): {}",
					duplicates.size(), duplicates);
			}
			if (!emptySubcategories.isEmpty())
			{
				log.warn("Important Unlocks: {} empty subcategory/subcategories: {}",
					emptySubcategories.size(), emptySubcategories);
			}
		}
		catch (IOException ex)
		{
			log.warn("Failed to load important_unlocks.json", ex);
		}
	}

	private static List<String> loadItems(List<String> source, Set<String> validCards,
		List<String> unknown, List<String> duplicates, String context)
	{
		if (source == null)
		{
			return Collections.emptyList();
		}

		LinkedHashMap<String, String> unique = new LinkedHashMap<>();
		for (String item : source)
		{
			if (item == null || item.trim().isEmpty())
			{
				continue;
			}
			String name = item.trim();
			String normalized = name.toLowerCase(Locale.ROOT);
			if (unique.putIfAbsent(normalized, name) != null)
			{
				duplicates.add(context + ": " + name);
				continue;
			}
			if (!validCards.contains(name.toLowerCase(Locale.ROOT)))
			{
				unknown.add(name);
			}
		}
		return Collections.unmodifiableList(new ArrayList<>(unique.values()));
	}

	public static class Category
	{
		public final String name;
		public final List<String> items;
		public final List<Subcategory> subcategories;
		public final List<String> allItems;

		Category(String name, List<String> items, List<Subcategory> subcategories)
		{
			this.name = name;
			this.items = Collections.unmodifiableList(items);
			this.subcategories = Collections.unmodifiableList(subcategories);
			LinkedHashMap<String, String> combined = new LinkedHashMap<>();
			for (String item : items)
			{
				combined.putIfAbsent(item.toLowerCase(Locale.ROOT), item);
			}
			for (Subcategory subcategory : subcategories)
			{
				for (String item : subcategory.items)
				{
					combined.putIfAbsent(item.toLowerCase(Locale.ROOT), item);
				}
			}
			this.allItems = Collections.unmodifiableList(new ArrayList<>(combined.values()));
		}
	}

	public static class Subcategory
	{
		public final String name;
		public final List<String> items;

		Subcategory(String name, List<String> items)
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
		List<SubcategoryDto> subcategories;
	}

	private static class SubcategoryDto
	{
		String name;
		List<String> items;
	}
}
