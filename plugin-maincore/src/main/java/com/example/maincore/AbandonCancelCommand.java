package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AbandonCancelCommand implements CommandExecutor {

    private final MainCorePlugin plugin;

    public AbandonCancelCommand(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        plugin.getAbandonRequestManager().cancel(player.getUniqueId());
        player.sendMessage(Component.text("포기를 취소했습니다.", NamedTextColor.GREEN));
        return true;
    }
}
