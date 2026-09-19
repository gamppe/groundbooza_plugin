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

/**
 * Per-player sidebar (rebuilt once a second) plus the "(칭호) 직업" tag under everyone's nametag.
 * Both live on the same per-viewer scoreboard, so the below-name objective has to carry a score
 * for every online player each rebuild - that's what lets the viewer see other people's tags.
 */
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
                    // One player's bad state must not blank everyone else's sidebar too.
                    try {
                        update(player);
                        plugin.getFlightListener().syncNonMoveCase(player);
                    } catch (RuntimeException e) {
                        plugin.getLogger().warning("Scoreboard update failed for " + player.getName() + ": " + e);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // once per second
    }

    private void update(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        buildSidebar(board, player);
        buildJobTags(board);
        player.setScoreboard(board);
    }

    private void buildSidebar(Scoreboard board, Player player) {
        Objective objective = board.registerNewObjective("maincore", "dummy",
                Component.text("땅 부자 타이쿤", NamedTextColor.GOLD));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        MainDatabase.Land here = plugin.getLandManager().getLandAt(player.getLocation());
        Component locationLine = Component.text("현재 위치 : ", NamedTextColor.WHITE).append(describeLand(player, here));

        Component infoHeader = Component.text(player.getName(), NamedTextColor.AQUA)
                .append(Component.text("님의 정보", NamedTextColor.WHITE));
        Component jobLine = Component.text("직업 : ", NamedTextColor.WHITE)
                .append(Component.text(plugin.getJobManager().displayLabel(player.getUniqueId()), NamedTextColor.YELLOW));

        long credits = plugin.getEconomyCache().get(player.getUniqueId());
        int deeds = plugin.getLandManager().countOwned(player.getUniqueId());
        Component creditLine = Component.text("보유한 크레딧 : ", NamedTextColor.WHITE)
                .append(Component.text(String.format("%,d", credits), NamedTextColor.YELLOW));
        Component deedLine = Component.text("보유한 땅문서 : ", NamedTextColor.WHITE)
                .append(Component.text(deeds, NamedTextColor.YELLOW));

        setLine(objective, 5, "location", locationLine);
        setLine(objective, 4, "----------------", Component.text("----------------", NamedTextColor.GRAY));
        setLine(objective, 3, "header", infoHeader);
        setLine(objective, 2, "job", jobLine);
        setLine(objective, 1, "credit", creditLine);
        setLine(objective, 0, "deed", deedLine);
    }

    private Component describeLand(Player player, MainDatabase.Land here) {
        if (here == null) {
            return Component.text("공유지", NamedTextColor.GREEN);
        }
        if (here.reservation()) {
            // Owner is the RESERVATION_OWNER sentinel (no real player behind it) - show who
            // holds the claim instead.
            String holderName = here.holder() == null ? null : nameOf(player, here.holder());
            return Component.text(here.name(), NamedTextColor.YELLOW)
                    .append(Component.text(" (선점: ", NamedTextColor.WHITE))
                    .append(Component.text(holderName == null ? "알 수 없음" : holderName, NamedTextColor.AQUA))
                    .append(Component.text(")", NamedTextColor.WHITE));
        }
        if (here.owner().equals(MainDatabase.MARKET_OWNER)) {
            return Component.text(here.name(), NamedTextColor.YELLOW)
                    .append(Component.text(" (거래소 매물)", NamedTextColor.WHITE));
        }
        String ownerName = nameOf(player, here.owner());
        return Component.text(ownerName == null ? "알 수 없음" : ownerName, NamedTextColor.AQUA)
                .append(Component.text("님의 땅", NamedTextColor.WHITE));
    }

    /** "(칭호) 직업" under every player's nametag. The number slot itself is repurposed: each
     * player's score gets a fixed-text number format holding their tag, and the objective's own
     * display name is left blank so nothing trails after it. */
    private void buildJobTags(Scoreboard board) {
        Objective tags = board.registerNewObjective("maincore_job", "dummy", Component.empty());
        tags.setDisplaySlot(DisplaySlot.BELOW_NAME);
        for (Player other : Bukkit.getOnlinePlayers()) {
            Score score = tags.getScore(other.getName());
            score.numberFormat(NumberFormat.fixed(
                    Component.text(plugin.getJobManager().displayLabel(other.getUniqueId()), NamedTextColor.YELLOW)));
            score.setScore(0);
        }
    }

    /** Null if the server has never seen that UUID (getOfflinePlayer().getName() is null then). */
    private String nameOf(Player viewer, java.util.UUID uuid) {
        if (uuid.equals(viewer.getUniqueId())) {
            return viewer.getName();
        }
        return Bukkit.getOfflinePlayer(uuid).getName();
    }

    private void setLine(Objective objective, int score, String entryKey, Component display) {
        Score s = objective.getScore(entryKey);
        s.customName(display);
        s.numberFormat(NumberFormat.blank());
        s.setScore(score);
    }
}
