package dev.smpb.findmyitems.craft;

import dev.smpb.findmyitems.model.StackKey;
import dev.smpb.findmyitems.observation.SlotReader;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Reload-scoped, immutable view of the supported crafting graph. */
public final class RecipeCatalog {
    private static final AtomicLong GENERATIONS = new AtomicLong();
    private final List<RecipeDefinition> recipes;
    private final Map<StackKey, List<RecipeDefinition>> byOutput;
    private final Set<StackKey> roots;
    private final Map<StackKey, Integer> sccIds;
    private final long generation;

    public enum Station { INVENTORY, CRAFTING_TABLE }

    public record RecipeDefinition(StackKey output, long outputBatch, List<List<StackKey>> ingredientOptions,
                                   Station station, int width, int height,
                                   Map<StackKey, Long> remainders,
                                   Map<StackKey, Map<StackKey, Long>> alternativeRemainders) {
        public RecipeDefinition {
            if (outputBatch <= 0) throw new IllegalArgumentException("outputBatch must be positive");
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("recipe dimensions must be positive");
            ingredientOptions = ingredientOptions.stream().map(List::copyOf).toList();
            if (ingredientOptions.stream().anyMatch(List::isEmpty)) {
                throw new IllegalArgumentException("ingredient alternatives must not be empty");
            }
            remainders = Map.copyOf(remainders);
            if (remainders.values().stream().anyMatch(value -> value == null || value < 0)) {
                throw new IllegalArgumentException("remainder quantities must be non-negative");
            }
            alternativeRemainders = alternativeRemainders.entrySet().stream().collect(
                    java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
                            entry -> Map.copyOf(entry.getValue())));
            if (alternativeRemainders.values().stream().flatMap(values -> values.values().stream())
                    .anyMatch(value -> value == null || value < 0)) {
                throw new IllegalArgumentException("alternative remainder quantities must be non-negative");
            }
        }

        public RecipeDefinition(StackKey output, long outputBatch, List<List<StackKey>> ingredientOptions) {
            this(output, outputBatch, ingredientOptions, Station.INVENTORY, 2, 2, Map.of(), Map.of());
        }

