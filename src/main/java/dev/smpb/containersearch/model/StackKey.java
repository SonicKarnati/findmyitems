package dev.smpb.containersearch.model;

import java.util.Objects;

public record StackKey(String itemId, String componentsJson) {
    public StackKey {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(componentsJson, "componentsJson");
        if (itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must not be blank");
        }
    }
}

