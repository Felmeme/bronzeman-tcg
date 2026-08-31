package com.bronzemantcg.feature;

import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;

@Singleton
public final class ChatFeedbackService
{
	private static final long CHAT_THROTTLE_MS = 1_200L;
	private static final int MAX_LISTED_MISSING_CARDS = 4;

	private final ChatMessageManager chatMessageManager;

	private long lastBlockMessageMs;

	@Inject
	public ChatFeedbackService(ChatMessageManager chatMessageManager)
	{
		this.chatMessageManager = chatMessageManager;
	}

	public void sendBlockedMessage(String entityName)
	{
		if (!reserveBlockedMessage())
		{
			return;
		}

		queueChat(String.format(Locale.US,
			"[Bronzeman TCG] You haven't collected the %s card yet - open more packs!",
			entityName));
	}

	public void sendBlockedCardsMessage(List<String> missingCards)
	{
		if (missingCards.size() == 1)
		{
			sendBlockedMessage(missingCards.get(0));
			return;
		}

		if (!reserveBlockedMessage())
		{
			return;
		}

		// Insanity mode can be missing dozens of seeds; keep the chat line readable.
		String listed = String.join(", ", missingCards.subList(
			0, Math.min(missingCards.size(), MAX_LISTED_MISSING_CARDS)));

		if (missingCards.size() > MAX_LISTED_MISSING_CARDS)
		{
			listed += String.format(Locale.US, " and %d more",
				missingCards.size() - MAX_LISTED_MISSING_CARDS);
		}

		queueChat(String.format(Locale.US,
			"[Bronzeman TCG] You haven't collected these cards yet: %s - open more packs!",
			listed));
	}

	/**
	 * CONSOLE type via the chat manager: raw GAMEMESSAGEs from addChatMessage get hidden
	 * when the player's Game chat tab is set to "Filter".
	 */
	public void queueChat(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(message)
			.build());
	}

	private boolean reserveBlockedMessage()
	{
		long now = System.currentTimeMillis();
		if (now - lastBlockMessageMs < CHAT_THROTTLE_MS)
		{
			return false;
		}

		lastBlockMessageMs = now;
		return true;
	}
}
