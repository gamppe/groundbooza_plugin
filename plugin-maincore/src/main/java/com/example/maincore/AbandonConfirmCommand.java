package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AbandonConfirmCommand implements CommandExecutor {

    private final MainCorePlugin plugin;

    public AbandonConfirmCommand(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        Integer landId = plugin.getAbandonRequestManager().take(player.getUniqueId());
        if (landId == null) {
            player.sendMessage(Component.text("대기 중인 포기 요청이 없습니다.", NamedTextColor.RED));
            return true;
        }
        MainDatabase.Land land = plugin.getLandManager().getLandById(landId);
        String name = land != null ? land.name() : "그 땅";

        plugin.getLandManager().abandonAsync(landId, () ->
                player.sendMessage(Component.text(name + " 땅의 소유권을 포기했습니다.", NamedTextColor.YELLOW)));
        return true;
    }
}
