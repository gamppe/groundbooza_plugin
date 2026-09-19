package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class HelpCommand implements CommandExecutor {

    private static final String[] COMMANDS = {
            "/땅 구매", "/땅 문서", "/땅 파기", "/크레딧", "/크레딧지급", "/거래", "/거래소", "/거래 등록", "/상점", "/파밍상자", "/파밍이동"
    };

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(Component.text("=== 추가된 명령어 목록 ===", NamedTextColor.GOLD));
        for (String cmd : COMMANDS) {
            sender.sendMessage(Component.text(cmd, NamedTextColor.AQUA));
        }
        sender.sendMessage(Component.text("자세한 사용법: /<명령어> ? (예: /땅구매 ?)", NamedTextColor.GRAY));
        return true;
    }
}
