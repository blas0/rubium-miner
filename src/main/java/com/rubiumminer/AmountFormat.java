package com.rubiumminer;

final class AmountFormat
{
	private AmountFormat()
	{
	}

	/**
	 * Short amounts for the session box, rounded down: 1503 -> "1503", 25_560 -> "25.5k",
	 * 125_900 -> "125k", 2_340_000 -> "2.3m", 140_000_000 -> "140m".
	 */
	static String compact(long n)
	{
		if (n < 0)
		{
			return "-" + compact(-n);
		}
		if (n < 10_000)
		{
			return Long.toString(n);
		}
		if (n < 100_000)
		{
			return tenths(n / 100) + "k";
		}
		if (n < 1_000_000)
		{
			return n / 1000 + "k";
		}
		if (n < 100_000_000)
		{
			return tenths(n / 100_000) + "m";
		}
		return n / 1_000_000 + "m";
	}

	/**
	 * Session totals, rounded down to one decimal from 1,000 up: 600 -> "600", 2952 -> "2.9k",
	 * 500_380 -> "500.3k", 1_100_000 -> "1.1m".
	 */
	static String total(long n)
	{
		if (n < 0)
		{
			return "-" + total(-n);
		}
		if (n < 1000)
		{
			return Long.toString(n);
		}
		if (n < 1_000_000)
		{
			return tenths(n / 100) + "k";
		}
		return tenths(n / 100_000) + "m";
	}

	/** 255 -> "25.5", 250 -> "25" */
	private static String tenths(long tenths)
	{
		return tenths % 10 == 0 ? Long.toString(tenths / 10) : tenths / 10 + "." + tenths % 10;
	}
}
