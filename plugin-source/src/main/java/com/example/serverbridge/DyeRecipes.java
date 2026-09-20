package com.example.serverbridge;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shop sells every dyeable block family in white; this makes sure each of them can be
 * recoloured one-for-one with a single dye. Wool, carpet, bed, candle and shulker box already
 * have that recipe in vanilla; terracotta / concrete powder / glass need 8 at a time and
 * concrete / banners can't be dyed at all - so those get a "1 white + 1 dye → 1 coloured"
 * shapeless recipe here. Farm-server copy of MainCore's DyeRecipes (same recipe keys under
 * the "serverbridge" namespace, so nothing collides if both ever load).
 */
public class DyeRecipes implements Listener {

    /** Every white block the shop sells, in shop order → Korean name. */
    public static final Map<Material, String> WHITE_BLOCKS = new LinkedHashMap<>();
    static {
        WHITE_BLOCKS.put(Material.WHITE_WOOL, "흰색 양털");
        WHITE_BLOCKS.put(Material.WHITE_CARPET, "흰색 카펫");
        WHITE_BLOCKS.put(Material.WHITE_TERRACOTTA, "흰색 테라코타");
        WHITE_BLOCKS.put(Material.WHITE_CONCRETE_POWDER, "흰색 콘크리트 가루");
        WHITE_BLOCKS.put(Material.WHITE_CONCRETE, "흰색 콘크리트");
        WHITE_BLOCKS.put(Material.WHITE_STAINED_GLASS, "흰색 색유리");
        WHITE_BLOCKS.put(Material.WHITE_STAINED_GLASS_PANE, "흰색 색유리판");
        WHITE_BLOCKS.put(Material.WHITE_BED, "흰색 침대");
        WHITE_BLOCKS.put(Material.WHITE_BANNER, "흰색 현수막");
        WHITE_BLOCKS.put(Material.WHITE_CANDLE, "흰색 양초");
        WHITE_BLOCKS.put(Material.WHITE_SHULKER_BOX, "흰색 셜커 상자");
    }

    /** Families with no 1:1 dye recipe in vanilla (the rest already work). */
    private static final List<Material> NEEDS_RECIPE = List.of(
            Material.WHITE_TERRACOTTA, Material.WHITE_CONCRETE_POWDER, Material.WHITE_CONCRETE,
            Material.WHITE_STAINED_GLASS, Material.WHITE_STAINED_GLASS_PANE, Material.WHITE_BANNER);

    private final Plugin plugin;
    private final List<NamespacedKey> keys = new ArrayList<>();

    public DyeRecipes(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Registers (re-registering on /reload is harmless - the old key is removed first). */
    public void register() {
        for (Material white : NEEDS_RECIPE) {
            String family = white.name().substring("WHITE_".length());
            for (DyeColor color : DyeColor.values()) {
                if (color == DyeColor.WHITE) continue;
                Material colored = Material.matchMaterial(color.name() + "_" + family);
                Material dye = Material.matchMaterial(color.name() + "_DYE");
                if (colored == null || dye == null) continue;
                NamespacedKey key = new NamespacedKey(plugin, "dye_" + colored.name().toLowerCase());
                Bukkit.removeRecipe(key);
                ShapelessRecipe recipe = new ShapelessRecipe(key, new ItemStack(colored));
                recipe.addIngredient(white);
                recipe.addIngredient(dye);
                Bukkit.addRecipe(recipe);
                keys.add(key);
            }
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.discoverRecipes(keys);
        }
        plugin.getLogger().info("Registered " + keys.size() + " white-block dye recipes.");
    }

    /** Custom recipes don't auto-unlock, so hand them to the recipe book on join. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipes(keys);
    }
}
