package dev.smpb.findmyitems.fixture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class FixtureCommandTest {
    private static final Path FIXTURE = Path.of("src/test/resources/findmyitems-test-fixture");

    @Test
    void fixtureUsesOwnedMarkersAndDoesNotResetOnDatapackLoad() throws IOException {
        var setup = read("data/findmyitems/function/setup.mcfunction");
        var reset = read("data/findmyitems/function/reset.mcfunction");
        var load = read("data/minecraft/tags/function/load.json");

        assertTrue(setup.contains("findmyitems_fixture"), "setup must mark blocks it owns");
        assertTrue(setup.contains("if block ~ ~ ~ minecraft:air run setblock"),
                "setup must claim only empty positions");
        assertFalse(setup.contains("\nsetblock"), "setup must not unconditionally overwrite blocks");
        assertTrue(reset.contains("findmyitems_fixture"), "reset must address owned markers");
        assertTrue(reset.contains("if block"), "reset must verify the owned block before removing it");
        assertFalse(load.contains("findmyitems:reset"), "datapack load must not reset arbitrary coordinates");
    }

    @Test
    void partialMaterialChestCanMakeADiamondPickaxe() throws IOException {
        var setup = read("data/findmyitems/function/setup.mcfunction");

        assertTrue(setup.contains("container.0 with minecraft:diamond 3"),
                "fixture must provide the three diamonds required by the pickaxe recipe");
        assertTrue(setup.contains("container.1 with minecraft:stick 2"),
                "fixture must provide the two sticks required by the pickaxe recipe");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(FIXTURE.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
