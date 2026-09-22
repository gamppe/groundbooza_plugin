package com.example.magicwar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /클래스 opens the same screen the guide book does. The two subcommands are testing tools for
 * whoever is running the event - they are permission-gated rather than hidden, so an operator
 * can rewind a class pick or skip a grind without restarting the round.
 */
public class ClassCommand implements CommandExecutor, TabCompleter {

    public static final String ADMIN_PERMISSION = "magicwar.admin";
    private static final List<String> SUBCOMMANDS = List.of("초기화", "퀘완");

    private final ClassController controller;
    private final ClassManager classes;
    private final QuestManager quests;
    private final SkillCooldowns cooldowns;

    public ClassCommand(ClassController controller, ClassManager classes, QuestManager quests,
                        SkillCooldowns cooldowns) {
        this.controller = controller;
        this.classes = classes;
        this.quests = quests;
        this.cooldowns = cooldowns;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }
        if (args.length == 0) {
            controller.open(player);
            return true;
        }
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED));
            return true;
        }
        switch (args[0]) {
            case "초기화" -> reset(player);
            case "퀘완" -> completeQuests(player);
            default -> player.sendMessage(Component.text("사용법: /클래스 [초기화|퀘완]", NamedTextColor.RED));
        }
        return true;
    }

    /** Back to having picked nothing: the class, its quests and any charges all go. Skill items
     * already handed out stay in the inventory but refuse to cast, since casting checks the
     * skill is still owned. */
    private void reset(Player player) {
        classes.reset(player.getUniqueId());
        quests.reset(player.getUniqueId());
        cooldowns.reset(player.getUniqueId());
        player.sendMessage(Component.text("클래스와 퀘스트 진행도를 초기화했습니다.", NamedTextColor.YELLOW));
        controller.openSelect(player);
    }

    private void completeQuests(Player player) {
        MagicClass magicClass = classes.classOf(player.getUniqueId());
        if (magicClass == null) {
            player.sendMessage(Component.text("먼저 클래스를 선택하세요.", NamedTextColor.RED));
            return;
        }
        QuestManager.boardFor(magicClass).forEach(quest -> quests.complete(player.getUniqueId(), quest));
        player.sendMessage(Component.text("모든 퀘스트를 완료 처리했습니다.", NamedTextColor.YELLOW));
        controller.openBoard(player);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !sender.hasPermission(ADMIN_PERMISSION)) {
            return List.of();
        }
        return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0])).toList();
    }
}
