package dev.smpb.findmyitems.craft;

import dev.smpb.findmyitems.model.StackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Breaks an item down into the raw materials needed to craft it, recursively, charging each
 * step against what the container index already holds.
 *
 * <p>A node is expanded only when the index cannot cover it: if you already have 40 sticks
 * stashed somewhere, the sticks node is a leaf and planks/logs are never walked. Stock is
 * consumed as the tree is built, so two branches cannot both claim the same 40 sticks.
 */
public final class CraftingPlanner {
    /** Deep enough for ore -> ingot -> block chains; short enough that a recipe loop cannot hang the GUI. */
    private static final int MAX_DEPTH = 8;

    private CraftingPlanner() {}

    /** Plans against an immutable catalog and never mutates the caller's inventory. */
    public static CraftingPlan plan(RecipeCatalog catalog, StackKey target, long amount,
                                    PlanningInventory inventory, PlanningPolicy policy) {
        return plan(catalog, target, amount, inventory, policy, () -> false);
    }

    public static CraftingPlan plan(RecipeCatalog catalog, StackKey target, long amount,
                                    PlanningInventory inventory, PlanningPolicy policy,
                                    BooleanSupplier cancelled) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        var memo = new HashMap<MemoKey, PlanningState>();
        var state = solve(catalog, target, amount, inventory, policy, Set.of(), cancelled, memo);
        return new CraftingPlan(state.node, state.inventory, state.consumed, state.surplus,
                state.missing, score(state), state.failedCandidatesCount);
    }

    private static PlanningState solve(RecipeCatalog catalog, StackKey item, long requested,
                                       PlanningInventory initial, PlanningPolicy policy,
                                       Set<StackKey> path, BooleanSupplier cancelled,
                                       Map<MemoKey, PlanningState> memo) {
        if (cancelled.getAsBoolean()) return PlanningState.leaf(item, requested, initial, true);
        var memoKey = new MemoKey(catalog.generation(), item, requested, initial.counts(),
                policy, path);
        var cached = memo.get(memoKey);
        if (cached != null) return cached;
        var indexed = Math.min(requested, initial.count(item));
        var stock = initial.consume(item, indexed);
        var shortfall = requested - indexed;
        if (shortfall == 0) {
            var result = PlanningState.covered(item, requested, indexed, stock);
            memo.put(memoKey, result);
            return result;
        }
        if (path.contains(item)) {
            var result = PlanningState.missing(item, requested, indexed, stock, false);
            memo.put(memoKey, result);
            return result;
        }

        var recipes = catalog.recipesFor(item);
        if (recipes.isEmpty()) {
            var result = PlanningState.missing(item, requested, indexed, stock, false);
            memo.put(memoKey, result);
            return result;
        }
        var best = (PlanningState) null;
        var tried = 0;
        for (var recipe : recipes) {
            if (cancelled.getAsBoolean() || tried++ >= policy.candidateCap()) break;
            if (recipe.ingredientOptions().stream().map(options -> bestIngredient(options, stock))
                    .anyMatch(ingredient -> catalog.sameScc(item, ingredient) && stock.count(ingredient) == 0)) {
                continue;
            }
            var candidate = evaluate(catalog, recipe, item, requested, indexed, shortfall, stock,
                    policy, path, cancelled, memo);
            if (candidate == null) continue;
            if (best == null || score(candidate).compareTo(score(best)) < 0) best = candidate;
        }
        if (best == null) best = PlanningState.missing(item, requested, indexed, stock, tried);
        memo.put(memoKey, best);
        return best;
    }

    private static PlanningState evaluate(RecipeCatalog catalog, RecipeCatalog.RecipeDefinition recipe,
                                           StackKey output, long requested, long indexed, long shortfall,
                                           PlanningInventory state, PlanningPolicy policy, Set<StackKey> path,
                                           BooleanSupplier cancelled, Map<MemoKey, PlanningState> memo) {
        final long crafts;
        try {
            crafts = Math.floorDiv(Math.addExact(shortfall, recipe.outputBatch() - 1), recipe.outputBatch());
        } catch (ArithmeticException overflow) {
            return PlanningState.failed(output, requested, indexed, state);
        }
        var nextPath = new HashSet<>(path);
        nextPath.add(output);
        var children = new ArrayList<CraftingPlan.Node>();
        var missing = new LinkedHashMap<StackKey, Long>();
        var consumed = new LinkedHashMap<StackKey, Long>();
        var surplus = new LinkedHashMap<StackKey, Long>();
        var conversionSource = (StackKey) null;
        var current = state;
        try {
            var ingredients = new LinkedHashMap<StackKey, Long>();
            for (var options : recipe.ingredientOptions()) {
                if (cancelled.getAsBoolean()) return null;
                var choice = bestIngredient(options, current);
                ingredients.merge(choice, crafts, Math::addExact);
            }
            for (var entry : ingredients.entrySet()) {
                var choice = entry.getKey();
                var quantity = entry.getValue();
                if (catalog.sameScc(output, choice) && current.count(choice) == 0) return null;
                if (catalog.sameScc(output, choice)) conversionSource = choice;
                var child = solve(catalog, choice, quantity, current, policy, nextPath, cancelled, memo);
                children.add(child.node);
                merge(missing, child.missing);
                merge(consumed, child.consumed);
                merge(surplus, child.surplus);
                current = child.inventory;
            }
            var extra = Math.subtractExact(Math.multiplyExact(crafts, recipe.outputBatch()), shortfall);
            if (extra > 0) {
                current = current.add(output, extra);
                merge(surplus, Map.of(output, extra));
            }
        } catch (ArithmeticException overflow) {
            return PlanningState.failed(output, requested, indexed, state);
        }
        var node = CraftingPlan.node(output, requested, indexed, crafts, children,
                consumed, surplus, conversionSource);
        return new PlanningState(node, current, consumed, surplus, missing, false);
    }

    private static StackKey bestIngredient(List<StackKey> options, PlanningInventory inventory) {
        var best = options.getFirst();
        var bestCount = inventory.count(best);
        for (var option : options) {
            var count = inventory.count(option);
            if (count > bestCount) { best = option; bestCount = count; }
        }
        return best;
    }

    private static PlanScore score(PlanningState state) {
        var missingQuantity = state.missing.values().stream().mapToLong(Long::longValue).sum();
        var depth = maxDepth(state.node);
        return new PlanScore(missingQuantity, state.missing.size(), 0, 0, state.consumed.size(),
                craftCount(state.node), 0, conversionCount(state.node), depth);
    }

    private static long craftCount(CraftingPlan.Node node) {
        return node.craftCount() + node.children().stream().mapToLong(CraftingPlanner::craftCount).sum();
    }

    private static long conversionCount(CraftingPlan.Node node) {
        return (node.conversionSource() == null ? 0 : 1)
                + node.children().stream().mapToLong(CraftingPlanner::conversionCount).sum();
    }

    private static long maxDepth(CraftingPlan.Node node) {
        var childDepth = node.children().stream().mapToLong(CraftingPlanner::maxDepth).max().orElse(-1);
        return childDepth + 1;
    }

    private static void merge(Map<StackKey, Long> target, Map<StackKey, Long> source) {
        source.forEach((key, value) -> target.merge(key, value, Math::addExact));
    }

    private record MemoKey(long catalogGeneration, StackKey item, long requested,
                           Map<StackKey, Long> inventory, PlanningPolicy policy,
                           Set<StackKey> activePath) {}

    private record PlanningState(CraftingPlan.Node node, PlanningInventory inventory,
                                 Map<StackKey, Long> consumed, Map<StackKey, Long> surplus,
                                 Map<StackKey, Long> missing, boolean failedCandidates,
                                 int failedCandidatesCount) {
        private PlanningState(CraftingPlan.Node node, PlanningInventory inventory,
                              Map<StackKey, Long> consumed, Map<StackKey, Long> surplus,
                              Map<StackKey, Long> missing, boolean failedCandidates) {
            this(node, inventory, consumed, surplus, missing, failedCandidates,
                    failedCandidates ? 1 : 0);
        }
        static PlanningState covered(StackKey item, long requested, long indexed, PlanningInventory inventory) {
            return new PlanningState(CraftingPlan.node(item, requested, indexed, 0, List.of(), Map.of(), Map.of(), null),
                    inventory, Map.of(item, indexed), Map.of(), Map.of(), false);
        }
        static PlanningState missing(StackKey item, long requested, long indexed, PlanningInventory inventory, int failures) {
            return new PlanningState(CraftingPlan.node(item, requested, indexed, 0, List.of(), Map.of(), Map.of(), null),
                    inventory, indexed == 0 ? Map.of() : Map.of(item, indexed), Map.of(), Map.of(item, requested - indexed), failures > 0, failures);
        }
        static PlanningState missing(StackKey item, long requested, long indexed, PlanningInventory inventory, boolean failed) {
            return missing(item, requested, indexed, inventory, failed ? 1 : 0);
        }
        static PlanningState leaf(StackKey item, long requested, PlanningInventory inventory, boolean failed) {
            return missing(item, requested, 0, inventory, failed);
        }
        static PlanningState failed(StackKey item, long requested, long indexed, PlanningInventory inventory) {
            return missing(item, requested, indexed, inventory, 1);
        }
    }

    /**
     * @param stack     one of the item, for display
     * @param needed    how many this branch requires
     * @param available how many of {@code needed} the index can already supply
     * @param children  what to craft the shortfall from; empty when covered or when no recipe exists
     */
    public record Material(ItemStack stack, int needed, int available, List<Material> children) {
        public Material {
            children = List.copyOf(children);
        }

        public int missing() {
            return Math.max(0, needed - available);
        }

        public boolean satisfied() {
            return missing() == 0;
        }

        /** True when the shortfall cannot be crafted either — the thing you actually have to go find. */
        public boolean isDeadEnd() {
            return !satisfied() && children.isEmpty();
        }
    }

    /**
     * @param stock how many of each item the index holds, by item id; consumed during planning
     */
    public static Material plan(RecipeManager recipes, Level level, Item target, int amount, Map<String, Integer> stock) {
        var byOutput = cachedOutputIndex(recipes, level);
        var working = new HashMap<>(stock);
        return expand(byOutput, level, target, amount, working, new HashSet<>(), 0);
    }

    /**
     * Every item a crafting table can actually produce.
     *
     * <p>This is also the filter for "what can I ask for": anything outside it — bedrock, command
     * blocks, spawn eggs, the rest of the creative menu — has no recipe to plan, so offering it as
     * a craft target is offering a guaranteed dead end.
     */
    public static Set<Item> craftable(RecipeManager recipes, Level level) {
        return cachedOutputIndex(recipes, level).keySet();
    }

    // ponytail: single-entry memo — the recipe set only changes on reload, and planning runs on
    // every keystroke. Swap for a proper invalidation hook if datapack reloads start mattering.
    private static RecipeManager cachedFor;
    private static Map<Item, RecipeHolder<?>> cachedIndex;

    private static synchronized Map<Item, RecipeHolder<?>> cachedOutputIndex(RecipeManager recipes, Level level) {
        if (recipes != cachedFor) {
            cachedIndex = outputIndex(recipes, level);
            cachedFor = recipes;
        }
        return cachedIndex;
    }

    private static Material expand(Map<Item, RecipeHolder<?>> byOutput, Level level, Item item, int needed,
                                   Map<String, Integer> stock, Set<Item> onPath, int depth) {
        var id = BuiltInRegistries.ITEM.getKey(item).toString();
        var have = stock.getOrDefault(id, 0);
        var used = Math.min(have, needed);
        stock.put(id, have - used);

        var missing = needed - used;
        var display = new ItemStack(item);
        if (missing == 0 || depth >= MAX_DEPTH || !onPath.add(item)) {
            return new Material(display, needed, used, List.of());
        }

        try {
            var recipe = byOutput.get(item);
            if (recipe == null) {
                return new Material(display, needed, used, List.of());
            }

            var perCraft = Math.max(1, resultCount(recipe, level, item));
            var crafts = Math.ceilDiv(missing, perCraft);

            // ingredients() lists one entry per grid slot, so three cobblestone slots become one
            // child needing 3x the crafts rather than three identical rows.
            var perIngredient = new LinkedHashMap<Item, Integer>();
            for (var ingredient : recipe.value().placementInfo().ingredients()) {
                var choice = preferStocked(ingredient, stock);
                if (choice != null) perIngredient.merge(choice, crafts, Integer::sum);
            }

            var children = new ArrayList<Material>();
            perIngredient.forEach((choice, count) ->
                    children.add(expand(byOutput, level, choice, count, stock, onPath, depth + 1)));
            return new Material(display, needed, used, children);
        } finally {
            onPath.remove(item);
        }
    }

    /** Of an ingredient's accepted items, pick one we already have; otherwise the first listed. */
    private static Item preferStocked(net.minecraft.world.item.crafting.Ingredient ingredient, Map<String, Integer> stock) {
        Item first = null;
        for (var holder : (Iterable<net.minecraft.core.Holder<Item>>) ingredient.items()::iterator) {
            var candidate = holder.value();
            if (first == null) first = candidate;
            var id = BuiltInRegistries.ITEM.getKey(candidate).toString();
            if (stock.getOrDefault(id, 0) > 0) return candidate;
        }
        return first;
    }

    private static int resultCount(RecipeHolder<?> recipe, Level level, Item item) {
        var context = SlotDisplayContext.fromLevel(level);
        for (var display : recipe.value().display()) {
            for (var stack : display.result().resolveForStacks(context)) {
                if (stack.getItem() == item) return stack.getCount();
            }
        }
        return 1;
    }

    /**
     * Maps each craftable item to the recipe that produces it.
     *
     * <p>Crafting-table recipes only. Smelting and stonecutting are deliberately excluded: they
     * run both ways (cobblestone smelts to stone, stone cuts back to cobblestone) and turn the
     * tree into a loop, and "go find the iron ingots" is the answer this mod exists to give
     * anyway. Ore-to-ingot steps therefore show up as leaves to go and locate.
     */
    private static Map<Item, RecipeHolder<?>> outputIndex(RecipeManager recipes, Level level) {
        var context = SlotDisplayContext.fromLevel(level);
        var byOutput = new HashMap<Item, RecipeHolder<?>>();

        for (var holder : recipes.getRecipes()) {
            if (holder.value().getType() != RecipeType.CRAFTING) continue;
            if (holder.value().isSpecial()) continue;
            for (var display : holder.value().display()) {
                for (var stack : display.result().resolveForStacks(context)) {
                    if (stack.isEmpty()) continue;
                    byOutput.putIfAbsent(stack.getItem(), holder);
                }
            }
        }
        return byOutput;
    }
}
