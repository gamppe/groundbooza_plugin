package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** 테라포밍 menu clicks and the brush's right-click. */
public class TerraformListener implements Listener {

    private final MainCorePlugin plugin;

    public TerraformListener(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TerraformHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getSlot();
        if (slot == TerraformHolder.SLOT_BACK) {
            plugin.getShopController().openBuyMenu(player);
            return;
        }
        TerraformController.Op op = TerraformHolder.opForSlot(slot);
        if (op != null) {
            plugin.getTerraformController().select(player, op);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBrushUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack hand = event.getItem();
        TerraformController terraform = plugin.getTerraformController();
        if (!terraform.isBrush(hand) || event.getClickedBlock() == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (terraform.isExpired(hand)) {
            hand.setAmount(0);
            player.sendMessage(Component.text("테라포밍 붓이 소멸했습니다.", NamedTextColor.GRAY));
            return;
        }
        terraform.brushClick(player, event.getClickedBlock());
    }
}
