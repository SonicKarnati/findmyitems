package dev.smpb.containersearch.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.smpb.containersearch.index.IndexSnapshot;
import dev.smpb.containersearch.index.IndexedContainer;
import dev.smpb.containersearch.model.BlockPosition;
import dev.smpb.containersearch.model.ContainerKind;
import dev.smpb.containersearch.model.SlotSnapshot;
import dev.smpb.containersearch.model.SourceKey;
import dev.smpb.containersearch.model.StackKey;
import dev.smpb.containersearch.model.StackSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JsonWorldStoreTest {
    private static final UUID PLAYER_ID = UUID.fromString("12345678-1234-1234-1234-123456789abc");

    @TempDir
    Path temp;

    @Test
    void worldKeyIsStableAndPlayerSpecific() {
        var first = WorldKey.singleplayer("My World", "My World", PLAYER_ID);
        var repeated = WorldKey.singleplayer("My World", "Renamed Label", PLAYER_ID);
        var anotherPlayer = WorldKey.singleplayer(
                "My World", "My World", UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

        assertEquals(first.id(), repeated.id());
        assertNotEquals(first.id(), anotherPlayer.id());
    }

    @Test
    void indexAndCraftRequestRoundTrip() throws Exception {
        var store = new JsonWorldStore(temp);
        var key = WorldKey.singleplayer("World A", "World A", PLAYER_ID);
        var snapshot = snapshot(12);
        var requests = List.of(new SavedCraftRequest(new StackKey("minecraft:observer", "{}"), 17));

        store.save(key, snapshot, requests);
        var result = store.load(key);

        assertEquals(LoadStatus.LOADED, result.status());
        assertEquals(snapshot, result.snapshot());
        assertEquals(requests, result.requests());
    }

    @Test
    void corruptPrimaryRecoversPreviousGoodBackup() throws Exception {
        var store = new JsonWorldStore(temp);
        var key = WorldKey.singleplayer("World A", "World A", PLAYER_ID);
        var original = snapshot(12);
        store.save(key, original, List.of());
        store.save(key, snapshot(20), List.of());
        Files.writeString(store.pathFor(key), "not json");

        var result = store.load(key);

        assertEquals(LoadStatus.RECOVERED_BACKUP, result.status());
        assertEquals(original, result.snapshot());
    }

    @Test
    void newerSchemaIsReadOnly() throws Exception {
        var store = new JsonWorldStore(temp);
        var key = WorldKey.singleplayer("World A", "World A", PLAYER_ID);
        Files.createDirectories(store.pathFor(key).getParent());
        Files.writeString(store.pathFor(key), "{\"schemaVersion\":999}");

        var result = store.load(key);

        assertEquals(LoadStatus.NEWER_SCHEMA, result.status());
        assertThrows(IOException.class, () -> store.save(key, snapshot(1), List.of()));
        assertEquals("{\"schemaVersion\":999}", Files.readString(store.pathFor(key)));
    }

    @Test
    void failedReplacementPreservesCurrentFile() throws Exception {
        var key = WorldKey.singleplayer("World A", "World A", PLAYER_ID);
        var working = new JsonWorldStore(temp);
        working.save(key, snapshot(12), List.of());
        var before = Files.readString(working.pathFor(key));
        var failingWriter = new AtomicFileWriter((source, target) -> {
            throw new IOException("simulated move failure");
        });
        var failing = new JsonWorldStore(temp, failingWriter);

        assertThrows(IOException.class, () -> failing.save(key, snapshot(20), List.of()));

        assertEquals(before, Files.readString(working.pathFor(key)));
    }

    private static IndexSnapshot snapshot(int count) {
        var source = SourceKey.storage(
                "minecraft:overworld",
                ContainerKind.CHEST,
                List.of(new BlockPosition(1, 64, 2)));
        var stack = new StackSnapshot(
                new StackKey("minecraft:cobblestone", "{}"),
                count,
                "Cobblestone",
                List.of("Building Blocks"));
        return new IndexSnapshot(List.of(new IndexedContainer(
                source,
                List.of(source),
                List.of(new SlotSnapshot(0, stack)),
                Instant.parse("2026-07-22T12:00:00Z"))));
    }
}
