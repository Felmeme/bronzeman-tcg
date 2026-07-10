package com.bronzemantcg;

import com.google.gson.Gson;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Item-name -> item-card catalog (1:1 today, no bracketed variants). See {@link CardNameCatalog}. */
@Singleton
public class TrackedItemCatalog extends CardNameCatalog
{
	@Inject
	public TrackedItemCatalog(Gson gson)
	{
		super(gson, "/tracked_item_names.json", "items");
	}
}
