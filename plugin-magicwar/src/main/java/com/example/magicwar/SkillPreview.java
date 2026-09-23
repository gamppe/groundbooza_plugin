package com.example.magicwar;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The targeting marker a skill paints on the ground while it is held: a flat tile on each block
 * the cast would cover, in whatever material suits the skill.
 *
 * <p>Particles were the obvious choice and are per-player for free, but their lifetime is set
 * client-side and cannot be shortened - dust in particular hangs around for seconds, so the
 * shape smeared behind the crosshair and lingered after the skill went on cooldown. Display
 * entities can be removed the same tick, which is the whole point of this class.
 *
 * <p>They are entities, so they would be visible to everyone; each tile is hidden from every
 * player but its owner, and from anyone who logs in later.
 */
public class SkillPreview implements Listener {

    /** Thin enough to read as paint on the ground rather than a block. */
    private static final Transformation TILE = new Transformation(
            new Vector3f(0f, 0f, 0f), new AxisAngle4f(0f, 0f, 0f, 1f),
            new Vector3f(1f, 0.02f, 1f), new AxisAngle4f(0f, 0f, 0f, 1f));

    private static final class Preview {
        Block anchor;
        Material material;
        final List<BlockDisplay> tiles = new ArrayList<>();
    }

    private final MagicWarPlugin plugin;
    private final Map<UUID, Preview> previews = new HashMap<>();

    public SkillPreview(MagicWarPlugin plugin) {
        this.plugin = plugin;
    }

    /** Draws (or moves) the marker. Does nothing at all while the shape has not moved, which
     * is most ticks - otherwise this would be a few hundred teleports every tick. */
    public void show(Player player, Block anchor, List<Block> area, Material material) {
        Preview preview = previews.computeIfAbsent(player.getUniqueId(), id -> new Preview());
        if (anchor.equals(preview.anchor) && material == preview.material
                && preview.tiles.size() == area.size()) {
            return;
        }
        boolean materialChanged = material != preview.material;
        preview.anchor = anchor;
        preview.material = material;

        while (preview.tiles.size() > area.size()) {
            preview.tiles.remove(preview.tiles.size() - 1).remove();
        }
        for (int i = 0; i < area.size(); i++) {
            Location at = area.get(i).getLocation().add(0, 1, 0);
            BlockDisplay tile = i < preview.tiles.size() ? preview.tiles.get(i) : null;
            if (tile == null || !tile.isValid() || !tile.getWorld().equals(at.getWorld())) {
                if (tile != null) {
                    tile.remove();
                }
                tile = spawnTile(player, at, material);
                if (i < preview.tiles.size()) {
                    preview.tiles.set(i, tile);
                } else {
                    preview.tiles.add(tile);
                }
                continue;
            }
            if (materialChanged) {
                tile.setBlock(material.createBlockData());
            }
            tile.teleport(at);
        }
    }

    public void hide(UUID uuid) {
        Preview preview = previews.remove(uuid);
        if (preview != null) {
            preview.tiles.forEach(BlockDisplay::remove);
        }
    }

    private BlockDisplay spawnTile(Player owner, Location at, Material material) {
        BlockDisplay tile = at.getWorld().spawn(at, BlockDisplay.class, display -> {
            display.setBlock(material.createBlockData());
            display.setTransformation(TILE);
            display.setBrightness(new Display.Brightness(15, 15)); // readable in a dark arena
            display.setViewRange(0.5f);
            display.setPersistent(false);
        });
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (!other.equals(owner)) {
                other.hideEntity(plugin, tile);
            }
        }
        return tile;
    }

    /** A player who logs in mid-cast would otherwise see everyone else's markers. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (Map.Entry<UUID, Preview> entry : previews.entrySet()) {
            if (entry.getKey().equals(event.getPlayer().getUniqueId())) {
                continue;
            }
            entry.getValue().tiles.forEach(tile -> event.getPlayer().hideEntity(plugin, tile));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hide(event.getPlayer().getUniqueId());
    }

    public void clear() {
        previews.values().forEach(preview -> preview.tiles.forEach(BlockDisplay::remove));
        previews.clear();
    }
}
