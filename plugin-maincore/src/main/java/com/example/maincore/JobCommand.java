package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/** /직업 - select screen when jobless, profile otherwise. OPs also get /직업 초기화 <닉네임>. */
public class JobCommand implements CommandExecutor, TabCompleter {

    private final MainCorePlugin plugin;

    public JobCommand(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equals("초기화")) {
            return handleReset(sender, args);
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }
        if (args.length >= 1 && args[0].equals("?")) {
            player.sendMessage(Component.text("/직업 - 직업이 없으면 선택 화면, 있으면 내 직업 프로필을 엽니다.", NamedTextColor.GRAY));
            player.sendMessage(Component.text("프로필에서 도구 업그레이드, 직업 전용 기능, 직업 변경(수수료)을 할 수 있습니다.", NamedTextColor.GRAY));
            return true;
        }
        plugin.getJobController().open(player);
        return true;
    }

    private boolean handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("maincore.jobreset")) {
            sender.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("사용법: /직업 초기화 <닉네임>", NamedTextColor.RED));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(Component.text("존재하지 않는 플레이어입니다: " + args[1], NamedTextColor.RED));
            return true;
        }
        plugin.getJobManager().resetAsync(target.getUniqueId(), () -> {
            sender.sendMessage(Component.text(args[1] + "의 직업을 초기화했습니다.", NamedTextColor.GREEN));
            Player online = target.getPlayer();
            if (online != null) {
                online.sendMessage(Component.text("관리자가 직업을 초기화했습니다. /직업 으로 다시 선택하세요.", NamedTextColor.YELLOW));
            }
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && sender.hasPermission("maincore.jobreset")) {
            return "초기화".startsWith(args[0]) ? List.of("초기화") : List.of();
        }
        if (args.length == 2 && args[0].equals("초기화")) {
            String prefix = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix)).toList();
        }
        return List.of();
    }
}
