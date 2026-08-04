package dev.smpb.findmyitems.index;

import dev.smpb.findmyitems.search.SearchDocument;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public record SearchQuery(List<String> terms) {
    public SearchQuery {
        terms = List.copyOf(terms);
    }

    public static SearchQuery parse(String input) {
        var normalized = SearchDocument.normalize(input);
        if (normalized.isEmpty()) {
            return new SearchQuery(List.of());
        }
        return new SearchQuery(List.copyOf(new LinkedHashSet<>(List.of(normalized.split("\\s+")))));
    }

    public Match match(SearchDocument document) {
        return match(document, true);
    }

    public Match match(SearchDocument document, boolean allowFuzzy) {
        if (terms.isEmpty()) return new Match(MatchCategory.EXACT_FULL_NAME, 0);
        var exactName = terms.size() == 1 && document.nameTokens().contains(terms.getFirst())
                || document.displayName().equals(String.join(" ", terms));
        if (exactName) {
            return new Match(MatchCategory.EXACT_FULL_NAME, distanceFromName(document));
        }
        if (terms.stream().allMatch(document.tokens()::contains)
                && terms.stream().anyMatch(term -> !document.nameTokens().contains(term))) {
            return new Match(MatchCategory.COMPLETE_WORD, distanceFromName(document));
        }
        if (orderedInName(document)) return new Match(MatchCategory.ORDERED_MULTI_TOKEN, terms.size());
        var namePrefix = terms.stream().allMatch(term ->
                document.nameTokens().stream().anyMatch(token -> token.startsWith(term)));
        var fuzzyScore = allowFuzzy ? fuzzyScore(document) : -1;
        var typoPrefix = allowFuzzy && namePrefix && isTypoPrefix(fuzzyScore);
        if (namePrefix && !typoPrefix) {
            return new Match(MatchCategory.WORD_PREFIX, terms.stream().mapToInt(String::length).sum());
        }
        if (!typoPrefix && terms.stream().allMatch(document.searchableText()::contains)) {
            return new Match(MatchCategory.SUBSTRING, document.searchableText().length());
        }
        if (allowFuzzy) {
            if (fuzzyScore >= 0) return new Match(MatchCategory.FUZZY, fuzzyScore);
        }
        return null;
    }

    private boolean isTypoPrefix(int fuzzyScore) {
        return fuzzyScore > 0 && terms.stream().anyMatch(term -> term.length() >= 4);
    }

    public enum MatchCategory { EXACT_FULL_NAME, COMPLETE_WORD, ORDERED_MULTI_TOKEN, WORD_PREFIX, SUBSTRING, FUZZY }

    public record Match(MatchCategory category, int score) {
        public Match { Objects.requireNonNull(category, "category"); }
    }

    private int distanceFromName(SearchDocument document) {
        return Math.abs(document.displayName().length() - String.join(" ", terms).length());
    }

    private boolean orderedInName(SearchDocument document) {
        var name = document.displayName().split("[^\\p{L}\\p{N}]+");
        var at = 0;
        for (var term : terms) {
            while (at < name.length && !name[at].equals(term)) at++;
            if (at == name.length) return false;
            at++;
        }
        return true;
    }

    private int fuzzyScore(SearchDocument document) {
        var total = 0;
        for (var term : terms) {
            var best = Integer.MAX_VALUE;
            for (var token : document.tokens()) best = Math.min(best, editDistance(term, token));
            var bound = Math.max(1, term.length() / 4);
            if (best > bound) return -1;
            total += best;
        }
        return total;
    }

    private static int editDistance(String left, String right) {
        var row = new int[right.length() + 1];
        for (var i = 0; i <= right.length(); i++) row[i] = i;
        for (var i = 1; i <= left.length(); i++) {
            var previous = row[0];
            row[0] = i;
            for (var j = 1; j <= right.length(); j++) {
                var current = row[j];
                row[j] = Math.min(Math.min(row[j] + 1, row[j - 1] + 1),
                        previous + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1));
                previous = current;
            }
        }
        return row[right.length()];
    }

    private static final String[] ROMAN = {"i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x"};

    /** Rewrites standalone roman numerals I-X as digits; everything else is left alone. */
    public static String arabicLevels(String document) {
        var out = new StringBuilder(document.length());
        for (var word : document.split("(?=\\s)|(?<=\\s)")) {
            var index = indexOfRoman(word.strip());
            out.append(index < 0 ? word : word.replace(word.strip(), String.valueOf(index + 1)));
        }
        return out.toString();
    }

    private static int indexOfRoman(String word) {
        for (int i = 0; i < ROMAN.length; i++) {
            if (ROMAN[i].equals(word)) return i;
        }
        return -1;
    }
}
