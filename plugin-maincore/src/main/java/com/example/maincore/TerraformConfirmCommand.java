package com.example.maincore;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Backs the [✓] (groundbuza_terraform_confirm) and [✗] (groundbuza_terraform_cancel) chat buttons. */
public class TerraformConfirmCommand implements CommandExecutor {

    private final MainCorePlugin plugin;

    public TerraformConfirmCommand(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (command.getName().equals("groundbuza_terraform_confirm")) {
            plugin.getTerraformController().confirm(player);
        } else {
            plugin.getTerraformController().cancel(player);
        }
        return true;
    }
}
