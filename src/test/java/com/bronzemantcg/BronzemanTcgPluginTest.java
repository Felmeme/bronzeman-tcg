package com.bronzemantcg;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BronzemanTcgPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BronzemanTcgPlugin.class);
		RuneLite.main(args);
	}
}
