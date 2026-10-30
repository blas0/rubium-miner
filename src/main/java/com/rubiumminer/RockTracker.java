package com.rubiumminer;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * Current state of every Rubium rock and deposit in the loaded scene, keyed by tile.
 */
class RockTracker
{
	@Value
	static class Rock
	{
		WorldPoint location;
		boolean deposit;
		boolean active;
		/** When the state last changed, or -1 if it was already like this when the scene loaded. */
		long changedAt;
	}

	private final Map<WorldPoint, Rock> rocks = new HashMap<>();
	/** Splinters you took from each rock since it last changed state. */
	private final Map<WorldPoint, Integer> yourSplinters = new HashMap<>();
	/** When each depleted rock respawns, from the game's own respawn timer. */
	private final Map<WorldPoint, Long> respawnAt = new HashMap<>();

	/**
	 * Record an object spawn. {@code observed} is false for spawns caused by a scene load,
	 * where we only learn the current state and not when it changed.
	 *
	 * @return the previous state of that tile, or null if it was unknown
	 */
	@Nullable
	Rock update(int objectId, WorldPoint location, long now, boolean observed)
	{
		boolean active = RubiumData.isActive(objectId);
		Rock previous = rocks.get(location);
		boolean changed = previous == null || previous.isActive() != active;
		// A repeat spawn in the same state keeps its history (and its "fresh" status)
		long changedAt = changed ? (observed ? now : -1) : previous.getChangedAt();
		rocks.put(location, new Rock(location, !RubiumData.isRock(objectId), active, changedAt));
		if (changed)
		{
			yourSplinters.remove(location);
		}
		if (active)
		{
			// Only on respawn: the timer can arrive just before the depleted rock spawns
			respawnAt.remove(location);
		}
		return previous;
	}

	void clear()
	{
		rocks.clear();
		yourSplinters.clear();
		respawnAt.clear();
	}

	/** The game started a respawn timer on the depleted rock at {@code location}. */
	void setRespawn(WorldPoint location, long at)
	{
		respawnAt.put(location, at);
	}

	/** Milliseconds until a depleted rock respawns (0 once it's due), or -1 if we didn't see its timer start. */
	long respawnRemainingMs(WorldPoint location, long now)
	{
		Long at = respawnAt.get(location);
		Rock rock = rocks.get(location);
		if (at == null || rock == null || rock.isActive())
		{
			return -1;
		}
		return Math.max(0, at - now);
	}

	/** You got {@code n} splinters from the rock at {@code location}. */
	void addYourSplinters(WorldPoint location, int n)
	{
		yourSplinters.merge(location, n, Integer::sum);
	}

	int yourSplinters(WorldPoint location)
	{
		return yourSplinters.getOrDefault(location, 0);
	}

	/**
	 * Splinters still guaranteed from an active rock, counting only what you took:
	 * every rock gives at least {@link RubiumData#GUARANTEED_SPLINTERS} before it can deplete.
	 *
	 * @return 0 to 2, or -1 if we didn't see the rock respawn and so don't know
	 */
	int guaranteedLeft(WorldPoint location)
	{
		Rock rock = rocks.get(location);
		if (rock == null || rock.isDeposit() || !rock.isActive())
		{
			return 0;
		}
		int taken = yourSplinters(location);
		if (rock.getChangedAt() < 0)
		{
			// Unknown history, but if you alone took two, the guarantee is used up either way
			return taken >= RubiumData.GUARANTEED_SPLINTERS ? 0 : -1;
		}
		return Math.max(0, RubiumData.GUARANTEED_SPLINTERS - taken);
	}

	/** "2 left", "1 left" (guaranteed splinters), then "1 in 6" (depletion chance per splinter), or null if unknown. */
	@Nullable
	String oddsLabel(WorldPoint location)
	{
		int left = guaranteedLeft(location);
		if (left < 0)
		{
			return null;
		}
		return left > 0 ? left + " left" : "1 in " + RubiumData.DEPLETE_ONE_IN;
	}

	/** Active rock we watched respawn that you haven't mined yet. Other players may have. */
	boolean isFresh(Rock rock)
	{
		return !rock.isDeposit() && rock.isActive() && rock.getChangedAt() >= 0 && yourSplinters(rock.getLocation()) == 0;
	}

	boolean isEmpty()
	{
		return rocks.isEmpty();
	}

	@Nullable
	Rock get(WorldPoint location)
	{
		return rocks.get(location);
	}

	Collection<Rock> all()
	{
		return Collections.unmodifiableCollection(rocks.values());
	}

	/** Closest active rubium rock (not deposit) to {@code from}, or null if none. */
	@Nullable
	Rock nearestActiveRock(WorldPoint from)
	{
		Rock best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (Rock rock : rocks.values())
		{
			if (rock.isDeposit() || !rock.isActive() || rock.getLocation().getPlane() != from.getPlane())
			{
				continue;
			}
			int d = rock.getLocation().distanceTo2D(from);
			if (d < bestDistance)
			{
				bestDistance = d;
				best = rock;
			}
		}
		return best;
	}

	int activeRockCount()
	{
		int n = 0;
		for (Rock rock : rocks.values())
		{
			if (!rock.isDeposit() && rock.isActive())
			{
				n++;
			}
		}
		return n;
	}

	int rockCount()
	{
		int n = 0;
		for (Rock rock : rocks.values())
		{
			if (!rock.isDeposit())
			{
				n++;
			}
		}
		return n;
	}
}
