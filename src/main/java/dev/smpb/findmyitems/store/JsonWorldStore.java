package dev.smpb.findmyitems.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.smpb.findmyitems.index.IndexSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class JsonWorldStore implements WorldStore {
    private final Path worldsDirectory;
    private final AtomicFileWriter writer;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Set<String> readOnlyKeys = new HashSet<>();

    public JsonWorldStore(Path configDirectory) {
        this(configDirectory, new AtomicFileWriter());
    }

    JsonWorldStore(Path configDirectory, AtomicFileWriter writer) {
        this.worldsDirectory = configDirectory.resolve("findmyitems").resolve("worlds");
        this.writer = writer;
    }

    public Path pathFor(WorldKey key) {
        return worldsDirectory.resolve(key.id() + ".json");
    }

    @Override
    public StoreResult load(WorldKey key) {
        var primary = pathFor(key);
        if (!Files.isRegularFile(primary)) {
            return loadBackupOrEmpty(key, LoadStatus.EMPTY);
        }
        var result = decode(primary, LoadStatus.LOADED);
        if (result.status() == LoadStatus.NEWER_SCHEMA) {
            readOnlyKeys.add(key.id());
            return result;
        }
        if (result.status() != LoadStatus.CORRUPT) {
            return result;
        }
        return loadBackupOrEmpty(key, LoadStatus.CORRUPT);
    }

    @Override
    public void save(
            WorldKey key,
            IndexSnapshot snapshot,
            List<SavedCraftRequest> requests) throws IOException {
        if (readOnlyKeys.contains(key.id())) {
            throw new IOException("world index uses a newer or unreadable schema");
        }
        var document = StoreDocument.from(key, snapshot, requests);
        writer.write(pathFor(key), gson.toJson(document));
    }

    private StoreResult loadBackupOrEmpty(WorldKey key, LoadStatus missingStatus) {
        var backup = pathFor(key).resolveSibling(pathFor(key).getFileName() + ".bak");
        if (!Files.isRegularFile(backup)) {
            return StoreResult.empty(missingStatus, missingStatus == LoadStatus.EMPTY ? "" : "index is corrupt");
        }
        var backupResult = decode(backup, LoadStatus.RECOVERED_BACKUP);
        if (backupResult.status() == LoadStatus.CORRUPT
                || backupResult.status() == LoadStatus.NEWER_SCHEMA) {
            readOnlyKeys.add(key.id());
            return StoreResult.empty(backupResult.status(), "primary and backup could not be loaded");
        }
        return backupResult;
    }

    private StoreResult decode(Path path, LoadStatus successStatus) {
        try {
            var json = Files.readString(path);
            var root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("schemaVersion")) {
                throw new JsonParseException("schemaVersion is missing");
            }
            var schemaVersion = root.get("schemaVersion").getAsInt();
            if (schemaVersion > StoreDocument.CURRENT_SCHEMA) {
                return StoreResult.empty(LoadStatus.NEWER_SCHEMA, "index was created by a newer version");
            }
            if (schemaVersion != StoreDocument.CURRENT_SCHEMA) {
                throw new JsonParseException("unsupported schema version " + schemaVersion);
            }
            var document = gson.fromJson(root, StoreDocument.class);
            return document.toResult(successStatus);
        } catch (IOException | RuntimeException exception) {
            return StoreResult.empty(LoadStatus.CORRUPT, exception.getMessage());
        }
    }
}
