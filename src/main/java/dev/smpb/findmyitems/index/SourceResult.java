package dev.smpb.findmyitems.index;

import dev.smpb.findmyitems.model.SourceKey;
import java.time.Instant;
import java.util.Objects;

public record SourceResult(SourceKey source, int count, Instant observedAt) {
    public SourceResult {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(observedAt, "observedAt");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }
}

