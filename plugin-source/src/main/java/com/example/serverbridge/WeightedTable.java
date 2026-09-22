package com.example.serverbridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Tiny weighted picker - weights are relative (they don't need to sum to 100). */
public final class WeightedTable<T> {

    private final List<T> items = new ArrayList<>();
    private final List<Double> weights = new ArrayList<>();
    private double total;

    public WeightedTable<T> add(double weight, T item) {
        items.add(item);
        weights.add(weight);
        total += weight;
        return this;
    }

    public T roll(Random random) {
        double r = random.nextDouble() * total;
        for (int i = 0; i < items.size(); i++) {
            r -= weights.get(i);
            if (r < 0) {
                return items.get(i);
            }
        }
        return items.get(items.size() - 1);
    }
}
