package com.rubiumminer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.mining.MiningPlugin;
import org.junit.Test;

public class RubiumLogicTest
{
	private static final WorldPoint DEPOSIT_A = new WorldPoint(2679, 8852, 0);
	private static final WorldPoint DEPOSIT_B = new WorldPoint(2685, 8847, 0);

	@Test
	public void yieldsFollowTheWikiTables()
	{
		assertEquals(1, RubiumData.splintersPerRockSwing(48));
		assertEquals(1, RubiumData.splintersPerRockSwing(71));
		assertEquals(2, RubiumData.splintersPerRockSwing(72));

		assertEquals(0, RubiumData.averageSplintersPerGeode(67));
		assertEquals(5, RubiumData.averageSplintersPerGeode(68));
		assertEquals(6, RubiumData.averageSplintersPerGeode(76));
		assertEquals(7, RubiumData.averageSplintersPerGeode(84));
		assertEquals(8, RubiumData.averageSplintersPerGeode(92));
		assertEquals(8, RubiumData.averageSplintersPerGeode(99));
	}

	@Test
	public void idsAreClassified()
	{
		assertTrue(RubiumData.isRubiumObject(58919));
		assertTrue(RubiumData.isRubiumObject(58922));
		assertFalse(RubiumData.isRubiumObject(58918));
		assertTrue(RubiumData.isRock(58920));
		assertFalse(RubiumData.isRock(58921));
		assertTrue(RubiumData.isActive(RubiumData.DEPOSIT));
		assertFalse(RubiumData.isActive(RubiumData.ROCK_EMPTY));
	}

	@Test
	public void trackerFindsNearestActiveRockAndReportsDepletion()
	{
		RockTracker t = new RockTracker();
		WorldPoint near = new WorldPoint(2680, 8836, 0);
		WorldPoint far = new WorldPoint(2687, 8849, 0);
		t.update(RubiumData.ROCK, near, 0, false);
		t.update(RubiumData.ROCK, far, 0, false);
		t.update(RubiumData.DEPOSIT, DEPOSIT_A, 0, false);

		WorldPoint me = new WorldPoint(2681, 8837, 0);
		assertEquals(near, t.nearestActiveRock(me).getLocation());
		assertEquals(2, t.activeRockCount());
		assertEquals(2, t.rockCount());

		RockTracker.Rock previous = t.update(RubiumData.ROCK_EMPTY, near, 1000, true);
		assertTrue("previous state is returned so the plugin can spot depletion", previous.isActive());
		assertEquals(far, t.nearestActiveRock(me).getLocation());
		assertEquals(1000, t.get(near).getChangedAt());

		t.update(RubiumData.ROCK_EMPTY, far, 2000, true);
		assertNull("deposits are never the nearest rock", t.nearestActiveRock(me));
	}

	@Test
	public void guaranteedSplintersCountDownFromAWatchedRespawn()
	{
		RockTracker t = new RockTracker();
		WorldPoint rock = new WorldPoint(2682, 8839, 0);
		t.update(RubiumData.ROCK_EMPTY, rock, 0, true);
		assertEquals("nothing is guaranteed from a depleted rock", 0, t.guaranteedLeft(rock));

		t.update(RubiumData.ROCK, rock, 5_000, true);
		RockTracker.Rock respawned = t.get(rock);
		assertTrue(t.isFresh(respawned));
		assertEquals(2, t.guaranteedLeft(rock));
		assertEquals("2 left", t.oddsLabel(rock));

		t.addYourSplinters(rock, 1);
		assertFalse(t.isFresh(t.get(rock)));
		assertEquals("1 left", t.oddsLabel(rock));

		// At level 72+ one swing gives two splinters
		t.addYourSplinters(rock, 2);
		assertEquals(0, t.guaranteedLeft(rock));
		assertEquals("1 in 6", t.oddsLabel(rock));

		// Depleting and respawning starts over
		t.update(RubiumData.ROCK_EMPTY, rock, 9_000, true);
		t.update(RubiumData.ROCK, rock, 20_000, true);
		assertEquals(0, t.yourSplinters(rock));
		assertEquals("2 left", t.oddsLabel(rock));
	}

