package dev.smpb.containersearch.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * User settings, persisted to {@code config/container-search.json}. Loaded once at client
 * start and edited from the Mod Menu screen. Single-instance via {@link #get()}.
 *
 * <p>The catalog keybind is not stored here — it is a vanilla {@code KeyMapping} owned by
 * Minecraft's own options, editable in Controls and mirrored in the settings screen.
 */
public final class ContainerSearchConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ContainerSearchConfig instance;

    /** Max distance (blocks) a remembered chest may be from the player to appear in search. 0 or less = unlimited. */
    public int searchDistance = 0;

    /** Whether to periodically re-scan loaded remembered chests for changes (single-player only). */
    public boolean hotReload = true;

    public static ContainerSearchConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    public boolean unlimitedDistance() {
        return searchDistance <= 0;
    }

    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("container-search.json");
    }

    private static ContainerSearchConfig load() {
        var file = path();
        if (Files.exists(file)) {
            try {
                var loaded = GSON.fromJson(Files.readString(file), ContainerSearchConfig.class);
                if (loaded != null) return loaded;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return new ContainerSearchConfig();
    }

    public void save() {
        try {
            var file = path();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
