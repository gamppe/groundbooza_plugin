package com.example.magicwar;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /클래스 - the same screen the guide book opens. */
public class ClassCommand implements CommandExecutor {

    private final ClassController controller;

    public ClassCommand(ClassController controller) {
        this.controller = controller;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }
        controller.open(player);
        return true;
    }
}
