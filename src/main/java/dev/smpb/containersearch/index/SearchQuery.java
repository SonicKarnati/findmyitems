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
        // Tooltips spell enchantment levels in roman numerals ("Smite IV"), but people type "smite 4".
        // Appending an arabic rewrite lets either spelling hit without touching what is stored.
        document = document + "\n" + arabicLevels(document);
        return terms.stream().allMatch(document::contains);
    }

    private static final String[] ROMAN = {"i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x"};

    /** Rewrites standalone roman numerals I-X as digits; everything else is left alone. */
    static String arabicLevels(String document) {
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

