package dev.smpb.findmyitems.index;

import dev.smpb.findmyitems.model.SlotSnapshot;
import dev.smpb.findmyitems.model.SourceKey;
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

