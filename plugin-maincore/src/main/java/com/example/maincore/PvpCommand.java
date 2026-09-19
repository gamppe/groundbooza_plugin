package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class PvpCommand implements CommandExecutor {

    private final MainCorePlugin plugin;

    public PvpCommand(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean nowEnabled = plugin.getPvpProtectionState().toggle();
        Component message = Component.text(
                "땅 엔티티 보호 / 펫 보호가 " + (nowEnabled ? "켜졌습니다." : "꺼졌습니다."),
                nowEnabled ? NamedTextColor.GREEN : NamedTextColor.RED);
        Bukkit.broadcast(message);
        return true;
    }
}
