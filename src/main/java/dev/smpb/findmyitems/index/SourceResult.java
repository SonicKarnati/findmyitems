package dev.smpb.findmyitems.index;

import dev.smpb.findmyitems.model.SourceKey;
import dev.smpb.findmyitems.model.SlotSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * One way in to some of an item.
 *
 * <p>{@code source} is a place to stand; {@code contentsKey} is the container it opens. They are
 * not the same thing and cannot be collapsed: a double chest is one container with two access
 * positions, each holding the whole count, so counting sources would report it as two chests and
 * summing them would double its stock. Group by {@code contentsKey} to count containers; pick
 * between {@code source}s to find the nearest way in.
 */
public record SourceResult(SourceKey source, SourceKey contentsKey, int count, Instant observedAt,
                           List<SlotSnapshot> locations) {
    public SourceResult(SourceKey source, SourceKey contentsKey, int count, Instant observedAt) {
        this(source, contentsKey, count, observedAt, List.of());
    }

    public SourceResult {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(contentsKey, "contentsKey");
        Objects.requireNonNull(observedAt, "observedAt");
        locations = List.copyOf(locations);
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }
}
