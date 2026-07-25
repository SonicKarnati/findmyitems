package dev.smpb.findmyitems.store;

import dev.smpb.findmyitems.index.IndexSnapshot;
import java.util.List;

public record StoreResult(
        IndexSnapshot snapshot,
        List<SavedCraftRequest> requests,
        LoadStatus status,
        String message) {
    public StoreResult {
        requests = List.copyOf(requests);
    }

    public static StoreResult empty(LoadStatus status, String message) {
        return new StoreResult(IndexSnapshot.EMPTY, List.of(), status, message);
    }
}

