package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class DeedTeleportListener implements Listener {

    public static final long COST = 1000L;

    private final MainCorePlugin plugin;

    public DeedTeleportListener(MainCorePlugin plugin) {
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
        Integer landId = plugin.getDeedItem().getLandId(item);
        if (landId == null) {
            return;
        }
        event.setCancelled(true);

        MainDatabase.Land land = plugin.getLandManager().getLandById(landId);
        if (land == null) {
            return;
        }

        Player player = event.getPlayer();
        plugin.getTeleportRequestManager().request(player.getUniqueId(), landId);

        player.sendMessage(Component.text(land.name() + " 땅으로 이동하시겠습니까? (" + COST + " 크레딧 소모) ",
                        NamedTextColor.AQUA)
                .append(Component.text("[✓]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/groundbuza_deed_tp_confirm")))
                .append(Component.text(" "))
                .append(Component.text("[✗]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/groundbuza_deed_tp_cancel"))));
    }
}
