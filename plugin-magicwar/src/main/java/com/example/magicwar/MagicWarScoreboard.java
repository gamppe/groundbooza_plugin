package com.example.magicwar;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.List;

/**
 * The sidebar: how many are left, what you are, and the quest board with finished lines struck
 * through. Rebuilt from scratch each tick it runs, which is cheap at event sizes and avoids
 * having to diff lines.
 *
 * <p>Sidebar entries have to be unique strings, so the visible text goes in each score's
 * customName and the entry itself is just an invisible index; the numbers are hidden with a
 * blank NumberFormat.
 */
public class MagicWarScoreboard {

    private final ArenaManager arena;
    private final ClassManager classes;
    private final QuestManager quests;

    public MagicWarScoreboard(ArenaManager arena, ClassManager classes, QuestManager quests) {
        this.arena = arena;
        this.classes = classes;
        this.quests = quests;
    }

    /** Called on a timer from the plugin. */
    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (arena.isRunning() && !arena.isLobby(player.getWorld())) {
                render(player);
            } else if (player.getScoreboard() != Bukkit.getScoreboardManager().getMainScoreboard()) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
    }

    private void render(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective("magicwar", Criteria.DUMMY,
                Component.text("마법전쟁서버", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());

        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("현재 남은 플레이어 수 : ", NamedTextColor.GRAY)
                .append(Component.text(arena.alivePlayers(), NamedTextColor.WHITE)));
        MagicClass magicClass = classes.classOf(player.getUniqueId());
        lines.add(Component.text("나의 클래스 : ", NamedTextColor.GRAY)
                .append(Component.text(classes.displayName(player.getUniqueId()) == null
                        ? "없음" : classes.displayName(player.getUniqueId()),
                        magicClass == null ? NamedTextColor.DARK_GRAY : NamedTextColor.AQUA)));
        lines.add(Component.text("-------------------------", NamedTextColor.DARK_GRAY));

        for (Quest quest : QuestManager.boardFor(magicClass, classes.advancementOf(player.getUniqueId()))) {
            int count = quests.count(player.getUniqueId(), quest);
            boolean done = quests.isComplete(player.getUniqueId(), quest);
            String progress = quest.isPlaceholder() ? "" : " (" + count + "/" + quest.target() + ")";
            Component line = Component.text("[" + quest.tag() + "]", quest.color())
                    .append(Component.text(" " + quest.title() + progress,
                            done || quest.isPlaceholder() ? NamedTextColor.DARK_GRAY : NamedTextColor.WHITE));
            lines.add(done ? line.decoration(TextDecoration.STRIKETHROUGH, true) : line);
        }

        // Sidebar draws highest score at the top, so count down as we go.
        int score = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            String entry = invisibleEntry(i);
            org.bukkit.scoreboard.Score row = objective.getScore(entry);
            row.customName(lines.get(i));
            row.setScore(score--);
        }
        player.setScoreboard(board);
    }

    /** A unique, invisible entry per row - the text lives in customName. */
    private static String invisibleEntry(int index) {
        return "§" + "0123456789abcdef".charAt(index % 16) + "§r";
    }
}
