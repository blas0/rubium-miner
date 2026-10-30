package com.rubiumminer;

import javax.annotation.Nullable;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * Estimates when the active Rubium deposit will deplete.
 *
 * <p>Only one of the two deposits is active at a time. Once someone starts mining it,
 * it lasts 4 to 5 minutes. The client can't see when another player started, only
 * when the deposit became active and when you started, so the estimate is a range:
 * the true start is somewhere between those two moments.
 */
class DepositTimer
{
	@Value
	static class Estimate
	{
		/** Earliest the deposit can deplete, in ms from now (never negative). */
		long minRemainingMs;
		/** Latest the deposit should deplete, in ms from now (never negative). */
		long maxRemainingMs;
		/** How long you have been mining it, in ms. */
		long minedForMs;
	}

	@Nullable
	private WorldPoint active;
	/** When the active deposit appeared, or -1 if it was already active when the scene loaded. */
	private long activeSince = -1;
	/** When you first mined the active deposit, or -1 if you haven't. */
	private long minedSince = -1;

	void onActive(WorldPoint location, long now, boolean observed)
	{
		active = location;
		activeSince = observed ? now : -1;
		minedSince = -1;
	}

	/** You mined (or clicked Mine on) the deposit at {@code location}. */
	void onMined(WorldPoint location, long now)
	{
		if (location.equals(active) && minedSince < 0)
		{
			minedSince = now;
		}
	}

	/** You got a geode, so you're mining whichever deposit is active. */
	void onGeode(long now)
	{
		if (active != null && minedSince < 0)
		{
			minedSince = now;
		}
	}

	/** @return true if the depleted deposit was the active one */
	boolean onDepleted(WorldPoint location)
	{
		if (location.equals(active))
		{
			active = null;
			activeSince = -1;
			minedSince = -1;
			return true;
		}
		return false;
	}

	void clear()
	{
		active = null;
		activeSince = -1;
		minedSince = -1;
	}

	@Nullable
	WorldPoint getActive()
	{
		return active;
	}

	/** @return the depletion window, or null until you have started mining the active deposit */
	@Nullable
	Estimate estimate(long now)
	{
		if (active == null || minedSince < 0)
		{
			return null;
		}
		// Earliest possible start is when it appeared; latest is when you started.
		long earliestStart = activeSince >= 0 ? Math.min(activeSince, minedSince) : minedSince;
		long min = Math.max(0, earliestStart + RubiumData.DEPOSIT_MIN_MS - now);
		long max = Math.max(0, minedSince + RubiumData.DEPOSIT_MAX_MS - now);
		return new Estimate(min, max, now - minedSince);
	}
}
