package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** /크레딧 (잔액) · /크레딧 출금 <숫자> (아이템으로 인출) · /크레딧 송금 <플레이어> <숫자>. */
public class CreditCommand implements CommandExecutor, TabCompleter {

    private final MainCorePlugin plugin;
    /** Senders with a withdraw/transfer already in flight - the balance check and the deduct
     * happen on different async steps, so a double-fired command could otherwise overdraw. */
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public CreditCommand(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }

        if (args.length == 0) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                long balance = plugin.getMainDatabase().getBalance(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(Component.text(String.format("보유 크레딧: %,d", balance), NamedTextColor.GOLD)));
            });
            return true;
        }

        switch (args[0]) {
            case "출금" -> handleWithdraw(player, args);
            case "송금" -> handleTransfer(player, args);
            case "?" -> sendHelp(player);
            default -> {
                player.sendMessage(Component.text("알 수 없는 하위 명령어입니다.", NamedTextColor.RED));
                sendHelp(player);
            }
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("/크레딧 - 내 크레딧 잔액 확인", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/크레딧 출금 <숫자> - 크레딧을 아이템으로 인출 (우클릭 시 환원)", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/크레딧 송금 <플레이어> <숫자> - 다른 플레이어에게 크레딧 보내기", NamedTextColor.GRAY));
    }

    /** Positive amount, or -1 after messaging the player why. */
    private long parseAmount(Player player, String raw) {
        long amount;
        try {
            amount = Long.parseLong(raw.replace(",", ""));
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("숫자를 입력해주세요.", NamedTextColor.RED));
            return -1;
        }
        if (amount <= 0) {
            player.sendMessage(Component.text("0보다 큰 숫자를 입력해주세요.", NamedTextColor.RED));
            return -1;
        }
        return amount;
    }

    private void handleWithdraw(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("사용법: /크레딧 출금 <숫자>", NamedTextColor.RED));
            return;
        }
        long amount = parseAmount(player, args[1]);
        if (amount < 0) {
            return;
        }
        if (!inFlight.add(player.getUniqueId())) {
            player.sendMessage(Component.text("이전 요청을 아직 처리 중입니다.", NamedTextColor.RED));
            return;
        }
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long balance = plugin.getMainDatabase().getBalance(uuid);
            if (balance < amount) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    inFlight.remove(uuid);
                    player.sendMessage(Component.text(String.format("크레딧이 부족합니다. (보유: %,d)", balance), NamedTextColor.RED));
                });
                return;
            }
            long updated = plugin.getMainDatabase().addBalance(uuid, -amount);
            plugin.getEconomyCache().set(uuid, updated);
            Bukkit.getScheduler().runTask(plugin, () -> {
                inFlight.remove(uuid);
                player.getInventory().addItem(plugin.getCreditItem().create(amount));
                player.sendMessage(Component.text(String.format("%,d 크레딧을 인출했습니다.", amount), NamedTextColor.GOLD));
            });
        });
    }

    private void handleTransfer(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("사용법: /크레딧 송금 <플레이어> <숫자>", NamedTextColor.RED));
            return;
        }
        long amount = parseAmount(player, args[2]);
        if (amount < 0) {
            return;
        }
        String targetName = args[1];
        if (targetName.equalsIgnoreCase(player.getName())) {
            player.sendMessage(Component.text("자기 자신에게는 송금할 수 없습니다.", NamedTextColor.RED));
            return;
        }
        // Online first (either case), then anyone this server has seen before - never a Mojang
        // lookup for a typo'd name.
        OfflinePlayer target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            target = Bukkit.getPlayer(targetName);
        }
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(targetName);
        }
        if (target == null || (!target.isOnline() && !target.hasPlayedBefore())) {
            player.sendMessage(Component.text("존재하지 않는 플레이어입니다: " + targetName, NamedTextColor.RED));
            return;
        }
        if (!inFlight.add(player.getUniqueId())) {
            player.sendMessage(Component.text("이전 요청을 아직 처리 중입니다.", NamedTextColor.RED));
            return;
        }

        UUID from = player.getUniqueId();
        UUID to = target.getUniqueId();
        String toName = target.getName() == null ? targetName : target.getName();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long balance = plugin.getMainDatabase().getBalance(from);
            if (balance < amount) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    inFlight.remove(from);
                    player.sendMessage(Component.text(String.format("크레딧이 부족합니다. (보유: %,d)", balance), NamedTextColor.RED));
                });
                return;
            }
            long senderUpdated = plugin.getMainDatabase().addBalance(from, -amount);
            long receiverUpdated = plugin.getMainDatabase().addBalance(to, amount);
            plugin.getEconomyCache().set(from, senderUpdated);
            Bukkit.getScheduler().runTask(plugin, () -> {
                inFlight.remove(from);
                player.sendMessage(Component.text(
                        String.format("%s님에게 %,d 크레딧을 송금했습니다. (잔액: %,d)", toName, amount, senderUpdated),
                        NamedTextColor.GOLD));
                Player receiver = Bukkit.getPlayer(to);
                if (receiver != null) {
                    plugin.getEconomyCache().set(to, receiverUpdated);
                    receiver.sendMessage(Component.text(
                            String.format("%s님이 %,d 크레딧을 보냈습니다. (잔액: %,d)", player.getName(), amount, receiverUpdated),
                            NamedTextColor.GOLD));
                }
            });
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String prefix = args[0];
            return Stream.of("출금", "송금").filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equals("송금")) {
            String prefix = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> !n.equals(sender.getName()) && n.toLowerCase().startsWith(prefix)).toList();
        }
        return List.of();
    }
}
