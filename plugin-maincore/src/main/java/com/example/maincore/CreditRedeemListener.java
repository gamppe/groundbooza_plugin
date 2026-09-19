package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class CreditRedeemListener implements Listener {

    private final MainCorePlugin plugin;

    public CreditRedeemListener(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        Long amount = plugin.getCreditItem().getAmount(item);
        if (amount == null) {
            return;
        }
        event.setCancelled(true);

        item.setAmount(item.getAmount() - 1);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long newBalance = plugin.getMainDatabase().addBalance(event.getPlayer().getUniqueId(), amount);
            plugin.getEconomyCache().set(event.getPlayer().getUniqueId(), newBalance);
            Bukkit.getScheduler().runTask(plugin, () ->
                    event.getPlayer().sendMessage(Component.text(
                            String.format("%,d 크레딧을 환원했습니다. (잔액: %,d)", amount, newBalance),
                            NamedTextColor.GOLD)));
        });
    }
}
