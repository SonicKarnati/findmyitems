package dev.smpb.findmyitems.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

final class FixtureCommandTest {
    private static final Path FIXTURE = Path.of("src/test/resources/findmyitems-test-fixture");
    private static final Pattern POSITIONED = Pattern.compile(
            "execute positioned (~-?\\d+|~) (~-?\\d+|~) (~-?\\d+|~) (.+)");
    private static final Pattern BLOCK = Pattern.compile(
            "if block ~ ~ ~ minecraft:air run setblock ~ ~ ~ minecraft:([a-z_]+)(?:\\[.*\\])?");
    private static final Pattern ITEM = Pattern.compile(
            "if entity @e\\[type=minecraft:marker,tag=findmyitems_fixture,distance=\\.\\.0\\.1\\] run "
                    + "item replace block ~ ~ ~ container\\.(\\d+) with minecraft:([a-z_]+) (\\d+)");
    private static final Pattern MARKER = Pattern.compile(
            "unless entity @e\\[type=minecraft:marker,tag=findmyitems_fixture,distance=\\.\\.0\\.1\\] "
                    + "if block ~ ~ ~ minecraft:air run summon minecraft:marker ~ ~ ~ "
                    + "\\{Tags:\\[\"findmyitems_fixture\",\"findmyitems_fixture_([a-z_]+)\"\\]\\}");
    private static final Pattern RESET = Pattern.compile(
            "execute as @e\\[type=minecraft:marker,tag=findmyitems_fixture,tag=findmyitems_fixture_([a-z_]+)\\] at @s "
                    + "if block ~ ~ ~ minecraft:([a-z_]+) run setblock ~ ~ ~ minecraft:air");

    private static final Map<String, FixtureGroup> GROUPS = groups();

    @Test
    void everySetupCommandMatchesTheFixtureModel() throws IOException {
        var commands = parseSetup(read("data/findmyitems/function/setup.mcfunction"));
        var markers = commands.stream().filter(Command::marker).toList();
        var blocks = commands.stream().filter(command -> command.block() != null).toList();
        var items = commands.stream().filter(command -> command.item() != null).toList();

        assertEquals(GROUPS.values().stream().mapToInt(group -> group.positions().size()).sum(), markers.size());
        assertEquals(markers.size(), blocks.size());
        assertEquals(11, items.size());
        assertEquals(expectedSetup(), commands);
        assertEquals(GROUPS.values().stream().flatMap(group -> group.positions().stream()).toList(),
                markers.stream().map(Command::position).toList());
        for (var group : GROUPS.values()) {
            assertEquals(group.positions(), positionsFor(group, markers), group.name());
            assertEquals(group.blocks(), blocksFor(group, blocks), group.name() + " blocks");
            assertEquals(group.items(), itemsFor(group, items), group.name() + " items");
        }
    }

    @Test
    void setupAndResetUseOnlyOwnedMarkerBlocks() throws IOException {
        var resetLines = commands(read("data/findmyitems/function/reset.mcfunction"));
        var setupLines = commands(read("data/findmyitems/function/setup.mcfunction"));

        assertTrue(!setupLines.isEmpty());
        assertTrue(setupLines.stream().allMatch(line -> POSITIONED.matcher(line).matches()),
                () -> "invalid setup commands: " + setupLines.stream()
                        .filter(line -> !POSITIONED.matcher(line).matches()).toList());
        var resetBlocks = new ArrayList<String>();
        for (var line : resetLines.subList(0, resetLines.size() - 1)) {
            var matcher = RESET.matcher(line);
            assertTrue(matcher.matches(), "invalid reset command: " + line);
            assertEquals(matcher.group(1), matcher.group(2), "reset must verify the marker's original type");
            resetBlocks.add(matcher.group(1) + ":" + matcher.group(2));
        }
        assertEquals(List.of("chest:chest", "stone:stone", "hopper:hopper", "crafting_table:crafting_table"),
                resetBlocks);
        assertTrue(resetLines.get(resetLines.size() - 1).equals(
                "kill @e[type=minecraft:marker,tag=findmyitems_fixture]"));
        assertEquals("{\n  \"values\": []\n}\n", read("data/minecraft/tags/function/load.json"));
    }

