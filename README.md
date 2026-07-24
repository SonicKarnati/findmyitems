# container-search

Pairs well with [Shulker Box Tooltip](https://modrinth.com/mod/shulkerboxtooltip), which previews shulker contents on hover — this mod indexes what is inside them, that one shows it to you. It is listed under `recommends`, so it is optional.

`container-search` is a client-side Fabric mod for Minecraft Java 26.2. It remembers supported storage containers you open in single-player worlds, makes their contents searchable, and uses normal container interaction to retrieve reachable items.

## Single-player only

This is a product decision, not a missing feature. The catalog is a memory of containers **you** opened. In a world only you can change, that memory stays true; on a server it goes stale the moment another player touches a chest, and there is no reliable way for a client to know that it has. A catalog that is confidently wrong is worse than no catalog.

So on a multiplayer server the mod stands down completely — no indexing, no filter box, and the catalog keybind says so. Modded containers are not supported either.

## Installing

Requires [Fabric Loader](https://fabricmc.net/use/) 0.19.3+, [Fabric API](https://modrinth.com/mod/fabric-api), [Mod Menu](https://modrinth.com/mod/modmenu) and [Cloth Config](https://modrinth.com/mod/cloth-config). Drop the jar from the [releases page](https://github.com/SonicKarnati/container-search/releases) into `mods/` alongside them.

Settings live in Mod Menu: how often remembered chests are re-scanned (in seconds, 0 to disable) and how far away that re-scan reaches (in blocks, 0 for unlimited).

## The catalog

Press `B`. Three views, switched with the tabs:

- **Items** — every item you have seen, with the nearest container holding it. The ender-eye button glows that container in the world, the hopper pulls the typed amount into your inventory, and the chest puts it back. Deposit is deliberately narrow: it only offers to return items the container already stocks, so it never guesses where an unfamiliar item belongs.
- **Containers** — every container you have opened, nearest first, with your ender chest pinned to the top even when empty (unless you are searching).
- **Crafting** — leave the box empty for every craftable item, alphabetically, and click one; or type a name directly. You get its material tree, charged against what your chests already hold. Indented rows are sub-crafts; a red row is something you have to go and find.

Ctrl+1/2/3 (Cmd on macOS) jumps between the views. Items and Containers each render as a list or a grid (the toggle on the right); in the grid, left-click takes and right-click locates.

Search covers item names, ids and tooltips, including enchantments — `smite 4` and `smite iv` both find the same sword, and two swords that differ only by enchantment stay two separate entries.

Items stashed inside a shulker box inside a chest are indexed and retrievable, up to four levels deep.

## Playing with it by hand

In a dev run (`./gradlew runClient`), `/cstest build` puts a row of stocked containers in front of you and `/cstest clear` puts the world back exactly as it was. The command is registered only in a development environment, so a released jar does not carry it.

Thirteen containers, each aimed at something specific:

| # | Container | What it is for |
| --- | --- | --- |
| 1 | Chest | Baseline: index, search, take |
| 2 | Double chest | One container across two positions |
| 3 | Trapped chest | Kind handling |
| 4 | Barrel | Kind handling |
| 5 | Shulker box block | Its own menu type (27 slots) |
| 6 | Ender chest | Pinned to the top of the Containers view |
| 7 | Chest of shulkers | Nested indexing and nested retrieval, one of them two levels deep |
| 8 | Chest | Items that differ only by component: two swords both named "Bee Stinger" but enchanted Sharpness V and Smite IV, two enchanted books, a plain sword, a damaged pickaxe |
| 9 | Chest | Partial crafting materials, so the Crafting view shows a mix of covered and missing |
| 10 | Chest | 512 iron ingots, for pushing the amount box past 64 |
| 11 | Chest | Stacking edge cases: beds and dragon eggs (never stack), buckets (16 empty, 1 filled), elytra, cake |
| 12 | Empty barrel | Empty-container display |
| 13 | Chest, 25 blocks out | Out of reach: locate works, take is greyed out |

`clear` only ever restores blocks that `build` overwrote, and that record is in memory — it does not survive a restart.

## Testing

Three layers, all runnable without launching the game by hand:

| Command | What it does | Time |
| --- | --- | --- |
| `./gradlew test` | Plain JUnit over the index, store, and model — no Minecraft. | ~1s |
| `./gradlew runGameTest` | Headless server: real levels, block entities and player inventories. Covers `RetrieveHandler` and indexing a real chest. | ~10s |
| `./gradlew runClientGameTest` | Boots the **real client**, creates a world, right-clicks a chest, presses the catalog keybind, types a query. | ~30s |

`./gradlew build` runs the first layer; the other two are separate tasks (they start Minecraft).

The client test writes screenshots to `build/run/clientGameTest/screenshots/` — that is where you look at the UI instead of loading a world yourself. Assertions in that test stay on facts (index contents, which screen is open), not pixels, so a deliberate visual change does not fail the build.

Adding a case:

- world/server behaviour → a `@GameTest` method in `src/gametest/.../ContainerSearchGameTest.java`
- a container that is not one block entity, an inventory that will not take what is offered, or anything else where the invariant is "nothing was created or destroyed" → `RetrieveEdgeCaseGameTest.java`
- anything involving input, screens or rendering → a step in `ContainerSearchClientGameTest.java`

New `@GameTest` classes must be listed in the `fabric-gametest` entrypoint of `src/gametest/resources/fabric.mod.json`, or they are silently never run.

## Contributing and reporting bugs

Bugs, ideas and questions all go through [GitHub Issues](https://github.com/SonicKarnati/container-search/issues) — pick a template and it will ask for what is actually needed to reproduce the problem. [`CONTRIBUTING.md`](CONTRIBUTING.md) covers the branch, commit and review conventions, and the checks a change has to pass.
