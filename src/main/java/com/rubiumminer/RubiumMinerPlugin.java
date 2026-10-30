package com.rubiumminer;

import com.google.inject.Provides;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Scene;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Rubium Miner",
	description = "Rubium mining helper: rock and deposit highlights, guaranteed-splinter counts, respawn and deposit timers, rates and alerts",
	tags = {"mining", "rubium", "sailing", "charred", "geode", "splinters", "skilling"},
	// Core Mining draws its own session box beside ours. Whichever of the two starts second turns the other
	// off. At launch core plugins start first, so if both are on, Mining wins: switch this plugin on once
	// from the plugin list and Mining stays off from then on.
	conflicts = "Mining"
)
public class RubiumMinerPlugin extends Plugin
{
	private static final int INVENTORY_SIZE = 28;
	// A Mine click on a deposit can land on any of its tiles; match it to the nearest tracked object
	private static final int CLICK_MATCH_DISTANCE = 2;
	// After a scene load the server corrects the map's default rock and deposit states over the next tick or two
	private static final int SETTLE_TICKS = 3;

	@Inject
	private Client client;

	@Inject
	private RubiumMinerConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private RubiumSceneOverlay sceneOverlay;

	@Inject
	private RubiumSessionOverlay sessionOverlay;

	@Inject
	private Notifier notifier;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Getter
	private final RockTracker rocks = new RockTracker();
	@Getter
	private final DepositTimer depositTimer = new DepositTimer();
	@Getter
	private final MiningSession session = new MiningSession();

	// Client thread only
	private final Map<WorldPoint, GameObject> objects = new HashMap<>();
	@Nullable
	private WorldPoint miningTarget;
	/** Ticks left before spawns count as changes we watched happen, rather than part of a scene load. */
	private int settleTicks;
	private int lastSplinters = -1;
	private int lastGeodes = -1;
	private int lastMiningXp = -1;
	private boolean notifiedFull;

