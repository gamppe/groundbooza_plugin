package com.example.magicwar;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bookkeeping the 서리기사 skills share: who is frostbitten, who is silenced, and which blocks
 * were turned to ice and owe a restore.
 *
 * <p>동상 is vanilla's powder-snow freeze used purely as a visual. Vanilla decays freeze ticks
 * the moment an entity leaves the snow and starts hurting it once it is fully frozen, so the
 * ticks are topped up every tick and freeze damage is cancelled outright - the state is a mark
 * for 얼음뭉치 to look for, not a damage source.
 */
public class FrostState implements Listener {

    /** Full freeze: the blue overlay is on and vanilla would start dealing damage, which is
     * exactly what onFreezeDamage stops. */
    private static final int FREEZE_TICKS = 160;

    private record TempBlock(Block block, BlockData original, long restoreAtMillis) {}

    private final MagicWarPlugin plugin;
    /** Entity id to when the frostbite lapses. */
    private final Map<UUID, Long> frostbitten = new HashMap<>();
    /** Entity id to when it may cast again. */
    private final Map<UUID, Long> silenced = new HashMap<>();
    private final List<TempBlock> tempBlocks = new ArrayList<>();
    private final java.util.Random random = new java.util.Random();

    public FrostState(MagicWarPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- 동상 ----------

    public void applyFrostbite(LivingEntity target, int seconds) {
        frostbitten.put(target.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        target.setFreezeTicks(FREEZE_TICKS);
    }

    public boolean isFrostbitten(Entity entity) {
        Long until = frostbitten.get(entity.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    public void clearFrostbite(Entity entity) {
        frostbitten.remove(entity.getUniqueId());
        entity.setFreezeTicks(0);
    }

    /** 동상 is a status, not a damage-over-time - vanilla's freeze damage is not wanted. */
    @EventHandler(ignoreCancelled = true)
    public void onFreezeDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FREEZE
                && frostbitten.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ---------- 주문 봉인 ----------

    public void silence(UUID uuid, int ticks) {
        silenced.put(uuid, System.currentTimeMillis() + ticks * 50L);
    }

    public boolean isSilenced(UUID uuid) {
        Long until = silenced.get(uuid);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            silenced.remove(uuid);
            return false;
        }
        return true;
    }

    // ---------- 임시 얼음 ----------

    /** Turns a block to ice and schedules it back. Blocks that are already ours are left alone
     * so an overlapping cast cannot bake the ice in permanently. */
    public void placeTemporary(Block block, Material material, int ticks) {
        if (block.getType() == material) {
            return;
        }
        tempBlocks.add(new TempBlock(block, block.getBlockData(), System.currentTimeMillis() + ticks * 50L));
        block.setType(material, false);
    }

    /** Called every tick from the plugin. */
    public void tick() {
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<UUID, Long>> frost = frostbitten.entrySet().iterator();
        while (frost.hasNext()) {
            Map.Entry<UUID, Long> entry = frost.next();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity == null || !entity.isValid() || entry.getValue() <= now) {
                if (entity != null) {
                    entity.setFreezeTicks(0);
                }
                frost.remove();
                continue;
            }
            // Vanilla melts the freeze the instant they are out of the snow; keep it topped up.
            entity.setFreezeTicks(FREEZE_TICKS);
        }

        silenced.entrySet().removeIf(entry -> entry.getValue() <= now);

        Iterator<TempBlock> blocks = tempBlocks.iterator();
        while (blocks.hasNext()) {
            TempBlock temp = blocks.next();
            if (temp.restoreAtMillis() > now) {
                continue;
            }
            blocks.remove();
            Material was = temp.block().getType();
            temp.block().setBlockData(temp.original(), false);
            temp.block().getWorld().spawnParticle(Particle.BLOCK,
                    temp.block().getLocation().add(0.5, 0.5, 0.5), 12, 0.3, 0.3, 0.3, 0.0,
                    was.createBlockData());
            // A cast lays down well over a hundred blocks; every one of them cracking at once
            // would be a wall of noise, so only a scattering of them is heard.
            if (random.nextInt(8) == 0) {
                temp.block().getWorld().playSound(temp.block().getLocation(),
                        Sound.BLOCK_GLASS_BREAK, 0.6f, 1.2f);
            }
        }
    }

    /** Puts every borrowed block back at once - used when a round ends under the ice. */
    public void clear() {
        tempBlocks.forEach(temp -> temp.block().setBlockData(temp.original(), false));
        tempBlocks.clear();
        frostbitten.keySet().forEach(id -> {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.setFreezeTicks(0);
            }
        });
        frostbitten.clear();
        silenced.clear();
    }

    /** Highest standable spot in this column near {@code aroundY}, so a spike follows the
     * ground instead of burying itself or floating. */
    public Block surfaceAt(Location origin, int x, int z, int aroundY) {
        for (int dy = 2; dy >= -3; dy--) {
            Block candidate = origin.getWorld().getBlockAt(x, aroundY + dy, z);
            if (candidate.getType().isAir() || !candidate.getType().isSolid()) {
                Block below = candidate.getRelative(0, -1, 0);
                if (below.getType().isSolid()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    public MagicWarPlugin plugin() {
        return plugin;
    }
}
