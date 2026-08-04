package dev.smpb.findmyitems.craft;

import dev.smpb.findmyitems.model.StackKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CraftingPlan(Node root, PlanningInventory remainingInventory,
                           Map<StackKey, Long> consumedDelta, Map<StackKey, Long> surplusDelta,
                           Map<StackKey, Long> missing, PlanScore score, int failedCandidates) {
    public CraftingPlan {
        consumedDelta = Map.copyOf(consumedDelta);
        surplusDelta = Map.copyOf(surplusDelta);
        missing = Map.copyOf(missing);
    }

    public record Node(StackKey item, long requested, long indexed, long missing, long craftCount,
                       List<Node> children, Map<StackKey, Long> consumed,
                       Map<StackKey, Long> generatedSurplus, StackKey conversionSource) {
        public Node {
            children = List.copyOf(children);
            consumed = Map.copyOf(consumed);
            generatedSurplus = Map.copyOf(generatedSurplus);
        }
    }

    public static Node node(StackKey item, long requested, long indexed, long craftCount,
                            List<Node> children, Map<StackKey, Long> consumed,
                            Map<StackKey, Long> generatedSurplus, StackKey conversionSource) {
        return new Node(item, requested, indexed, Math.max(0, requested - indexed), craftCount,
                children, consumed, generatedSurplus, conversionSource);
    }

    public static Node rootNode(StackKey item, long requested, PlanningInventory inventory,
                                PlanScore score) {
        var indexed = Math.min(requested, inventory.count(item));
        return node(item, requested, indexed, 0, List.of(), Map.of(), Map.of(), null);
    }

    public static CraftingPlan root(StackKey item, long requested, PlanningInventory inventory, PlanScore score) {
        var node = rootNode(item, requested, inventory, score);
        return of(node, inventory, Map.of(), Map.of(), Map.of(), score);
    }

    public static CraftingPlan of(Node root, PlanningInventory remainingInventory,
                                  Map<StackKey, Long> consumed, Map<StackKey, Long> surplus,
                                  Map<StackKey, Long> missing, PlanScore score) {
        return new CraftingPlan(root, remainingInventory, consumed, surplus, missing, score, 0);
    }

    public long missing(String itemId) {
        return missing.entrySet().stream().filter(entry -> entry.getKey().itemId().equals(itemId))
                .mapToLong(Map.Entry::getValue).sum();
    }

    public long generatedSurplus(StackKey key) { return surplusDelta.getOrDefault(key, 0L); }

    public boolean hasConversion(String sourceId, String outputId) {
        return flattenedNodes().stream().anyMatch(node -> node.conversionSource() != null
                && node.conversionSource().itemId().equals(sourceId) && node.item().itemId().equals(outputId));
    }

    public long conversionCount() {
        return flattenedNodes().stream().filter(node -> node.conversionSource() != null).count();
    }

    public List<String> flattenedItemIds() { return flattenedNodes().stream().map(node -> node.item().itemId()).toList(); }

    private List<Node> flattenedNodes() {
        var nodes = new ArrayList<Node>();
        flatten(root, nodes);
        return nodes;
    }

    private static void flatten(Node node, List<Node> out) {
        out.add(node);
        node.children().forEach(child -> flatten(child, out));
    }
}
