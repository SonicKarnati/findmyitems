package dev.smpb.findmyitems.index;

import dev.smpb.findmyitems.model.StackKey;
import java.util.List;
import java.util.Objects;

public record ItemResult(
        StackKey key,
        String displayName,
        List<String> tooltip,
        int totalCount,
        List<SourceResult> sources) {
    public ItemResult {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(displayName, "displayName");
        tooltip = List.copyOf(tooltip);
        sources = List.copyOf(sources);
        if (totalCount <= 0) {
            throw new IllegalArgumentException("totalCount must be positive");
        }
    }

    public StackKey identity() {
        return key;
    }

    public String stableIdentity() {
        return key.itemId() + "\u0000" + key.componentsJson();
    }
}
