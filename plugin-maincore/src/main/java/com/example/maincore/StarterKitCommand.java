package com.example.maincore;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** OP-only debug command to re-receive the new-player starter kit for testing. */
public class StarterKitCommand implements CommandExecutor {

    private final MainCorePlugin plugin;

    public StarterKitCommand(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }
        plugin.getStarterKitListener().giveKit(player);
        return true;
    }
}
