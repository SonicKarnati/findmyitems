package dev.smpb.findmyitems.craft;

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
