package dev.smpb.findmyitems.index;

import java.util.List;

public record IndexSnapshot(List<IndexedContainer> containers) {
    public static final IndexSnapshot EMPTY = new IndexSnapshot(List.of());

    public IndexSnapshot {
        containers = List.copyOf(containers);
    }
}

