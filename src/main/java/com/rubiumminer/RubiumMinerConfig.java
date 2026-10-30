package com.rubiumminer;

import java.awt.Color;
import lombok.AllArgsConstructor;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(RubiumMinerConfig.GROUP)
public interface RubiumMinerConfig extends Config
{
	String GROUP = "rubiumminer";

	/** Keys from earlier versions, cleared on startup so they don't linger in the profile. */
	String[] OBSOLETE_KEYS = {
		"highlightActiveRocks", "activeRockColor", "highlightNearestRock", "nearestRockColor",
		"highlightDepletedRocks", "depletedRockColor", "showGuaranteed", "markFreshRocks",
		"showDepositTimer", "notifyInventoryFull",
	};

	@AllArgsConstructor
	enum RockHighlight
	{
		OFF("Off"),
		NEAREST("Nearest only"),
		ALL("All active");

		private final String name;

		@Override
		public String toString()
		{
			return name;
		}
	}

	@ConfigSection(name = "Rocks", description = "Rubium rock highlights", position = 0)
	String rocksSection = "rocks";

	@ConfigSection(name = "Deposits", description = "Rubium deposit highlight and timer", position = 10)
	String depositsSection = "deposits";

	@ConfigSection(name = "Session", description = "Splinters, Mining XP and GP overlay", position = 20)
	String sessionSection = "session";

	@ConfigSection(name = "Notifications", description = "Alerts while mining rubium", position = 30)
	String notificationsSection = "notifications";

	// ---- rocks ----

	@ConfigItem(keyName = "rockHighlight", name = "Rock highlight",
		description = "Outline the active rock closest to you, or every active rock (the closest one gets a thicker line)",
		position = 1, section = rocksSection)
	default RockHighlight rockHighlight()
	{
		return RockHighlight.NEAREST;
	}

	@Alpha
	@ConfigItem(keyName = "rockColor", name = "Rock color",
		description = "Outline color for active rubium rocks", position = 2, section = rocksSection)
	default Color rockColor()
	{
		return new Color(255, 215, 0, 230);
	}

	@ConfigItem(keyName = "rockLabels", name = "Rock labels",
		description = "Above the rock you're mining: splinters still guaranteed (\"2 left\", \"1 left\"), then \"1 in 6\" "
			+ "per splinter. Rocks you saw respawn and haven't mined are labeled \"Fresh\", "
			+ "and depleted rocks count down to their respawn in seconds. "
			+ "Counts only your splinters, so it's exact only if nobody else mined the rock",
		position = 3, section = rocksSection)
	default boolean rockLabels()
	{
		return true;
	}

	// ---- deposits ----

	@ConfigItem(keyName = "highlightDeposit", name = "Deposit",
		description = "Outline the active deposit with a depletion timer (4-5 minutes after mining starts), and label the other one",
		position = 11, section = depositsSection)
	default boolean highlightDeposit()
	{
		return true;
	}

	@Alpha
	@ConfigItem(keyName = "depositColor", name = "Deposit color",
		description = "Outline color for the active deposit", position = 12, section = depositsSection)
	default Color depositColor()
	{
		return new Color(255, 120, 40, 200);
	}

	// ---- session ----

	@ConfigItem(keyName = "showSessionOverlay", name = "Session overlay",
		description = "Show splinters, Mining XP and GP with hourly rates", position = 21, section = sessionSection)
	default boolean showSessionOverlay()
	{
		return true;
	}

	@Range(min = 1, max = 60)
	@Units(Units.MINUTES)
	@ConfigItem(keyName = "sessionTimeout", name = "Session timeout",
		description = "Reset the session after this long without gaining splinters, geodes or XP",
		position = 22, section = sessionSection)
	default int sessionTimeout()
	{
		return 5;
	}

	// ---- notifications ----

	@ConfigItem(keyName = "notifyDepositDepleted", name = "Deposit depleted",
		description = "Notify when the active deposit depletes and the other one opens", position = 31, section = notificationsSection)
	default Notification notifyDepositDepleted()
	{
		return Notification.ON;
	}

	@ConfigItem(keyName = "notifyRockDepleted", name = "Your rock depleted",
		description = "Notify when the rock you were mining depletes", position = 32, section = notificationsSection)
	default Notification notifyRockDepleted()
	{
		return Notification.OFF;
	}

	@ConfigItem(keyName = "inventoryFullAlert", name = "Inventory full",
		description = "Notify when your inventory fills up in the mine (time to crack geodes or bank)",
		position = 33, section = notificationsSection)
	default Notification inventoryFullAlert()
	{
		return Notification.OFF;
	}
}
