package dev.smpb.findmyitems.search;

import dev.smpb.findmyitems.model.StackKey;
import dev.smpb.findmyitems.model.StackSnapshot;
import dev.smpb.findmyitems.index.SearchQuery;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record SearchDocument(
        StackKey key,
        String displayName,
        String itemIdentifier,
        String itemPath,
        List<String> tooltip,
        String componentFingerprint,
        Set<String> nameTokens,
        Set<String> tokens,
        String searchableText) {
    public SearchDocument {
        tooltip = List.copyOf(tooltip);
        nameTokens = Set.copyOf(nameTokens);
        tokens = Set.copyOf(tokens);
    }

    public static SearchDocument from(StackSnapshot stack) {
        var name = normalize(stack.displayName());
        var identifier = normalize(stack.key().itemId());
        var path = identifier.replace('_', ' ');
        var tooltip = stack.tooltip().stream().map(SearchDocument::normalize).toList();
        var source = String.join("\n", name, identifier, path, String.join("\n", tooltip));
        var arabic = source + "\n" + SearchQuery.arabicLevels(source);
        var allTokens = tokens(arabic);
        return new SearchDocument(stack.key(), name, identifier, path, tooltip, stack.key().componentsJson(),
                Set.copyOf(tokens(name)), allTokens, arabic);
    }

    public static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    static Set<String> tokens(String value) {
        return Arrays.stream(normalize(value).split("[^\\p{L}\\p{N}]+"))
                .filter(token -> !token.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
