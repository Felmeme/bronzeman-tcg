package com.bronzemantcg.interop;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

/** Coordinates the read-only PluginMessage ownership API exposed by OSRS TCG. */
@Slf4j
@Singleton
public final class OsrsTcgInteropService
{
	private static final String API_NAMESPACE = "osrstcg";
	private static final String API_QUERY = "query-owned-names";
	private static final String API_REPLY = "owned-names";
	private static final String API_CHANGED = "owned-names-changed";
	private static final String API_NAMES_KEY = "ownedNames";
	private static final String API_ITEM_IDS_KEY = "ownedItemIds";
	private static final String API_NPC_IDS_KEY = "ownedNpcIds";
	private static final String API_GROUP_KEY = "groupKey";
	// ~60s between retries while unanswered. Older OSRS TCG versions simply never answer,
	// and TcgCollectionReader continues serving their persisted-state fallback.
	private static final int QUERY_RETRY_TICKS = 100;

	private final TcgCollectionReader collectionReader;
	private final EventBus eventBus;

	private int queryTicks = -1;

	@Inject
	public OsrsTcgInteropService(TcgCollectionReader collectionReader, EventBus eventBus)
	{
		this.collectionReader = collectionReader;
		this.eventBus = eventBus;
	}

	/**
	 * Drops any API snapshot retained from a previous run and arms the first query. The
	 * query is posted from a tick because this plugin's subscribers are registered only
	 * after startUp returns, and EventBus posting is synchronous.
	 */
	public void startUp()
	{
		resetForCurrentProfile();
	}

	/** Drops the previous profile's ownership and asks OSRS TCG for the new profile. */
	public void onProfileChanged()
	{
		resetForCurrentProfile();
	}

	/** Posts the initial/retry query and stops polling once an API payload is accepted. */
	public void onGameTick()
	{
		if (queryTicks < 0 || --queryTicks >= 0)
		{
			return;
		}

		if (!collectionReader.hasApiData())
		{
			eventBus.post(new PluginMessage(API_NAMESPACE, API_QUERY));
		}
		// EventBus.post is synchronous, so an answered query has already installed API
		// data by this point. Push updates keep it current after the first reply.
		queryTicks = collectionReader.hasApiData() ? -1 : QUERY_RETRY_TICKS;
	}

	/** Validates and applies an OSRS TCG ownership reply or unsolicited push. */
	public UpdateResult onPluginMessage(PluginMessage event)
	{
		if (event == null || !API_NAMESPACE.equals(event.getNamespace())
			|| (!API_REPLY.equals(event.getName()) && !API_CHANGED.equals(event.getName())))
		{
			return UpdateResult.IGNORED;
		}

		Map<String, Object> data = event.getData();
		Object names = data == null ? null : data.get(API_NAMES_KEY);
		if (!(names instanceof List))
		{
			return UpdateResult.IGNORED;
		}

		boolean firstPayload = !collectionReader.hasApiData();
		Object itemIds = data.get(API_ITEM_IDS_KEY);
		Object npcIds = data.get(API_NPC_IDS_KEY);
		Object groupKey = data.get(API_GROUP_KEY);
		collectionReader.onApiOwnership((List<?>) names,
			itemIds instanceof List ? (List<?>) itemIds : null,
			npcIds instanceof List ? (List<?>) npcIds : null,
			groupKey instanceof String ? (String) groupKey : null);

		if (firstPayload)
		{
			log.info("osrs-tcg PluginMessage API active; collection now push-updated.");
			return UpdateResult.FIRST_UPDATE;
		}
		return UpdateResult.UPDATED;
	}

	private void resetForCurrentProfile()
	{
		collectionReader.invalidate();
		queryTicks = 0;
	}

	public enum UpdateResult
	{
		IGNORED(false, false),
		UPDATED(true, false),
		FIRST_UPDATE(true, true);

		private final boolean accepted;
		private final boolean firstPayload;

		UpdateResult(boolean accepted, boolean firstPayload)
		{
			this.accepted = accepted;
			this.firstPayload = firstPayload;
		}

		public boolean isAccepted()
		{
			return accepted;
		}

		public boolean isFirstPayload()
		{
			return firstPayload;
		}
	}
}
