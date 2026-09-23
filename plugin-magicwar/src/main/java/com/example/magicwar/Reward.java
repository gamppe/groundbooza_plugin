package com.example.magicwar;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What finishing a quest - or taking a 전직 - hands over.
 *
 * <p>Every reward is the same shape: a bag of named amounts that {@link Perks} adds up. A few
 * keys are wired to vanilla stats (see the constants on {@link Perks}); the rest are switches
 * the skills read for themselves, which is what lets a reward like "볼트마법이 3갈래로 발사됨"
 * stay a data entry here instead of becoming a branch in the reward code.
 *
 * <p>{@code text} is only what the board prints. A reward with nothing behind it - a 전직
 * unlock, which is decided by the quest being complete rather than by any grant - is text only.
 */
public record Reward(String text, Map<String, Double> grants) {

    public static Reward of(String text) {
        return new Reward(text, Map.of());
    }

    public static Reward of(String text, String key, double amount) {
        return new Reward(text, Map.of(key, amount));
    }

    public static Reward of(String text, String key, double amount, String key2, double amount2) {
        Map<String, Double> grants = new LinkedHashMap<>();
        grants.put(key, amount);
        grants.put(key2, amount2);
        return new Reward(text, Map.copyOf(grants));
    }
}