    @Test
    void readmeCoordinateMapMatchesTheFixtureModel() throws IOException {
        var readme = readRoot("README.md");
        var documented = new LinkedHashMap<String, String>();
        for (var line : readme.split("\\R")) {
            var matcher = Pattern.compile("\\| (.+?) \\| (.+) \\|").matcher(line);
            if (matcher.find() && matcher.group(1).startsWith("`~")) {
                documented.put(matcher.group(1).replace("`", ""), matcher.group(2));
            }
        }

        assertEquals(8, documented.size());
        for (var group : GROUPS.values()) {
            var coordinateKey = String.join(" and ", group.readmeCoordinates());
            assertTrue(documented.containsKey(coordinateKey), "README is missing " + coordinateKey);
            assertTrue(documented.get(coordinateKey).contains(group.readmeDescription()), group.name());
        }
    }

    private static List<Command> parseSetup(String source) {
        var parsed = new ArrayList<Command>();
        for (var line : commands(source)) {
            var positioned = POSITIONED.matcher(line);
            assertTrue(positioned.matches(), "invalid setup command: " + line);
            var tail = positioned.group(4);
            var marker = MARKER.matcher(tail);
            var block = BLOCK.matcher(tail);
            var item = ITEM.matcher(tail);
            assertTrue(marker.matches() ^ block.matches() ^ item.matches(), "unknown setup command: " + line);
            parsed.add(new Command(new Position(positioned.group(1), positioned.group(2), positioned.group(3)),
                    marker.matches() ? marker.group(1) : null, block.matches() ? block.group(1) : null,
                    item.matches() ? new Item(item.group(1), item.group(2), Integer.parseInt(item.group(3))) : null));
        }
        return parsed;
    }

    private static List<Position> positionsFor(FixtureGroup group, List<Command> commands) {
        return commands.stream().filter(Command::marker).map(Command::position).filter(group.positions()::contains).toList();
    }

    private static List<String> blocksFor(FixtureGroup group, List<Command> commands) {
        return commands.stream().filter(command -> group.positions().contains(command.position()))
                .map(Command::block).filter(block -> block != null).toList();
    }

    private static List<Item> itemsFor(FixtureGroup group, List<Command> commands) {
        return commands.stream().filter(command -> group.positions().contains(command.position()))
                .map(Command::item).filter(item -> item != null).toList();
    }

    private static List<Command> expectedSetup() {
        var expected = new ArrayList<Command>();
        addBlock(expected, "~2 ~ ~2", "chest");
        addItem(expected, "~2 ~ ~2", new Item("0", "white_bed", 1));
        addItem(expected, "~2 ~ ~2", new Item("1", "bedrock", 1));
        addBlock(expected, "~4 ~ ~2", "chest");
        addBlock(expected, "~4 ~ ~1", "stone");
        addItem(expected, "~4 ~ ~2", new Item("0", "diamond", 32));
        addBlock(expected, "~6 ~ ~2", "chest");
        addBlock(expected, "~5 ~ ~1", "stone");
        addBlock(expected, "~7 ~ ~1", "stone");
        addItem(expected, "~6 ~ ~2", new Item("0", "emerald", 5));
        addBlock(expected, "~8 ~ ~2", "chest");
        addBlock(expected, "~9 ~ ~2", "chest");
        addItem(expected, "~8 ~ ~2", new Item("0", "iron_ingot", 64));
        addItem(expected, "~9 ~ ~2", new Item("0", "iron_ingot", 64));
        addBlock(expected, "~12 ~ ~2", "chest");
        addBlock(expected, "~12 ~1 ~2", "hopper");
        addItem(expected, "~12 ~1 ~2", new Item("0", "gold_ingot", 16));
        addBlock(expected, "~14 ~ ~2", "crafting_table");
        addBlock(expected, "~16 ~ ~2", "chest");
        addItem(expected, "~16 ~ ~2", new Item("0", "diamond", 3));
        addItem(expected, "~16 ~ ~2", new Item("1", "stick", 2));
        addItem(expected, "~16 ~ ~2", new Item("2", "oak_log", 3));
        addBlock(expected, "~30 ~ ~2", "chest");
        addItem(expected, "~30 ~ ~2", new Item("0", "diamond", 8));
        return expected;
    }

