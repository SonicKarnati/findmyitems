package dev.smpb.findmyitems.craft;

import dev.smpb.findmyitems.model.StackKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record CraftingPlan(Node root, PlanningInventory remainingInventory,
                           Map<StackKey, Long> consumedDelta, Map<StackKey, Long> surplusDelta,
                           Map<StackKey, Long> missing, Map<StackKey, Long> remainders,
                           PlanScore score, int failedCandidates, boolean cancelled) {
    public CraftingPlan {
        consumedDelta = Map.copyOf(consumedDelta);
        surplusDelta = Map.copyOf(surplusDelta);
        missing = Map.copyOf(missing);
        remainders = Map.copyOf(remainders);
    }

    public record Node(StackKey item, long requested, long indexed, long missing, long craftCount,
                       List<Node> children, Map<StackKey, Long> consumed,
                       Map<StackKey, Long> generatedSurplus, StackKey conversionSource, PlanScore score) {
        public Node {
            children = List.copyOf(children);
            consumed = Map.copyOf(consumed);
            generatedSurplus = Map.copyOf(generatedSurplus);
        }

        public Node withScore(PlanScore score) {
            return new Node(item, requested, indexed, missing, craftCount, children, consumed,
                    generatedSurplus, conversionSource, score);
        }
    }

    public static Node node(StackKey item, long requested, long indexed, long craftCount,
                            List<Node> children, Map<StackKey, Long> consumed,
                            Map<StackKey, Long> generatedSurplus, StackKey conversionSource) {
        return node(item, requested, indexed, craftCount, children, consumed, generatedSurplus,
                conversionSource, null);
    }

    public static Node node(StackKey item, long requested, long indexed, long craftCount,
                            List<Node> children, Map<StackKey, Long> consumed,
                            Map<StackKey, Long> generatedSurplus, StackKey conversionSource,
                            PlanScore score) {
        return new Node(item, requested, indexed, Math.max(0, requested - indexed), craftCount,
                children, consumed, generatedSurplus, conversionSource, score);
    }

    public static Node rootNode(StackKey item, long requested, PlanningInventory inventory,
                                PlanScore score) {
        var indexed = Math.min(requested, inventory.count(item));
        return node(item, requested, indexed, 0, List.of(), Map.of(), Map.of(), null, score);
    }

    public static CraftingPlan root(StackKey item, long requested, PlanningInventory inventory, PlanScore score) {
        var node = rootNode(item, requested, inventory, score);
        return of(node, inventory, Map.of(), Map.of(), Map.of(), score);
    }

    public static CraftingPlan of(Node root, PlanningInventory remainingInventory,
                                  Map<StackKey, Long> consumed, Map<StackKey, Long> surplus,
                                  Map<StackKey, Long> missing, PlanScore score) {
        return new CraftingPlan(root, remainingInventory, consumed, surplus, missing, Map.of(), score, 0, false);
    }

    public static CraftingPlan of(Node root, PlanningInventory remainingInventory,
                                  Map<StackKey, Long> consumed, Map<StackKey, Long> surplus,
                                  Map<StackKey, Long> missing, Map<StackKey, Long> remainders,
                                  PlanScore score, int failedCandidates, boolean cancelled) {
        return new CraftingPlan(root, remainingInventory, consumed, surplus, missing, remainders,
                score, failedCandidates, cancelled);
    }

    public long missing(String itemId) {
        return missing.entrySet().stream().filter(entry -> entry.getKey().itemId().equals(itemId))
                .mapToLong(Map.Entry::getValue).reduce(0, Math::addExact);
    }

    public long generatedSurplus(StackKey key) { return surplusDelta.getOrDefault(key, 0L); }

    public boolean hasConversion(String sourceId, String outputId) {
        return flattenedNodes().stream().anyMatch(node -> node.conversionSource() != null
                && node.conversionSource().itemId().equals(sourceId) && node.item().itemId().equals(outputId));
    }

    public long conversionCount() {
        var count = 0L;
        for (var node : flattenedNodes()) {
            if (node.conversionSource() != null) count = Math.addExact(count, 1);
        }
        return count;
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
