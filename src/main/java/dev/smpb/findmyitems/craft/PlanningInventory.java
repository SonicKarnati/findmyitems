package dev.smpb.findmyitems.craft;

import dev.smpb.findmyitems.model.StackKey;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable stock state. Every planner branch receives a new state before consuming anything. */
public final class PlanningInventory {
    private final Map<StackKey, Long> counts;

    private PlanningInventory(Map<StackKey, Long> counts) {
        this.counts = Map.copyOf(counts);
    }

    public static PlanningInventory empty() {
        return new PlanningInventory(Map.of());
    }

    public static PlanningInventory of(Map<StackKey, ? extends Number> counts) {
        var copy = new LinkedHashMap<StackKey, Long>();
        counts.forEach((key, value) -> {
            long count = value.longValue();
            if (count < 0) throw new IllegalArgumentException("negative stock");
            if (count > 0) copy.put(key, count);
        });
        return new PlanningInventory(copy);
    }

    public long count(StackKey key) {
        return counts.getOrDefault(key, 0L);
    }

    public Map<StackKey, Long> counts() {
        return counts;
    }

    public PlanningInventory consume(StackKey key, long amount) {
        if (amount < 0 || amount > count(key)) throw new IllegalArgumentException("invalid consumption");
        var copy = new LinkedHashMap<>(counts);
        var left = count(key) - amount;
        if (left == 0) copy.remove(key); else copy.put(key, left);
        return new PlanningInventory(copy);
    }

    public PlanningInventory add(StackKey key, long amount) {
        if (amount < 0) throw new IllegalArgumentException("negative addition");
        if (amount == 0) return this;
        var copy = new LinkedHashMap<>(counts);
        copy.put(key, Math.addExact(count(key), amount));
        return new PlanningInventory(copy);
    }
}
