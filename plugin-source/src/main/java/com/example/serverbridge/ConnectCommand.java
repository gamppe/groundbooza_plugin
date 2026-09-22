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

/**
 * Sends the player to one named Velocity backend. One instance per destination command
 * (/마법전쟁서버, /땅부자서버, ...), each bound to a key under `servers:` in config.yml, so adding
 * another backend is a config entry plus a plugin.yml command - no new class.
 *
 * <p>Running it on the server you are already on is a no-op on the proxy's side, so it is
 * refused here with a message instead.
 */
public class ConnectCommand implements CommandExecutor {

    private final ServerBridgePlugin plugin;
    /** Velocity's [servers] key, e.g. "magic". */
    private final String server;
    /** What to call it in chat, e.g. "마법전쟁". */
    private final String label;

    public ConnectCommand(ServerBridgePlugin plugin, String server, String label) {
        this.plugin = plugin;
        this.server = server;
        this.label = label;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }
        if (server.equalsIgnoreCase(plugin.getServerName())) {
            player.sendMessage(Component.text("이미 " + this.label + " 서버에 있습니다.", NamedTextColor.RED));
            return true;
        }
        player.sendMessage(Component.text(this.label + " 서버로 이동합니다...", NamedTextColor.AQUA));
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to send server-switch plugin message: " + e.getMessage());
            player.sendMessage(Component.text("서버 이동 실패: " + e.getMessage(), NamedTextColor.RED));
        }
        return true;
    }
}
