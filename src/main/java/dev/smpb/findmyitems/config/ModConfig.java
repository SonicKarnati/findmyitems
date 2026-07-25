package dev.smpb.findmyitems.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public int rescanIntervalSeconds = 5;
    public int searchDistanceBlocks = 64;

    public static ModConfig load(Path path) {
        if (Files.isRegularFile(path)) {
            try {
                return GSON.fromJson(Files.readString(path), ModConfig.class);
            } catch (IOException | com.google.gson.JsonParseException e) {
                return new ModConfig();
            }
        }
        return new ModConfig();
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
