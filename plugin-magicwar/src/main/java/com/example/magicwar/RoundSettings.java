package com.example.magicwar;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Everything config.yml says about how a round runs, read once at enable. */
public class RoundSettings {

    /** One step of the shrinking border: hold for {@code waitSeconds}, warn the players and
     * give them {@code warnSeconds} to move, then take {@code overSeconds} to close in to
     * {@code toSize} blocks across. */
    public record ShrinkStage(int waitSeconds, int warnSeconds, double toSize, int overSeconds) {}

    public final String lobbyWorld;
    public final String arenaPrefix;
    public final int countdownSeconds;
    public final boolean giveMap;
    public final int graceSeconds;
    public final int borderHalfWidth;
    public final double damagePerBlock;
    public final double damageBuffer;
    public final int warningDistance;
    public final boolean randomCenter;
    public final List<ShrinkStage> shrinkStages;

    public RoundSettings(FileConfiguration config) {
        this.lobbyWorld = config.getString("lobby-world", "world");
        this.arenaPrefix = config.getString("arena-prefix", "arena_");
        this.countdownSeconds = config.getInt("countdown-seconds", 10);
        this.giveMap = config.getBoolean("give-map", true);
        this.graceSeconds = config.getInt("grace-seconds", 300);
        this.borderHalfWidth = config.getInt("border.half-width", 999);
        this.damagePerBlock = config.getDouble("border.damage-per-block", 1.0);
        this.damageBuffer = config.getDouble("border.damage-buffer", 1.0);
        this.warningDistance = config.getInt("border.warning-distance", 30);
        this.randomCenter = config.getBoolean("border.random-center", true);
        this.shrinkStages = readStages(config);
    }

    public double fullSize() {
        return borderHalfWidth * 2.0;
    }

    /** Accepts the inline `{ wait: .., to: .., over: .. }` list; anything malformed is skipped
     * rather than failing the whole plugin, since a broken stage should not stop the event. */
    private static List<ShrinkStage> readStages(FileConfiguration config) {
        List<ShrinkStage> stages = new ArrayList<>();
        for (Object raw : config.getList("shrink-stages", List.of())) {
            int wait;
            int warn;
            double to;
            int over;
            if (raw instanceof ConfigurationSection section) {
                wait = section.getInt("wait", -1);
                warn = section.getInt("warn", 0);
                to = section.getDouble("to", -1);
                over = section.getInt("over", -1);
            } else if (raw instanceof Map<?, ?> map) {
                wait = asInt(map.get("wait"), -1);
                warn = asInt(map.get("warn"), 0);
                to = asDouble(map.get("to"), -1);
                over = asInt(map.get("over"), -1);
            } else {
                continue;
            }
            if (wait < 0 || warn < 0 || to <= 0 || over < 0) {
                continue;
            }
            stages.add(new ShrinkStage(wait, warn, to, over));
        }
        return List.copyOf(stages);
    }

    private static int asInt(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }

    private static double asDouble(Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }
}
