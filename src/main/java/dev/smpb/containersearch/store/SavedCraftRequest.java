package dev.smpb.containersearch.store;

import dev.smpb.containersearch.model.StackKey;
import java.util.Objects;

public record SavedCraftRequest(StackKey output, int count) {
    public SavedCraftRequest {
        Objects.requireNonNull(output, "output");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }
}

