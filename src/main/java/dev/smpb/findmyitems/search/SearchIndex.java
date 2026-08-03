package dev.smpb.findmyitems.search;

import dev.smpb.findmyitems.index.SearchQuery;
import dev.smpb.findmyitems.model.StackSnapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SearchIndex {
    private final List<SearchDocument> documents;
    private final Map<String, Set<SearchDocument>> exactNames = new HashMap<>();
    private final Map<String, Set<SearchDocument>> tokens = new HashMap<>();
    private final Map<String, Set<SearchDocument>> prefixes = new HashMap<>();
    private final Map<String, Set<SearchDocument>> trigrams = new HashMap<>();

    public SearchIndex(Collection<StackSnapshot> stacks) {
        this.documents = stacks.stream().map(SearchDocument::from).toList();
        for (var document : documents) {
            exactNames.computeIfAbsent(document.displayName(), ignored -> new HashSet<>()).add(document);
            for (var token : document.tokens()) {
                tokens.computeIfAbsent(token, ignored -> new HashSet<>()).add(document);
                for (var prefix : prefixes(token)) {
                    prefixes.computeIfAbsent(prefix, ignored -> new HashSet<>()).add(document);
                }
                for (var trigram : trigrams(token)) {
                    trigrams.computeIfAbsent(trigram, ignored -> new HashSet<>()).add(document);
                }
            }
        }
    }

    public List<SearchDocument> search(SearchQuery query, int limit) {
        if (limit <= 0) return List.of();
        var candidates = candidates(query);
        return candidates.stream()
                .map(document -> new Ranked(document, query.match(document)))
                .filter(ranked -> ranked.match() != null)
                .sorted(Comparator.comparingInt((Ranked ranked) -> ranked.match().category().ordinal())
                        .thenComparingInt(ranked -> ranked.match().score())
                        .thenComparing(ranked -> ranked.document().displayName())
                        .thenComparing(ranked -> ranked.document().itemIdentifier())
                        .thenComparing(ranked -> ranked.document().componentFingerprint()))
                .limit(limit)
                .map(Ranked::document)
                .toList();
    }

    private Set<SearchDocument> candidates(SearchQuery query) {
        if (query.terms().isEmpty()) return new HashSet<>(documents);
        var candidates = new HashSet<SearchDocument>();
        for (var term : query.terms()) {
            var exact = tokens.get(term);
            if (exact != null) candidates.addAll(exact);
            var prefix = prefixes.get(term);
            if (prefix != null) candidates.addAll(prefix);
            for (var trigram : trigrams(term)) {
                var fuzzy = trigrams.get(trigram);
                if (fuzzy != null) candidates.addAll(fuzzy);
            }
        }
        if (candidates.isEmpty()) candidates.addAll(documents);
        return candidates;
    }

    private static Set<String> prefixes(String token) {
        var result = new HashSet<String>();
        for (var i = 1; i <= token.length(); i++) result.add(token.substring(0, i));
        return result;
    }

    private static Set<String> trigrams(String token) {
        var result = new HashSet<String>();
        if (token.length() < 3) {
            result.add(token);
            return result;
        }
        for (var i = 0; i <= token.length() - 3; i++) result.add(token.substring(i, i + 3));
        return result;
    }

    private record Ranked(SearchDocument document, SearchQuery.Match match) {}
}
