package dev.smpb.findmyitems.craft;

import dev.smpb.findmyitems.model.StackKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class DisplayPlanTest {
    private static StackKey key(String id) {
        return new StackKey(id, "{}");
    }

    @Test
    void independentRootsStartAtDepthZeroAndKeepStableIdentity() {
        var left = CraftingPlan.root(key("example:left"), 1, PlanningInventory.empty(),
                new PlanScore(0, 0, 0, 0, 0));
        var right = CraftingPlan.root(key("example:right"), 1, PlanningInventory.empty(),
                new PlanScore(0, 0, 0, 0, 0));

        var rows = DisplayPlan.flatten(List.of(left, right));

        assertEquals(List.of(0, 0), rows.stream().map(DisplayPlan.Row::depth).toList());
        assertEquals(List.of("example:left[{}]", "example:right[{}]"), rows.stream().map(DisplayPlan.Row::rootId).toList());
        assertEquals(List.of("example:left[{}]", "example:right[{}]"), rows.stream().map(DisplayPlan.Row::nodeId).toList());
        assertTrue(rows.stream().allMatch(row -> row.parentId() == null));
    }

    @Test
    void flatteningPassesDepthByValueForChildren() {
        var child = CraftingPlan.node(key("example:child"), 1, 0, 1, List.of(),
                Map.of(), Map.of(), null);
        var root = CraftingPlan.node(key("example:root"), 1, 0, 1, List.of(child),
                Map.of(), Map.of(), null);

        var rows = DisplayPlan.flatten(CraftingPlan.of(root, PlanningInventory.empty(),
                Map.of(), Map.of(), Map.of(), new PlanScore(0, 0, 0, 0, 0)));

        assertEquals(List.of(0, 1), rows.stream().map(DisplayPlan.Row::depth).toList());
        assertEquals("example:root[{}]", rows.getFirst().rootId());
        assertEquals("example:root[{}]", rows.get(1).rootId());
        assertEquals("example:root[{}]", rows.get(1).parentId());
    }

    @Test
    void rootNodeCarriesItsScore() {
        var score = new PlanScore(1, 2, 3, 4, 5);
        var plan = CraftingPlan.root(key("example:root"), 1, PlanningInventory.empty(), score);

        assertEquals(score, plan.root().score());
    }
}
