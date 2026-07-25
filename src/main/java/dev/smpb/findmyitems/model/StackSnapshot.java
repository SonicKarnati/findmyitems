package dev.smpb.findmyitems.model;

import java.util.List;
import java.util.Objects;

public record StackSnapshot(
        StackKey key,
        int count,
        String displayName,
        List<String> tooltip) {
    public StackSnapshot {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(displayName, "displayName");
        tooltip = List.copyOf(tooltip);
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }
}

