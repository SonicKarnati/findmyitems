package dev.smpb.containersearch.store;

import dev.smpb.containersearch.index.IndexSnapshot;
import java.io.IOException;
import java.util.List;

public interface WorldStore {
    StoreResult load(WorldKey key);

    void save(WorldKey key, IndexSnapshot snapshot, List<SavedCraftRequest> requests) throws IOException;
}

