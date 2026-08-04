package dev.smpb.findmyitems.craft;

import dev.smpb.findmyitems.model.StackKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** The authoritative immutable, cycle-safe crafting planner. */
public final class CraftingPlanner {
    private CraftingPlanner() {}

    public static CraftingPlan plan(RecipeCatalog catalog, StackKey target, long amount,
                                    PlanningInventory inventory, PlanningPolicy policy) {
        return plan(catalog, target, amount, inventory, policy, () -> false);
    }

    public static CraftingPlan plan(RecipeCatalog catalog, StackKey target, long amount,
                                    PlanningInventory inventory, PlanningPolicy policy,
                                    BooleanSupplier cancelled) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        var memo = new HashMap<MemoKey, PlanningState>();
        var budget = new int[]{policy.candidateCap()};
        var state = solve(catalog, target, amount, inventory, policy, Set.of(), cancelled, memo, budget);
        PlanScore score;
        try {
            score = score(state);
        } catch (ArithmeticException overflow) {
            state = PlanningState.failed(target, amount, inventory);
            score = score(state);
        }
        var root = state.node.withScore(score);
        return new CraftingPlan(root, state.inventory, state.consumed, state.surplus,
                state.missing, state.remainders, score, state.failedCandidates, state.cancelled);
    }

    private static PlanningState solve(RecipeCatalog catalog, StackKey item, long requested,
                                       PlanningInventory initial, PlanningPolicy policy,
                                       Set<StackKey> path, BooleanSupplier cancelled,
                                       Map<MemoKey, PlanningState> memo, int[] budget) {
        if (cancelled.getAsBoolean()) return PlanningState.cancelled(item, requested, initial);
        var key = new MemoKey(catalog.generation(), item, requested, initial.counts(), policy, path);
        var cached = memo.get(key);
        if (cached != null) return cached;

        var indexed = Math.min(requested, initial.count(item));
        var stock = initial.consume(item, indexed);
        var shortfall = requested - indexed;
        if (shortfall == 0) {
            var result = addDirectConsumption(PlanningState.covered(item, requested, indexed, stock), item, indexed);
            memo.put(key, result);
            return result;
        }
        if (path.contains(item)) {
            var result = PlanningState.missing(item, requested, indexed, stock, 0);
            memo.put(key, result);
            return result;
        }

        var best = new Best();
        for (var recipe : catalog.recipesFor(item)) {
            if (budget[0] == 0) break;
            if (!allowed(recipe, policy)) continue;
            evaluateChoices(catalog, recipe, item, requested, indexed, shortfall, stock,
                    policy, path, cancelled, memo, budget, new ArrayList<>(), 0, best);
            if (cancelled.getAsBoolean()) break;
        }
        PlanningState result = best.value;
        if (result == null) {
            result = cancelled.getAsBoolean()
                    ? PlanningState.cancelled(item, requested, stock)
                    : PlanningState.missing(item, requested, indexed, stock, 1);
        }
        result = addDirectConsumption(result, item, indexed);
        memo.put(key, result);
        return result;
    }

    private static void evaluateChoices(RecipeCatalog catalog, RecipeCatalog.RecipeDefinition recipe,
                                        StackKey output, long requested, long indexed, long shortfall,
                                        PlanningInventory stock, PlanningPolicy policy, Set<StackKey> path,
                                        BooleanSupplier cancelled, Map<MemoKey, PlanningState> memo,
                                        int[] budget, List<StackKey> selected, int slot, Best best) {
        if (cancelled.getAsBoolean() || budget[0] == 0) return;
        if (slot < recipe.ingredientOptions().size()) {
            for (var option : recipe.ingredientOptions().get(slot)) {
                selected.add(option);
                evaluateChoices(catalog, recipe, output, requested, indexed, shortfall, stock, policy,
                        path, cancelled, memo, budget, selected, slot + 1, best);
                selected.removeLast();
                if (cancelled.getAsBoolean() || budget[0] == 0) return;
            }
            return;
        }
        budget[0]--;
        var candidate = evaluateCandidate(catalog, recipe, output, requested, indexed, shortfall,
                stock, policy, path, cancelled, memo, budget, selected);
        if (candidate == null) return;
        if (best.value == null || compare(candidate, best.value) < 0) best.value = candidate;
    }

    private static PlanningState evaluateCandidate(RecipeCatalog catalog, RecipeCatalog.RecipeDefinition recipe,
                                                    StackKey output, long requested, long indexed, long shortfall,
                                                    PlanningInventory state, PlanningPolicy policy, Set<StackKey> path,
                                                    BooleanSupplier cancelled, Map<MemoKey, PlanningState> memo,
                                                    int[] budget, List<StackKey> selected) {
        final long crafts;
        try {
            crafts = Math.floorDiv(Math.addExact(shortfall, Math.subtractExact(recipe.outputBatch(), 1)),
                    recipe.outputBatch());
        } catch (ArithmeticException overflow) {
            return PlanningState.failed(output, requested, indexed, state);
        }
        var nextPath = new HashSet<>(path);
        nextPath.add(output);
        var quantities = new LinkedHashMap<StackKey, Long>();
        try {
            for (var ingredient : selected) quantities.merge(ingredient, crafts, Math::addExact);
        } catch (ArithmeticException overflow) {
            return PlanningState.failed(output, requested, indexed, state);
        }

        var current = state;
        var children = new ArrayList<CraftingPlan.Node>();
        var missing = new LinkedHashMap<StackKey, Long>();
        var consumed = new LinkedHashMap<StackKey, Long>();
        var surplus = new LinkedHashMap<StackKey, Long>();
        var remainders = new LinkedHashMap<StackKey, Long>();
        StackKey conversionSource = null;
        var childrenSatisfied = true;
        try {
            for (var entry : quantities.entrySet()) {
                if (cancelled.getAsBoolean()) return null;
                var ingredient = entry.getKey();
                if (catalog.sameScc(output, ingredient) && current.count(ingredient) == 0) return null;
                if (catalog.sameScc(output, ingredient)) conversionSource = ingredient;
                var child = solve(catalog, ingredient, entry.getValue(), current, policy, nextPath,
                        cancelled, memo, budget);
                children.add(child.node);
                merge(missing, child.missing);
                merge(consumed, child.consumed);
                merge(surplus, child.surplus);
                merge(remainders, child.remainders);
                current = child.inventory;
                if (!child.missing.isEmpty()) childrenSatisfied = false;
                if (child.cancelled) return child;
            }
            if (!childrenSatisfied) {
                var node = CraftingPlan.node(output, requested, indexed, 0, List.of(),
                        Map.of(), Map.of(), conversionSource);
                return new PlanningState(node, state, Map.of(), Map.of(), Map.of(), missing, 0, false);
            }
            var produced = Math.multiplyExact(crafts, recipe.outputBatch());
            var extra = Math.subtractExact(produced, shortfall);
            if (extra > 0) {
                current = current.add(output, extra);
                merge(surplus, Map.of(output, extra));
            }
            for (var ingredient : selected) {
                var selectedRemainders = recipe.alternativeRemainders().get(ingredient);
                if (selectedRemainders == null) continue;
                for (var remainder : selectedRemainders.entrySet()) {
                    var count = Math.multiplyExact(crafts, remainder.getValue());
                    current = current.add(remainder.getKey(), count);
                    merge(remainders, Map.of(remainder.getKey(), count));
                }
            }
            for (var remainder : recipe.remainders().entrySet()) {
                var count = Math.multiplyExact(crafts, remainder.getValue());
                current = current.add(remainder.getKey(), count);
                merge(remainders, Map.of(remainder.getKey(), count));
            }
        } catch (ArithmeticException overflow) {
            return PlanningState.failed(output, requested, indexed, state);
        }
        var node = CraftingPlan.node(output, requested, indexed, crafts, children,
                consumed, surplus, remainders, conversionSource, null);
        return new PlanningState(node, current, consumed, surplus, remainders, missing, 0, false);
    }

    private static PlanningState addDirectConsumption(PlanningState state, StackKey item, long amount) {
        if (amount == 0 || state.cancelled) return state;
        var consumed = new LinkedHashMap<>(state.consumed);
        consumed.merge(item, amount, Math::addExact);
        var nodeConsumed = new LinkedHashMap<>(state.node.consumed());
        nodeConsumed.merge(item, amount, Math::addExact);
        var node = CraftingPlan.node(state.node.item(), state.node.requested(), state.node.indexed(),
                state.node.craftCount(), state.node.children(), nodeConsumed,
                state.node.generatedSurplus(), state.node.generatedRemainders(),
                state.node.conversionSource(), state.node.score());
        return new PlanningState(node, state.inventory, consumed, state.surplus, state.remainders,
                state.missing, state.failedCandidates, false);
    }

    private static boolean allowed(RecipeCatalog.RecipeDefinition recipe, PlanningPolicy policy) {
        if (recipe.station() == RecipeCatalog.Station.CRAFTING_TABLE) {
            return policy.allowCraftingTable() && recipe.width() <= 3 && recipe.height() <= 3;
        }
        return policy.allowInventoryCrafting() && recipe.width() <= 2 && recipe.height() <= 2;
    }

    private static int compare(PlanningState left, PlanningState right) {
        try {
            return score(left).compareTo(score(right));
        } catch (ArithmeticException overflow) {
            return 1;
        }
    }

    private static PlanScore score(PlanningState state) {
        var missingQuantity = checkedSum(state.missing.values());
        var depth = maxDepth(state.node);
        return new PlanScore(missingQuantity, state.missing.size(), craftCount(state.node),
                conversionCount(state.node), depth);
    }

    private static long craftCount(CraftingPlan.Node node) {
        var total = node.craftCount();
        for (var child : node.children()) total = Math.addExact(total, craftCount(child));
        return total;
    }

    private static long conversionCount(CraftingPlan.Node node) {
        var total = node.conversionSource() == null ? 0L : 1L;
        for (var child : node.children()) total = Math.addExact(total, conversionCount(child));
        return total;
    }

    private static long maxDepth(CraftingPlan.Node node) {
        var max = 0L;
        for (var child : node.children()) max = Math.max(max, maxDepth(child));
        return Math.addExact(max, 1);
    }

    private static long checkedSum(Iterable<Long> values) {
        var total = 0L;
        for (var value : values) total = Math.addExact(total, value);
        return total;
    }

    private static void merge(Map<StackKey, Long> target, Map<StackKey, Long> source) {
        source.forEach((key, value) -> target.merge(key, value, Math::addExact));
    }

    private record MemoKey(long generation, StackKey item, long requested, Map<StackKey, Long> inventory,
                           PlanningPolicy policy, Set<StackKey> activePath) {}

    private static final class Best {
        private PlanningState value;
    }

    private record PlanningState(CraftingPlan.Node node, PlanningInventory inventory,
                                 Map<StackKey, Long> consumed, Map<StackKey, Long> surplus,
                                 Map<StackKey, Long> remainders, Map<StackKey, Long> missing,
                                 int failedCandidates, boolean cancelled) {
        static PlanningState covered(StackKey item, long requested, long indexed, PlanningInventory inventory) {
            return new PlanningState(CraftingPlan.node(item, requested, indexed, 0, List.of(),
                    Map.of(), Map.of(), null), inventory, Map.of(), Map.of(), Map.of(), Map.of(), 0, false);
        }

        static PlanningState missing(StackKey item, long requested, long indexed,
                                      PlanningInventory inventory, int failures) {
            return new PlanningState(CraftingPlan.node(item, requested, indexed, 0, List.of(),
                    Map.of(), Map.of(), null), inventory, Map.of(), Map.of(), Map.of(),
                    Map.of(item, requested - indexed), failures, false);
        }

        static PlanningState cancelled(StackKey item, long requested, PlanningInventory inventory) {
            return new PlanningState(CraftingPlan.node(item, requested, 0, 0, List.of(),
                    Map.of(), Map.of(), null), inventory, Map.of(), Map.of(), Map.of(),
                    Map.of(item, requested), 0, true);
        }

        static PlanningState failed(StackKey item, long requested, long indexed, PlanningInventory inventory) {
            return missing(item, requested, indexed, inventory, 1);
        }

        static PlanningState failed(StackKey item, long requested, PlanningInventory inventory) {
            return missing(item, requested, 0, inventory, 1);
        }
    }
}
