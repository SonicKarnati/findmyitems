package dev.smpb.findmyitems.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record ContainerObservation(
        SourceKey contentsKey,
        List<SourceKey> accessSources,
        List<SlotSnapshot> slots,
        Instant observedAt) {
    public ContainerObservation {
        Objects.requireNonNull(contentsKey, "contentsKey");
        accessSources = List.copyOf(accessSources);
        slots = List.copyOf(slots);
        Objects.requireNonNull(observedAt, "observedAt");
        // The ender inventory is the exception: it is player data, so it can be read with no block
        // placed and no chunk loaded. Every other container is only ever seen through one.
        if (accessSources.isEmpty() && !contentsKey.equals(SourceKey.enderInventory())) {
            throw new IllegalArgumentException("at least one access source is required");
        }
        var indexes = new HashSet<Integer>();
        for (var slot : slots) {
            if (!indexes.add(slot.slotIndex())) {
                throw new IllegalArgumentException("slot indexes must be unique");
            }
        }
    }
}
