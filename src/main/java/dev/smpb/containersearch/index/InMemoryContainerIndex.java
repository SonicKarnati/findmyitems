package dev.smpb.containersearch.index;

import dev.smpb.containersearch.model.ContainerObservation;
import dev.smpb.containersearch.model.SourceKey;
import dev.smpb.containersearch.model.StackKey;
import dev.smpb.containersearch.model.StackSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class InMemoryContainerIndex implements ContainerIndex {
    private final Map<SourceKey, IndexedContainer> containers = new LinkedHashMap<>();
    private long revision;

    @Override
    public long revision() {
        return revision;
    }

    @Override
    public void observe(ContainerObservation observation) {
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
            revision++;
            return;
        }
    }

    @Override
    public List<ItemResult> search(String input) {
        var query = SearchQuery.parse(input);
        var aggregated = new LinkedHashMap<StackKey, MutableItem>();
        for (var container : containers.values()) {
            var local = aggregateContainer(container);
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
        revision++;
    }

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
            for (var source : container.accessSources()) {
                sources.add(new SourceResult(source, count, container.observedAt()));
            }
        }

        private ItemResult toResult() {
            return new ItemResult(example.key(), example.displayName(), example.tooltip(), totalCount, sources);
        }
    }
}
