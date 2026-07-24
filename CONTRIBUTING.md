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
4. **Run the checks.**

   ```sh
   ./gradlew build          # compile + unit tests
   ./gradlew runGameTest    # headless server tests
   ```

   `runClientGameTest` boots the real client; run it if you touched anything visual. CI runs the first two on every pull request.

5. **Write the commit message for the person doing the bisect.** `fix: reach the far half of a double chest` beats `fix chest bug`. Prefixes in use: `feat:`, `fix:`, `perf:`, `docs:`, `test:`, `chore:`.

## House style

The code is commented for *why*, not *what* — if a line looks strange, the comment explains the constraint that made it strange, and if it is not strange, it has no comment. Match that. Method and class names carry the *what*.

Two standing constraints worth knowing before you design something:

- **Nothing is destroyed.** Every path that moves items has to conserve them, including when the inventory is full and including in creative mode, where vanilla's own `Inventory.add` will happily void the remainder. Retrieval counts what actually landed rather than trusting a return value, for exactly this reason.
- **Client-side means client-side.** No packets the vanilla client would not send, no server-side companion mod. Where the mod needs authoritative state it uses the *integrated* server, which is why it is single-player only.

## Releasing

Maintainers: tag `v<version>` matching `mod_version` in `gradle.properties` and push the tag. The release workflow builds and attaches the jar.
