package com.example.maincore;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Backs both the [✓] (groundbuza_job_confirm) and [✗] (groundbuza_job_cancel) chat buttons. */
public class JobConfirmCommand implements CommandExecutor {

    private final MainCorePlugin plugin;

    public JobConfirmCommand(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (command.getName().equals("groundbuza_job_confirm")) {
            plugin.getJobController().confirmChoice(player);
        } else {
            plugin.getJobController().cancelChoice(player);
        }
        return true;
    }
}
