package com.bronzemantcg;

import com.google.gson.Gson;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/** NPC-name -> monster-card-variants catalog. See {@link CardNameCatalog}. */
@Singleton
public class TrackedMonsterCatalog extends CardNameCatalog
{
	private static final Map<String, Set<String>> QUEST_NPC_CARD_ALIASES = Map.of(
		"avan", Set.of("man"),
		"afflicted(ulsquire)", Set.of("afflicted"),
		"ulsquire shauncy", Set.of("afflicted"));

	@Inject
	public TrackedMonsterCatalog(Gson gson)
	{
		super(gson, "/tracked_monster_names.json", "NPCs");
	}

	@Override
	public Set<String> getCardVariantsLowerCase(String entityName)
	{
		if (entityName != null)
		{
			Set<String> alias = QUEST_NPC_CARD_ALIASES.get(
				entityName.trim().toLowerCase(Locale.ROOT));
			if (alias != null)
			{
				return alias;
			}
		}
		return super.getCardVariantsLowerCase(entityName);
	}
}
