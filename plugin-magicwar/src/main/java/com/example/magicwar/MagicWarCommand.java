package com.example.magicwar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/** /마법전쟁 시작 · /마법전쟁 중지 */
public class MagicWarCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("시작", "중지");

    private final ArenaManager arena;

    public MagicWarCommand(ArenaManager arena) {
        this.arena = arena;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Component.text("사용법: /마법전쟁 <시작|중지>", NamedTextColor.RED));
            return true;
        }
        String error = switch (args[0]) {
            case "시작" -> arena.start();
            case "중지" -> arena.stop();
            default -> "사용법: /마법전쟁 <시작|중지>";
        };
        if (error != null) {
            sender.sendMessage(Component.text(error, NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0])).toList();
    }
}
