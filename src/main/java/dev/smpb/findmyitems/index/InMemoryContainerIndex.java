package dev.smpb.findmyitems.index;

import dev.smpb.findmyitems.model.ContainerObservation;
import dev.smpb.findmyitems.model.SourceKey;
import dev.smpb.findmyitems.model.StackKey;
import dev.smpb.findmyitems.model.StackSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class InMemoryContainerIndex implements ContainerIndex {
    private final Map<SourceKey, IndexedContainer> containers = new LinkedHashMap<>();
    private final Map<SourceKey, CachedAggregate> aggregates = new HashMap<>();
    private long revision;

    @Override
    public long revision() {
        return revision;
    }

    @Override
    public void observe(ContainerObservation observation) {
        // A block position belongs to exactly one container. Evict any stale
        // container whose footprint overlaps this one's but has a different key
        // (e.g. a double chest [A,B] whose half was broken, re-observed as [A]).
        var positions = observation.contentsKey().positions();
        if (!positions.isEmpty()) {
            containers.keySet().removeIf(existing ->
                    !existing.equals(observation.contentsKey())
                            && existing.positions().stream().anyMatch(positions::contains));
        }
        var accessSources = new LinkedHashSet<SourceKey>();
        var previous = containers.get(observation.contentsKey());
        if (observation.contentsKey().equals(SourceKey.enderInventory()) && previous != null) {
            accessSources.addAll(previous.accessSources());
        }
        accessSources.addAll(observation.accessSources());
        containers.put(
                observation.contentsKey(),
                new IndexedContainer(
                        observation.contentsKey(),
                        List.copyOf(accessSources),
                        observation.slots(),
                        observation.observedAt()));
        revision++;
    }

    @Override
    public void markMissing(SourceKey source) {
        var direct = containers.remove(source);
        if (direct != null) {
            pruneAggregates();
            revision++;
            return;
        }
        for (var entry : new ArrayList<>(containers.entrySet())) {
            if (!entry.getValue().accessSources().contains(source)) {
                continue;
            }
            var remaining = entry.getValue().accessSources().stream()
                    .filter(candidate -> !candidate.equals(source))
                    .toList();
            if (remaining.isEmpty() && !entry.getKey().equals(SourceKey.enderInventory())) {
                containers.remove(entry.getKey());
            } else {
                var value = entry.getValue();
                containers.put(
                        entry.getKey(),
                        new IndexedContainer(value.contentsKey(), remaining, value.slots(), value.observedAt()));
            }
            pruneAggregates();
            revision++;
            return;
        }
    }

    @Override
    public List<ItemResult> search(String input) {
        var query = SearchQuery.parse(input);
        var aggregated = new LinkedHashMap<StackKey, MutableItem>();
        for (var container : containers.values()) {
            var local = aggregateOf(container);
            for (var entry : local.entrySet()) {
                var stack = entry.getValue().example;
                if (!query.matches(stack)) {
                    continue;
                }
                var item = aggregated.computeIfAbsent(stack.key(), ignored -> new MutableItem(stack));
                item.accept(container, entry.getValue().count, stack);
            }
        }
        return aggregated.values().stream()
                .map(MutableItem::toResult)
                .sorted(Comparator.comparing(ItemResult::displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(result -> result.key().itemId())
                        .thenComparing(result -> result.key().componentsJson()))
                .toList();
    }

    @Override
    public IndexSnapshot snapshot() {
        return new IndexSnapshot(List.copyOf(containers.values()));
    }

    @Override
    public void replace(IndexSnapshot snapshot) {
        containers.clear();
        for (var container : snapshot.containers()) {
            containers.put(container.contentsKey(), container);
        }
        aggregates.clear();
        revision++;
    }

    /**
     * Per-container slot totals, computed once per container revision.
     *
     * <p>Search runs on every keystroke and walks every container; containers change only when one
     * is opened or re-scanned. Records are immutable, so the instance a cached total was computed
     * from doubles as its validity check — no invalidation bookkeeping to get wrong.
     */
    private Map<StackKey, LocalItem> aggregateOf(IndexedContainer container) {
        var cached = aggregates.get(container.contentsKey());
        if (cached != null && cached.source() == container) {
            return cached.items();
        }
        var computed = aggregateContainer(container);
        aggregates.put(container.contentsKey(), new CachedAggregate(container, computed));
        return computed;
    }

    /** Drops cached totals for containers that are no longer indexed. */
    private void pruneAggregates() {
        aggregates.keySet().retainAll(containers.keySet());
    }

    private record CachedAggregate(IndexedContainer source, Map<StackKey, LocalItem> items) {}

    private static Map<StackKey, LocalItem> aggregateContainer(IndexedContainer container) {
        var local = new LinkedHashMap<StackKey, LocalItem>();
        for (var slot : container.slots()) {
            local.compute(
                    slot.stack().key(),
                    (ignored, existing) -> existing == null
                            ? new LocalItem(slot.stack(), slot.stack().count())
                            : new LocalItem(slot.stack(), existing.count + slot.stack().count()));
        }
        return local;
    }

    private record LocalItem(StackSnapshot example, int count) {
    }

    private static final class MutableItem {
        private StackSnapshot example;
        private Instant exampleObservedAt = Instant.MIN;
        private int totalCount;
        private final List<SourceResult> sources = new ArrayList<>();

        private MutableItem(StackSnapshot example) {
            this.example = example;
        }

        private void accept(IndexedContainer container, int count, StackSnapshot candidate) {
            totalCount += count;
            if (container.observedAt().isAfter(exampleObservedAt)) {
                example = candidate;
                exampleObservedAt = container.observedAt();
            }
            // A remembered ender inventory outlives every block that opened it, and markMissing
            // keeps it deliberately. Listing such a container under its own contents key keeps the
            // row's total equal to what the row can name: counted but unlisted stock lands in the
            // headline number and nowhere else, so the total, the container tally and the Take
            // clamp each tell the player a different story.
            List<SourceKey> access = container.accessSources().isEmpty()
                    ? List.of(container.contentsKey())
                    : container.accessSources();
            for (var source : access) {
                sources.add(new SourceResult(source, container.contentsKey(), count, container.observedAt()));
            }
        }

        private ItemResult toResult() {
            return new ItemResult(example.key(), example.displayName(), example.tooltip(), totalCount, sources);
        }
    }
}
