package dev.smpb.findmyitems.search;

import dev.smpb.findmyitems.FindMyItemsClient;
import dev.smpb.findmyitems.index.SearchQuery;
import dev.smpb.findmyitems.model.StackKey;
import dev.smpb.findmyitems.model.StackSnapshot;
import dev.smpb.findmyitems.observation.SlotReader;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;


/**
 * Adds a filter box to every vanilla container screen (the player inventory and
 * any chest/barrel/shulker/etc). Typing dims the slots whose item does not match,
 * so matching items stand out. Purely visual — nothing in the container moves.
 *
 * <p>Registers on {@link ScreenEvents#AFTER_INIT}: on each screen init it injects an
 * {@link EditBox} into the screen's widget list and hooks {@code afterExtract} to draw
 * the dim overlay after the screen's own contents. No mixins are used — the GUI origin
 * ({@code leftPos}/{@code topPos}) is read via an access widener.
 */
public final class InventorySearchController {
    private static final int SLOT_SIZE = 16;
    private static final int BOX_HEIGHT = 12;
    private static final int BOX_WIDTH = 100;
    private static final int BOX_GAP = 4;
    /** Translucent near-black laid over non-matching slots. */
    private static final int DIM_COLOR = 0xC0080808;

    public InventorySearchController() {
        ScreenEvents.AFTER_INIT.register(this::onAfterInit);
    }

    private void onAfterInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        // Single-player only, like the rest of the mod: a filter box appearing on a server is a
        // promise the catalog behind it cannot keep.
        if (!FindMyItemsClient.activeWorld()) return;
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return;
        // The creative inventory already ships its own item search.
        if (screen instanceof CreativeModeInventoryScreen) return;
        if (!isEnabled(screen, FindMyItemsClient.config())) return;

        var box = new EditBox(
                client.font,
                containerScreen.leftPos,
                containerScreen.topPos - BOX_HEIGHT - BOX_GAP,
                BOX_WIDTH,
                BOX_HEIGHT,
                Component.translatable("screen.findmyitems.filter"));
        box.setMaxLength(50);
        box.setBordered(true);
        box.setHint(Component.translatable("screen.findmyitems.filter_hint"));

        // Injected into the screen's widget list so it renders, focuses, and types natively.
        Screens.getWidgets(screen).add(box);

        ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, tickDelta) ->
                dimNonMatching(containerScreen, graphics, box.getValue()));

        // While the box is focused, swallow keys that would otherwise close the screen or
        // trigger hotbar swaps, so they type into the box instead. Editing keys and Escape
        // still fall through to the box / vanilla handling.
        ScreenKeyboardEvents.allowKeyPress(screen).register((s, keyEvent) -> {
            if (!box.isFocused()) return true;
            var options = client.options;
            if (options.keyInventory.matches(keyEvent)
                    || options.keySwapOffhand.matches(keyEvent)
                    || options.keyDrop.matches(keyEvent)) {
                return false;
            }
            for (var hotbarKey : options.keyHotbarSlots) {
                if (hotbarKey.matches(keyEvent)) return false;
            }
            return true;
        });
    }

    /** Returns whether the filter bar is enabled for this eligible screen. */
    static boolean isEnabled(Screen screen, dev.smpb.findmyitems.config.ModConfig config) {
        if (screen instanceof CreativeModeInventoryScreen) return false;
        if (screen instanceof InventoryScreen) return config.filterInventory;
        return config.filterContainers
                && (screen instanceof ContainerScreen || screen instanceof ShulkerBoxScreen);
    }

    private static void dimNonMatching(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, String query) {
        if (query.isBlank()) return;

        var left = screen.leftPos;
        var top = screen.topPos;
        for (var slot : screen.getMenu().slots) {
            if (!slot.isActive()) continue;
            var stack = slot.getItem();
            if (stack.isEmpty() || matches(stack, query)) continue;

            var x = left + slot.x;
            var y = top + slot.y;
            graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, DIM_COLOR);
        }
    }

    private static boolean matches(ItemStack stack, String query) {
        var client = Minecraft.getInstance();
        var tooltipContext = client.level == null
                ? Item.TooltipContext.EMPTY
                : Item.TooltipContext.of(client.level);
        var tooltip = stack.getTooltipLines(tooltipContext, client.player, TooltipFlag.NORMAL);
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        var snapshot = new StackSnapshot(
                new StackKey(id, SlotReader.serializeComponents(stack.getComponentsPatch(),
                        SlotReader.registriesOf(client.player))),
                1,
                stack.getHoverName().getString(),
                tooltip.stream().map(Component::getString).toList());
        return SearchQuery.parse(query).match(dev.smpb.findmyitems.search.SearchDocument.from(snapshot)) != null;
    }
}
