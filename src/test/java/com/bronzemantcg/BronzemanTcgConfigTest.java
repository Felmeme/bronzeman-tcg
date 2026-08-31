package com.bronzemantcg;

import java.lang.reflect.Method;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

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
}