        public RecipeDefinition(StackKey output, long outputBatch, List<List<StackKey>> ingredientOptions,
                                 Station station, int width, int height, Map<StackKey, Long> remainders) {
            this(output, outputBatch, ingredientOptions, station, width, height, remainders, Map.of());
        }
    }

    private RecipeCatalog(List<RecipeDefinition> recipes) {
        this.recipes = List.copyOf(recipes);
        var grouped = new LinkedHashMap<StackKey, List<RecipeDefinition>>();
        for (var recipe : recipes) grouped.computeIfAbsent(recipe.output(), ignored -> new ArrayList<>()).add(recipe);
        var frozen = new LinkedHashMap<StackKey, List<RecipeDefinition>>();
        grouped.forEach((key, value) -> frozen.put(key, List.copyOf(value)));
        byOutput = Map.copyOf(frozen);
        roots = Set.copyOf(grouped.keySet());
        sccIds = Map.copyOf(classifySccs(recipes));
        generation = GENERATIONS.incrementAndGet();
    }

    public static RecipeCatalog of(List<RecipeDefinition> recipes) {
        return new RecipeCatalog(recipes);
    }

    public static RecipeDefinition recipe(StackKey output, long batch, List<List<StackKey>> ingredientOptions) {
        return new RecipeDefinition(output, batch, ingredientOptions);
    }

    public static RecipeDefinition recipe(StackKey output, long batch, List<List<StackKey>> ingredientOptions,
                                          Station station, int width, int height,
                                          Map<StackKey, Long> remainders) {
        return new RecipeDefinition(output, batch, ingredientOptions, station, width, height, remainders);
    }

    public static RecipeDefinition recipeWithAlternativeRemainders(StackKey output, long batch,
                                                                    List<List<StackKey>> ingredientOptions,
                                                                    Station station, int width, int height,
                                                                    Map<StackKey, Map<StackKey, Long>> remainders) {
        return new RecipeDefinition(output, batch, ingredientOptions, station, width, height, Map.of(), remainders);
    }

    public static RecipeCatalog from(RecipeManager manager, Level level) {
        var context = SlotDisplayContext.fromLevel(level);
        var found = new ArrayList<RecipeDefinition>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            var recipe = holder.value();
            if (recipe.getType() != RecipeType.CRAFTING || recipe.isSpecial()) continue;
            for (var display : recipe.display()) {
                for (var result : display.result().resolveForStacks(context)) {
                    if (result.isEmpty()) continue;
                    var output = stackKey(result, level);
                    var placement = recipe.placementInfo();
                    var width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 2;
                    var height = recipe instanceof ShapedRecipe shaped ? shaped.getHeight() : 2;
                    if (!(recipe instanceof ShapedRecipe) && placement.ingredients().size() > 4) {
                        width = 3;
                        height = 3;
                    }
                    var station = width <= 2 && height <= 2 ? Station.INVENTORY : Station.CRAFTING_TABLE;
                    var slots = new ArrayList<List<StackKey>>();
                    for (Ingredient ingredient : placement.ingredients()) {
                        var choices = new ArrayList<StackKey>();
                        for (var holderItem : (Iterable<net.minecraft.core.Holder<net.minecraft.world.item.Item>>) ingredient.items()::iterator) {
                            var ingredientStack = new net.minecraft.world.item.ItemStack(holderItem.value());
                            choices.add(stackKey(ingredientStack, level));
                        }
                        if (!choices.isEmpty()) slots.add(choices);
                    }
                    var alternativeRemainders = alternativeRemainders(recipe, placement, width, height, level);
                    found.add(new RecipeDefinition(output, result.getCount(), slots, station, width, height,
                            Map.of(), alternativeRemainders));
                }
            }
        }
        return new RecipeCatalog(found);
    }

    public Set<StackKey> craftableRoots() { return roots; }
    public List<RecipeDefinition> recipesFor(StackKey output) { return byOutput.getOrDefault(output, List.of()); }
    public long generation() { return generation; }
    public int sccId(StackKey key) { return sccIds.getOrDefault(key, -1); }
    public boolean sameScc(StackKey left, StackKey right) { return sccId(left) >= 0 && sccId(left) == sccId(right); }
    public List<RecipeDefinition> recipes() { return recipes; }

    public static StackKey stackKey(net.minecraft.world.item.ItemStack stack, Level level) {
        return new StackKey(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                SlotReader.serializeComponents(stack.getComponentsPatch(), level.registryAccess()));
    }

    private static Map<StackKey, Map<StackKey, Long>> alternativeRemainders(
            net.minecraft.world.item.crafting.Recipe<?> recipe, net.minecraft.world.item.crafting.PlacementInfo placement,
            int width, int height, Level level) {
        if (!(recipe instanceof CraftingRecipe craftingRecipe)) return Map.of();
        var sample = new ArrayList<net.minecraft.world.item.ItemStack>();
        for (var i = 0; i < width * height; i++) sample.add(net.minecraft.world.item.ItemStack.EMPTY);
        var slots = placement.ingredients();
        for (var i = 0; i < slots.size() && i < sample.size(); i++) {
            var first = (Iterable<net.minecraft.core.Holder<net.minecraft.world.item.Item>>) slots.get(i).items()::iterator;
            var iterator = first.iterator();
            if (iterator.hasNext()) sample.set(i, new net.minecraft.world.item.ItemStack(iterator.next().value()));
        }
        var result = new LinkedHashMap<StackKey, Map<StackKey, Long>>();
        for (var slot = 0; slot < slots.size() && slot < sample.size(); slot++) {
            for (var holderItem : (Iterable<net.minecraft.core.Holder<net.minecraft.world.item.Item>>) slots.get(slot).items()::iterator) {
                var input = new ArrayList<>(sample);
                input.set(slot, new net.minecraft.world.item.ItemStack(holderItem.value()));
                var remainderStacks = craftingRecipe.getRemainingItems(CraftingInput.of(width, height, input));
                if (slot >= remainderStacks.size() || remainderStacks.get(slot).isEmpty()) continue;
                var ingredientKey = stackKey(input.get(slot), level);
                var remainderKey = stackKey(remainderStacks.get(slot), level);
                result.computeIfAbsent(ingredientKey, ignored -> new LinkedHashMap<>())
                        .put(remainderKey, 1L);
            }
        }
        return result;
    }

    private static Map<StackKey, Integer> classifySccs(List<RecipeDefinition> recipes) {
        var graph = new LinkedHashMap<StackKey, Set<StackKey>>();
        for (var recipe : recipes) {
            graph.computeIfAbsent(recipe.output(), ignored -> new LinkedHashSet<>());
            for (var options : recipe.ingredientOptions()) for (var ingredient : options) {
                graph.computeIfAbsent(ingredient, ignored -> new LinkedHashSet<>());
            }
        }
        for (var recipe : recipes) {
            for (var options : recipe.ingredientOptions()) for (var ingredient : options) {
                if (graph.containsKey(ingredient)) graph.get(recipe.output()).add(ingredient);
            }
        }
        var index = new int[]{0};
        var stack = new ArrayDeque<StackKey>();
        var onStack = new HashSet<StackKey>();
        var indexes = new HashMap<StackKey, Integer>();
        var low = new HashMap<StackKey, Integer>();
        var result = new LinkedHashMap<StackKey, Integer>();
        for (var key : graph.keySet()) if (!indexes.containsKey(key)) tarjan(key, graph, index, stack, onStack, indexes, low, result);
        return result;
    }

    private static void tarjan(StackKey key, Map<StackKey, Set<StackKey>> graph, int[] index,
                               ArrayDeque<StackKey> stack, Set<StackKey> onStack,
                               Map<StackKey, Integer> indexes, Map<StackKey, Integer> low,
                               Map<StackKey, Integer> result) {
        indexes.put(key, index[0]); low.put(key, index[0]++);
        stack.push(key); onStack.add(key);
        for (var next : graph.getOrDefault(key, Set.of())) {
            if (!indexes.containsKey(next)) {
                tarjan(next, graph, index, stack, onStack, indexes, low, result);
                low.put(key, Math.min(low.get(key), low.get(next)));
            } else if (onStack.contains(next)) low.put(key, Math.min(low.get(key), indexes.get(next)));
        }
        if (low.get(key).equals(indexes.get(key))) {
            int id = result.values().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
            StackKey member;
            do {
                member = stack.pop(); onStack.remove(member); result.put(member, id);
            } while (!member.equals(key));
        }
    }
}
