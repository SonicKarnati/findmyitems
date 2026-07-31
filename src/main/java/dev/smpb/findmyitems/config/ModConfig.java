package dev.smpb.findmyitems.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public int rescanIntervalSeconds = 5;
    /**
     * Rescan radius in chunks, matching the unit view and simulation distance already use — a
     * player can compare "8 chunks" against their render distance without doing arithmetic.
     * 0 means unlimited. The default is the 64 blocks this used to be.
     */
    public int searchDistanceChunks = 4;
    /** Whether the catalog opens in grid layout. A reading preference, so it outlives the screen. */
    public boolean gridLayout = false;
    public boolean filterInventory = true;
    public boolean filterContainers = true;

    private transient Path path;

    public static ModConfig load(Path path) {
        var config = read(path);
        config.path = path;
        return config;
    }

    private static ModConfig read(Path path) {
        if (Files.isRegularFile(path)) {
            try {
                var parsed = GSON.fromJson(Files.readString(path), ModConfig.class);
                // Gson hands back null for an empty or literal-null file rather than failing.
                if (parsed != null) return parsed;
            } catch (IOException | com.google.gson.JsonParseException e) {
                return new ModConfig();
            }
        }
        return new ModConfig();
    }

    /** Writes back to wherever this was loaded from. No-op for a config that was never loaded. */
    public void save() {
        if (path != null) save(path);
    }

    public int searchDistanceBlocks() {
        return searchDistanceChunks * 16;
    }

    public void save(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
