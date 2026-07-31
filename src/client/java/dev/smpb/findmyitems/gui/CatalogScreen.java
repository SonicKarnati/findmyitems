package dev.smpb.findmyitems.gui;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.smpb.findmyitems.craft.CraftingPlanner;
import dev.smpb.findmyitems.config.ModConfig;
import dev.smpb.findmyitems.index.ContainerIndex;
import dev.smpb.findmyitems.index.IndexedContainer;
import dev.smpb.findmyitems.index.ItemResult;
import dev.smpb.findmyitems.index.SourceResult;
import dev.smpb.findmyitems.model.ContainerKind;
import dev.smpb.findmyitems.model.ContainerObservation;
import dev.smpb.findmyitems.model.SourceKey;
import dev.smpb.findmyitems.model.StackKey;
import dev.smpb.findmyitems.observation.SlotReader;
import dev.smpb.findmyitems.retrieval.GhostOpen;
import dev.smpb.findmyitems.retrieval.RetrieveHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CatalogScreen extends Screen {
    static final int TITLE_Y = 8;
    static final int TABS_Y = 22;
    static final int SEARCH_Y = 46;
    static final int WIDGET_HEIGHT = 20;
    static final int LIST_Y = 72;
    static final int FOOTER_HEIGHT = 22;
    static final int ROW_HEIGHT = 28;
    static final int SLOT_SIZE = 18;
    static final int CELL_SIZE = 22;
    static final int BUTTON_SIZE = 20;
    static final int ICON_SIZE = 16;
    static final int GAP = 4;
    static final int AMOUNT_WIDTH = 56;
    static final int LAYOUT_BUTTON_WIDTH = 52;
    static final int MAX_LIST_WIDTH = 420;
    static final int INDENT = 10;
    /** Width of the grid's detail pane. Enough for a container name and a coordinate triple. */
    static final int DETAIL_WIDTH = 150;
    static final int DETAIL_PADDING = 6;
    static final int DETAIL_LINE = 10;


    private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
    private static final Identifier BUTTON_HOVER = Identifier.withDefaultNamespace("widget/button_highlighted");
    private static final Identifier BUTTON_DISABLED = Identifier.withDefaultNamespace("widget/button_disabled");
    private static final Identifier SLOT = Identifier.withDefaultNamespace("container/slot");

    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFA0A0A0;
    private static final int TEXT_DISABLED = 0xFF6E6E6E;
    private static final int TEXT_MISSING = 0xFFFF7B6B;
    private static final int TEXT_OK = 0xFF8CE07A;
    private static final int LIST_BACKGROUND = 0xC0101010;
    private static final int LIST_BORDER = 0xFF3A3A3A;
    private static final int CELL_HOVER = 0x60FFFFFF;

    public enum View { ITEMS, CONTAINERS, CRAFTING }

    public enum Layout { LIST, GRID }

    /** Rebuilt only when the recipes or the index actually change — see the methods that read them. */
    private static Set<Item> cachedCraftableSource;
    private static List<ItemStack> cachedCraftableStacks = List.of();
    private static List<SearchableItem> cachedSearchableItems;

    private Map<String, Integer> cachedStock;
    private long cachedStockRevision = -1;

    private final ContainerIndex index;
    private final ModConfig config;
    private EditBox searchField;
    private EditBox amountField;
    private RowList rowList;
    private final Map<View, Button> tabs = new HashMap<>();
    private Button layoutButton;

    private String currentQuery = "";
    private View view = View.ITEMS;
    private int amount = 64;
    private int resultCount;
    private long lastSeenRevision = -1;
    private String status = "";
    private ItemResult hoveredItem;
    private final List<ActionRegion> actionRegions = new ArrayList<>();

    public CatalogScreen(ContainerIndex index, ModConfig config) {
        super(Component.translatable("screen.findmyitems.catalog"));
        this.index = index;
        this.config = config;
    }

    /**
     * List or grid, read from the config every time.
     *
     * <p>Not a field: the screen is rebuilt from scratch on every press of the open key, so anything
     * kept here is forgotten the moment you close the catalog. It is a preference about how you like
     * to read a list, which makes it config-shaped — and that also carries it across a restart.
     */
    private Layout layout() {
        return config.gridLayout ? Layout.GRID : Layout.LIST;
    }

    // ---------------------------------------------------------------- layout

    private int listWidth() {
        return Math.min(MAX_LIST_WIDTH, width - 24);
    }

    private int listLeft() {
        return (width - listWidth()) / 2;
    }

    @Override
    protected void init() {
        var left = listLeft();
        var total = listWidth();

        var tabWidth = (total - LAYOUT_BUTTON_WIDTH - GAP - 2 * GAP) / 3;
        var x = left;
        for (var candidate : View.values()) {
            var button = Button.builder(tabLabel(candidate), b -> switchTo(candidate))
                    .bounds(x, TABS_Y, tabWidth, WIDGET_HEIGHT)
                    .build();
            tabs.put(candidate, button);
            addRenderableWidget(button);
            x += tabWidth + GAP;
        }

        layoutButton = Button.builder(layoutLabel(), b -> toggleLayout())
                .bounds(left + total - LAYOUT_BUTTON_WIDTH, TABS_Y, LAYOUT_BUTTON_WIDTH, WIDGET_HEIGHT)
                .build();
        addRenderableWidget(layoutButton);

        var searchWidth = total - AMOUNT_WIDTH - GAP;
        searchField = new EditBox(font, left, SEARCH_Y, searchWidth, WIDGET_HEIGHT,
                Component.translatable("screen.findmyitems.search"));
        searchField.setMaxLength(64);
        searchField.setValue(currentQuery);
        searchField.setResponder(this::onSearchChanged);
        addRenderableWidget(searchField);
        setInitialFocus(searchField);

        amountField = new EditBox(font, left + searchWidth + GAP, SEARCH_Y, AMOUNT_WIDTH, WIDGET_HEIGHT,
                Component.translatable("screen.findmyitems.amount"));
        amountField.setMaxLength(4);
        amountField.setValue(String.valueOf(amount));
        amountField.setResponder(this::onAmountTyped);
        addRenderableWidget(amountField);

        rebuildList();
        refreshChrome();
    }

    /**
     * The grid's detail pane, which the list layout does without.
     *
     * <p>A grid cell is an icon and a number: it has room to say <em>how many</em> and nowhere to
     * say <em>where</em>. The list rows carry that on their subtitle; the grid needs somewhere to
     * put it, so the pane is permanently reserved rather than popped up over the cells — a panel
     * that appears under the cursor moves the thing you were about to click.
     */
    private boolean hasDetailPane() {
        return view == View.ITEMS && layout() == Layout.GRID;
    }

    private int gridWidth() {
        return hasDetailPane() ? listWidth() - DETAIL_WIDTH - GAP : listWidth();
    }

    private void rebuildList() {
        if (rowList != null) removeWidget(rowList);
        rowList = new RowList(minecraft, gridWidth(), height, listLeft(),
                layout() == Layout.GRID ? CELL_SIZE : ROW_HEIGHT);
        addRenderableWidget(rowList);
        updateResults();
    }

    private void refreshChrome() {
        tabs.forEach((candidate, button) -> {
            button.setMessage(tabLabel(candidate));
            button.active = candidate != view;
        });
        layoutButton.setMessage(layoutLabel());
        // A crafting plan is a tree; a grid cannot show the nesting, so the toggle is meaningless there.
        layoutButton.active = view != View.CRAFTING;
        // Amount drives how many to take (items) or how many to craft (crafting), but nothing in containers.
        amountField.visible = view != View.CONTAINERS;
        searchField.setHint(Component.translatable(switch (view) {
            case ITEMS -> "screen.findmyitems.hint.items";
            case CONTAINERS -> "screen.findmyitems.hint.containers";
            case CRAFTING -> "screen.findmyitems.hint.crafting";
        }));
    }

    private Component tabLabel(View candidate) {
        return Component.translatable(switch (candidate) {
            case ITEMS -> "screen.findmyitems.view.items";
            case CONTAINERS -> "screen.findmyitems.view.containers";
            case CRAFTING -> "screen.findmyitems.view.crafting";
        });
    }

    /** The toggle is labelled with the layout it switches to, not the one you are in. */
    private Component layoutLabel() {
        return Component.translatable(layout() == Layout.LIST
                ? "screen.findmyitems.layout.grid"
                : "screen.findmyitems.layout.list");
    }

    private void switchTo(View next) {
        if (view == next) return;
        view = next;
        refreshChrome();
        rebuildList();
        // Switching views is always followed by typing, so hand the cursor back to the search box.
        setFocused(searchField);
    }

    private void toggleLayout() {
        config.gridLayout = !config.gridLayout;
        config.save();
        refreshChrome();
        rebuildList();
        setFocused(searchField);
    }

    // ---------------------------------------------------------------- input

    private void onSearchChanged(String query) {
        currentQuery = query;
        updateResults();
    }

    private void onAmountTyped(String typed) {
        var digits = typed.replaceAll("\\D", "");
        if (!digits.equals(typed)) {
            amountField.setValue(digits);
            return;
        }
        amount = digits.isEmpty() ? 1 : Math.min(9999, Integer.parseInt(digits));
        if (view == View.CRAFTING) updateResults();
    }

    private void setAmount(int next) {
        amount = Math.max(1, Math.min(9999, next));
        amountField.setValue(String.valueOf(amount));
    }

    /**
     * Ctrl+1/2/3 (Cmd on macOS) jumps between the views. The plain digits are left alone because
     * the search box owns them — you type "64 arrows" far more often than you switch tabs.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (isCommandOrControl()) {
            var picked = switch (event.key()) {
                case GLFW.GLFW_KEY_1 -> View.ITEMS;
                case GLFW.GLFW_KEY_2 -> View.CONTAINERS;
                case GLFW.GLFW_KEY_3 -> View.CRAFTING;
                default -> null;
            };
            if (picked != null) {
                switchTo(picked);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    /**
     * Polls whether Ctrl or Cmd is physically held, rather than reading the event's modifier bits.
     *
     * <p>Both halves matter: {@code hasControlDown()} only reports the Control bit, so the macOS
     * Cmd shortcut would never fire, and modifier bits are absent entirely from synthetic key
     * events — which is how the client game test drives this. Asking the window directly is
     * correct for a real keyboard and drivable from a test.
     */
    private static boolean isCommandOrControl() {
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, InputConstants.KEY_LCONTROL)
                || InputConstants.isKeyDown(window, InputConstants.KEY_RCONTROL)
                || InputConstants.isKeyDown(window, InputConstants.KEY_LSUPER)
                || InputConstants.isKeyDown(window, InputConstants.KEY_RSUPER);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (rowList != null && rowList.isMouseOver(event.x(), event.y())) {
            for (var region : actionRegions) {
                if (!region.contains((int) event.x(), (int) event.y())) continue;
                var action = event.button() == 1 ? region.secondary() : region.primary();
                if (action != null) {
                    action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (rowList != null && rowList.isMouseOver(x, y)) {
            for (var region : actionRegions) {
                if (region.scrollsAmount() && region.contains((int) x, (int) y)) {
                    setAmount(amount + (int) scrollY);
                    if (view == View.CRAFTING) updateResults();
                    return true;
                }
            }
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        actionRegions.clear();
        // Whichever grid cell is under the cursor claims this during the list's own render below.
        hoveredItem = null;
        super.extractRenderState(graphics, mouseX, mouseY, a);

        if (hasDetailPane()) drawDetailPane(graphics);

        graphics.centeredText(font, title, width / 2, TITLE_Y, TEXT);

        if (resultCount == 0 && !status.isEmpty()) {
            graphics.centeredText(font, Component.literal(status), width / 2, height / 2 - 4, TEXT_DIM);
        }

        var footer = switch (view) {
            case ITEMS -> Component.translatable(resultCount == 1
                    ? "screen.findmyitems.footer.items.one"
                    : "screen.findmyitems.footer.items", resultCount, amount);
            case CONTAINERS -> Component.translatable("screen.findmyitems.footer.containers", resultCount);
            case CRAFTING -> currentQuery.isBlank()
                    ? Component.translatable("screen.findmyitems.footer.craft_index", resultCount)
                    : Component.translatable("screen.findmyitems.footer.crafting", amount);
        };
        graphics.centeredText(font, footer, width / 2, height - FOOTER_HEIGHT + 6, TEXT_DIM);
    }

    /**
     * Draws the grid's detail pane: every container holding the hovered item, and what it holds.
     *
     * <p>The headline count answers "have I got any"; this answers "where, and can I get at it".
     * Sources are grouped by container rather than listed one per access position, so a double
     * chest is one line — two lines of 64 for one chest of 64 would be the same lie the item
     * total used to tell.
     */
    private void drawDetailPane(GuiGraphicsExtractor graphics) {
        var left = listLeft() + gridWidth() + GAP;
        var top = LIST_Y;
        var bottom = height - FOOTER_HEIGHT;
        graphics.fill(left, top, left + DETAIL_WIDTH, bottom, LIST_BACKGROUND);
        graphics.outline(left, top, DETAIL_WIDTH, bottom - top, LIST_BORDER);

        var item = hoveredItem;
        var x = left + DETAIL_PADDING;
        var y = top + DETAIL_PADDING;
        graphics.enableScissor(left + 1, top + 1, left + DETAIL_WIDTH - 1, bottom - 1);

        if (item == null) {
            graphics.text(font, Component.translatable("screen.findmyitems.detail.hint").getString(),
                    x, y, TEXT_DIM);
            graphics.disableScissor();
            return;
        }

        graphics.text(font, item.displayName(), x, y, TEXT);
        y += DETAIL_LINE + 2;
        graphics.text(font, Component.translatable(
                "screen.findmyitems.detail.total", item.totalCount()).getString(), x, y, TEXT_DIM);
        y += DETAIL_LINE + 4;

        for (var container : containerBreakdown(item)) {
            if (y > bottom - DETAIL_LINE) break;
            var reason = unreachableReason(container.where());
            graphics.text(font, container.count() + " × " + kindLabel(container.where().kind()),
                    x, y, reason == null ? TEXT_OK : TEXT_MISSING);
            y += DETAIL_LINE;
            graphics.text(font, positionLabel(container.where()), x + INDENT, y, TEXT_DIM);
            y += DETAIL_LINE;
            if (reason != null) {
                graphics.text(font, reason.getString(), x + INDENT, y, TEXT_DISABLED);
                y += DETAIL_LINE;
            }
            y += 3;
        }
        graphics.disableScissor();
    }

    /** One line's worth of a breakdown: a container, the nearest way in, and what it holds. */
    private record ContainerShare(SourceKey where, int count) {}

    private static List<ContainerShare> containerBreakdown(ItemResult item) {
        var byContainer = new LinkedHashMap<SourceKey, SourceResult>();
        for (var source : item.sources()) {
            byContainer.merge(source.contentsKey(), source,
                    (a, b) -> distanceSqr(a.source()) <= distanceSqr(b.source()) ? a : b);
        }
        return byContainer.values().stream()
                .sorted(Comparator.comparingDouble(source -> distanceSqr(source.source())))
                .map(source -> new ContainerShare(source.source(), source.count()))
                .toList();
    }

    private static String positionLabel(SourceKey key) {
        if (key.positions().isEmpty()) {
            return Component.translatable("screen.findmyitems.anywhere").getString();
        }
        var p = key.positions().getFirst();
        return p.x() + ", " + p.y() + ", " + p.z();
    }

    /** Why this container cannot be taken from right now, or null when it can. */
    private Component unreachableReason(SourceKey key) {
        var player = Minecraft.getInstance().player;
        if (player == null) return Component.translatable("screen.findmyitems.detail.no_world");
        if (key.positions().isEmpty()) {
            return Component.translatable("screen.findmyitems.detail.remembered_ender");
        }
        if (!key.dimension().equals(player.level().dimension().identifier().toString())) {
            return Component.translatable("screen.findmyitems.detail.other_dimension");
        }
        if (!inReach(key)) {
            return Component.translatable("screen.findmyitems.detail.too_far", (int) Math.sqrt(distanceSqr(key)));
        }
        return null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Re-reads the index when it has actually changed.
     *
     * <p>The background rescan updates the index on schedule, but nothing used to tell an open
     * catalog about it — results only refreshed when you retyped the search or reopened the screen,
     * which made a 1-second rescan interval look like it was doing nothing. Guarded on the revision
     * so a quiet tick costs one long compare rather than a full re-search.
     */
    @Override
    public void tick() {
        super.tick();
        if (index.revision() == lastSeenRevision) return;
        lastSeenRevision = index.revision();
        updateResults();
    }

    // ---------------------------------------------------------------- data

    void updateResults() {
        status = "";
        var rows = switch (view) {
            case ITEMS -> itemRows();
            case CONTAINERS -> containerRows();
            case CRAFTING -> craftingRows();
        };
        rowList.setRows(rows);
    }

    private List<Row> itemRows() {
        var results = index.search(currentQuery);
        resultCount = results.size();
        if (results.isEmpty()) {
            status = currentQuery.isEmpty()
                    ? Component.translatable("screen.findmyitems.empty").getString()
                    : Component.translatable("screen.findmyitems.no_results", currentQuery).getString();
            return List.of();
        }
        return layout() == Layout.LIST
                ? results.stream().<Row>map(ItemRow::new).toList()
                : chunk(results, ItemGridRow::new);
    }

    private List<Row> containerRows() {
        var known = index.snapshot().containers();
        var cards = new ArrayList<ContainerCard>();

        var hasEnder = known.stream().anyMatch(c -> c.contentsKey().equals(SourceKey.enderInventory()));
        // The ender chest is always reachable in spirit, so it heads the list even when unseen.
        if (!hasEnder && currentQuery.isBlank()) {
            cards.add(ContainerCard.emptyEnder());
        }
        for (var container : known) {
            var card = ContainerCard.of(container);
            if (card.matches(currentQuery)) cards.add(card);
        }

        cards.sort(Comparator.comparing((ContainerCard c) -> !c.isEnder())
                .thenComparingDouble(ContainerCard::distanceSqr));

        resultCount = cards.size();
        if (cards.isEmpty()) {
            status = Component.translatable("screen.findmyitems.no_containers").getString();
            return List.of();
        }
        return layout() == Layout.LIST
                ? cards.stream().<Row>map(ContainerRow::new).toList()
                : chunk(cards, ContainerGridRow::new);
    }

    private List<Row> craftingRows() {
        resultCount = 0;
        if (currentQuery.isBlank()) {
            return allItemRows();
        }

        var mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        var level = mc.level;
        if (server == null || level == null) {
            status = Component.translatable("screen.findmyitems.craft.singleplayer_only").getString();
            return List.of();
        }

        var target = resolveItem(currentQuery);
        if (target == null) {
            status = Component.translatable("screen.findmyitems.craft.unknown_item", currentQuery).getString();
            return List.of();
        }

        var plan = CraftingPlanner.plan(server.getRecipeManager(), level, target, amount, stock());
        var rows = new ArrayList<Row>();
        flatten(plan, 0, rows);
        resultCount = rows.size();
        return rows;
    }

    /** Every craftable item, alphabetical, as a menu to pick a craft target from. */
    private List<Row> allItemRows() {
        var mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        var level = mc.level;
        if (server == null || level == null) {
            status = Component.translatable("screen.findmyitems.craft.singleplayer_only").getString();
            return List.of();
        }

        var rows = craftableStacks(server.getRecipeManager(), level).stream()
                .<Row>map(ItemChoiceRow::new)
                .toList();
        resultCount = rows.size();
        return rows;
    }

    /**
     * The craftable items, sorted by display name, cached until the recipes change.
     *
     * <p>Sorting a thousand items means a thousand translation lookups, and this list is rebuilt
     * every time the crafting view refreshes — which is every keystroke. The planner hands back the
     * same set instance until a datapack reload replaces it, so that instance is the cache key.
     */
    private static List<ItemStack> craftableStacks(RecipeManager recipes, Level level) {
        var craftable = CraftingPlanner.craftable(recipes, level);
        if (craftable != cachedCraftableSource) {
            cachedCraftableStacks = craftable.stream()
                    .map(ItemStack::new)
                    .sorted(Comparator.comparing(stack -> stack.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
            cachedCraftableSource = craftable;
        }
        return cachedCraftableStacks;
    }

    private void flatten(CraftingPlanner.Material material, int depth, List<Row> out) {
        out.add(new MaterialRow(material, depth));
        for (var child : material.children()) {
            flatten(child, depth + 1, out);
        }
    }

    /**
     * How many of each item the index knows about, keyed by item id.
     *
     * <p>Cached against the index revision: the crafting view asks for this on every keystroke, and
     * a full {@code search("")} walks every slot of every container to answer it.
     */
    private Map<String, Integer> stock() {
        if (cachedStock != null && cachedStockRevision == index.revision()) {
            return cachedStock;
        }
        var counts = new HashMap<String, Integer>();
        for (var result : index.search("")) {
            counts.merge(result.key().itemId(), result.totalCount(), Integer::sum);
        }
        cachedStock = counts;
        cachedStockRevision = index.revision();
        return counts;
    }

    /** Best item for a typed query: exact id first, then id prefix, then display name. */
    private static Item resolveItem(String query) {
        var needle = query.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return null;

        var exact = BuiltInRegistries.ITEM.get(Identifier.tryParse(needle.contains(":") ? needle : "minecraft:" + needle));
        if (exact != null && exact.isPresent()) return exact.get().value();

        Item byPath = null;
        Item byName = null;
        for (var candidate : searchableItems()) {
            if (byPath == null && candidate.path().contains(needle.replace('_', ' '))) byPath = candidate.item();
            if (byName == null && candidate.name().contains(needle)) byName = candidate.item();
            if (byPath != null && byName != null) break;
        }
        return byName != null ? byName : byPath;
    }

    /**
     * Item ids and display names, lowercased once.
     *
     * <p>Built on first use and kept: this used to allocate an {@link ItemStack} and run a
     * translation lookup for all ~1300 items on every keystroke.
     */
    private static List<SearchableItem> searchableItems() {
        if (cachedSearchableItems == null) {
            cachedSearchableItems = BuiltInRegistries.ITEM.stream()
                    .map(item -> new SearchableItem(
                            item,
                            BuiltInRegistries.ITEM.getKey(item).getPath().replace('_', ' '),
                            new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return cachedSearchableItems;
    }

    private record SearchableItem(Item item, String path, String name) {}

    private <T> List<Row> chunk(List<T> values, java.util.function.Function<List<T>, Row> factory) {
        var columns = Math.max(1, (rowList.getRowWidth() - GAP) / CELL_SIZE);
        var rows = new ArrayList<Row>();
        for (int i = 0; i < values.size(); i += columns) {
            rows.add(factory.apply(values.subList(i, Math.min(values.size(), i + columns))));
        }
        return rows;
    }

    // ---------------------------------------------------------------- actions

    private void takeItem(ItemResult item) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;

        var server = mc.getSingleplayerServer();
        if (server == null) return;

        var source = nearestReachableSource(item);
        if (source == null) return;

        // The grid reaches this without going past the list row's disabled button, so the guard
        // lives here where every caller passes. A retrieval into a full inventory would open the
        // chest, move nothing and say nothing — indistinguishable from success until you go to build.
        if (RetrieveHandler.roomFor(player, buildStack(item.key())) == 0) {
            player.sendOverlayMessage(Component.translatable("message.findmyitems.inventory_full"));
            return;
        }

        var pos = source.source().positions().getFirst();
        var mcPos = new BlockPos(pos.x(), pos.y(), pos.z());
        var dim = source.source().dimension();
        var itemId = item.key().itemId();
        var componentsJson = item.key().componentsJson();
        var requested = amount;
        var reach = config.retrieveDistanceBlocks;

        GhostOpen.openThen(mcPos, () -> server.execute(() -> {
            var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
            if (serverPlayer == null) return;

            var worldKey = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dim));
            var world = server.getLevel(worldKey);
            if (world == null) return;

            var success = RetrieveHandler.retrieve(
                    serverPlayer, mcPos, dim, itemId, componentsJson, requested, reach);
            if (!success) return;

            var be = world.getBlockEntity(mcPos);
            if (be instanceof Container container && !be.isRemoved()) {
                var slots = SlotReader.readContainerSlots(container, serverPlayer);
                var contentsKey = SourceKey.storage(dim, source.source().kind(), source.source().positions());
                var observation = new ContainerObservation(contentsKey, List.of(source.source()), slots, Instant.now());

                Minecraft.getInstance().execute(() -> {
                    index.observe(observation);
                    updateResults();
                });
            }
        }));
    }

    /** Glows a container in the world. The glow is the whole feedback — no toast on top of it. */
    private static void locate(SourceKey key) {
        if (key.positions().isEmpty()) return;

        var positions = key.positions().stream()
                .map(p -> new BlockPos(p.x(), p.y(), p.z()))
                .toList();
        ChestHighlighter.highlight(positions, key.dimension());
    }

    /**
     * Pushes matching items from the inventory back into the container they came from. Only
     * offered when that container already stocks this exact item — see
     * {@link RetrieveHandler#deposit}.
     */
    private void depositItem(ItemResult item) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        var server = mc.getSingleplayerServer();
        if (player == null || server == null) return;

        var source = nearestReachableSource(item);
        if (source == null) return;

        var pos = source.source().positions().getFirst();
        var mcPos = new BlockPos(pos.x(), pos.y(), pos.z());
        var dim = source.source().dimension();
        var itemId = item.key().itemId();
        var componentsJson = item.key().componentsJson();
        var requested = amount;
        var reach = config.retrieveDistanceBlocks;

        GhostOpen.openThen(mcPos, () -> server.execute(() -> {
            var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
            if (serverPlayer == null) return;

            var worldKey = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dim));
            var world = server.getLevel(worldKey);
            if (world == null) return;

            var moved = RetrieveHandler.deposit(serverPlayer, mcPos, itemId, componentsJson, requested, reach);
            if (moved == 0) return;

            var be = world.getBlockEntity(mcPos);
            if (be instanceof Container container && !be.isRemoved()) {
                var slots = SlotReader.readContainerSlots(container, serverPlayer);
                var contentsKey = SourceKey.storage(dim, source.source().kind(), source.source().positions());
                var observation = new ContainerObservation(contentsKey, List.of(source.source()), slots, Instant.now());

                Minecraft.getInstance().execute(() -> {
                    index.observe(observation);
                    updateResults();
                });
            }
        }));
    }

    /**
     * What clicking Take would actually move, and why it differs from what was asked for.
     *
     * <p>Three numbers meet here: the amount in the box, what the nearest reachable container holds,
     * and how much of it the inventory can still accept. The button has to promise the smallest of
     * them — a tooltip reading "Take 55" over a chest with four chickens in it is a lie the click
     * then exposes — and has to go dead entirely when the answer is zero, because a retrieval that
     * moves nothing still swings the chest lid and still leaves the catalog up, which reads as
     * success to everyone who has ever used it.
     */
    private enum Limit { NONE, ROOM, STOCK, UNREACHABLE }

    private record TakePlan(int count, Limit limit) {}

    private TakePlan planTake(ItemResult item, SourceResult nearest, ItemStack stack) {
        var player = Minecraft.getInstance().player;
        var available = nearest == null ? 0 : nearest.count();
        var room = player == null ? 0 : RetrieveHandler.roomFor(player, stack);
        var count = Math.min(amount, Math.min(available, room));
        if (count >= amount) return new TakePlan(count, Limit.NONE);
        if (room <= available) return new TakePlan(count, Limit.ROOM);
        return new TakePlan(count, unreachableCount(item) > 0 ? Limit.UNREACHABLE : Limit.STOCK);
    }

    /** Stock the row counts but no position can be walked to — a remembered ender inventory. */
    private static int unreachableCount(ItemResult item) {
        return item.sources().stream()
                .filter(source -> source.source().positions().isEmpty())
                .mapToInt(source -> source.count())
                .sum();
    }

    /** How many of this exact item the player is carrying — the cap on what deposit can move. */
    private static int carried(ItemResult item) {
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;

        var inventory = player.getInventory();
        var held = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(item.key().itemId())) continue;
            if (!SlotReader.serializeComponents(stack.getComponentsPatch(), SlotReader.registriesOf(player))
                    .equals(item.key().componentsJson())) continue;
            held += stack.getCount();
        }
        return held;
    }

    private void locateItem(ItemResult item) {
        var source = nearestSource(item);
        if (source != null) locate(source.source());
    }

    /** Finds an indexed item by id so a crafting-tree node can point at a real chest. */
    private ItemResult lookup(String itemId) {
        return index.search(itemId).stream()
                .filter(result -> result.key().itemId().equals(itemId))
                .findFirst()
                .orElse(null);
    }

    // ---------------------------------------------------------------- geometry helpers

    /** Closest container holding the item in the player's current dimension, at any distance. */
    private static SourceResult nearestSource(ItemResult item) {
        var player = Minecraft.getInstance().player;
        if (player == null) return null;

        var dimension = player.level().dimension().identifier().toString();
        return item.sources().stream()
                .filter(s -> s.source().dimension().equals(dimension))
                .filter(s -> !s.source().positions().isEmpty())
                .min(Comparator.comparingDouble(s -> distanceSqr(s.source())))
                .orElse(null);
    }

    private SourceResult nearestReachableSource(ItemResult item) {
        var nearest = nearestSource(item);
        return nearest != null && inReach(nearest.source()) ? nearest : null;
    }

    /** Defers to the same reach rule the server enforces, rather than re-deriving a radius here. */
    private boolean inReach(SourceKey source) {
        var player = Minecraft.getInstance().player;
        if (player == null || source.positions().isEmpty()) return false;
        var p = source.positions().getFirst();
        return RetrieveHandler.inReach(player, new BlockPos(p.x(), p.y(), p.z()), config.retrieveDistanceBlocks);
    }

    private static double distanceSqr(SourceKey source) {
        var player = Minecraft.getInstance().player;
        if (player == null || source.positions().isEmpty()) return Double.MAX_VALUE;
        var p = source.positions().getFirst();
        var playerPos = player.position();
        var dx = (p.x() + 0.5) - playerPos.x();
        var dy = (p.y() + 0.5) - playerPos.y();
        var dz = (p.z() + 0.5) - playerPos.z();
        return dx * dx + dy * dy + dz * dz;
    }

    static Item containerItem(ContainerKind kind) {
        return switch (kind) {
            case CHEST -> Items.CHEST;
            case TRAPPED_CHEST -> Items.TRAPPED_CHEST;
            case BARREL -> Items.BARREL;
            case SHULKER_BOX -> Items.SHULKER_BOX;
            case ENDER_CHEST -> Items.ENDER_CHEST;
        };
    }

    private static String sourceLabel(SourceResult source) {
        return kindLabel(source.source().kind());
    }

    /** Uses the item's own name so a resource pack or language pack renames it too. */
    static String kindLabel(ContainerKind kind) {
        return new ItemStack(containerItem(kind)).getHoverName().getString();
    }

    /** Stack-size style label: 4 chars max, so it fits in the item slot corner. */
    static String compactCount(int count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 100_000) return (count / 1000) + "k";
        return "99k+";
    }

    static ItemStack buildStack(StackKey key) {
        var id = Identifier.parse(key.itemId());
        var itemHolder = BuiltInRegistries.ITEM.get(id);
        if (itemHolder.isEmpty()) return ItemStack.EMPTY;

        var stack = new ItemStack(itemHolder.get());
        if (!key.componentsJson().equals("{}")) {
            try {
                var json = JsonParser.parseString(key.componentsJson());
                var level = Minecraft.getInstance().level;
                var ops = level == null
                        ? JsonOps.INSTANCE
                        : level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
                var pair = DataComponentPatch.CODEC.decode(ops, json).getOrThrow();
                stack.applyComponents(pair.getFirst());
            } catch (Exception ignored) {
            }
        }
        return stack;
    }

    // ---------------------------------------------------------------- container cards

    /** One row's worth of facts about a remembered container. */
    private record ContainerCard(SourceKey key, ContainerKind kind, int itemCount, String contents, double distanceSqr) {
        static ContainerCard of(IndexedContainer container) {
            var count = container.slots().stream().mapToInt(s -> s.stack().count()).sum();
            var names = container.slots().stream()
                    .limit(8)
                    .map(s -> s.stack().displayName())
                    .distinct()
                    .toList();
            return new ContainerCard(container.contentsKey(), container.contentsKey().kind(), count,
                    String.join(", ", names), CatalogScreen.distanceSqr(container.contentsKey()));
        }

        static ContainerCard emptyEnder() {
            return new ContainerCard(SourceKey.enderInventory(), ContainerKind.ENDER_CHEST, 0, "", -1);
        }

        boolean isEnder() {
            return kind == ContainerKind.ENDER_CHEST;
        }

        boolean matches(String query) {
            var needle = query.strip().toLowerCase(Locale.ROOT);
            if (needle.isEmpty()) return true;
            return (kindLabel(kind) + " " + position() + " " + key.dimension() + " " + contents)
                    .toLowerCase(Locale.ROOT).contains(needle);
        }

        String position() {
            if (key.positions().isEmpty()) return Component.translatable("screen.findmyitems.anywhere").getString();
            var p = key.positions().getFirst();
            return "%d, %d, %d".formatted(p.x(), p.y(), p.z());
        }

        ItemStack icon() {
            return new ItemStack(containerItem(kind));
        }
    }

    // ---------------------------------------------------------------- widgets

    private final class RowList extends ObjectSelectionList<Row> {
        private final int listWidth;

        RowList(Minecraft minecraft, int listWidth, int screenHeight, int listX, int rowHeight) {
            super(minecraft, listWidth, screenHeight - LIST_Y - FOOTER_HEIGHT, LIST_Y, rowHeight);
            this.listWidth = listWidth;
            setX(listX);
        }

        void setRows(List<Row> rows) {
            clearEntries();
            rows.forEach(this::addEntry);
            setScrollAmount(0);
        }

        @Override
        public int getRowWidth() {
            return listWidth - 8;
        }

        @Override
        protected int scrollBarX() {
            return getRight() - 6;
        }

        // Vanilla only paints a list background outside a world, so paint our own panel first.
        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(getX(), getY(), getRight(), getBottom(), LIST_BACKGROUND);
            graphics.outline(getX(), getY(), getWidth(), getHeight(), LIST_BORDER);
            super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
        }
    }

    private abstract class Row extends ObjectSelectionList.Entry<Row> {
        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            return false;
        }

        /** Draws button chrome and reports whether the cursor is over it. */
        boolean actionButton(GuiGraphicsExtractor graphics, int x, int y, Item icon,
                             int mouseX, int mouseY, boolean enabled, Component tooltip) {
            var over = mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE;
            var background = !enabled ? BUTTON_DISABLED : over ? BUTTON_HOVER : BUTTON;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, background, x, y, BUTTON_SIZE, BUTTON_SIZE);
            // Drawn as an item, not a sprite, so resource packs restyle these buttons too.
            graphics.item(new ItemStack(icon),
                    x + (BUTTON_SIZE - ICON_SIZE) / 2, y + (BUTTON_SIZE - ICON_SIZE) / 2);
            if (over) {
                graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
            }
            return over;
        }

        void slot(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, String count) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x, y, SLOT_SIZE, SLOT_SIZE);
            graphics.item(stack, x + 1, y + 1);
            if (count != null) {
                graphics.itemDecorations(font, stack, x + 1, y + 1, count);
            }
        }
    }

    /** Items view, one item per row: icon, name, where it lives, locate and take. */
    private final class ItemRow extends Row {
        private final ItemResult item;
        private final ItemStack stack;

        ItemRow(ItemResult item) {
            this.item = item;
            this.stack = buildStack(item.key());
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            var top = getY();
            var left = getContentX();
            var right = getContentRight();
            var middle = top + ROW_HEIGHT / 2;

            slot(graphics, stack, left, middle - SLOT_SIZE / 2, compactCount(item.totalCount()));

            var nearest = nearestSource(item);
            var reachable = nearest != null && inReach(nearest.source());
            var outOfReach = Component.translatable("screen.findmyitems.tooltip.out_of_reach");
            var held = carried(item);

            var buttonY = middle - BUTTON_SIZE / 2;
            var takeX = right - BUTTON_SIZE;
            var depositX = takeX - GAP - BUTTON_SIZE;
            var locateX = depositX - GAP - BUTTON_SIZE;

            var textLeft = left + SLOT_SIZE + 8;
            graphics.enableScissor(textLeft, top, locateX - GAP, top + ROW_HEIGHT);
            graphics.text(font, item.displayName(), textLeft, top + 5, TEXT);
            graphics.text(font, subtitle(nearest), textLeft, top + 16, TEXT_DIM);
            graphics.disableScissor();

            actionButton(graphics, locateX, buttonY, Items.ENDER_EYE, mouseX, mouseY, nearest != null, nearest != null
                    ? Component.translatable("screen.findmyitems.locate", sourceLabel(nearest))
                    : Component.translatable("screen.findmyitems.tooltip.nowhere"));
            actionRegions.add(ActionRegion.click(locateX, buttonY, () -> locateItem(item)));

            // Deposit is offered only where the chest already stocks this exact item, so the
            // button is dead unless you are carrying some of something that lives there.
            var canDeposit = reachable && held > 0;
            actionButton(graphics, depositX, buttonY, Items.CHEST, mouseX, mouseY, canDeposit,
                    !reachable ? outOfReach
                            : held == 0 ? Component.translatable("screen.findmyitems.deposit.none")
                            : Component.translatable("screen.findmyitems.deposit", Math.min(amount, held)));
            if (canDeposit) {
                actionRegions.add(ActionRegion.take(depositX, buttonY, () -> depositItem(item)));
            }

            var plan = planTake(item, nearest, stack);
            var canTake = reachable && plan.count() > 0;
            actionButton(graphics, takeX, buttonY, Items.HOPPER, mouseX, mouseY, canTake,
                    !reachable ? outOfReach : takeTooltip(plan));
            if (canTake) {
                actionRegions.add(ActionRegion.take(takeX, buttonY, () -> takeItem(item)));
            }
        }

        /** Names the reason the button promises less than the amount box asks for. */
        private Component takeTooltip(TakePlan plan) {
            return switch (plan.limit()) {
                case NONE -> Component.translatable("screen.findmyitems.take", plan.count());
                case ROOM -> plan.count() == 0
                        ? Component.translatable("screen.findmyitems.take.full")
                        : Component.translatable("screen.findmyitems.take.max.room", plan.count());
                case STOCK -> Component.translatable("screen.findmyitems.take.max.stock", plan.count());
                case UNREACHABLE -> Component.translatable(
                        "screen.findmyitems.take.max.unreachable", plan.count(), unreachableCount(item));
            };
        }

        private String subtitle(SourceResult nearest) {
            // Distinct containers, not access positions: a double chest is one chest, not two.
            var containers = (int) item.sources().stream().map(SourceResult::contentsKey).distinct().count();
            var where = Component.translatable(containers == 1
                    ? "screen.findmyitems.in_container"
                    : "screen.findmyitems.in_containers", containers).getString();
            if (nearest != null) {
                var pos = nearest.source().positions().getFirst();
                where += " · " + pos.x() + ", " + pos.y() + ", " + pos.z();
            }
            var unreachable = unreachableCount(item);
            if (unreachable > 0) {
                where += " · " + Component.translatable(
                        "screen.findmyitems.unreachable", unreachable).getString();
            }
            return where;
        }

        @Override
        public Component getNarration() {
            return Component.literal(item.displayName() + ", " + item.totalCount());
        }
    }

    /** Items view, grid layout: a strip of item slots. Left-click takes, right-click locates. */
    private final class ItemGridRow extends Row {
        private final List<ItemResult> items;

        ItemGridRow(List<ItemResult> items) {
            this.items = items;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            var x = getContentX();
            var y = getY() + (CELL_SIZE - SLOT_SIZE) / 2;

            for (var item : items) {
                var stack = buildStack(item.key());
                slot(graphics, stack, x, y, compactCount(item.totalCount()));

                if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                    graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, CELL_HOVER);
                    graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
                    hoveredItem = item;
                }
                actionRegions.add(ActionRegion.grid(x, y, SLOT_SIZE,
                        () -> takeItem(item), () -> locateItem(item)));
                x += CELL_SIZE;
            }
        }

        @Override
        public Component getNarration() {
            return Component.translatable("screen.findmyitems.view.items");
        }
    }

    /** Containers view, one container per row. */
    private final class ContainerRow extends Row {
        private final ContainerCard card;

        ContainerRow(ContainerCard card) {
            this.card = card;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            var top = getY();
            var left = getContentX();
            var right = getContentRight();
            var middle = top + ROW_HEIGHT / 2;

            slot(graphics, card.icon(), left, middle - SLOT_SIZE / 2,
                    card.itemCount() > 0 ? compactCount(card.itemCount()) : null);

            var buttonY = middle - BUTTON_SIZE / 2;
            var locateX = right - BUTTON_SIZE;
            var locatable = !card.key().positions().isEmpty();

            var textLeft = left + SLOT_SIZE + 8;
            graphics.enableScissor(textLeft, top, locateX - GAP, top + ROW_HEIGHT);
            graphics.text(font, kindLabel(card.kind()) + "  " + card.position(), textLeft, top + 5, TEXT);
            graphics.text(font, summary(), textLeft, top + 16, TEXT_DIM);
            graphics.disableScissor();

            actionButton(graphics, locateX, buttonY, Items.ENDER_EYE, mouseX, mouseY, locatable, locatable
                    ? Component.translatable("screen.findmyitems.locate", kindLabel(card.kind()))
                    : Component.translatable("screen.findmyitems.tooltip.nowhere"));
            actionRegions.add(ActionRegion.click(locateX, buttonY, () -> locate(card.key())));
        }

        private String summary() {
            if (card.itemCount() == 0) {
                return Component.translatable("screen.findmyitems.container.empty").getString();
            }
            return Component.translatable("screen.findmyitems.container.holds", card.itemCount()).getString()
                    + (card.contents().isEmpty() ? "" : " · " + card.contents());
        }

        @Override
        public Component getNarration() {
            return Component.literal(kindLabel(card.kind()) + " " + card.position());
        }
    }

    /** Containers view, grid layout: container icons, click to locate. */
    private final class ContainerGridRow extends Row {
        private final List<ContainerCard> cards;

        ContainerGridRow(List<ContainerCard> cards) {
            this.cards = cards;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            var x = getContentX();
            var y = getY() + (CELL_SIZE - SLOT_SIZE) / 2;

            for (var card : cards) {
                slot(graphics, card.icon(), x, y, card.itemCount() > 0 ? compactCount(card.itemCount()) : null);

                if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                    graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, CELL_HOVER);
                    graphics.setComponentTooltipForNextFrame(font, List.of(
                            Component.literal(kindLabel(card.kind())),
                            Component.literal(card.position()),
                            Component.translatable("screen.findmyitems.container.holds", card.itemCount())
                    ), mouseX, mouseY);
                }
                actionRegions.add(ActionRegion.grid(x, y, SLOT_SIZE, () -> locate(card.key()), null));
                x += CELL_SIZE;
            }
        }

        @Override
        public Component getNarration() {
            return Component.translatable("screen.findmyitems.view.containers");
        }
    }

    /** Crafting view with an empty box: pick an item to plan. Clicking one types it into the search. */
    private final class ItemChoiceRow extends Row {
        private final ItemStack stack;

        ItemChoiceRow(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            var top = getY();
            var left = getContentX();
            var right = getContentRight();
            var middle = top + ROW_HEIGHT / 2;

            if (mouseX >= left && mouseX < right && mouseY >= top && mouseY < top + ROW_HEIGHT) {
                graphics.fill(left, top, right, top + ROW_HEIGHT, CELL_HOVER);
            }

            slot(graphics, stack, left, middle - SLOT_SIZE / 2, null);
            graphics.text(font, stack.getHoverName().getString(), left + SLOT_SIZE + 8, middle - 4, TEXT);

            actionRegions.add(ActionRegion.row(left, top, right, top + ROW_HEIGHT, this::plan));
        }

        /** Typing the id rather than the display name so {@code resolveItem} lands on this exact item. */
        private void plan() {
            searchField.setValue(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
            CatalogScreen.this.setFocused(searchField);
        }

        @Override
        public Component getNarration() {
            return stack.getHoverName();
        }
    }

    /** Crafting view: one node of the material tree, indented by depth. */
    private final class MaterialRow extends Row {
        private final CraftingPlanner.Material material;
        private final int depth;

        MaterialRow(CraftingPlanner.Material material, int depth) {
            this.material = material;
            this.depth = depth;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            var top = getY();
            var left = getContentX() + depth * INDENT;
            var right = getContentRight();
            var middle = top + ROW_HEIGHT / 2;
            var stack = material.stack();

            slot(graphics, stack, left, middle - SLOT_SIZE / 2, compactCount(material.needed()));

            var buttonY = middle - BUTTON_SIZE / 2;
            var locateX = right - BUTTON_SIZE;
            var found = material.available() > 0 ? lookup(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()) : null;

            var textLeft = left + SLOT_SIZE + 8;
            graphics.enableScissor(textLeft, top, locateX - GAP, top + ROW_HEIGHT);
            graphics.text(font, stack.getHoverName().getString(), textLeft, top + 5, TEXT);
            graphics.text(font, status(), textLeft, top + 16, statusColor());
            graphics.disableScissor();

            if (mouseX >= left && mouseX < left + SLOT_SIZE && mouseY >= middle - SLOT_SIZE / 2 && mouseY < middle + SLOT_SIZE / 2) {
                graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
            }

            actionButton(graphics, locateX, buttonY, Items.ENDER_EYE, mouseX, mouseY, found != null, found != null
                    ? Component.translatable("screen.findmyitems.locate", stack.getHoverName().getString())
                    : Component.translatable("screen.findmyitems.tooltip.nowhere"));
            if (found != null) {
                actionRegions.add(ActionRegion.click(locateX, buttonY, () -> locateItem(found)));
            }
        }

        private String status() {
            if (material.satisfied()) {
                return Component.translatable("screen.findmyitems.craft.have", material.available()).getString();
            }
            var key = material.isDeadEnd()
                    ? "screen.findmyitems.craft.short"
                    : "screen.findmyitems.craft.craft";
            return Component.translatable(key, material.missing(), material.available()).getString();
        }

        private int statusColor() {
            if (material.satisfied()) return TEXT_OK;
            return material.isDeadEnd() ? TEXT_MISSING : TEXT_DIM;
        }

        @Override
        public Component getNarration() {
            return Component.literal(material.stack().getHoverName().getString() + " " + status());
        }
    }

    /**
     * A clickable box collected while rendering. Rows are drawn before clicks are dispatched, so
     * this is how a list entry publishes where its buttons ended up.
     */
    private record ActionRegion(int left, int top, int right, int bottom,
                                Runnable primary, Runnable secondary, boolean scrollsAmount) {
        static ActionRegion click(int x, int y, Runnable action) {
            return new ActionRegion(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, action, null, false);
        }

        static ActionRegion take(int x, int y, Runnable action) {
            return new ActionRegion(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, action, null, true);
        }

        static ActionRegion grid(int x, int y, int size, Runnable primary, Runnable secondary) {
            return new ActionRegion(x, y, x + size, y + size, primary, secondary, false);
        }

        static ActionRegion row(int left, int top, int right, int bottom, Runnable action) {
            return new ActionRegion(left, top, right, bottom, action, null, false);
        }

        boolean contains(int mx, int my) {
            return mx >= left && mx < right && my >= top && my < bottom;
        }
    }
}
