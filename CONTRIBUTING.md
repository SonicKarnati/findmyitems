# Contributing to findmyitems

How to report problems, request changes, and submit code to `findmyitems`.

## Overview

`findmyitems` is a client-side Fabric mod with a deliberately small feature surface. Changes are evaluated on whether they make the container index more accurate or more usable, rather than on whether they add functionality.

Two constraints apply to every change:

- **Item counts are conserved.** Any code path that moves items must not create or destroy them. This includes the case where the player's inventory is full, and the case where the player is in creative mode. Minecraft's own `Inventory.add` reports success when it has placed only part of a stack, and in creative mode it discards the remainder. `RetrieveHandler` therefore counts what actually arrived in the inventory rather than relying on that return value.
- **The mod stays client-side and single-player.** It sends no packets that a vanilla client would not send, and requires no server-side component. Where it needs authoritative world state it uses the integrated server of a single-player world. Do not add a partial multiplayer mode; see the README for why multiplayer is excluded.

## Reporting a bug

Open an issue using the **Bug report** template. It asks for:

- What happened, and what you expected instead
- Steps to reproduce
- The mod version
- The Minecraft, Fabric Loader, and Fabric API versions
- Other installed mods
- Relevant lines from `logs/latest.log`

Two notes before filing:

- The mod disabling itself on a multiplayer server is intended behaviour, not a bug.
- An incorrect item count is the most useful category of report. The index stores what a container held when it was last read. If a count is wrong, describe what changed the container between that reading and the moment the count was displayed.

If you are running from source, the index file at `config/findmyitems/worlds/ID.json` is a useful attachment. It contains item names and block coordinates from your world; review it before posting.

## Requesting a feature

Open an issue using the **Feature request** template. Describe the situation in your world that prompted the request, not only the feature you have in mind. A described problem allows for a wider range of solutions.

## Development

### Requirements

- JDK `25` or newer
- The included Gradle wrapper; no separate Gradle install

### Build and run

```sh
./gradlew build      # compile, then run the JUnit tests
./gradlew runClient  # launch a development client with the mod loaded
```

### Testing

Three layers are available, in increasing cost:

| Command | Scope | Approximate time |
| --- | --- | --- |
| `./gradlew test` | JUnit over the index, store, and model types. No Minecraft classes are loaded. | 1 second |
| `./gradlew runGameTest` | Headless server. Real levels, block entities, and player inventories. Covers retrieval and indexing. | 10 seconds |
| `./gradlew runClientGameTest` | Launches a real client, creates a world, and drives the mod through input. | 30 seconds |

`./gradlew build` runs the first layer only. Continuous integration runs the first two on every pull request.

`runClientGameTest` opens a game window and writes screenshots to `build/run/clientGameTest/screenshots/`. Its assertions check facts such as index contents and which screen is open, not pixels, so an intended visual change does not fail the build.

### Where tests belong

| Change type | Location |
| --- | --- |
| No Minecraft required: index, store, model | `src/test/`, plain JUnit |
| World, block entities, inventories | A `@GameTest` method in `src/gametest/` |
| A container that is not a single block entity, an inventory that cannot accept what is offered, or any case whose invariant is that no items were created or destroyed | `RetrieveEdgeCaseGameTest` |
| Input, screens, rendering | A step in `FindMyItemsClientGameTest` |

New `@GameTest` classes must be listed in the `fabric-gametest` entrypoint of `src/gametest/resources/fabric.mod.json`. A class that is not listed there is never run, and no error is reported.

### The testbed command

In a development client (`./gradlew runClient`), `/fmitest build` places a row of stocked containers in front of the player, and `/fmitest clear` restores the blocks that `build` overwrote.

The command is registered only when Fabric reports a development environment, and the `debug` package is excluded from the released jar by the `jar` task in `build.gradle`.

`clear` restores only blocks recorded by `build` during the current session. That record is held in memory and does not survive a restart.

The testbed places thirteen containers, each covering a specific case:

| # | Container | Case covered |
| --- | --- | --- |
| 1 | Chest | Baseline indexing, search, and retrieval |
| 2 | Double chest | One container occupying two block positions |
| 3 | Trapped chest | Container type handling |
| 4 | Barrel | Container type handling |
| 5 | Shulker box block | A distinct menu type with 27 slots |
| 6 | Ender chest | Listed first in the Containers view |
| 7 | Chest containing shulker boxes | Nested indexing and retrieval, including one item two levels deep |
| 8 | Chest | Items differing only by components: two identically named swords with different enchantments, two enchanted books, a plain sword, a damaged pickaxe |
| 9 | Chest | Partial crafting materials, so the Crafting view shows both covered and missing rows |
| 10 | Chest | 512 iron ingots, for amounts above one stack |
| 11 | Chest | Stacking edge cases: beds and dragon eggs, which do not stack; buckets, filled and empty; elytra; cake |
| 12 | Empty barrel | Display of an empty container |
| 13 | Chest, 25 blocks away | Out of interaction range: highlighting works, retrieval is disabled |

## Submitting a change

1. **Branch from `main`.** Use `fix/short-description` or `feat/short-description`.
2. **Keep the change focused.** One reason per pull request. A rename combined with a behaviour change makes the behaviour change difficult to review.
3. **Add a test.** Any change involving a branch, a loop, or items moving between two places needs one. See [Where tests belong](#where-tests-belong).
4. **Run the checks.** `./gradlew build` and `./gradlew runGameTest` at minimum. Add `./gradlew runClientGameTest` if the change affects input, screens, or rendering.
5. **Write a specific commit message.** For example, `fix: reach the far half of a double chest` rather than `fix chest bug`. Prefixes in use: `feat:`, `fix:`, `perf:`, `docs:`, `test:`, `chore:`.
6. **Open a pull request.** The template asks what changed, why, which checks were run, and which test covers it.

Do not add AI co-author trailers to commit messages or attribution lines to pull request descriptions. See `CLAUDE.md`.

## Code style

Comments explain why a piece of code is written the way it is, not what it does. Code that needs no explanation carries no comment. Names carry the description of behaviour.

Deliberate simplifications with a known limit are marked with a `ponytail:` comment that names the limit and the upgrade path, for example a single-entry memo that would need proper invalidation if datapack reloads became relevant.

## Releasing

For maintainers:

1. Update `mod_version` in `gradle.properties`.
2. Add a dated section to `CHANGELOG.md`.
3. Commit and push to `main`.
4. Tag the commit `vVERSION`, matching `mod_version` exactly, and push the tag.

The `release` workflow then compiles the mod, runs the JUnit and headless server tests, verifies that the tag matches `mod_version`, and creates a draft GitHub release with the jar attached. Review the draft before publishing it.
