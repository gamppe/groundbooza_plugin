package com.example.magicwar;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Everything config.yml says about how a round runs, read once at enable. */
public class RoundSettings {

    /** One step of the shrinking border: hold for {@code waitSeconds}, then take
     * {@code overSeconds} to close in to {@code toSize} blocks across. */
    public record ShrinkStage(int waitSeconds, double toSize, int overSeconds) {}

    public final String lobbyWorld;
    public final String arenaPrefix;
    public final int countdownSeconds;
    public final int graceSeconds;
    public final int borderHalfWidth;
    public final double damagePerBlock;
    public final double damageBuffer;
    public final int warningDistance;
    public final boolean avoidOcean;
    public final double maxOceanFraction;
    public final int samplesPerSide;
    public final int maxAttempts;
    public final List<ShrinkStage> shrinkStages;

    public RoundSettings(FileConfiguration config) {
        this.lobbyWorld = config.getString("lobby-world", "world");
        this.arenaPrefix = config.getString("arena-prefix", "arena_");
        this.countdownSeconds = config.getInt("countdown-seconds", 10);
        this.graceSeconds = config.getInt("grace-seconds", 300);
        this.borderHalfWidth = config.getInt("border.half-width", 999);
        this.damagePerBlock = config.getDouble("border.damage-per-block", 1.0);
        this.damageBuffer = config.getDouble("border.damage-buffer", 1.0);
        this.warningDistance = config.getInt("border.warning-distance", 30);
        this.avoidOcean = config.getBoolean("terrain.avoid-ocean", true);
        this.maxOceanFraction = config.getDouble("terrain.max-ocean-percent", 20) / 100.0;
        this.samplesPerSide = Math.max(2, config.getInt("terrain.samples-per-side", 5));
        this.maxAttempts = Math.max(1, config.getInt("terrain.max-attempts", 3));
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
            double to;
            int over;
            if (raw instanceof ConfigurationSection section) {
                wait = section.getInt("wait", -1);
                to = section.getDouble("to", -1);
                over = section.getInt("over", -1);
            } else if (raw instanceof Map<?, ?> map) {
                wait = asInt(map.get("wait"), -1);
                to = asDouble(map.get("to"), -1);
                over = asInt(map.get("over"), -1);
            } else {
                continue;
            }
            if (wait < 0 || to <= 0 || over < 0) {
                continue;
            }
            stages.add(new ShrinkStage(wait, to, over));
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
