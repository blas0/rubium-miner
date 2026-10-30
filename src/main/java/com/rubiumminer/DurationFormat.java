package com.rubiumminer;

final class DurationFormat
{
	private DurationFormat()
	{
	}

	/** 95_000 -> "1:35" */
	static String clock(long ms)
	{
		long seconds = Math.max(0, ms) / 1000;
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}

	/** Whole seconds, rounded up so it reads "1s" until the very end: 34_100 -> "35s" */
	static String seconds(long ms)
	{
		return (Math.max(0, ms) + 999) / 1000 + "s";
	}

	/** "1:35" when both ends match to the second, else "0:35-1:35" */
	static String range(long minMs, long maxMs)
	{
		String min = clock(minMs);
		String max = clock(maxMs);
		return min.equals(max) ? max : min + "-" + max;
	}
}
