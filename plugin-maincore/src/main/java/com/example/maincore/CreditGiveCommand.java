package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class CreditGiveCommand implements CommandExecutor, TabCompleter {

    private final MainCorePlugin plugin;

    public CreditGiveCommand(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equals("?")) {
            sender.sendMessage(Component.text("/크레딧지급 <닉네임> <액수> - 해당 플레이어에게 크레딧 지급 (OP 전용)", NamedTextColor.GRAY));
            return true;
        }
        if (!sender.isOp()) {
            sender.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("사용법: /크레딧지급 <닉네임> <액수>", NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("접속 중인 플레이어를 찾을 수 없습니다: " + args[0], NamedTextColor.RED));
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("숫자를 입력해주세요.", NamedTextColor.RED));
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long newBalance = plugin.getMainDatabase().addBalance(target.getUniqueId(), amount);
            plugin.getEconomyCache().set(target.getUniqueId(), newBalance);
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Component.text(String.format("%s에게 %,d 크레딧을 지급했습니다. (잔액: %,d)",
                        target.getName(), amount, newBalance), NamedTextColor.GREEN));
                target.sendMessage(Component.text(String.format("%,d 크레딧을 받았습니다.", amount), NamedTextColor.GOLD));
            });
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !sender.isOp()) {
            return List.of();
        }
        String prefix = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(prefix))
                .toList();
    }
}