    private static void addBlock(List<Command> commands, String position, String block) {
        var parsed = Position.parse(position);
        commands.add(new Command(parsed, block, null, null));
        commands.add(new Command(parsed, null, block, null));
    }

    private static void addItem(List<Command> commands, String position, Item item) {
        commands.add(new Command(Position.parse(position), null, null, item));
    }

    private static Map<String, FixtureGroup> groups() {
        var groups = new LinkedHashMap<String, FixtureGroup>();
        add(groups, "accessible", List.of("~2 ~ ~2"), List.of("chest"),
                List.of(new Item("0", "white_bed", 1), new Item("1", "bedrock", 1)), "~2 ~ ~2", "Accessible chest");
        add(groups, "obstructed", List.of("~4 ~ ~2", "~4 ~ ~1"), List.of("chest", "stone"),
                List.of(new Item("0", "diamond", 32)), "~4 ~ ~2", "Obstructed chest");
        add(groups, "doorway", List.of("~6 ~ ~2", "~5 ~ ~1", "~7 ~ ~1"), List.of("chest", "stone", "stone"),
                List.of(new Item("0", "emerald", 5)), "~6 ~ ~2", "Doorway-visible chest");
        add(groups, "double", List.of("~8 ~ ~2", "~9 ~ ~2"), List.of("chest", "chest"),
                List.of(new Item("0", "iron_ingot", 64), new Item("0", "iron_ingot", 64)), "~8 ~ ~2 and ~9 ~ ~2", "Adjacent double chest");
        add(groups, "hopper", List.of("~12 ~ ~2", "~12 ~1 ~2"), List.of("chest", "hopper"),
                List.of(new Item("0", "gold_ingot", 16)), "~12 ~ ~2", "Hopper-fed chest");
        add(groups, "table", List.of("~14 ~ ~2"), List.of("crafting_table"), List.of(), "~14 ~ ~2", "Crafting table");
        add(groups, "partial", List.of("~16 ~ ~2"), List.of("chest"), List.of(new Item("0", "diamond", 3), new Item("1", "stick", 2), new Item("2", "oak_log", 3)), "~16 ~ ~2", "Partial-material chest");
        add(groups, "far", List.of("~30 ~ ~2"), List.of("chest"), List.of(new Item("0", "diamond", 8)), "~30 ~ ~2", "Far chest");
        return groups;
    }

    private static void add(Map<String, FixtureGroup> groups, String name, List<String> positions, List<String> blocks,
            List<Item> items, String readmeCoordinates, String readmeDescription) {
        groups.put(name, new FixtureGroup(name, positions.stream().map(Position::parse).toList(), blocks, items,
                List.of(readmeCoordinates), readmeDescription));
    }

    private static List<String> commands(String source) {
        return source.lines().map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(FIXTURE.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private static String readRoot(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
    }

    private record Position(String x, String y, String z) {
        static Position parse(String value) {
            var parts = value.split(" ");
            return new Position(parts[0], parts[1], parts[2]);
        }
    }

    private record Item(String slot, String material, int quantity) {}

    private record Command(Position position, String markerType, String block, Item item) {
        boolean marker() {
            return markerType != null;
        }
    }

    private record FixtureGroup(String name, List<Position> positions, List<String> blocks, List<Item> items,
            List<String> readmeCoordinates, String readmeDescription) {}
}