	@Test
	public void unknownHistoryUntilYouTakeTwo()
	{
		RockTracker t = new RockTracker();
		WorldPoint rock = new WorldPoint(2684, 8842, 0);
		t.update(RubiumData.ROCK, rock, 0, false);
		assertFalse("a rock that was already up when the scene loaded isn't fresh", t.isFresh(t.get(rock)));
		assertEquals(-1, t.guaranteedLeft(rock));
		assertNull(t.oddsLabel(rock));

		t.addYourSplinters(rock, 1);
		assertNull(t.oddsLabel(rock));
		t.addYourSplinters(rock, 1);
		assertEquals("1 in 6", t.oddsLabel(rock));
	}

	@Test
	public void repeatSpawnInSameStateKeepsHistory()
	{
		RockTracker t = new RockTracker();
		WorldPoint rock = new WorldPoint(2680, 8844, 0);
		t.update(RubiumData.ROCK_EMPTY, rock, 0, true);
		t.update(RubiumData.ROCK, rock, 1_000, true);
		t.addYourSplinters(rock, 1);
		// e.g. a second spawn event for the same active object
		t.update(RubiumData.ROCK, rock, 2_000, true);
		assertEquals(1, t.yourSplinters(rock));
		assertEquals(1_000, t.get(rock).getChangedAt());
	}

	@Test
	public void respawnCountdownFromTheGamesTimer()
	{
		RockTracker t = new RockTracker();
		WorldPoint rock = new WorldPoint(2684, 8851, 0);
		t.update(RubiumData.ROCK, rock, 0, true);

		// The timer can arrive before the depleted rock spawns: nothing to show on an active rock yet
		t.setRespawn(rock, 1_000 + 59 * 600);
		assertEquals(-1, t.respawnRemainingMs(rock, 1_000));

		t.update(RubiumData.ROCK_EMPTY, rock, 1_000, true);
		assertEquals(35_400, t.respawnRemainingMs(rock, 1_000));
		assertEquals("rounded up", "36s", DurationFormat.seconds(t.respawnRemainingMs(rock, 1_000)));
		assertEquals("1s", DurationFormat.seconds(t.respawnRemainingMs(rock, 36_000)));
		assertEquals("a late respawn stays at 0s", 0, t.respawnRemainingMs(rock, 40_000));

		t.update(RubiumData.ROCK, rock, 37_000, true);
		assertEquals("cleared on respawn", -1, t.respawnRemainingMs(rock, 37_000));
		t.update(RubiumData.ROCK_EMPTY, rock, 50_000, true);
		assertEquals("a depletion we got no timer for shows nothing", -1, t.respawnRemainingMs(rock, 50_000));
	}

	@Test
	public void depositTimerNeedsYouToStartMining()
	{
		DepositTimer timer = new DepositTimer();
		timer.onActive(DEPOSIT_A, 0, true);
		assertNull(timer.estimate(10_000));

		timer.onMined(DEPOSIT_B, 5_000);
		assertNull("mining the inactive deposit doesn't start the timer", timer.estimate(10_000));

		timer.onMined(DEPOSIT_A, 10_000);
		DepositTimer.Estimate e = timer.estimate(70_000);
		assertNotNull(e);
		// It appeared at 0, so someone may have started then: 240s - 70s = 170s at the earliest
		assertEquals(170_000, e.getMinRemainingMs());
		// You started at 10s: 10s + 300s - 70s = 240s at the latest
		assertEquals(240_000, e.getMaxRemainingMs());
		assertEquals(60_000, e.getMinedForMs());
	}

	@Test
	public void depositTimerWithoutSeeingItAppear()
	{
		DepositTimer timer = new DepositTimer();
		timer.onActive(DEPOSIT_A, 0, false);
		timer.onGeode(30_000);
		DepositTimer.Estimate e = timer.estimate(30_000);
		assertEquals(240_000, e.getMinRemainingMs());
		assertEquals(300_000, e.getMaxRemainingMs());

		assertEquals(0, timer.estimate(400_000).getMaxRemainingMs());
	}

	@Test
	public void depositDepletionClearsOnlyTheActiveOne()
	{
		DepositTimer timer = new DepositTimer();
		timer.onActive(DEPOSIT_A, 0, true);
		assertFalse(timer.onDepleted(DEPOSIT_B));
		assertTrue(timer.onDepleted(DEPOSIT_A));
		assertNull(timer.getActive());

		timer.onActive(DEPOSIT_B, 1_000, true);
		assertSame(DEPOSIT_B, timer.getActive());
	}

