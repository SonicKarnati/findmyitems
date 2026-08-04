package dev.smpb.findmyitems.search;

import static org.junit.jupiter.api.Assertions.*;

import dev.smpb.findmyitems.index.SearchQuery;
import dev.smpb.findmyitems.model.StackKey;
import dev.smpb.findmyitems.model.StackSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
    void wordPrefixAndSubstringOutrankFuzzyMatches() {
        assertEquals(SearchQuery.MatchCategory.WORD_PREFIX,
                match("White Bed", "minecraft:white_bed", "whi").category());
        assertEquals(SearchQuery.MatchCategory.SUBSTRING,
                match("Bedrock", "minecraft:bedrock", "edr").category());
    }

    @Test
    void preservesItemIdWhileHumanizingItsPathForSearch() {
        var document = SearchDocument.from(stack("minecraft:oak_log", "Oak Log"));
        assertEquals("minecraft:oak_log", document.itemIdentifier());
        assertNotNull(SearchQuery.parse("oak log").match(document));
    }

    @Test
    void rootIndexExcludesRecipeIngredients() {
        var parent = stack("minecraft:oak_planks", "Oak Planks");
        var ingredient = stack("minecraft:oak_log", "Oak Log");
        var index = SearchIndex.rootOnly(List.of(parent, ingredient), Set.of(parent.key()));

        assertEquals(List.of("minecraft:oak_planks"), index.search(SearchQuery.parse("oak"), 20)
                .stream().map(SearchDocument::itemIdentifier).toList());
    }

    @Test
    void searchLimitIsAppliedAfterDeterministicRanking() {
        var stacks = new ArrayList<StackSnapshot>();
        for (var i = 0; i < 20; i++) stacks.add(stack("example:item_" + i, "Item " + i));
        var results = new SearchIndex(stacks).search(SearchQuery.parse("item"), 3);

        assertEquals(3, results.size());
        assertEquals(List.of("example:item_0", "example:item_1", "example:item_2"),
                results.stream().map(SearchDocument::itemIdentifier).toList());
    }

    @Test
    void fuzzyDistanceUsesReducedCandidates() {
        var stacks = new ArrayList<StackSnapshot>();
        for (var i = 0; i < 100; i++) stacks.add(stack("example:item_" + i, "Item " + i));
        stacks.add(stack("minecraft:white_bed", "White Bed"));
        var index = new SearchIndex(stacks);

        assertEquals(List.of("minecraft:white_bed"), index.search(SearchQuery.parse("whit"), 20)
                .stream().map(SearchDocument::itemIdentifier).toList());
        assertTrue(index.lastCandidateCount() < stacks.size());
        assertTrue(index.lastFuzzyCandidateCount() < stacks.size());
    }

    @Test
    void ranksAllMatchCategoriesInOrder() {
        var stacks = List.of(
                stack("example:exact", "White Bed"),
                new StackSnapshot(new StackKey("example:complete", "{}"), 1, "White Wool",
                        List.of("White Bed")),
                stack("example:ordered", "White Bed Deluxe"),
                stack("example:prefix", "White Bedside"),
                stack("example:substring", "White Stonebed"),
                stack("example:fuzzy", "White Bec"));
        var results = new SearchIndex(stacks).search(SearchQuery.parse("white bed"), 20);

        assertEquals(List.of("example:exact", "example:complete", "example:ordered", "example:prefix",
                        "example:substring", "example:fuzzy"),
                results.stream().map(SearchDocument::itemIdentifier).toList());
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
