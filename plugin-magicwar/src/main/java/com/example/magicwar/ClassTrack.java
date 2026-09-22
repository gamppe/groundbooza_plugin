package com.example.magicwar;

import org.bukkit.Material;

import java.util.List;
import java.util.function.IntFunction;

/** One upgrade line of a class, laid out like MainCore's UpgradeTrack so the screen reads the
 * same as /직업 does on the main server. */
public final class ClassTrack {

    public static final int MAX_LEVEL = 5;

    private final String label;
    private final Material icon;
    private final List<String> description;
    private final IntFunction<String> effectAt;
    private final boolean available;

    private ClassTrack(String label, Material icon, List<String> description,
                       IntFunction<String> effectAt, boolean available) {
        this.label = label;
        this.icon = icon;
        this.description = description;
        this.effectAt = effectAt;
        this.available = available;
    }

    public static ClassTrack of(String label, Material icon, List<String> description, IntFunction<String> effectAt) {
        return new ClassTrack(label, icon, description, effectAt, true);
    }

    /** Placeholder while the actual upgrades are still being decided: shown in the layout,
     * not buyable. */
    public static ClassTrack pending(String label) {
        return new ClassTrack(label, Material.BARRIER, List.of("아직 준비 중인 강화입니다"), level -> "-", false);
    }

    public String label() {
        return label;
    }

    public Material icon() {
        return icon;
    }

    public List<String> description() {
        return description;
    }

    public String effectAt(int level) {
        return effectAt.apply(Math.max(0, Math.min(MAX_LEVEL, level)));
    }

    public boolean available() {
        return available;
    }
}
