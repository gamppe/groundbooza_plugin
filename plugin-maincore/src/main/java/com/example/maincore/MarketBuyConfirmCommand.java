package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MarketBuyConfirmCommand implements CommandExecutor {

    private final MainCorePlugin plugin;

    public MarketBuyConfirmCommand(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (args.length < 1) {
            return true;
        }
        int listingId;
        try {
            listingId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            return true;
        }
        plugin.getMarketController().buy(player, listingId);
        return true;
    }
}
