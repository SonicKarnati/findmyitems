package dev.smpb.findmyitems.model;

import java.util.List;
import java.util.Objects;

public record StackSnapshot(
        StackKey key,
        int count,
        String displayName,
        List<String> tooltip,
        Provenance provenance) {
    public StackSnapshot(StackKey key, int count, String displayName, List<String> tooltip) {
        this(key, count, displayName, tooltip, Provenance.empty());
    }

    public StackSnapshot {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(displayName, "displayName");
        tooltip = List.copyOf(tooltip);
        Objects.requireNonNull(provenance, "provenance");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }

    public record Provenance(List<Integer> slots, int holderSlot) {
        public Provenance {
            slots = List.copyOf(slots);
            if (holderSlot < -1) {
                throw new IllegalArgumentException("holderSlot must not be less than -1");
            }
            if (holderSlot >= 0 && !slots.contains(holderSlot)) {
                throw new IllegalArgumentException("holderSlot must be in slots");
            }
        }

        public static Provenance empty() {
            return new Provenance(List.of(), -1);
        }
    }
}
