package com.bronzemantcg.panel;

import com.bronzemantcg.BronzemanTcgConfig;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Remembers that the current RuneScape profile has received a v1-capable OSRS TCG payload.
 * This controls panel presentation only; live ownership and remote-catalogue activation must
 * continue to use the current PluginMessage payload.
 */
@Slf4j
@Singleton
public final class V1PresentationState
{
	static final String CONFIG_KEY = "v1PresentationActive";

	private final Persistence persistence;

	@Inject
	public V1PresentationState(ConfigManager configManager)
	{
		this(new ConfigPersistence(configManager));
	}

	V1PresentationState(Persistence persistence)
	{
		this.persistence = persistence;
	}

	/** Current live capability always wins, even if profile persistence is unavailable. */
	public boolean isActive(boolean liveV1Capable)
	{
		return liveV1Capable || readPersisted();
	}

	/**
	 * Permanently activates v1 presentation for this profile after its first capable payload.
	 *
	 * @return true when this call newly persisted the presentation state
	 */
	public boolean observeLiveCapability(boolean liveV1Capable)
	{
		if (!liveV1Capable || readPersisted())
		{
			return false;
		}
		try
		{
			persistence.write("true");
			return true;
		}
		catch (RuntimeException ex)
		{
			log.warn("Unable to persist v1 panel presentation for this profile", ex);
			return false;
		}
	}

	private boolean readPersisted()
	{
		try
		{
			return Boolean.parseBoolean(persistence.read());
		}
		catch (RuntimeException ex)
		{
			log.warn("Unable to read v1 panel presentation for this profile", ex);
			return false;
		}
	}

	interface Persistence
	{
		String read();

		void write(String value);
	}

	private static final class ConfigPersistence implements Persistence
	{
		private final ConfigManager configManager;

		private ConfigPersistence(ConfigManager configManager)
		{
			this.configManager = configManager;
		}

		@Override
		public String read()
		{
			return configManager.getRSProfileConfiguration(
				BronzemanTcgConfig.GROUP, CONFIG_KEY);
		}

		@Override
		public void write(String value)
		{
			configManager.setRSProfileConfiguration(
				BronzemanTcgConfig.GROUP, CONFIG_KEY, value);
		}
	}
}
