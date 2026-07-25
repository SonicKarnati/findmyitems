package dev.smpb.findmyitems.store;

import dev.smpb.findmyitems.index.IndexSnapshot;
import java.io.IOException;
import java.util.List;

public interface WorldStore {
    StoreResult load(WorldKey key);

    void save(WorldKey key, IndexSnapshot snapshot, List<SavedCraftRequest> requests) throws IOException;
}