	@Provides
	RubiumMinerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RubiumMinerConfig.class);
	}

	@Override
	protected void startUp()
	{
		for (String key : RubiumMinerConfig.OBSOLETE_KEYS)
		{
			configManager.unsetConfiguration(RubiumMinerConfig.GROUP, key);
		}
		overlayManager.add(sceneOverlay);
		overlayManager.add(sessionOverlay);
		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				lastMiningXp = client.getSkillExperience(Skill.MINING);
				ItemContainer inv = client.getItemContainer(InventoryID.INV);
				if (inv != null)
				{
					lastSplinters = inv.count(RubiumData.SPLINTERS);
					lastGeodes = inv.count(RubiumData.GEODE);
				}
				scanScene();
			}
		});
	}

	/** Objects already loaded don't fire spawn events, so pick them up when the plugin starts. */
	private void scanScene()
	{
		Scene scene = client.getTopLevelWorldView().getScene();
		Tile[][][] tiles = scene.getTiles();
		int plane = client.getTopLevelWorldView().getPlane();
		long now = System.currentTimeMillis();
		for (Tile[] column : tiles[plane])
		{
			for (Tile tile : column)
			{
				if (tile == null)
				{
					continue;
				}
				for (GameObject object : tile.getGameObjects())
				{
					if (object != null && RubiumData.isRubiumObject(object.getId()))
					{
						WorldPoint location = object.getWorldLocation();
						objects.put(location, object);
						rocks.update(object.getId(), location, now, false);
						if (object.getId() == RubiumData.DEPOSIT)
						{
							depositTimer.onActive(location, now, false);
						}
					}
				}
			}
		}
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(sceneOverlay);
		overlayManager.remove(sessionOverlay);
		objects.clear();
		rocks.clear();
		depositTimer.clear();
		session.reset();
		miningTarget = null;
		lastSplinters = lastGeodes = lastMiningXp = -1;
	}

	/** True while Rubium rocks or deposits are loaded, i.e. you're in or near the mine. */
	boolean inMine()
	{
		return !rocks.isEmpty();
	}

	Map<WorldPoint, GameObject> getObjects()
	{
		return Collections.unmodifiableMap(objects);
	}

	@Nullable
	WorldPoint getMiningTarget()
	{
		return miningTarget;
	}

	int visibleMiningLevel()
	{
		return client.getBoostedSkillLevel(Skill.MINING);
	}

	int geodesInInventory()
	{
		return Math.max(0, lastGeodes);
	}

	// ---- scene ----

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOADING || state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			settleTicks = SETTLE_TICKS;
			objects.clear();
			rocks.clear();
			miningTarget = null;
		}
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			// A different world has its own deposit state
			depositTimer.clear();
			lastSplinters = lastGeodes = lastMiningXp = -1;
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (settleTicks > 0)
		{
			settleTicks--;
		}
		session.expire(System.currentTimeMillis(), config.sessionTimeout() * 60_000L);
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		GameObject object = event.getGameObject();
		int id = object.getId();
		if (!RubiumData.isRubiumObject(id))
		{
			return;
		}

		long now = System.currentTimeMillis();
		WorldPoint location = object.getWorldLocation();
		boolean observed = settleTicks == 0;
		objects.put(location, object);
		RockTracker.Rock previous = rocks.update(id, location, now, observed);

		boolean deposit = !RubiumData.isRock(id);
		boolean active = RubiumData.isActive(id);

		if (deposit && active && (observed || !location.equals(depositTimer.getActive())))
		{
			depositTimer.onActive(location, now, observed);
		}

		boolean depleted = previous != null && previous.isActive() && !active;
		if (!depleted)
		{
			return;
		}

		// Only alert on depletions we watched happen, not the server correcting a freshly loaded scene
		if (deposit)
		{
			if (depositTimer.onDepleted(location) && observed)
			{
				notifier.notify(config.notifyDepositDepleted(), "Rubium deposit depleted. The other deposit is now active.");
			}
		}
		else if (observed && location.equals(miningTarget))
		{
			miningTarget = null;
			notifier.notify(config.notifyRockDepleted(), "Your rubium rock depleted.");
		}
	}

	/**
	 * The game draws its own respawn timer on a depleted rock with this script. Core Mining reads the
	 * same arguments: [1] the tile, [2] the depleted object, [5] the respawn time in ticks.
	 */
	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() != ScriptID.ADD_OVERLAYTIMER_LOC)
		{
			return;
		}
		Object[] args = event.getScriptEvent().getArguments();
		if ((int) args[2] == RubiumData.ROCK_EMPTY)
		{
			int ticks = (int) args[5];
			rocks.setRespawn(WorldPoint.fromCoord((int) args[1]),
				System.currentTimeMillis() + (long) ticks * Constants.GAME_TICK_LENGTH);
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		GameObject object = event.getGameObject();
		if (RubiumData.isRubiumObject(object.getId()))
		{
			// The replacement object (active <-> depleted) spawns on the same tile right after
			objects.remove(object.getWorldLocation(), object);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		// Read-only: we note which rock you chose, and never change or add menu entries
		if (!"Mine".equals(event.getMenuOption()) || !RubiumData.isRubiumObject(event.getId()))
		{
			return;
		}
		var wv = client.getTopLevelWorldView();
		WorldPoint clicked = WorldPoint.fromScene(wv, event.getParam0(), event.getParam1(), wv.getPlane());
		RockTracker.Rock rock = nearestTracked(clicked);
		if (rock == null)
		{
			return;
		}
		long now = System.currentTimeMillis();
		miningTarget = rock.getLocation();
		session.interact(now);
		if (rock.isDeposit())
		{
			depositTimer.onMined(rock.getLocation(), now);
		}
	}

	@Nullable
	private RockTracker.Rock nearestTracked(WorldPoint point)
	{
		RockTracker.Rock best = null;
		int bestDistance = CLICK_MATCH_DISTANCE + 1;
		for (RockTracker.Rock rock : rocks.all())
		{
			int d = rock.getLocation().distanceTo2D(point);
			if (rock.getLocation().getPlane() == point.getPlane() && d < bestDistance)
			{
				bestDistance = d;
				best = rock;
			}
		}
		return best;
	}

	// ---- gains ----

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INV)
		{
			return;
		}
		ItemContainer inv = event.getItemContainer();
		int splinters = inv.count(RubiumData.SPLINTERS);
		int geodes = inv.count(RubiumData.GEODE);
		long now = System.currentTimeMillis();

		// Only count what you gain in the mine, not withdrawals from a bank
		boolean counting = inMine() && !bankOpen() && lastSplinters >= 0;
		if (counting && splinters > lastSplinters)
		{
			int gained = splinters - lastSplinters;
			session.addSplinters(gained, now);

			// Splinters with no geode used up came from a rock: the one you last clicked Mine on
			RockTracker.Rock target = miningTarget == null ? null : rocks.get(miningTarget);
			if (geodes >= lastGeodes && target != null && !target.isDeposit())
			{
				rocks.addYourSplinters(miningTarget, gained);
			}
		}
		if (counting && geodes > lastGeodes)
		{
			session.addGeodes(geodes - lastGeodes, now);
			depositTimer.onGeode(now);
		}
		lastSplinters = splinters;
		lastGeodes = geodes;

		int used = 0;
		for (Item item : inv.getItems())
		{
			if (item.getId() > 0)
			{
				used++;
			}
		}
		if (used < INVENTORY_SIZE)
		{
			notifiedFull = false;
		}
		else if (inMine() && !notifiedFull)
		{
			notifiedFull = true;
			notifier.notify(config.inventoryFullAlert(), geodes > 0
				? "Inventory full: " + geodes + " geodes to crack open."
				: "Inventory full.");
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() != Skill.MINING)
		{
			return;
		}
		int xp = event.getXp();
		if (lastMiningXp >= 0 && xp > lastMiningXp && inMine())
		{
			session.addXp(xp - lastMiningXp, System.currentTimeMillis());
		}
		lastMiningXp = xp;
	}

	private boolean bankOpen()
	{
		Widget bank = client.getWidget(InterfaceID.Bankmain.ITEMS);
		return bank != null && !bank.isHidden();
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		if ("rubium".equalsIgnoreCase(event.getCommand()) && event.getArguments().length > 0
			&& "reset".equalsIgnoreCase(event.getArguments()[0]))
		{
			session.reset();
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage("Rubium Miner session reset.")
				.build());
		}
	}
}
