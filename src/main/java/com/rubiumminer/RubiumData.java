package com.rubiumminer;

import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;

/**
 * Rubium facts from the OSRS Wiki (Rubium rocks, Rubium deposit, Rubium geode pages, October 2026).
 */
final class RubiumData
{
	static final int ROCK = ObjectID.RUBIUMROCK1;
	static final int ROCK_EMPTY = ObjectID.RUBIUMROCK1_EMPTY;
	static final int DEPOSIT = ObjectID.RUBIUMDEPOSIT1;
	static final int DEPOSIT_EMPTY = ObjectID.RUBIUMDEPOSIT1_EMPTY;

	static final int SPLINTERS = ItemID.RUBIUM_SPLINTERS;
	static final int GEODE = ItemID.RUBIUM_GEODE;

	static final int DEPOSIT_LEVEL = 68;

	/**
	 * Every rubium rock gives at least this many splinters (to anyone) before it can deplete.
	 * After that, each splinter has about a 1 in 6 chance to deplete it.
	 */
	static final int GUARANTEED_SPLINTERS = 2;
	static final int DEPLETE_ONE_IN = 6;

	/** A deposit lasts 4 to 5 minutes once someone starts mining it. */
	static final long DEPOSIT_MIN_MS = 4 * 60_000L;
	static final long DEPOSIT_MAX_MS = 5 * 60_000L;

	private RubiumData()
	{
	}

	static boolean isRubiumObject(int id)
	{
		return id == ROCK || id == ROCK_EMPTY || id == DEPOSIT || id == DEPOSIT_EMPTY;
	}

	static boolean isRock(int id)
	{
		return id == ROCK || id == ROCK_EMPTY;
	}

	static boolean isActive(int id)
	{
		return id == ROCK || id == DEPOSIT;
	}

	/** Splinters per successful swing on a rubium rock, before Varrock armour / celestial ring / cape. */
	static int splintersPerRockSwing(int visibleMiningLevel)
	{
		return visibleMiningLevel >= 72 ? 2 : 1;
	}

	/**
	 * Average splinters from cracking one geode at a visible Mining level, or 0 below 68.
	 * Wiki ranges: 68-75: 3-7, 76-83: 4-8, 84-91: 5-9, 92+: 6-10.
	 */
	static int averageSplintersPerGeode(int visibleMiningLevel)
	{
		if (visibleMiningLevel >= 92)
		{
			return 8;
		}
		if (visibleMiningLevel >= 84)
		{
			return 7;
		}
		if (visibleMiningLevel >= 76)
		{
			return 6;
		}
		if (visibleMiningLevel >= DEPOSIT_LEVEL)
		{
			return 5;
		}
		return 0;
	}
}
