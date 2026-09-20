package com.example.serverbridge;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Stonecutter shortcuts for a building server: an oak log cuts into any other log/stem, and a
 * plank cuts into that wood's stairs, slabs, fence, gate, door, trapdoor, plate, button and
 * sign one-for-one (two slabs, like stone). Farm-server copy of MainCore's WoodRecipes.
 */
public class WoodRecipes implements Listener {

    private static final List<Material> OTHER_LOGS = List.of(
            Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG, Material.ACACIA_LOG,
            Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG, Material.PALE_OAK_LOG,
            Material.BAMBOO_BLOCK, Material.CRIMSON_STEM, Material.WARPED_STEM);

    private static final List<String> WOOD_TYPES = List.of(
            "OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK", "MANGROVE", "CHERRY", "PALE_OAK",
            "BAMBOO", "CRIMSON", "WARPED");

    /** Suffix → how many one plank yields. */
    private static final String[] DERIVED = {
            "STAIRS", "SLAB", "FENCE", "FENCE_GATE", "DOOR", "TRAPDOOR", "PRESSURE_PLATE", "BUTTON", "SIGN"};

    private final Plugin plugin;
    private final List<NamespacedKey> keys = new ArrayList<>();

    public WoodRecipes(Plugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        for (Material log : OTHER_LOGS) {
            add("log_" + log.name().toLowerCase(), new ItemStack(log), Material.OAK_LOG);
        }
        for (String wood : WOOD_TYPES) {
            Material planks = Material.matchMaterial(wood + "_PLANKS");
            if (planks == null) continue;
            for (String suffix : DERIVED) {
                Material result = Material.matchMaterial(wood + "_" + suffix);
                if (result == null) continue;
                int amount = suffix.equals("SLAB") ? 2 : 1;
                add("plank_" + result.name().toLowerCase(), new ItemStack(result, amount), planks);
            }
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.discoverRecipes(keys);
        }
        plugin.getLogger().info("Registered " + keys.size() + " wood stonecutter recipes.");
    }

    private void add(String name, ItemStack result, Material input) {
        NamespacedKey key = new NamespacedKey(plugin, "cut_" + name);
        Bukkit.removeRecipe(key);
        Bukkit.addRecipe(new StonecuttingRecipe(key, result, input));
        keys.add(key);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipes(keys);
    }
}
