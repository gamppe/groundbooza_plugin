package com.example.maincore;

import org.bukkit.Material;

import java.util.List;
import java.util.function.IntFunction;

/** One of a job's three upgrade lines (see Job.tracks()). Each has MAX_LEVEL steps that all
 * cost the same ladder of credits; `effectAt` renders what a given level actually does. */
public final class UpgradeTrack {

    public static final int MAX_LEVEL = 5;
    /** Price of going from level N to N+1 (index by current level). */
    public static final long[] PRICES = {50_000L, 100_000L, 150_000L, 200_000L, 250_000L};

    private final String label;
    private final Material icon;
    private final List<String> description;
    private final IntFunction<String> effectAt;
    private final boolean available;

    private UpgradeTrack(String label, Material icon, List<String> description, IntFunction<String> effectAt, boolean available) {
        this.label = label;
        this.icon = icon;
        this.description = description;
        this.effectAt = effectAt;
        this.available = available;
    }

    public static UpgradeTrack of(String label, Material icon, List<String> description, IntFunction<String> effectAt) {
        return new UpgradeTrack(label, icon, description, effectAt, true);
    }

    /** Placeholder for jobs whose extra tracks haven't been designed yet - shown but not buyable. */
    public static UpgradeTrack unavailable() {
        return new UpgradeTrack("준비 중", Material.BARRIER, List.of("아직 준비 중인 업그레이드입니다"), level -> "-", false);
    }

    public static long price(int currentLevel) {
        if (currentLevel < 0 || currentLevel >= PRICES.length) {
            return -1;
        }
        return PRICES[currentLevel];
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
