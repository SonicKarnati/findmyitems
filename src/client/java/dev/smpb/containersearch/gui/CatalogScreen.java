package dev.smpb.containersearch.gui;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.smpb.containersearch.index.ContainerIndex;
import dev.smpb.containersearch.index.ItemResult;
import dev.smpb.containersearch.index.SourceResult;
import dev.smpb.containersearch.model.StackKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class CatalogScreen extends Screen {
    static final int SEARCH_HEIGHT = 20;
    static final int SEARCH_Y = 20;
    static final int LIST_Y = SEARCH_Y + SEARCH_HEIGHT + 8;
    static final int ITEM_HEIGHT = 24;
    static final int RIGHT_PANEL_X_OFFSET = 16;

    private final ContainerIndex index;
    private EditBox searchField;
    private ItemListWidget itemList;
    private ItemResult selectedItem;
    private String currentQuery = "";

    public CatalogScreen(ContainerIndex index) {
        super(Component.translatable("screen.container-search.catalog"));
        this.index = index;
    }

    @Override
    protected void init() {
        var searchWidth = Math.min(400, width - 40);
        searchField = new EditBox(font, width / 2 - searchWidth / 2, SEARCH_Y, searchWidth, SEARCH_HEIGHT,
                Component.translatable("screen.container-search.search"));
        searchField.setMaxLength(64);
        searchField.setHint(Component.translatable("screen.container-search.search_hint"));
        searchField.setResponder(this::onSearchChanged);
        addRenderableWidget(searchField);
        setInitialFocus(searchField);

        rebuildList();
    }

    private void rebuildList() {
        if (itemList != null) removeWidget(itemList);
        itemList = new ItemListWidget(this, minecraft, width, height);
        addRenderableWidget(itemList);
        updateResults();
    }

    private void onSearchChanged(String query) {
        currentQuery = query;
        updateResults();
    }

    void updateResults() {
        var results = currentQuery.isEmpty() ? index.search("") : index.search(currentQuery);
        itemList.updateResults(results);
    }

    void selectItem(ItemResult item) {
        selectedItem = item;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);

        if (selectedItem != null && itemList != null) {
            renderSourcePanel(graphics, mouseX, mouseY);
        }
    }

    private void renderSourcePanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        var left = itemList.getRight();
        if (left < 0) return;

        var x = left + RIGHT_PANEL_X_OFFSET;
        var panelWidth = width - x - 8;
        if (panelWidth < 100) return;

        var y = LIST_Y;

        var header = Component.translatable("screen.container-search.sources_header", selectedItem.displayName());
        graphics.text(font, header, x, y, 0xFFFFFF);
        y += 14;

        for (var source : selectedItem.sources()) {
            var reachable = isReachable(source);
            var color = reachable ? 0x55FF55 : 0x555555;

            graphics.text(font, sourceLabel(source), x, y, color);
            y += 10;

            graphics.text(font, positionText(source), x + 4, y, color);
            y += 10;

            graphics.text(font, formatTimeAgo(source.observedAt()), x + 4, y, color);
            y += 12;
        }
    }

    private static boolean isReachable(SourceResult source) {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;

        var dimension = player.level().dimension().identifier().toString();
        if (!source.source().dimension().equals(dimension)) return false;

        var pos = source.source().positions().getFirst();
        var playerPos = player.position();
        var dx = (pos.x() + 0.5) - playerPos.x();
        var dy = (pos.y() + 0.5) - playerPos.y();
        var dz = (pos.z() + 0.5) - playerPos.z();
        var distSqr = dx * dx + dy * dy + dz * dz;
        return distSqr <= 5.0 * 5.0;
    }

    private static String sourceLabel(SourceResult source) {
        return switch (source.source().kind()) {
            case CHEST -> "Chest";
            case TRAPPED_CHEST -> "Trapped Chest";
            case BARREL -> "Barrel";
            case SHULKER_BOX -> "Shulker Box";
            case ENDER_CHEST -> "Ender Chest";
        };
    }

    private static String positionText(SourceResult source) {
        var pos = source.source().positions().getFirst();
        return "(%d, %d, %d) %s".formatted(pos.x(), pos.y(), pos.z(), source.source().dimension());
    }

    private static String formatTimeAgo(Instant observedAt) {
        var ago = Duration.between(observedAt, Instant.now());
        if (ago.isNegative()) return "just now";
        if (ago.toMinutes() < 1) return "just now";
        if (ago.toHours() < 1) return ago.toMinutes() + "m ago";
        if (ago.toDays() < 1) return ago.toHours() + "h ago";
        return ago.toDays() + "d ago";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    static ItemStack buildStack(StackKey key) {
        var id = Identifier.parse(key.itemId());
        var itemHolder = BuiltInRegistries.ITEM.get(id);
        if (itemHolder.isEmpty()) return ItemStack.EMPTY;

        var stack = new ItemStack(itemHolder.get());
        if (!key.componentsJson().equals("{}")) {
            try {
                var json = JsonParser.parseString(key.componentsJson());
                var pair = DataComponentPatch.CODEC
                        .decode(JsonOps.INSTANCE, json)
                        .getOrThrow();
                stack.applyComponents(pair.getFirst());
            } catch (Exception ignored) {
            }
        }
        return stack;
    }

    private static final class ItemListWidget extends ObjectSelectionList<ItemListWidget.Entry> {
        private final CatalogScreen screen;

        ItemListWidget(CatalogScreen screen, Minecraft minecraft, int screenWidth, int screenHeight) {
            super(minecraft, Math.min(300, screenWidth * 2 / 5), screenHeight - LIST_Y - 8, LIST_Y, ITEM_HEIGHT);
            this.screen = screen;
            setX(8);
        }

        void updateResults(List<ItemResult> results) {
            clearEntries();
            for (var item : results) {
                addEntry(new Entry(this, screen, item));
            }
            if (children().isEmpty()) {
                screen.selectItem(null);
            }
        }

        @Override
        public int getRowWidth() {
            return width - 16;
        }

        @Override
        protected int scrollBarX() {
            return getRight() - 6;
        }

        private final class Entry extends ObjectSelectionList.Entry<Entry> {
            private final ItemListWidget list;
            private final CatalogScreen screen;
            private final ItemResult item;
            private final ItemStack displayStack;

            Entry(ItemListWidget list, CatalogScreen screen, ItemResult item) {
                this.list = list;
                this.screen = screen;
                this.item = item;
                this.displayStack = buildStack(item.key());
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                var y = getY();
                var x = getX();

                if (list.getSelected() == this) {
                    graphics.fill(x, y, getRight(), y + ITEM_HEIGHT, 0x33FFFFFF);
                }

                graphics.item(displayStack, x + 2, y + 3);
                graphics.text(minecraft.font, item.displayName(), x + 24, y + 4, 0xFFFFFF);
                graphics.text(minecraft.font, "x" + item.totalCount(), x + 24, y + 14, 0xAAAAAA);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                if (event.button() == 0) {
                    list.setSelected(this);
                    screen.selectItem(item);
                    return true;
                }
                return false;
            }

            @Override
            public Component getNarration() {
                return Component.literal(item.displayName());
            }
        }
    }
}
