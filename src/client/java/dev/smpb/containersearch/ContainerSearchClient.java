package dev.smpb.containersearch;

import dev.smpb.containersearch.gui.CatalogScreen;
import dev.smpb.containersearch.index.ContainerIndex;
import dev.smpb.containersearch.index.InMemoryContainerIndex;
import dev.smpb.containersearch.observation.ObservationCollector;
import dev.smpb.containersearch.observation.PositionCache;
import dev.smpb.containersearch.store.JsonWorldStore;
import dev.smpb.containersearch.store.WorldKey;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.List;

public final class ContainerSearchClient implements ClientModInitializer {
    private ContainerIndex index;
    private KeyMapping openCatalogKey;
    private JsonWorldStore store;

    @Override
    public void onInitializeClient() {
        index = new InMemoryContainerIndex();
        new ObservationCollector(index);

        var configDir = Minecraft.getInstance().gameDirectory.toPath().resolve("config");
        store = new JsonWorldStore(configDir);

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            var pos = hitResult.getBlockPos();
            PositionCache.record(pos, level.dimension().identifier().toString());
            return InteractionResult.PASS;
        });

        openCatalogKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.container-search.open_catalog",
                GLFW.GLFW_KEY_B,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            var server = client.getSingleplayerServer();
            if (server == null) return;

            var levelName = server.getWorldData().getLevelName();
            var playerId = client.player.getUUID();
            var key = WorldKey.singleplayer(levelName, levelName, playerId);

            var result = store.load(key);
            index.replace(result.snapshot());
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            var server = client.getSingleplayerServer();
            if (server == null) return;

            var levelName = server.getWorldData().getLevelName();
            var playerId = client.player.getUUID();
            var key = WorldKey.singleplayer(levelName, levelName, playerId);

            try {
                store.save(key, index.snapshot(), List.of());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
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
