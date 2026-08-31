package com.bronzemantcg.restriction;

import com.bronzemantcg.catalog.ResourceNodeCatalog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Predicate;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Pure policy for one exact fishing-spot action. */
public final class FishingRequirementPolicy
{
	private static final String TOOL_ROLE = "tool";
	private static final String HARPOON_CARD = "harpoon";

	private FishingRequirementPolicy()
	{
	}

	public static List<String> missingRequirements(ResourceNodeCatalog.Rule rule,
		FishingRestrictionMode mode, Predicate<String> ownsCard, Set<String> carriedInputs)
	{
		if (rule == null || mode == null || mode == FishingRestrictionMode.OFF)
		{
			return Collections.emptyList();
		}

		Set<String> carried = carriedInputs == null ? Collections.emptySet() : carriedInputs;
		List<String> missing = new ArrayList<>();
		for (ResourceNodeCatalog.CardGroup group : rule.groups)
		{
			if (!TOOL_ROLE.equals(group.role))
			{
				continue;
			}

			List<String> carriedFromGroup = carriedFromGroup(group, carried);
			// With no harpoon carried the game itself decides whether Barbarian Training permits
			// bare-handed fishing. No restricted item can be used in that path.
			if (isHarpoonGroup(group) && carriedFromGroup.isEmpty())
			{
				continue;
			}

			if (!group.isSatisfied(ownsCard))
			{
				addOnce(missing, String.join(" / ", group.displayCards));
				continue;
			}

			// If another alternative satisfies the group, a locked applicable item actually
			// carried must not be allowed to borrow that unlock.
			for (String carriedCard : carriedFromGroup)
			{
				if (!ownsCard.test(carriedCard))
				{
					addOnce(missing, carriedCard);
				}
			}
		}

		if (mode == FishingRestrictionMode.CARD_REQUIRED
			|| mode == FishingRestrictionMode.ALL_CATCHES)
		{
			for (String catchCard : rule.missingRequirements(
				ownsCard, Collections.singleton(TOOL_ROLE),
				mode == FishingRestrictionMode.ALL_CATCHES))
			{
				addOnce(missing, catchCard);
			}
		}
		return missing;
	}

	private static boolean isHarpoonGroup(ResourceNodeCatalog.CardGroup group)
	{
		return group.lowerCards.contains(HARPOON_CARD);
	}

	private static List<String> carriedFromGroup(ResourceNodeCatalog.CardGroup group,
		Set<String> carriedInputs)
	{
		List<String> matches = new ArrayList<>();
		for (String carried : carriedInputs)
		{
			if (carried != null
				&& group.lowerCards.contains(carried.trim().toLowerCase(Locale.ROOT)))
			{
				matches.add(carried);
			}
		}
		return matches;
	}

	private static void addOnce(List<String> missing, String card)
	{
		if (!missing.contains(card))
		{
			missing.add(card);
		}
	}
}
