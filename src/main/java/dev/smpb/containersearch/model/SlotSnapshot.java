package dev.smpb.containersearch.model;

import java.util.Objects;

public record SlotSnapshot(int slotIndex, StackSnapshot stack) {
    public SlotSnapshot {
        Objects.requireNonNull(stack, "stack");
        if (slotIndex < 0) {
            throw new IllegalArgumentException("slotIndex must not be negative");
        }
    }
}

