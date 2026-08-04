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
        for (var i = 0; i < plans.size(); i++) flatten(plans.get(i).root(), 0, "root-" + i, null,
                "root-" + i, rows);
        return List.copyOf(rows);
    }

    private static void flatten(CraftingPlan.Node node, int depth, String rootId, String parentId,
                                String nodeId, List<Row> out) {
        out.add(new Row(rootId, nodeId, parentId, depth, node.item(), node.requested(),
                node.indexed(), node.missing()));
        for (var i = 0; i < node.children().size(); i++) {
            flatten(node.children().get(i), depth + 1, rootId, nodeId,
                    nodeId + "." + i, out);
        }
    }
}
