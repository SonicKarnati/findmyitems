# findmyitems

You know you have iron somewhere. You built eleven chests and you were disciplined about it for about a week.

`findmyitems` remembers what was in every container you have opened, lets you search all of it at once, and hands you the item without making you walk back and open the chest yourself.

Client-side Fabric mod for Minecraft Java 26.2. **Single-player worlds only** — see [below](#why-single-player-only) for why that is on purpose.

## Installing

You need [Fabric Loader](https://fabricmc.net/use/) 0.19.3 or newer, and these mods in your `mods/` folder:

- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Mod Menu](https://modrinth.com/mod/modmenu)
- [Cloth Config](https://modrinth.com/mod/cloth-config)

Then drop `findmyitems-*.jar` from the [releases page](https://github.com/SonicKarnati/findmyitems/releases) in next to them.

Optional but a good pairing: [Shulker Box Tooltip](https://modrinth.com/mod/shulkerboxtooltip). This mod indexes what is inside your shulker boxes; that one shows you on hover.

## How it works

**Open chests. That is the whole setup.** Every container you open is remembered — what was in it, where it is, and when you last saw it. Nothing is scanned behind your back; if you have never opened it, it is not in the catalog.

**Press `B`** to open the catalog. Three tabs, and `Ctrl+1` / `Ctrl+2` / `Ctrl+3` (`Cmd` on macOS) jump between them:

### Items

Everything you have seen, and the nearest container holding it. Three buttons on each row:

| Button | What it does |
| --- | --- |
| 🧿 Ender eye | Outlines that container in the world — through walls, so you can see it from here |
| 🪣 Hopper | Takes the amount in the box straight into your inventory |
| 📦 Chest | Puts items back where they came from |

Taking and putting back only work when you are close enough to have clicked the chest yourself. Out of reach, the buttons grey out and the ender eye still works — go and look.

Putting things back is deliberately narrow: it only offers to return items that container **already stocks**. The mod does not guess where an unfamiliar item belongs, because guessing means scattering your inventory across the nearest chest.

### Containers

Every container you have opened, nearest first. Your ender chest is pinned to the top even when it is empty.

### Crafting

Type an item, or leave the box empty and pick from the list of everything craftable. You get its full material tree, already charged against what your chests hold:

- **Indented rows** are sub-crafts — the planks under the chest, the logs under the planks
- **Green** means your chests already cover it
- **Red** means you have to go and find it

Sticks you already have stay a single row; it does not walk down to logs for something you have forty of in a barrel.

## Searching

The box searches names, ids and tooltips together, so `smite` finds the sword and `iron` finds the ingots, the blocks and the pickaxe.

- **Enchantments work either way you write them** — `smite 4` and `smite iv` both find the same sword
- **Items that differ only by enchantment stay separate** — two diamond swords named "Bee Stinger" are two rows if one is Sharpness and the other is Smite, because they are not interchangeable
- **Shulker boxes are see-through** — items inside a shulker inside a chest are indexed and retrievable, four levels deep

There is also a filter box on every ordinary container screen. Type in it and non-matching slots dim, so what you want stands out. Nothing moves; it is only a highlight.

## Settings

In Mod Menu, under findmyitems:

| Setting | What it does |
| --- | --- |
| **Rescan interval** | How often nearby remembered chests are quietly re-checked, in seconds. `0` turns it off, and the catalog then only updates when you open something. |
| **Search distance** | How far that re-check reaches, in blocks. `0` is unlimited. Lower it if you have a very large base and notice a hitch. |

The catalog keybind lives in the ordinary Controls screen, with everything else. It is `B` by default.

## What it does not do

**Multiplayer.** See below.

**Modded containers.** Chests, trapped chests, barrels, ender chests and shulker boxes. A backpack from another mod is not indexed.

**Chests you have not opened.** Knowing what is inside a container you have never looked in is not something a client can honestly do.

**Chests that changed while you were away.** If a hopper drained the barrel since you last opened it, the catalog will say so only after the next re-scan reaches it — and only if it is loaded and in range. Taking something that is no longer there fails cleanly rather than inventing it.

### Why single-player only

The catalog is a memory of containers **you** opened. In a world only you can change, that memory stays true. On a server, it goes stale the moment another player touches a chest — and there is no reliable way for your client to find out that it has. You would get a catalog that is confidently wrong, which is worse than no catalog at all.

So on a multiplayer server the mod stands down completely: no indexing, no filter box, and the catalog keybind tells you why. This is a decision, not a to-do.

## Something wrong?

[Open an issue.](https://github.com/SonicKarnati/findmyitems/issues) There is a template that asks for what is actually needed to find the cause. A catalog showing the wrong count is the most useful kind of report — say what changed the chest between the last time you opened it and the moment the catalog lied.

Want to change the code? [`CONTRIBUTING.md`](CONTRIBUTING.md) has the build, the tests and the conventions.
