# Changelog

## 0.1.0 — unreleased

First release. Single-player only.

### The catalog

- `B` opens a catalog of every container you have opened, in three views: Items, Containers and Crafting.
- Search covers names, ids and tooltips, including enchantment levels in either spelling (`smite 4`, `smite iv`).
- Take, put back and locate, straight from the list. Taking opens the container on the server for real — the lid swings and the sound plays — without putting its GUI in your way.
- Locating draws a pulsing gold outline around the container, visible through walls.
- Items inside shulker boxes inside chests are indexed and retrievable, four levels deep.
- The Crafting view plans a material tree charged against what your chests already hold, listing only items that can actually be crafted.
- A filter box on every vanilla container screen dims the slots that do not match.

### Notes

- The index is stored per save directory and per player, so two worlds sharing a name do not share a catalog.
- On multiplayer servers the mod stands down entirely. See the README for why.
