package com.example.serverbridge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /귓속말 <플레이어> <메시지>. Works across main/farm: a target on this server is messaged
 * directly; otherwise the proxy is asked for its full player list ("PlayerList ALL") and, if the
 * name is on it, the text is relayed through the proxy's "Message" subchannel. Lives in
 * ServerBridge so both servers have it.
 */
public class WhisperCommand implements CommandExecutor, TabCompleter, PluginMessageListener {

    private static final long LOOKUP_TIMEOUT_TICKS = 40L; // 2s with no proxy reply → give up

    private record Pending(String targetName, String message, int timeoutTask) {}

    private final ServerBridgePlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public WhisperCommand(ServerBridgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("사용법: /귓속말 <플레이어> <메시지>", NamedTextColor.RED));
            return true;
        }
        String targetName = args[0];
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        if (message.isEmpty()) {
            player.sendMessage(Component.text("메시지를 입력하세요.", NamedTextColor.RED));
            return true;
        }
        if (targetName.equalsIgnoreCase(player.getName())) {
            player.sendMessage(Component.text("자기 자신에게는 보낼 수 없습니다.", NamedTextColor.RED));
            return true;
        }

        Player local = Bukkit.getPlayerExact(targetName);
        if (local == null) {
            local = Bukkit.getPlayer(targetName);
        }
        if (local != null) {
            local.sendMessage(incoming(player.getName(), message));
            player.sendMessage(outgoing(local.getName(), message));
            return true;
        }

        // Not here - maybe on the other server. Ask the proxy who's online, answer in onPluginMessageReceived.
        if (pending.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("이전 귓속말을 아직 처리 중입니다.", NamedTextColor.RED));
            return true;
        }
        int timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pending.remove(player.getUniqueId()) != null) {
                player.sendMessage(Component.text("접속 중인 플레이어가 아닙니다.", NamedTextColor.RED));
            }
        }, LOOKUP_TIMEOUT_TICKS).getTaskId();
        pending.put(player.getUniqueId(), new Pending(targetName, message, timeout));
        sendProxy(player, out -> {
            out.writeUTF("PlayerList");
            out.writeUTF("ALL");
        });
        return true;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player carrier, byte[] bytes) {
        if (!channel.equals("BungeeCord")) {
            return;
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (!in.readUTF().equals("PlayerList") || !in.readUTF().equals("ALL")) {
                return;
            }
            List<String> online = new ArrayList<>();
            for (String name : in.readUTF().split(", ")) {
                if (!name.isBlank()) online.add(name);
            }
            resolvePending(online);
        } catch (IOException e) {
            plugin.getLogger().warning("Bad PlayerList reply from proxy: " + e.getMessage());
        }
    }

    private void resolvePending(List<String> online) {
        for (Map.Entry<UUID, Pending> entry : pending.entrySet()) {
            Player sender = Bukkit.getPlayer(entry.getKey());
            Pending p = entry.getValue();
            pending.remove(entry.getKey());
            Bukkit.getScheduler().cancelTask(p.timeoutTask());
            if (sender == null) {
                continue;
            }
            String exact = online.stream().filter(n -> n.equalsIgnoreCase(p.targetName())).findFirst().orElse(null);
            if (exact == null) {
                sender.sendMessage(Component.text("접속 중인 플레이어가 아닙니다.", NamedTextColor.RED));
                continue;
            }
            String legacy = "§d[귓속말] §b" + sender.getName() + " §7→ §b나§7: §f" + p.message();
            sendProxy(sender, out -> {
                out.writeUTF("Message");
                out.writeUTF(exact);
                out.writeUTF(legacy);
            });
            sender.sendMessage(outgoing(exact, p.message()));
        }
    }

    private interface Payload {
        void write(DataOutputStream out) throws IOException;
    }

    private void sendProxy(Player via, Payload payload) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            payload.write(out);
            via.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to send proxy plugin message: " + e.getMessage());
        }
    }

    private Component incoming(String from, String message) {
        return Component.text("[귓속말] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(from, NamedTextColor.AQUA))
                .append(Component.text(" → ", NamedTextColor.GRAY))
                .append(Component.text("나", NamedTextColor.AQUA))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(message, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
    }

    private Component outgoing(String to, String message) {
        return Component.text("[귓속말] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text("나", NamedTextColor.AQUA))
                .append(Component.text(" → ", NamedTextColor.GRAY))
                .append(Component.text(to, NamedTextColor.AQUA))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(message, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(n -> !n.equals(sender.getName()) && n.toLowerCase().startsWith(prefix)).toList();
    }
}
