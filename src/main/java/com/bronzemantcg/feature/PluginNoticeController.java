package com.bronzemantcg.feature;

import com.bronzemantcg.interop.TcgCollectionReader;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;

@Singleton
public final class PluginNoticeController
{
	// Game ticks are 600ms. ~9s clears the login chat burst; ~30min between reminders
	// is often enough to notice, rare enough not to nag.
	private static final int WELCOME_DELAY_TICKS = 15;
	private static final int REMINDER_TICKS = 3000;
	// ~60s. Checked far more often than the other notices: switching OSRS TCG off is a
	// quieter way to dodge restrictions than switching this plugin off, so it should
	// surface quickly rather than sit unnoticed for half an hour.
	private static final int REQUIRED_PLUGIN_TICKS = 100;
	// OSRS TCG's PluginDescriptor name. Checking PluginManager keeps this independent
	// from whichever collection transport is currently available.
	private static final String REQUIRED_PLUGIN = "OSRS TCG";

	private final Client client;
	private final ChatFeedbackService chatFeedbackService;
	private final TcgCollectionReader collectionReader;
	private final PluginManager pluginManager;

	private boolean welcomeShown;
	// Countdowns in game ticks; negative means idle.
	private int welcomeDelayTicks = -1;
	private int reminderTicks = -1;
	private int requiredPluginTicks = -1;

	@Inject
	public PluginNoticeController(Client client, ChatFeedbackService chatFeedbackService,
		TcgCollectionReader collectionReader, PluginManager pluginManager)
	{
		this.client = client;
		this.chatFeedbackService = chatFeedbackService;
		this.collectionReader = collectionReader;
		this.pluginManager = pluginManager;
	}

	public void startUp()
	{
		welcomeShown = false;
		welcomeDelayTicks = -1;
		reminderTicks = -1;
		requiredPluginTicks = -1;
		scheduleWelcome();
	}

	public void onGameStateChanged(GameState gameState)
	{
		switch (gameState)
		{
			case LOGGED_IN:
				scheduleWelcome();
				break;
			case LOGIN_SCREEN:
				// Re-arm for the next login. World hops run HOPPING -> LOADING -> LOGGED_IN
				// without passing through the login screen, so hopping never re-greets.
				welcomeShown = false;
				welcomeDelayTicks = -1;
				break;
			default:
				break;
		}
	}

	/**
	 * Drives the delayed greeting and recurring health notices. Game ticks only arrive
	 * while logged in, so a logged-out session never counts down.
	 */
	public void onGameTick()
	{
		if (welcomeDelayTicks >= 0 && --welcomeDelayTicks < 0)
		{
			// The greeting posts every notice itself, so nothing else runs this tick.
			showWelcomeMessage();
			return;
		}
		if (reminderTicks >= 0 && --reminderTicks < 0)
		{
			postPeriodicNotices();
		}
		if (requiredPluginTicks >= 0 && --requiredPluginTicks < 0)
		{
			requiredPluginTicks = REQUIRED_PLUGIN_TICKS;
			warnRequiredPluginDisabled();
		}
	}

	/**
	 * Arm the greeting countdown. Firing on the login event itself buries the message
	 * under the client's own login spam - clan broadcasts, welcome text and other
	 * plugins all post in the first few seconds - so it waits them out instead.
	 */
	private void scheduleWelcome()
	{
		if (welcomeShown || welcomeDelayTicks >= 0 || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		welcomeDelayTicks = WELCOME_DELAY_TICKS;
	}

	/** One greeting per login, once the login chat has settled. Not optional - it is a
	 * single line that confirms the plugin loaded and shows collection progress. */
	private void showWelcomeMessage()
	{
		welcomeShown = true;
		reminderTicks = REMINDER_TICKS;
		requiredPluginTicks = REQUIRED_PLUGIN_TICKS;

		if (collectionReader.isStateAvailable())
		{
			chatFeedbackService.queueChat("[Bronzeman TCG] Plugin is active - Good luck on the pulls!");
			if (!collectionReader.hasApiData())
			{
				// Fallback path (pre-API OSRS TCG, or its API has not answered yet): the config
				// read lags behind card pulls, so nudge the player to relog. Self-retiring once
				// the API connects.
				chatFeedbackService.queueChat("[Bronzeman TCG] Not Connected to OSRS TCG API - Please relog if you are "
					+ "missing new card unlocks. Last known collection still active. Check OSRS TCG is enabled.");
			}
		}
		else
		{
			// Greeting with zero collected would misreport an unread collection as empty.
			warnCollectionUnreadable();
		}
		warnRequiredPluginDisabled();
	}

	/** Re-checks periodically so the collection warning keeps surfacing while it applies. */
	private void postPeriodicNotices()
	{
		reminderTicks = REMINDER_TICKS;
		warnCollectionUnreadable();
		// warnRequiredPluginDisabled() deliberately absent - it has its own faster timer.
	}

	/**
	 * Enforcement stands down when the collection cannot be read, so the player has to
	 * be told. The notice repeats because its usual cause needs the player to act.
	 */
	private void warnCollectionUnreadable()
	{
		if (collectionReader.isStateAvailable())
		{
			return;
		}
		chatFeedbackService.queueChat("[Bronzeman TCG] - Can't read your OSRS TCG collection, so restrictions are "
			+ "OFF. Check that the OSRS TCG plugin is installed and up to date, and that you've "
			+ "opened at least one pack.");
	}

	/**
	 * Disabling OSRS TCG leaves its stored collection intact, so restrictions keep using
	 * it. The player still needs telling because they have stopped earning new cards.
	 */
	private void warnRequiredPluginDisabled()
	{
		// An unreadable collection already gets its own, more urgent warning.
		if (isRequiredPluginEnabled() || !collectionReader.isStateAvailable())
		{
			return;
		}
		chatFeedbackService.queueChat("[Bronzeman TCG] - The OSRS TCG plugin is turned off. Restrictions are still "
			+ "active using your last known collection, but you won't earn any new cards until "
			+ "you turn it back on.");
	}

	/**
	 * Several plugin instances can share a display name (a disabled hub copy alongside a
	 * sideloaded dev build), so every match is checked rather than trusting the first one.
	 */
	private boolean isRequiredPluginEnabled()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
			if (descriptor != null && REQUIRED_PLUGIN.equals(descriptor.name())
				&& pluginManager.isPluginEnabled(plugin))
			{
				return true;
			}
		}
		return false;
	}
}
