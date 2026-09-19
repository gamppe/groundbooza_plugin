package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LandBuyConfirmCommand implements CommandExecutor {

    private final MainCorePlugin plugin;

    public LandBuyConfirmCommand(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        PendingPurchaseManager.Pending pending = plugin.getPendingPurchaseManager().take(player.getUniqueId());
        if (pending == null) {
            player.sendMessage(Component.text("대기 중인 구매 요청이 없습니다.", NamedTextColor.RED));
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long balance = plugin.getMainDatabase().getBalance(player.getUniqueId());
            if (balance < pending.price()) {
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(
                        Component.text(String.format("크레딧이 부족합니다. 필요: %,d, 보유: %,d", pending.price(), balance),
                                NamedTextColor.RED)));
                return;
            }
            if (pending.price() > 0) {
                long updated = plugin.getMainDatabase().addBalance(player.getUniqueId(), -pending.price());
                plugin.getEconomyCache().set(player.getUniqueId(), updated);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                // The DB row (not the physical paper) is the actual entitlement, so it's
                // recorded first - the item handed out is just a regenerable printout of it.
                plugin.getLandManager().createPendingDeedAsync(player.getUniqueId(), pending.landName(), false, deed -> {
                    if (deed == null) {
                        player.sendMessage(Component.text("구매 처리 중 오류가 발생했습니다.", NamedTextColor.RED));
                        return;
                    }
                    player.getInventory().addItem(plugin.getPendingDeedItem().create(deed));
                    player.sendMessage(Component.text(
                            "빈 땅문서 '" + pending.landName() + "' 을 구매했습니다. 손에 들고 원하는 구역에서 우클릭하세요.",
                            NamedTextColor.GREEN));
                });
            });
        });
        return true;
    }
}
