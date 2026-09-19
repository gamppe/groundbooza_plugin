package com.example.serverbridge;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class BoxCommand implements CommandExecutor {

    private final ServerBridgePlugin plugin;

    public BoxCommand(ServerBridgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }

        int size = plugin.getBoxSize();
        String title = plugin.getBoxTitle();

        player.sendMessage(Component.text("상자를 불러오는 중..."));

        // DB access must not happen on the main thread.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ItemStack[] contents = plugin.getDatabaseManager().loadBoxContents(player.getUniqueId(), size);

            // Back to the main thread to touch Bukkit inventory API.
            Bukkit.getScheduler().runTask(plugin, () -> {
                BoxInventoryHolder holder = new BoxInventoryHolder(player.getUniqueId());
                Inventory inv = Bukkit.createInventory(holder, size, Component.text(title));
                inv.setContents(contents);
                holder.setInventory(inv);
                player.openInventory(inv);
            });
        });

        return true;
    }
}
