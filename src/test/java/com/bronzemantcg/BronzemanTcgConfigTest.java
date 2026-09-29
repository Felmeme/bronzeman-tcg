package com.bronzemantcg;

import java.lang.reflect.Method;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BronzemanTcgConfigTest
{
	@Test
	public void remoteCatalogNetworkingIsOptInAndWarned() throws Exception
	{
		BronzemanTcgConfig defaults = new BronzemanTcgConfig() { };
		assertFalse(defaults.allowRemoteCatalog());

		Method method = BronzemanTcgConfig.class.getMethod("allowRemoteCatalog");
		ConfigItem item = method.getAnnotation(ConfigItem.class);
		assertNotNull(item);
		assertFalse(item.warning().trim().isEmpty());
	}

	@Test
	public void betaPlayerLookupIsExplicitOptInAndWarned() throws Exception
	{
		BronzemanTcgConfig defaults = new BronzemanTcgConfig() { };
		assertFalse(defaults.allowBetaCardLookup());

		Method method = BronzemanTcgConfig.class.getMethod("allowBetaCardLookup");
		ConfigItem item = method.getAnnotation(ConfigItem.class);
		assertNotNull(item);
		assertEquals("allowBetaCardLookup", item.keyName());
		assertFalse(item.warning().trim().isEmpty());
		assertTrue(item.description().contains("No request is made automatically"));
	}

	@Test
	public void betaVariantUnlocksRemainEnabledByDefault() throws Exception
	{
		BronzemanTcgConfig defaults = new BronzemanTcgConfig() { };
		assertFalse(defaults.disableBetaVariants());

		Method method = BronzemanTcgConfig.class.getMethod("disableBetaVariants");
		ConfigItem item = method.getAnnotation(ConfigItem.class);
		assertNotNull(item);
		assertEquals("disableBetaVariants", item.keyName());
		assertTrue(item.description().contains("does not delete the Beta cache"));
		assertTrue(item.description().contains("Current OSRS TCG and shared ownership still apply"));
	}
}
