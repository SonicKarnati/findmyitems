package dev.smpb.findmyitems.search;

import static org.junit.jupiter.api.Assertions.*;

import dev.smpb.findmyitems.index.SearchQuery;
import dev.smpb.findmyitems.model.StackKey;
import dev.smpb.findmyitems.model.StackSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

final class SearchQueryTest {
    @Test
    void normalizesRepeatedWhitespaceAndDuplicateTerms() {
        assertEquals(List.of("white", "bed"), SearchQuery.parse("  WHITE   BED  ").terms());
        assertEquals(List.of("white", "bed"), SearchQuery.parse("white bed white").terms());
    }

    @Test
    void matchesCompleteNameTermsAsExactName() {
        assertEquals(SearchQuery.MatchCategory.EXACT_FULL_NAME,
                match("White Bed", "minecraft:white_bed", "bed").category());
        assertEquals(SearchQuery.MatchCategory.EXACT_FULL_NAME,
                match("White Bed", "minecraft:white_bed", "white bed").category());
    }

    @Test
    void fuzzyMatchAllowsAUsedWordTypo() {
        assertEquals(SearchQuery.MatchCategory.FUZZY,
                match("White Bed", "minecraft:white_bed", "whit bed").category());
    }

    @Test
    void substringMatchesLongerWords() {
        assertNotNull(SearchQuery.parse("bed").match(SearchDocument.from(
                stack("minecraft:bedrock", "Bedrock"))));
    }

    @Test
    void romanEnchantmentLevelsHaveArabicAliases() {
        assertNotNull(SearchQuery.parse("sharpness 5").match(SearchDocument.from(
                new StackSnapshot(new StackKey("minecraft:diamond_sword", "{sharpness}"), 1,
                        "Diamond Sword", List.of("Sharpness V")))));
    }

    private static SearchQuery.Match match(String name, String id, String query) {
        var match = SearchQuery.parse(query).match(SearchDocument.from(stack(id, name)));
        assertNotNull(match);
        return match;
    }

    private static StackSnapshot stack(String id, String name) {
        return new StackSnapshot(new StackKey(id, "{}"), 1, name, List.of());
    }
}
