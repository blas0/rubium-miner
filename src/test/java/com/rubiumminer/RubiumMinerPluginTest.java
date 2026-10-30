package com.rubiumminer;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class RubiumMinerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(RubiumMinerPlugin.class);
		RuneLite.main(args);
	}
}
