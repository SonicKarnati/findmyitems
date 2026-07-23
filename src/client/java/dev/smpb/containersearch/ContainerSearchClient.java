package dev.smpb.containersearch;

import dev.smpb.containersearch.gui.CatalogScreen;
import dev.smpb.containersearch.index.ContainerIndex;
import dev.smpb.containersearch.index.InMemoryContainerIndex;
import dev.smpb.containersearch.observation.ObservationCollector;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class ContainerSearchClient implements ClientModInitializer {
    private ContainerIndex index;
    private KeyMapping openCatalogKey;

    @Override
    public void onInitializeClient() {
        index = new InMemoryContainerIndex();
        new ObservationCollector(index);

        openCatalogKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.container-search.open_catalog",
                GLFW.GLFW_KEY_B,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(Minecraft client) {
        if (openCatalogKey.consumeClick()) {
            var current = client.gui.screen();
            if (current instanceof CatalogScreen catalogScreen) {
                catalogScreen.onClose();
            } else if (current == null) {
                client.gui.setScreen(new CatalogScreen(index));
            }
        }
    }
}
