package com.example.magicwar;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Blocks a skill borrowed and owes back. Every terrain-shaping skill goes through here rather
 * than editing the world outright, so an arena is never left full of somebody's ice or magma -
 * and a round that ends mid-cast can hand the lot back at once.
 *
 * <p>Physics is off on both the placement and the restore: a lava source placed with physics
 * would start flowing the moment it appears and outlive the block that spawned it.
 */
public class TempBlocks {

    private record Borrowed(Block block, BlockData original, long restoreAtMillis) {}

    private final List<Borrowed> borrowed = new ArrayList<>();
    private final Random random = new Random();

    /** Blocks already showing {@code material} are left alone, so overlapping casts cannot
     * chain their restores and bake the change in permanently. */
    public void place(Block block, Material material, int ticks) {
        if (block.getType() == material) {
            return;
        }
        borrowed.add(new Borrowed(block, block.getBlockData(), System.currentTimeMillis() + ticks * 50L));
        block.setType(material, false);
    }

    /** Called every tick from the plugin. */
    public void tick() {
        long now = System.currentTimeMillis();
        Iterator<Borrowed> it = borrowed.iterator();
        while (it.hasNext()) {
            Borrowed temp = it.next();
            if (temp.restoreAtMillis() > now) {
                continue;
            }
            it.remove();
            restore(temp, true);
        }
    }

    private void restore(Borrowed temp, boolean withEffects) {
        Block block = temp.block();
        BlockData was = block.getBlockData();
        block.setBlockData(temp.original(), false);
        if (!withEffects) {
            return;
        }
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().spawnParticle(Particle.BLOCK, at, 12, 0.3, 0.3, 0.3, 0.0, was);
        // A cast lays down well over a hundred blocks; every one of them breaking at once would
        // be a wall of noise, so only a scattering is heard.
        if (random.nextInt(8) == 0) {
            block.getWorld().playSound(at, was.getSoundGroup().getBreakSound(), 0.6f, 1.2f);
        }
    }

    /** Hands everything back at once, quietly - used when a round ends under the rubble. */
    public void clear() {
        borrowed.forEach(temp -> restore(temp, false));
        borrowed.clear();
    }
}
