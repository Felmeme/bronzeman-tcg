package com.bronzemantcg;

import com.google.gson.Gson;
import javax.inject.Inject;
import javax.inject.Singleton;

/** NPC-name -> monster-card-variants catalog. See {@link CardNameCatalog}. */
@Singleton
public class TrackedMonsterCatalog extends CardNameCatalog
{
	@Inject
	public TrackedMonsterCatalog(Gson gson)
	{
		super(gson, "/tracked_monster_names.json", "NPCs");
	}
}
