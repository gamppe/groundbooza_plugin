package com.example.magicwar;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.Color;

/**
 * Draws the live world border on top of a normal filled map. Vanilla maps show terrain only -
 * the border is not part of the picture - so the ring is painted here, over the terrain the
 * default renderer already put down.
 *
 * <p>At {@link MapView.Scale#FARTHEST} one pixel is 16 blocks, so a 128x128 map covers
 * 2048x2048 - just enough for a 1998-wide arena.
 */
public class BorderMapRenderer extends MapRenderer {

    private static final int SIZE = 128;
    private static final Color WALL = new Color(190, 60, 255);
    private static final Color WALL_EDGE = new Color(120, 0, 190);

    /** Blocks per pixel at the scale this renderer expects. */
    private static final int BLOCKS_PER_PIXEL = 16;

    public BorderMapRenderer() {
        super(true); // per-player: everyone sees the border from their own map
    }

    @Override
    public void render(MapView view, MapCanvas canvas, Player player) {
        World world = view.getWorld();
        if (world == null || !world.equals(player.getWorld())) {
            return;
        }
        WorldBorder border = world.getWorldBorder();
        Location centre = border.getCenter();
        double half = border.getSize() / 2;

        int left = toPixel(centre.getX() - half, view.getCenterX());
        int right = toPixel(centre.getX() + half, view.getCenterX());
        int top = toPixel(centre.getZ() - half, view.getCenterZ());
        int bottom = toPixel(centre.getZ() + half, view.getCenterZ());

        // A two-pixel wall: thin lines vanish against the terrain at this zoom.
        drawRect(canvas, left, top, right, bottom, WALL);
        drawRect(canvas, left - 1, top - 1, right + 1, bottom + 1, WALL_EDGE);
    }

    /** World coordinate to map pixel. The map's own centre sits at pixel 64. */
    private static int toPixel(double world, int mapCentre) {
        return (int) Math.round((world - mapCentre) / BLOCKS_PER_PIXEL) + SIZE / 2;
    }

    /** Outline only, clipped to the canvas - the border usually runs off the edge early on. */
    private static void drawRect(MapCanvas canvas, int x1, int y1, int x2, int y2, Color color) {
        for (int x = Math.max(0, x1); x <= Math.min(SIZE - 1, x2); x++) {
            plot(canvas, x, y1, color);
            plot(canvas, x, y2, color);
        }
        for (int y = Math.max(0, y1); y <= Math.min(SIZE - 1, y2); y++) {
            plot(canvas, x1, y, color);
            plot(canvas, x2, y, color);
        }
    }

    private static void plot(MapCanvas canvas, int x, int y, Color color) {
        if (x >= 0 && x < SIZE && y >= 0 && y < SIZE) {
            canvas.setPixelColor(x, y, color);
        }
    }
}
