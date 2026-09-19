package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DeedTeleportConfirmCommand implements CommandExecutor {

    private final MainCorePlugin plugin;

    public DeedTeleportConfirmCommand(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        Integer landId = plugin.getTeleportRequestManager().take(player.getUniqueId());
        if (landId == null) {
            player.sendMessage(Component.text("대기 중인 이동 요청이 없습니다.", NamedTextColor.RED));
            return true;
        }
        MainDatabase.Land land = plugin.getLandManager().getLandById(landId);
        if (land == null) {
            player.sendMessage(Component.text("존재하지 않는 땅입니다.", NamedTextColor.RED));
            return true;
        }

        long cost = DeedTeleportListener.COST;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long balance = plugin.getMainDatabase().getBalance(player.getUniqueId());
            if (balance < cost) {
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(
                        Component.text(String.format("크레딧이 부족합니다. 필요: %,d, 보유: %,d", cost, balance),
                                NamedTextColor.RED)));
                return;
            }
            long updated = plugin.getMainDatabase().addBalance(player.getUniqueId(), -cost);
            plugin.getEconomyCache().set(player.getUniqueId(), updated);

            Bukkit.getScheduler().runTask(plugin, () -> {
                double centerX = LandGrid.minX(land.cellX()) + LandGrid.CELL_SIZE / 2.0;
                double centerZ = LandGrid.minZ(land.cellZ()) + LandGrid.CELL_SIZE / 2.0;
                int y = player.getWorld().getHighestBlockYAt((int) centerX, (int) centerZ) + 1;
                player.teleportAsync(new Location(player.getWorld(), centerX, y, centerZ));
                player.sendMessage(Component.text(land.name() + " 땅으로 이동했습니다. (" + cost + " 크레딧 소모)",
                        NamedTextColor.GREEN));
            });
        });
        return true;
    }
}
