package com.bronzemantcg;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Transcript-derived, informational Faux Cardcore preset. */
@Singleton
final class FauxCardcoreProfile
{
	private String profileName = "Faux Cardcore";
	private List<Entry> rules = Collections.emptyList();
	private List<Entry> principles = Collections.emptyList();
	private List<Entry> routes = Collections.emptyList();
	private List<String> highImpactCards = Collections.emptyList();

	@Inject
	FauxCardcoreProfile(Gson gson)
	{
		try (InputStream stream = getClass().getResourceAsStream("/faux_cardcore.json"))
		{
			if (stream != null)
			{
				FauxCardcoreProfile loaded = gson.fromJson(
					new InputStreamReader(stream, StandardCharsets.UTF_8), FauxCardcoreProfile.class);
				if (loaded != null)
				{
					profileName = loaded.profileName;
					rules = immutable(loaded.rules);
					principles = immutable(loaded.principles);
					routes = immutable(loaded.routes);
					highImpactCards = loaded.highImpactCards == null
						? Collections.emptyList() : Collections.unmodifiableList(loaded.highImpactCards);
				}
			}
		}
		catch (Exception ignored)
		{
			// The planner remains usable with its built-in goal graph if the profile is absent.
		}
	}

	private FauxCardcoreProfile()
	{
	}

	String getProfileName()
	{
		return profileName;
	}

	List<Entry> getRules()
	{
		return rules;
	}

	List<Entry> getPrinciples()
	{
		return principles;
	}

	List<Entry> getRoutes()
	{
		return routes;
	}

	List<String> getHighImpactCards()
	{
		return highImpactCards;
	}

	private static List<Entry> immutable(List<Entry> entries)
	{
		return entries == null ? Collections.emptyList() : Collections.unmodifiableList(entries);
	}

	static final class Entry
	{
		String title;
		String detail;
		String sourceUrl;
	}
}
