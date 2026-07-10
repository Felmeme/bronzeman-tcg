package com.bronzemantcg;

import com.google.inject.Provides;
import java.util.Locale;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

/**
 * Bronzeman-style restriction driven by the OSRS TCG plugin's collection:
 * you may only attack NPCs whose card you have already pulled.
 *
 * Interop is read-only via osrs-tcg's persisted ConfigManager state
 * (see {@link TcgCollectionReader}); there is no compile-time dependency,
 * so both plugins can be installed independently from the Plugin Hub.
 */
@Slf4j
@PluginDescriptor(
	name = "Bronzeman TCG",
	description = "Restricts attacking NPCs until you've collected their card in the OSRS TCG plugin",
	tags = {"bronzeman", "tcg", "restriction", "ironman", "challenge"}
)
public class BronzemanTcgPlugin extends Plugin
{
	private static final String ATTACK_OPTION = "attack";
	private static final long CHAT_THROTTLE_MS = 1_200L;

	@Inject
	private Client client;

	@Inject
	private BronzemanTcgConfig config;

	@Inject
	private TcgCollectionReader collectionReader;

	@Inject
	private TrackedMonsterCatalog monsterCatalog;

	private long lastBlockMessageMs;

	@Override
	protected void startUp()
	{
		collectionReader.invalidate();
		log.info("Bronzeman TCG started. Tracking {} TCG monster cards.", monsterCatalog.size());
	}

	@Override
	protected void shutDown()
	{
		log.info("Bronzeman TCG stopped.");
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		// New account/profile: never let a previous profile's collection linger.
		collectionReader.invalidate();
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		NPC npc = event.getMenuEntry().getNpc();
		if (npc == null)
		{
			return;
		}

		if (!isRestrictedInteraction(event))
		{
			return;
		}

		String npcName = resolveNpcName(npc);
		if (npcName == null || npcName.isEmpty())
		{
			return;
		}

		// Not part of the TCG catalog at all -> never restrict (it could never be unlocked).
		if (!monsterCatalog.isTracked(npcName))
		{
			return;
		}

		// Owned (normal or foil) -> allowed.
		if (collectionReader.getOwnedCardNamesLowerCase().contains(npcName.toLowerCase(Locale.ROOT)))
		{
			return;
		}

		event.consume();
		sendBlockedMessage(npcName);
	}

	private boolean isRestrictedInteraction(MenuOptionClicked event)
	{
		MenuAction action = event.getMenuAction();
		if (action == null)
		{
			return false;
		}

		switch (action)
		{
			case NPC_FIRST_OPTION:
			case NPC_SECOND_OPTION:
			case NPC_THIRD_OPTION:
			case NPC_FOURTH_OPTION:
			case NPC_FIFTH_OPTION:
			{
				if (!config.restrictAttacks())
				{
					return false;
				}
				String option = event.getMenuOption();
				return option != null
					&& ATTACK_OPTION.equals(Text.removeTags(option).trim().toLowerCase(Locale.ROOT));
			}
			case WIDGET_TARGET_ON_NPC:
				// Spell cast on NPC, or item used on NPC.
				return config.restrictSpellCasts();
			default:
				return false;
		}
	}

	private String resolveNpcName(NPC npc)
	{
		// Prefer the transformed composition so multi-form NPCs report their current form's name.
		NPCComposition composition = npc.getTransformedComposition();
		String name = composition != null ? composition.getName() : npc.getName();
		if (name == null)
		{
			return null;
		}
		return Text.removeTags(name).trim();
	}

	private void sendBlockedMessage(String npcName)
	{
		if (!config.chatFeedback())
		{
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastBlockMessageMs < CHAT_THROTTLE_MS)
		{
			return;
		}
		lastBlockMessageMs = now;
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			String.format(Locale.US,
				"[Bronzeman TCG] You haven't collected the %s card yet - open more packs!", npcName),
			null);
	}

	@Provides
	BronzemanTcgConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BronzemanTcgConfig.class);
	}
}
