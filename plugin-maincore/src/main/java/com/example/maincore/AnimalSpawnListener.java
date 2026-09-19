package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * Animals never appear on their own on main-server: the only way to get one is a 농부 using a
 * spawn egg (bought from their special shop). Offspring of those animals are still allowed -
 * breeding, chicken eggs and the like - so a farm can grow once it's been started.
 */
public class AnimalSpawnListener implements Listener {

    /** Spawn reasons that trace back to an animal a player already legitimately has. */
    private static final Set<CreatureSpawnEvent.SpawnReason> ALLOWED = Set.of(
            CreatureSpawnEvent.SpawnReason.SPAWNER_EGG,  // gated to 농부 in onUseSpawnEgg below
            CreatureSpawnEvent.SpawnReason.BREEDING,
            CreatureSpawnEvent.SpawnReason.EGG,
            CreatureSpawnEvent.SpawnReason.SHEARED,      // mooshroom → cow
            CreatureSpawnEvent.SpawnReason.CUSTOM);      // other plugins / commands

    private final MainCorePlugin plugin;

    public AnimalSpawnListener(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Animals)) {
            return;
        }
        if (!ALLOWED.contains(event.getSpawnReason())) {
            event.setCancelled(true);
        }
    }

    /** Spawn eggs only work in a 농부's hands. */
    @EventHandler(ignoreCancelled = true)
    public void onUseSpawnEgg(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !isSpawnEgg(item.getType())) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.getJobManager().hasJob(player.getUniqueId(), Job.FARMER)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("스폰알은 농부만 사용할 수 있습니다.", NamedTextColor.RED));
            return;
        }
        AnimalEggItem eggs = plugin.getAnimalEggItem();
        if (!eggs.isVariantEgg(item)) {
            return; // a plain vanilla egg (e.g. /give) - let vanilla handle it
        }
        // Our own egg: spawn the exact variant ourselves instead of vanilla's random roll.
        event.setCancelled(true);
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Location where = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        if (!player.isOp() && !plugin.getLandManager().canBuild(where, player.getUniqueId())) {
            player.sendMessage(Component.text("다른 사람의 땅에는 동물을 소환할 수 없습니다.", NamedTextColor.RED));
            return;
        }
        if (eggs.spawn(item, where, player) != null && player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    /** No sneaking an egg past the job check through a dispenser. */
    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (isSpawnEgg(event.getItem().getType())) {
            event.setCancelled(true);
        }
    }

    private static boolean isSpawnEgg(Material material) {
        return material.name().endsWith("_SPAWN_EGG");
    }
}
