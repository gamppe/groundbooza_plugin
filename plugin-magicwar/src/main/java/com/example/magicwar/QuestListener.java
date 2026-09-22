package com.example.magicwar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/** Feeds kills and mined blocks into QuestManager. Only counts while a round is actually being
 * fought in the arena, so nothing accrues in the lobby. */
public class QuestListener implements Listener {

    private final ArenaManager arena;
    private final ClassManager classes;
    private final QuestManager quests;

    public QuestListener(ArenaManager arena, ClassManager classes, QuestManager quests) {
        this.arena = arena;
        this.classes = classes;
        this.quests = quests;
    }

    private boolean counts(Player player) {
        return arena.isRunning() && !arena.isLobby(player.getWorld());
    }

    @EventHandler(ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !counts(killer)) {
            return;
        }
        quests.onKill(killer.getUniqueId(), classes.classOf(killer.getUniqueId()), event.getEntity().getType(),
                (quest, finished) -> report(killer, quest, finished));
    }

    @EventHandler(ignoreCancelled = true)
    public void onMine(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!counts(player)) {
            return;
        }
        quests.onMine(player.getUniqueId(), classes.classOf(player.getUniqueId()), event.getBlock().getType(),
                (quest, finished) -> report(player, quest, finished));
    }

    private void report(Player player, Quest quest, boolean finished) {
        if (finished) {
            player.sendMessage(Component.text("퀘스트 완료: " + quest.display()
                    + " — /클래스 에서 보상을 받으세요.", NamedTextColor.GREEN));
            player.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.4f);
            return;
        }
        player.sendActionBar(Component.text(quest.display() + " ("
                + quests.count(player.getUniqueId(), quest) + "/" + quest.target() + ")", quest.color()));
    }
}
