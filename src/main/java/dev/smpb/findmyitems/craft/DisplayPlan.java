package dev.smpb.findmyitems.craft;

import dev.smpb.findmyitems.model.StackKey;

import java.util.ArrayList;
import java.util.List;

public final class DisplayPlan {
    private DisplayPlan() {}

    public record Row(String rootId, String nodeId, String parentId, int depth,
                      StackKey item, long requested, long indexed, long missing) {}

    public static List<Row> flatten(CraftingPlan plan) {
        return flatten(List.of(plan));
    }

    public static List<Row> flatten(List<CraftingPlan> plans) {
        var rows = new ArrayList<Row>();
        for (var plan : plans) {
            var rootId = stableId(plan.root().item());
            flatten(plan.root(), 0, rootId, null, rootId, rows);
        }
        return List.copyOf(rows);
    }

    private static void flatten(CraftingPlan.Node node, int depth, String rootId, String parentId,
                                String nodeId, List<Row> out) {
        out.add(new Row(rootId, nodeId, parentId, depth, node.item(), node.requested(),
                node.indexed(), node.missing()));
        var seen = new java.util.HashMap<String, Integer>();
        for (var child : node.children()) {
            var childId = stableId(child.item());
            var occurrence = seen.merge(childId, 1, Integer::sum);
            if (occurrence > 1) childId += "#" + occurrence;
            flatten(child, depth + 1, rootId, nodeId, nodeId + "/" + childId, out);
        }
    }

    private static String stableId(StackKey key) {
        return key.itemId() + "[" + key.componentsJson() + "]";
    }
}
