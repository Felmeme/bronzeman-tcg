package com.bronzemantcg;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Data-driven foil cascades. A listed foil card unlocks every item in the listed slots. */
@Singleton
public class FoilUnlockCatalog
{
	private Map<String, List<String>> slotCascades = Collections.emptyMap();
	private Map<String, String> itemSlots = Collections.emptyMap();

	@Inject
	public FoilUnlockCatalog(Gson gson)
	{
		try (InputStream rules = getClass().getResourceAsStream("/foil_unlocks.json");
			 InputStream slots = getClass().getResourceAsStream("/equipment_slots.json"))
		{
			Rules parsedRules = gson.fromJson(new InputStreamReader(rules, StandardCharsets.UTF_8), Rules.class);
			Slots parsedSlots = gson.fromJson(new InputStreamReader(slots, StandardCharsets.UTF_8), Slots.class);
			if (parsedRules != null && parsedRules.slotCascades != null)
			{
				slotCascades = parsedRules.slotCascades;
			}
			if (parsedSlots != null && parsedSlots.itemSlots != null)
			{
				itemSlots = parsedSlots.itemSlots;
			}
		}
		catch (Exception ignored)
		{
			// Missing optional data means exact-card behavior, never an accidental broad unlock.
		}
	}

	public boolean isUnlockedByFoil(String itemName, Set<String> ownedFoils)
	{
		if (itemName == null || ownedFoils == null || ownedFoils.isEmpty())
		{
			return false;
		}
		String slot = itemSlots.get(itemName.trim().toLowerCase(Locale.ROOT));
		if (slot == null)
		{
			return false;
		}
		for (String foil : ownedFoils)
		{
			List<String> slots = slotCascades.get(foil);
			if (slots != null && slots.contains(slot))
			{
				return true;
			}
		}
		return false;
	}

	public List<FoilSummary> summarize(Set<String> ownedFoils)
	{
		List<FoilSummary> summaries = new ArrayList<>();
		for (String foil : ownedFoils)
		{
			List<String> slots = slotCascades.get(foil);
			if (slots == null)
			{
				continue;
			}
			int count = 0;
			for (String slot : itemSlots.values())
			{
				if (slots.contains(slot))
				{
					count++;
				}
			}
			summaries.add(new FoilSummary(foil, new ArrayList<>(slots), count));
		}
		return Collections.unmodifiableList(summaries);
	}

	/** Every equipment item inherited through the active foil slot cascades. */
	public Set<String> inheritedItemNames(Set<String> ownedFoils)
	{
		if (ownedFoils == null || ownedFoils.isEmpty())
		{
			return Collections.emptySet();
		}
		java.util.HashSet<String> inherited = new java.util.HashSet<>();
		for (Map.Entry<String, String> item : itemSlots.entrySet())
		{
			for (String foil : ownedFoils)
			{
				List<String> slots = slotCascades.get(foil);
				if (slots != null && slots.contains(item.getValue()))
				{
					inherited.add(item.getKey());
					break;
				}
			}
		}
		return Collections.unmodifiableSet(inherited);
	}

	public static final class FoilSummary
	{
		public final String card;
		public final List<String> slots;
		public final int inheritedItems;

		private FoilSummary(String card, List<String> slots, int inheritedItems)
		{
			this.card = card;
			this.slots = Collections.unmodifiableList(slots);
			this.inheritedItems = inheritedItems;
		}
	}

	private static class Rules { Map<String, List<String>> slotCascades; }
	private static class Slots { Map<String, String> itemSlots; }
}
