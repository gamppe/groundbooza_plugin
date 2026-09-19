package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Store a horse into / release it from a 말 보관 목줄 (HorseLeadItem). */
public class HorseLeadListener implements Listener {

    private final MainCorePlugin plugin;

    public HorseLeadListener(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    /** Runs before AnimalFeeding / vanilla leashing so a lead on a horse is always ours. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onStore(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Horse horse)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        HorseLeadItem leads = plugin.getHorseLeadItem();
        if (!leads.isLead(hand)) {
            return;
        }
        event.setCancelled(true);
        if (leads.isStored(hand)) {
            player.sendMessage(Component.text("이미 말이 보관된 목줄입니다. 블록을 우클릭해 먼저 꺼내세요.", NamedTextColor.RED));
            return;
        }
        if (!horse.isTamed() || horse.getOwner() == null || !player.getUniqueId().equals(horse.getOwner().getUniqueId())) {
            player.sendMessage(Component.text("내가 길들인 말만 보관할 수 있습니다.", NamedTextColor.RED));
            return;
        }
        if (!horse.getPassengers().isEmpty()) {
            player.sendMessage(Component.text("누군가 타고 있는 말은 보관할 수 없습니다.", NamedTextColor.RED));
            return;
        }
        ItemStack stored = leads.store(horse);
        if (player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }
        for (ItemStack leftover : player.getInventory().addItem(stored).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        player.sendMessage(Component.text("말을 목줄에 보관했습니다.", NamedTextColor.GREEN));
    }

    @EventHandler(ignoreCancelled = true)
    public void onRelease(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) {
            return;
        }
        ItemStack hand = event.getItem();
        HorseLeadItem leads = plugin.getHorseLeadItem();
        if (!leads.isLead(hand)) {
            return;
        }
        event.setCancelled(true); // an empty lead must not leash anything / do vanilla things either
        if (!leads.isStored(hand)) {
            return;
        }
        Player player = event.getPlayer();
        Location where = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        if (!player.isOp() && !plugin.getLandManager().canBuild(where, player.getUniqueId())) {
            player.sendMessage(Component.text("다른 사람의 땅에는 말을 꺼낼 수 없습니다.", NamedTextColor.RED));
            return;
        }
        leads.release(hand, player, where);
        hand.setAmount(hand.getAmount() - 1);
        player.sendMessage(Component.text("말을 꺼냈습니다.", NamedTextColor.GREEN));
    }
}
