package dev.smpb.findmyitems.search;

import dev.smpb.findmyitems.index.SearchQuery;
import dev.smpb.findmyitems.model.StackSnapshot;

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
    private int lastCandidateCount;
    private int lastFuzzyCandidateCount;

    public SearchIndex(Collection<StackSnapshot> stacks) {
        this(stacks, stacks.stream().map(StackSnapshot::key).collect(java.util.stream.Collectors.toSet()));
    }

    private SearchIndex(Collection<StackSnapshot> stacks, Set<dev.smpb.findmyitems.model.StackKey> rootKeys) {
        this.documents = stacks.stream().filter(stack -> rootKeys.contains(stack.key())).map(SearchDocument::from).toList();
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
            for (var trigram : trigrams(document.searchableText().replaceAll("\\s+", ""))) {
                trigrams.computeIfAbsent(trigram, ignored -> new HashSet<>()).add(document);
            }
        }
    }

    public static SearchIndex rootOnly(Collection<StackSnapshot> candidates,
                                       Set<dev.smpb.findmyitems.model.StackKey> rootKeys) {
        return new SearchIndex(candidates, Set.copyOf(rootKeys));
    }

    public List<SearchDocument> search(SearchQuery query, int limit) {
        if (limit <= 0) return List.of();
        var candidates = candidates(query);
        var fuzzyCandidates = fuzzyCandidates(query);
        lastCandidateCount = candidates.size();
        lastFuzzyCandidateCount = fuzzyCandidates.size();
        var matches = new HashMap<SearchDocument, SearchQuery.Match>();
        for (var document : candidates) {
            if (fuzzyCandidates.contains(document)) continue;
            var match = query.match(document, false);
            if (match != null) matches.put(document, match);
        }
        for (var document : fuzzyCandidates) {
            if (matches.containsKey(document)) continue;
            var match = query.match(document);
            if (match != null) matches.put(document, match);
        }
        return matches.entrySet().stream()
                .map(entry -> new Ranked(entry.getKey(), entry.getValue()))
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
        var exact = exactNames.get(String.join(" ", query.terms()));
        if (exact != null) candidates.addAll(exact);
        for (var term : query.terms()) {
            var tokenMatches = tokens.get(term);
            if (tokenMatches != null) candidates.addAll(tokenMatches);
            var prefix = prefixes.get(term);
            if (prefix != null) candidates.addAll(prefix);
        }
        if (candidates.isEmpty()) candidates.addAll(documents);
        return candidates;
    }

    private Set<SearchDocument> fuzzyCandidates(SearchQuery query) {
        var candidates = new HashSet<SearchDocument>();
        for (var term : query.terms()) {
            for (var trigram : trigrams(term)) {
                var fuzzy = trigrams.get(trigram);
                if (fuzzy != null) candidates.addAll(fuzzy);
            }
        }
        return candidates;
    }

    int lastCandidateCount() {
        return lastCandidateCount;
    }

    int lastFuzzyCandidateCount() {
        return lastFuzzyCandidateCount;
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
