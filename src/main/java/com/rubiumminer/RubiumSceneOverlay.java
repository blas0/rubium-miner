package com.rubiumminer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Outlines active rubium rocks (thicker for the nearest one) and the active deposit with its
 * depletion timer, counts down depleted rocks' respawns in seconds, and labels the inactive deposit. Outlines only, no fill, and labels sit on a
 * dark backing so they stay readable over the lava. Drawing only: click zones are untouched.
 */
class RubiumSceneOverlay extends Overlay
{
	private static final Stroke THIN = new BasicStroke(1f);
	private static final Stroke THICK = new BasicStroke(2f);
	private static final Color NO_FILL = new Color(0, 0, 0, 0);
	private static final Color LABEL_BACKING = new Color(0, 0, 0, 150);
	private static final Color NEXT_COLOR = new Color(170, 170, 170);
	/** Shared with the session box, where it colors the hourly rates. */
	static final Color GUARANTEED_COLOR = new Color(120, 255, 160);
	private static final int LABEL_PAD_X = 4;
	private static final int LABEL_PAD_Y = 1;
	private static final int LABEL_ARC = 6;

	private final Client client;
	private final RubiumMinerPlugin plugin;
	private final RubiumMinerConfig config;

	@Inject
	RubiumSceneOverlay(Client client, RubiumMinerPlugin plugin, RubiumMinerConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!plugin.inMine())
		{
			return null;
		}

		RubiumMinerConfig.RockHighlight highlight = config.rockHighlight();
		Player player = client.getLocalPlayer();
		RockTracker.Rock nearest = player != null && highlight != RubiumMinerConfig.RockHighlight.OFF
			? plugin.getRocks().nearestActiveRock(player.getWorldLocation())
			: null;
		Map<WorldPoint, GameObject> objects = plugin.getObjects();
		long now = System.currentTimeMillis();

		for (RockTracker.Rock rock : plugin.getRocks().all())
		{
			GameObject object = objects.get(rock.getLocation());
			if (object == null)
			{
				continue;
			}
			if (rock.isDeposit())
			{
				renderDeposit(g, object, rock, now);
				continue;
			}
			if (!rock.isActive())
			{
				long respawn = plugin.getRocks().respawnRemainingMs(rock.getLocation(), now);
				if (config.rockLabels() && respawn >= 0)
				{
					label(g, object, DurationFormat.seconds(respawn), Color.WHITE);
				}
				continue;
			}

			if (rock == nearest)
			{
				outline(g, object, config.rockColor(), THICK);
			}
			else if (highlight == RubiumMinerConfig.RockHighlight.ALL)
			{
				outline(g, object, config.rockColor(), THIN);
			}
			if (config.rockLabels())
			{
				renderRockLabel(g, object, rock);
			}
		}
		return null;
	}

	private void renderRockLabel(Graphics2D g, GameObject object, RockTracker.Rock rock)
	{
		RockTracker rocks = plugin.getRocks();
		if (rock.getLocation().equals(plugin.getMiningTarget()))
		{
			String odds = rocks.oddsLabel(rock.getLocation());
			if (odds != null)
			{
				label(g, object, odds, rocks.guaranteedLeft(rock.getLocation()) > 0 ? GUARANTEED_COLOR : Color.WHITE);
			}
		}
		else if (rocks.isFresh(rock))
		{
			label(g, object, "Fresh", GUARANTEED_COLOR);
		}
	}

	private void renderDeposit(Graphics2D g, GameObject object, RockTracker.Rock rock, long now)
	{
		if (!config.highlightDeposit())
		{
			return;
		}
		if (!rock.isActive())
		{
			label(g, object, "Next deposit", NEXT_COLOR);
			return;
		}

		outline(g, object, config.depositColor(), THICK);
		DepositTimer.Estimate estimate = plugin.getDepositTimer().estimate(now);
		String text = estimate == null
			? "Active"
			: estimate.getMaxRemainingMs() == 0
				? "Depleting any moment"
				: "Depletes in " + DurationFormat.range(estimate.getMinRemainingMs(), estimate.getMaxRemainingMs());
		label(g, object, text, config.depositColor());
	}

	private static void outline(Graphics2D g, GameObject object, Color color, Stroke stroke)
	{
		Shape hull = object.getConvexHull();
		if (hull != null)
		{
			OverlayUtil.renderPolygon(g, hull, color, NO_FILL, stroke);
		}
	}

	private static void label(Graphics2D g, GameObject object, String text, Color color)
	{
		Point at = object.getCanvasTextLocation(g, text, 140);
		if (at == null)
		{
			return;
		}
		FontMetrics metrics = g.getFontMetrics();
		Object antialias = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(LABEL_BACKING);
		g.fillRoundRect(
			at.getX() - LABEL_PAD_X,
			at.getY() - metrics.getAscent() - LABEL_PAD_Y,
			metrics.stringWidth(text) + LABEL_PAD_X * 2,
			metrics.getAscent() + metrics.getDescent() + LABEL_PAD_Y * 2,
			LABEL_ARC, LABEL_ARC);
		if (antialias != null)
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialias);
		}
		OverlayUtil.renderTextLocation(g, at, text, color);
	}
}
