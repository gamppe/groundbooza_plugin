package com.example.magicwar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * The one door into {@link QuestManager}. Everything that could ever be a quest objective -
 * listeners for the vanilla side, {@link SkillEffects} for the spells - reports it here as a
 * goal name and a value, and this decides whether anyone cares.
 *
 * <p>Nothing counts outside a live round in the arena, so the lobby stays a lobby.
 */
public class QuestTracker {

    private final ArenaManager arena;
    private final ClassManager classes;
    private final QuestManager quests;

    public QuestTracker(ArenaManager arena, ClassManager classes, QuestManager quests) {
        this.arena = arena;
        this.classes = classes;
        this.quests = quests;
    }

    public boolean counts(Player player) {
        return player != null && arena.isRunning() && !arena.isLobby(player.getWorld());
    }

    public List<Quest> board(Player player) {
        return QuestManager.boardFor(classes.classOf(player.getUniqueId()),
                classes.advancementOf(player.getUniqueId()));
    }

    public void fire(Player player, String goal) {
        fire(player, goal, null, 1);
    }

    public void fire(Player player, String goal, String name) {
        fire(player, goal, name, 1);
    }

    public void fire(Player player, String goal, String name, int value) {
        if (!counts(player)) {
            return;
        }
        quests.fire(player.getUniqueId(), classes.classOf(player.getUniqueId()),
                classes.advancementOf(player.getUniqueId()), goal, name, value,
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
