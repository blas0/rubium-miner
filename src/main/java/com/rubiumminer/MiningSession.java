package com.rubiumminer;

/**
 * Splinters, geodes and Mining XP gained since the session started. A session starts
 * on the first gain and ends after {@code timeoutMs} with no gains.
 *
 * <p>Hourly rates use active time, not wall-clock time: the time between one interaction
 * (a gain, or clicking Mine) and the next, skipping any gap longer than {@link #IDLE_GAP_MS}.
 * Banking, AFK breaks and walking off don't drag the rates down.
 */
class MiningSession
{
	/** A pause longer than this between interactions counts as idle and is left out of the rates. */
	static final long IDLE_GAP_MS = 60_000;
	/** Rates show 0 until this much active time has passed, so early numbers don't spike. */
	static final long MIN_ACTIVE_MS = 60_000;

	private long startedAt = -1;
	private long lastGainAt = -1;
	private long lastActivityAt = -1;
	private long activeMs;
	private int splinters;
	private int geodes;
	private int xp;

	void addSplinters(int n, long now)
	{
		gain(now);
		splinters += n;
	}

	void addGeodes(int n, long now)
	{
		gain(now);
		geodes += n;
	}

	void addXp(int n, long now)
	{
		gain(now);
		xp += n;
	}

	/** You clicked Mine. Counts toward active time, but doesn't start a session on its own. */
	void interact(long now)
	{
		if (isActive())
		{
			activity(now);
		}
		else
		{
			// The walk and first swings before the first gain still count once it arrives
			lastActivityAt = now;
		}
	}

	private void gain(long now)
	{
		if (startedAt < 0)
		{
			startedAt = now;
		}
		lastGainAt = now;
		activity(now);
	}

	private void activity(long now)
	{
		if (lastActivityAt >= 0)
		{
			long gap = now - lastActivityAt;
			if (gap > 0 && gap <= IDLE_GAP_MS)
			{
				activeMs += gap;
			}
		}
		lastActivityAt = Math.max(lastActivityAt, now);
	}

	boolean isActive()
	{
		return startedAt >= 0;
	}

	/** End the session if nothing was gained for {@code timeoutMs}. */
	void expire(long now, long timeoutMs)
	{
		if (isActive() && now - lastGainAt > timeoutMs)
		{
			reset();
		}
	}

	void reset()
	{
		startedAt = -1;
		lastGainAt = -1;
		lastActivityAt = -1;
		activeMs = 0;
		splinters = 0;
		geodes = 0;
		xp = 0;
	}

	int getSplinters()
	{
		return splinters;
	}

	int getGeodes()
	{
		return geodes;
	}

	int getXp()
	{
		return xp;
	}

	long getActiveMs()
	{
		return activeMs;
	}

	/** Per-hour rate for {@code amount} over active time; 0 until there's a minute of it. */
	long perHour(long amount)
	{
		if (activeMs < MIN_ACTIVE_MS)
		{
			return 0;
		}
		return amount * 3_600_000L / activeMs;
	}
}
