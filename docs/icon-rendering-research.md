# Icon rendering in findmyitems — research & recommendation

**Question:** what's the best way to draw icons in this mod without pulling in extra
dependencies? Fabric API is acceptable (and already a dependency); a third‑party GUI
library is not.

**Short answer:** you don't need anything new. There are two kinds of "icon" and both
are handled by vanilla Minecraft — Fabric API only supplies the *screen hooks*, not the
icons themselves. There is no Fabric "icon library," and none is needed.

- **Item / block icons** → `GuiGraphicsExtractor.item(stack, x, y)` (already used).
- **Custom UI icons** (magnifying glass, arrows, badges) → ship a PNG as a GUI *sprite*
  and draw it with `GuiGraphicsExtractor.blitSprite(...)`, or reuse a vanilla sprite by
  its `Identifier` for zero new assets.

Everything below is for MC 26.2 / the render pipeline this project targets
(`GuiGraphicsExtractor`, verified against the mapped client jar).

---

## 1. Item and block icons — already free

Anything that is a real Minecraft item or block renders itself. No assets, no atlas
work, no dependency:

```java
graphics.item(itemStack, x, y);        // 16×16 icon, model + enchant glint + count
graphics.item(itemStack, x, y, seed);  // seed variant for randomised models
```

`CatalogScreen` (`ItemListWidget.Entry.extractContent`) and the new inventory search
overlay both rely on this. `buildStack(StackKey)` in `CatalogScreen` shows how to turn a
stored item id + component JSON back into a renderable `ItemStack`. **If the "icon" you
want is an item, this is the whole story — stop here.**

## 2. Custom UI icons — vanilla GUI sprites (recommended)

For chrome that isn't an item (a search glyph, a "take" arrow, a source-type badge), the
engine-supported path is the **GUI sprite atlas**. You drop a PNG in the resource pack;
vanilla stitches it into the `gui` atlas at load and lets you draw it by `Identifier`.

**Ship the texture** (this mod's assets live under `src/main/resources`):

```
src/main/resources/assets/findmyitems/textures/gui/sprites/icon/search.png
```

**Draw it:**

```java
import com.mojang.blaze3d.pipeline.RenderPipelines;   // vanilla pipelines
import net.minecraft.resources.Identifier;

private static final Identifier SEARCH_ICON =
        Identifier.of("findmyitems", "icon/search"); // path is relative to .../sprites/

graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SEARCH_ICON, x, y, 16, 16);
```

Notes:
- The atlas resolves `Identifier` → `assets/<namespace>/textures/gui/sprites/<path>.png`,
  so `icon/search` maps to the file above.
- 9‑slice / tiling (stretchable borders, like the vanilla button background) is supported
  via a companion `.mcmeta` in `.../sprites/.../` — only needed for resizable panels, not
  fixed 16×16 glyphs.
- This is pure vanilla + resource pack. Fabric API is not involved in the drawing at all.

## 3. Custom UI icons — reuse vanilla sprites (zero new assets)

If a stock icon already fits, reference its `Identifier` directly and ship **nothing**.
Vanilla widget sprites live at `minecraft:.../textures/gui/sprites/...` and can be blitted
the same way. Handy for arrows, checkmarks, warning/slot backgrounds, scroller knobs, etc.
Browse them in the unpacked client jar under `assets/minecraft/textures/gui/sprites/` to
copy the exact path. (Do confirm the id per version — sprite paths get reorganised between
releases.)

## 4. Options considered and rejected

| Approach | Verdict |
| --- | --- |
| **Vanilla item render** (`item(...)`) | ✅ Use for item icons. Already in the codebase. |
| **Vanilla GUI sprites** (`blitSprite` + PNG) | ✅ Use for custom UI icons. No dependency. |
| **Reuse vanilla sprite `Identifier`s** | ✅ Best when a stock icon fits — zero assets. |
| **Raw `blit(RenderPipeline, Identifier, ...)` to a loose texture** | ⚠️ Works, but you manage the texture yourself and lose atlas batching. Prefer sprites. |
| **Bitmap/`unihex` font glyphs rendered as text** | ⚠️ Powerful for inline-with-text icons, but a heavier setup (font provider + glyph sheet). Overkill here. |
| **Third‑party GUI libs** (owo-lib, etc.) | ❌ Extra dependency — explicitly out of scope. |
| **"Fabric API icon module"** | ❌ Does not exist. Fabric API gives screen/render *hooks* (`ScreenEvents`), not icon assets. |

## 5. What Fabric API actually contributes

Fabric API's role for GUI work is the hook layer, not icons:
- `ScreenEvents` (`AFTER_INIT`, `afterExtract`, `afterBackground`) — inject widgets and
  draw overlays on *vanilla* screens without a mixin. The inventory search overlay uses
  these.
- `ScreenKeyboardEvents` / `ScreenMouseEvents` — intercept input on those screens.
- `Screens.getWidgets(screen)` — the mutable widget list to add an `EditBox`/button into.

The actual pixels always come from vanilla `GuiGraphicsExtractor` (`item`, `blitSprite`,
`blit`, `fill`, `text`).

## Recommendation

1. Item icons: keep using `graphics.item(...)`.
2. Custom UI icons: add PNGs under `assets/findmyitems/textures/gui/sprites/…` and
   draw with `blitSprite`; reuse vanilla sprite ids where one already fits.
3. Add no new dependencies — Fabric API (already present) plus vanilla covers all of it.
