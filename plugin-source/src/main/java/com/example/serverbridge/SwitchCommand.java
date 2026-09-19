package com.example.serverbridge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class SwitchCommand implements CommandExecutor {

    private final ServerBridgePlugin plugin;

    public SwitchCommand(ServerBridgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }

        String target = plugin.getTargetServer();
        player.sendMessage(Component.text("[" + target + "] 서버로 이동합니다...", NamedTextColor.AQUA));

        try {
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteArray);
            out.writeUTF("Connect");
            out.writeUTF(target);
            player.sendPluginMessage(plugin, "BungeeCord", byteArray.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to send server-switch plugin message: " + e.getMessage());
            player.sendMessage(Component.text("서버 이동 실패: " + e.getMessage(), NamedTextColor.RED));
        }
        return true;
    }
}
