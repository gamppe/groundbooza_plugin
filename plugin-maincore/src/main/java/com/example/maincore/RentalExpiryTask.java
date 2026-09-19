package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Every second, refreshes the countdown lore on rented tools and removes ones that expired. */
public class RentalExpiryTask {

    private final MainCorePlugin plugin;

    public RentalExpiryTask(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, 20L, 20L);
    }

    private void sweep() {
        RentalToolItem rental = plugin.getRentalToolItem();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerInventory inventory = player.getInventory();
            ItemStack[] contents = inventory.getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (!rental.isRentalTool(item)) {
                    continue;
                }
                Long expiresAt = rental.getExpiresAt(item);
                if (expiresAt == null) {
                    continue;
                }
                if (System.currentTimeMillis() >= expiresAt) {
                    inventory.setItem(i, null);
                    player.sendMessage(Component.text("대여한 도구가 만료되어 사라졌습니다.", NamedTextColor.RED));
                } else {
                    rental.refreshLore(item);
                    inventory.setItem(i, item);
                }
            }
        }
    }
}
