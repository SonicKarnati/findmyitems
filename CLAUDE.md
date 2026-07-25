# findmyitems — instructions for Claude

## Commits

Do not add `Co-Authored-By: Claude` or any AI co-author trailer to git commit messages. Do not add "Generated with Claude Code" or any similar attribution line to pull request descriptions.

## Build and test

```sh
./gradlew build          # compile + JUnit
./gradlew runGameTest    # headless server tests — run these before saying a change works
```

`runClientGameTest` boots a real client and opens a window; ask before running it.

Full conventions, house style and the testbed live in [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Two standing constraints

- **Nothing is destroyed.** Any path that moves items must conserve them — including a full inventory, and including creative mode, where vanilla's `Inventory.add` voids the leftover.
- **Single-player only, on purpose.** The mod stands down on multiplayer servers. Do not add a partial multiplayer mode; see the README for the reasoning.
