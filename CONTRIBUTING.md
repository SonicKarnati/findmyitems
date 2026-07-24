# Contributing

Thanks for looking. This is a small mod with a deliberately small surface, so the bar for a change is "it makes the catalog more trustworthy", not "it adds a feature".

## Reporting a bug

Open an issue with the **Bug report** template. It asks for the versions, the steps, and what you expected instead — those three things are what makes a report actionable, and a report without them usually ends in a week of back and forth.

Two things worth knowing before you file:

- **Multiplayer is not a bug.** The mod stands down entirely on servers, by design. See the README for why.
- **A wrong count is the interesting kind of bug.** The index only knows what it has seen. If the catalog claims a chest holds something it does not, say what changed the chest between the last time you opened it and the moment the catalog lied — that is the detail that finds the cause.

If you are running from source, `container-search.log` lines and the contents of `config/container-search/worlds/*.json` are useful attachments. That JSON is your index; it contains item names and coordinates from your world, so skim it before pasting.

## Asking for a feature

Use the **Feature request** template. The one question it really wants answered is what you were trying to do when you wanted it — a described problem gets a better solution than a described solution.

## Changing code

1. **Branch off `main`.** Name it `fix/short-thing` or `feat/short-thing`.
2. **Keep the diff small.** One reason per pull request. A drive-by rename in the same commit as a behaviour change makes the behaviour change unreviewable.
3. **Leave a test behind.** Anything with a branch, a loop, or an item moving between two places gets one. Where it goes:
   - no Minecraft needed (index, store, model) → `src/test/`, plain JUnit
   - world, block entities, inventories → a `@GameTest` in `src/gametest/`
   - input, screens, rendering → a step in `ContainerSearchClientGameTest`

   New `@GameTest` classes must be added to the `fabric-gametest` entrypoint in `src/gametest/resources/fabric.mod.json` or they never run.
4. **Run the checks.** See [Testing](#testing) below. CI runs the first two layers on every pull request.

5. **Write the commit message for the person doing the bisect.** `fix: reach the far half of a double chest` beats `fix chest bug`. Prefixes in use: `feat:`, `fix:`, `perf:`, `docs:`, `test:`, `chore:`.

## Testing

Three layers, all runnable without launching the game by hand:

| Command | What it does | Time |
| --- | --- | --- |
| `./gradlew build` | Compile, plus plain JUnit over the index, store and model — no Minecraft. | ~1s |
| `./gradlew runGameTest` | Headless server: real levels, block entities and player inventories. Covers retrieval and indexing a real chest. | ~10s |
| `./gradlew runClientGameTest` | Boots the **real client**, creates a world, right-clicks a chest, presses the catalog keybind, types a query. | ~30s |

The client test writes screenshots to `build/run/clientGameTest/screenshots/` — that is where you look at the UI instead of loading a world yourself. Its assertions stay on facts (index contents, which screen is open) rather than pixels, so a deliberate visual change does not fail the build.

## The testbed world

In a dev run (`./gradlew runClient`), `/cstest build` puts a row of stocked containers in front of you and `/cstest clear` puts the world back exactly as it was. The command is registered only in a development environment, and the class is stripped from the released jar entirely.

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

## House style

The code is commented for *why*, not *what* — if a line looks strange, the comment explains the constraint that made it strange, and if it is not strange, it has no comment. Match that. Method and class names carry the *what*.

Two standing constraints worth knowing before you design something:

- **Nothing is destroyed.** Every path that moves items has to conserve them, including when the inventory is full and including in creative mode, where vanilla's own `Inventory.add` will happily void the remainder. Retrieval counts what actually landed rather than trusting a return value, for exactly this reason.
- **Client-side means client-side.** No packets the vanilla client would not send, no server-side companion mod. Where the mod needs authoritative state it uses the *integrated* server, which is why it is single-player only.

## Releasing

Maintainers: tag `v<version>` matching `mod_version` in `gradle.properties` and push the tag. The release workflow builds and attaches the jar.
