package com.rubiumminer;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

/**
 * Session box, three lines of white totals with green hourly rates:
 * <pre>
 * Splinters: 129 (1503/hr)
 * Mining XP: 3.9k xp (25.5k/hr)
 * GP: 18.5k gp (125k/hr)
 * </pre>
 * Rates are over active time (see {@link MiningSession}). The box widens to fit its text.
 */
class RubiumSessionOverlay extends OverlayPanel
{
	private final RubiumMinerPlugin plugin;
	private final RubiumMinerConfig config;
	private final ItemManager itemManager;

	@Inject
	RubiumSessionOverlay(RubiumMinerPlugin plugin, RubiumMinerConfig config, ItemManager itemManager)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		MiningSession session = plugin.getSession();
		if (!config.showSessionOverlay() || !plugin.inMine() || !session.isActive())
		{
			return null;
		}

		List<String> lines = new ArrayList<>();
		lines.add(line("Splinters", session.getSplinters(), "", session));
		lines.add(line("Mining XP", session.getXp(), " xp", session));
		long price = itemManager.getItemPrice(RubiumData.SPLINTERS);
		if (price > 0)
		{
			lines.add(line("GP", session.getSplinters() * price, " gp", session));
		}

		fitWidth(g, lines);
		for (String text : lines)
		{
			panelComponent.getChildren().add(LineComponent.builder().left(text).build());
		}
		return super.render(g);
	}

	/**
	 * "Mining XP: 6.1k xp (24.6k/hr)", with the rate left off during the first minute of active time.
	 * The rate goes last because overlay text understands an opening color tag but prints a closing one
	 * literally, so the green has to run to the end of the line.
	 */
	private static String line(String label, long total, String unit, MiningSession session)
	{
		long perHour = session.perHour(total);
		String text = label + ": " + AmountFormat.total(total) + unit;
		return perHour > 0
			? text + " " + ColorUtil.prependColorTag("(" + AmountFormat.compact(perHour) + "/hr)", RubiumSceneOverlay.GUARANTEED_COLOR)
			: text;
	}

	/** Widen the box so no line wraps or spills past its edge, including when you've resized it narrower. */
	private void fitWidth(Graphics2D g, List<String> lines)
	{
		FontMetrics metrics = g.getFontMetrics();
		int widest = 0;
		for (String text : lines)
		{
			widest = Math.max(widest, metrics.stringWidth(Text.removeTags(text)));
		}
		int needed = Math.max(ComponentConstants.STANDARD_WIDTH, widest + ComponentConstants.STANDARD_BORDER * 2 + 2);

		Dimension yours = getPreferredSize();
		if (yours == null)
		{
			panelComponent.setPreferredSize(new Dimension(needed, 0));
		}
		else if (yours.width < needed)
		{
			setPreferredSize(new Dimension(needed, yours.height));
		}
	}
}
