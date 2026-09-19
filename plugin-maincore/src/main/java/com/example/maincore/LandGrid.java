package com.example.maincore;

import org.bukkit.Location;

/** 3x3-chunk (48x48 block) fixed grid cell math. */
public final class LandGrid {

    public static final int CELL_SIZE = 48; // 3 chunks * 16 blocks

    private LandGrid() {
    }

    public static int cellX(Location loc) {
        return Math.floorDiv(loc.getBlockX(), CELL_SIZE);
    }

    public static int cellZ(Location loc) {
        return Math.floorDiv(loc.getBlockZ(), CELL_SIZE);
    }

    public static int minX(int cellX) {
        return cellX * CELL_SIZE;
    }

    public static int minZ(int cellZ) {
        return cellZ * CELL_SIZE;
    }

    public static int maxX(int cellX) {
        return cellX * CELL_SIZE + CELL_SIZE - 1;
    }

    public static int maxZ(int cellZ) {
        return cellZ * CELL_SIZE + CELL_SIZE - 1;
    }

    public static boolean isSameCell(Location a, Location b) {
        return cellX(a) == cellX(b) && cellZ(a) == cellZ(b);
    }
}
