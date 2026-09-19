package com.example.serverbridge;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;
import java.util.Set;

/** Farm-server copy of MainCore's JobListener.onFish: 어부's catches depend on the biome. */
public class FisherAbilities implements Listener {

    private static final Set<Material> VANILLA_FISH = Set.of(
            Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH);

    private final JobCache jobs;
    private final Random random = new Random();

    public FisherAbilities(JobCache jobs) {
        this.jobs = jobs;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH
                || !jobs.hasJob(event.getPlayer().getUniqueId(), JobCache.FISHER)
                || !(event.getCaught() instanceof Item caughtItem)) {
            return;
        }
        ItemStack stack = caughtItem.getItemStack();
        if (!VANILLA_FISH.contains(stack.getType())) {
            return;
        }
        Material replacement = fishFor(event.getHook().getLocation().getBlock().getBiome());
        if (replacement != stack.getType()) {
            caughtItem.setItemStack(new ItemStack(replacement, stack.getAmount()));
        }
    }

    private Material fishFor(Biome biome) {
        String key = biome.getKey().getKey();
        if (key.contains("frozen") || key.contains("cold") || key.contains("snowy")
                || key.contains("ice") || key.contains("taiga") || key.contains("grove")) {
            return Material.SALMON;
        }
        if (key.contains("warm") || key.contains("jungle") || key.contains("mangrove") || key.contains("lush")) {
            return random.nextDouble() < 0.2 ? Material.PUFFERFISH : Material.TROPICAL_FISH;
        }
        if (key.contains("ocean") || key.contains("beach")) {
            return Material.COD;
        }
        return random.nextBoolean() ? Material.COD : Material.SALMON;
    }
}
