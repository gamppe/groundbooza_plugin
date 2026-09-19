package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CreditCommand implements CommandExecutor {

    private final MainCorePlugin plugin;

    public CreditCommand(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }

        if (args.length > 0 && args[0].equals("?")) {
            player.sendMessage(Component.text("/크레딧 - 내 크레딧 잔액 확인", NamedTextColor.GRAY));
            player.sendMessage(Component.text("/크레딧 <숫자> - 크레딧을 아이템으로 인출 (우클릭 시 환원)", NamedTextColor.GRAY));
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

        long amount;
        try {
            amount = Long.parseLong(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("숫자를 입력해주세요.", NamedTextColor.RED));
            return true;
        }
        if (amount <= 0) {
            player.sendMessage(Component.text("0보다 큰 숫자를 입력해주세요.", NamedTextColor.RED));
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long balance = plugin.getMainDatabase().getBalance(player.getUniqueId());
            if (balance < amount) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(Component.text("크레딧이 부족합니다. (보유: " + balance + ")", NamedTextColor.RED)));
                return;
            }
            long updated = plugin.getMainDatabase().addBalance(player.getUniqueId(), -amount);
            plugin.getEconomyCache().set(player.getUniqueId(), updated);
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.getInventory().addItem(plugin.getCreditItem().create(amount));
                player.sendMessage(Component.text(String.format("%,d 크레딧을 인출했습니다.", amount), NamedTextColor.GOLD));
            });
        });
        return true;
    }
}
