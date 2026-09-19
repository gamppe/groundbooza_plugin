package com.example.maincore;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

public class ScoreboardManager {

    private final MainCorePlugin plugin;

    public ScoreboardManager(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    update(player);
                    plugin.getFlightListener().syncNonMoveCase(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // once per second
    }

    private void update(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective("maincore", "dummy",
                Component.text(player.getName(), NamedTextColor.AQUA).append(Component.text("의 정보", NamedTextColor.WHITE)));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        MainDatabase.Land here = plugin.getLandManager().getLandAt(player.getLocation());
        Component landLine;
        if (here == null) {
            landLine = Component.text("공유지", NamedTextColor.WHITE);
        } else {
            String ownerName = here.owner().equals(player.getUniqueId())
                    ? player.getName()
                    : Bukkit.getOfflinePlayer(here.owner()).getName();
            landLine = Component.text(ownerName, NamedTextColor.AQUA)
                    .append(Component.text("님의 ", NamedTextColor.WHITE))
                    .append(Component.text(here.name(), NamedTextColor.YELLOW));
        }

        long credits = plugin.getEconomyCache().get(player.getUniqueId());
        int deeds = plugin.getLandManager().countOwned(player.getUniqueId());

        Component creditLine = Component.text("보유한 크레딧 : ", NamedTextColor.WHITE)
                .append(Component.text(String.format("%,d", credits), NamedTextColor.YELLOW));
        Component deedLine = Component.text("보유한 땅문서 : ", NamedTextColor.WHITE)
                .append(Component.text(deeds, NamedTextColor.YELLOW));

        setLine(objective, 3, "----------------", Component.text("----------------", NamedTextColor.GRAY));
        setLine(objective, 2, "land", landLine);
        setLine(objective, 1, "credit", creditLine);
        setLine(objective, 0, "deed", deedLine);

        player.setScoreboard(board);
    }

    private void setLine(Objective objective, int score, String entryKey, Component display) {
        Score s = objective.getScore(entryKey);
        s.customName(display);
        s.numberFormat(NumberFormat.blank());
        s.setScore(score);
    }
}