	@Test
	public void sessionRatesUseActiveTimeAndTimeout()
	{
		MiningSession s = new MiningSession();
		assertFalse(s.isActive());

		s.addSplinters(2, 0);
		s.addXp(30, 0);
		s.addSplinters(1, 30_000);
		assertEquals("no rate during the first minute", 0, s.perHour(s.getSplinters()));

		// Keep getting a splinter every 30s until 10 minutes in: 20 gaps of active time, 22 splinters
		for (int i = 2; i <= 20; i++)
		{
			s.addSplinters(1, i * 30_000L);
		}
		assertEquals(600_000, s.getActiveMs());
		assertEquals(22 * 6, s.perHour(s.getSplinters()));

		// A 10-minute break (bank, AFK) isn't active time, so the rate doesn't drop
		s.addSplinters(1, 600_000 + 600_000);
		assertEquals(600_000, s.getActiveMs());
		assertEquals(23 * 6, s.perHour(s.getSplinters()));

		s.expire(1_200_000 + 299_000, 300_000);
		assertTrue(s.isActive());
		s.expire(1_200_000 + 301_000, 300_000);
		assertFalse(s.isActive());
		assertEquals(0, s.getSplinters());
		assertEquals(0, s.getActiveMs());
	}

	@Test
	public void mineClicksCountAsActiveTime()
	{
		MiningSession s = new MiningSession();
		s.interact(0);
		assertFalse("a click alone doesn't start a session", s.isActive());

		// Walking to the rock and swinging until the first splinter counts
		s.addSplinters(1, 12_000);
		assertEquals(12_000, s.getActiveMs());

		// Clicking the next rock 40s later, splinter 20s after that: both gaps count
		s.interact(52_000);
		s.addSplinters(1, 72_000);
		assertEquals(72_000, s.getActiveMs());

		// A click after a long pause starts counting again from the click
		s.interact(300_000);
		s.addSplinters(1, 310_000);
		assertEquals(82_000, s.getActiveMs());
	}

	@Test
	public void conflictsWithCoreMiningByItsDescriptorName()
	{
		PluginDescriptor descriptor = RubiumMinerPlugin.class.getAnnotation(PluginDescriptor.class);
		assertTrue(Arrays.asList(descriptor.conflicts()).contains(
			MiningPlugin.class.getAnnotation(PluginDescriptor.class).name()));
	}

	@Test
	public void compactAmounts()
	{
		assertEquals("129", AmountFormat.compact(129));
		assertEquals("1503", AmountFormat.compact(1503));
		assertEquals("3990", AmountFormat.compact(3990));
		assertEquals("9999", AmountFormat.compact(9999));
		assertEquals("10k", AmountFormat.compact(10_000));
		assertEquals("18.5k", AmountFormat.compact(18_500));
		assertEquals("25.5k", AmountFormat.compact(25_560));
		assertEquals("99.9k", AmountFormat.compact(99_999));
		assertEquals("125k", AmountFormat.compact(125_900));
		assertEquals("1m", AmountFormat.compact(1_000_000));
		assertEquals("2.3m", AmountFormat.compact(2_340_000));
		assertEquals("140m", AmountFormat.compact(140_000_000));
	}

	@Test
	public void totalAmounts()
	{
		assertEquals("36", AmountFormat.total(36));
		assertEquals("600", AmountFormat.total(600));
		assertEquals("999", AmountFormat.total(999));
		assertEquals("1k", AmountFormat.total(1000));
		assertEquals("2.9k", AmountFormat.total(2952));
		assertEquals("6.1k", AmountFormat.total(6150));
		assertEquals("500.3k", AmountFormat.total(500_380));
		assertEquals("999.9k", AmountFormat.total(999_999));
		assertEquals("1.1m", AmountFormat.total(1_100_000));
		assertEquals("12m", AmountFormat.total(12_000_000));
	}

	@Test
	public void durationFormatting()
	{
		assertEquals("0:00", DurationFormat.clock(-5));
		assertEquals("1:35", DurationFormat.clock(95_000));
		assertEquals("2:50-4:00", DurationFormat.range(170_000, 240_000));
		assertEquals("4:00", DurationFormat.range(240_400, 240_900));
		assertEquals("0s", DurationFormat.seconds(0));
		assertEquals("35s", DurationFormat.seconds(34_100));
		assertEquals("35s", DurationFormat.seconds(35_000));
	}
}
