package dev.smpb.containersearch.index;

import dev.smpb.containersearch.model.SlotSnapshot;
import dev.smpb.containersearch.model.SourceKey;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record IndexedContainer(
        SourceKey contentsKey,
        List<SourceKey> accessSources,
        List<SlotSnapshot> slots,
        Instant observedAt) {
    public IndexedContainer {
        Objects.requireNonNull(contentsKey, "contentsKey");
        accessSources = List.copyOf(accessSources);
        slots = List.copyOf(slots);
        Objects.requireNonNull(observedAt, "observedAt");
    }
}

