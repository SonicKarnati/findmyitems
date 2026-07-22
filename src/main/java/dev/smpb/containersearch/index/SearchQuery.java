package dev.smpb.containersearch.index;

import dev.smpb.containersearch.model.StackSnapshot;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public record SearchQuery(List<String> terms) {
    public SearchQuery {
        terms = List.copyOf(terms);
    }

    public static SearchQuery parse(String input) {
        var normalized = input == null ? "" : input.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return new SearchQuery(List.of());
        }
        return new SearchQuery(List.copyOf(new LinkedHashSet<>(Arrays.asList(normalized.split("\\s+")))));
    }

    public boolean matches(StackSnapshot stack) {
        if (terms.isEmpty()) {
            return true;
        }
        var document = String.join(
                        "\n",
                        stack.displayName(),
                        stack.key().itemId(),
                        String.join("\n", stack.tooltip()))
                .toLowerCase(Locale.ROOT);
        return terms.stream().allMatch(document::contains);
    }
}

