# ADR-0012: TW icon asset pipeline (silhouette XML vs. coloured PNG)

**Status**: Accepted
**Date**: 2026-05-17
**Origin**: v1.0.2 redesign of `ic_tw_coord.xml` after operator feedback that the earlier W rendered as "Tn" — see end of [§History](#history) for the iteration trail.

This ADR documents the canonical pipeline for the plugin's two TW-glyph icons (silhouette Tools-menu icon + coloured Plugin/Settings icon), so the next person to redesign them can recreate every downstream artefact from a single source of truth.

## Context

The plugin ships TW branding in two distinct asset families:

| Asset | Path | Format | Where it's shown |
| --- | --- | --- | --- |
| **Silhouette** | `app/src/main/res/drawable/ic_tw_coord.xml` | Android vector drawable | Tools menu (single white tint mask applied by ATAK) |
| **Coloured** | `app/src/main/res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_tw_coord_plugin.png` | 5-density raster | Plugin Manager + `Settings → Tool Preferences` row (rendered as-is, full colour) |

Both must show the *same* TW letterform — anything else surfaces as a noticeable inconsistency to operators flipping between the Tools strip and the Settings list. The Tools-menu sibling `ic_tw_coord_goto.xml` (globe + arrow) lives in the same pipeline but does not appear in the coloured variant family.

Before this ADR, the two assets were maintained independently:

- The silhouette XML was hand-edited polygon-by-polygon.
- The coloured PNG was rendered by `tmp/icon-candidates/install.py` (a one-off script, since deleted) using a TrueType font (Arial Bold) for the TW characters on an OD hex plate.

Result: the silhouette's polygon T and W *never matched* the coloured PNG's TTF-rendered T and W; rolling out the v1.0.2 redesign (thin T crossbar + 4-stroke W with explicit V valleys) was the moment to also unify them under a single source of truth.

## Decision

**The silhouette vector XML is the single source of truth for TW geometry.** The coloured PNG is derived from it.

Two committed scripts handle every downstream regeneration:

### `scripts/render-doc-icons.py` — parse XML → doc-preview PNG

Inputs:

- `app/src/main/res/drawable/ic_tw_coord.xml`
- `app/src/main/res/drawable/ic_tw_coord_goto.xml`

Outputs:

- `docs/images/08a-tools-icon-tw-coord.png`
- `docs/images/08b-tools-icon-tw-coord-goto.png`

It parses each vector drawable via `xml.etree.ElementTree`, walks every `<path>`, and dispatches a minimal SVG-path subset (`M`, `L`, `Z` polygons; `a` arcs in the "two half-arcs that close" idiom recognised as full circles/ellipses; single `M+L` recognised as a stroked line) to PIL primitives. The output is a pure-white silhouette composited on a pure-black panel — visually identical to how the icon renders inside an ATAK Tools-menu cell, suitable for inline embedding in `docs/user-guide.md` and `docs/user-guide_zh.md`.

Unknown path commands print `! skipping unrecognised path:` rather than failing silently. When the XML grows to use curves or other commands, extend the dispatcher in `render_path()`.

### `scripts/render-plugin-icon-png.py` — parse XML → coloured 5-density PNG

Inputs:

- `app/src/main/res/drawable/ic_tw_coord.xml` (same file as above)

Outputs:

- `app/src/main/res/drawable-mdpi/ic_tw_coord_plugin.png` (48×48)
- `app/src/main/res/drawable-hdpi/ic_tw_coord_plugin.png` (72×72)
- `app/src/main/res/drawable-xhdpi/ic_tw_coord_plugin.png` (96×96)
- `app/src/main/res/drawable-xxhdpi/ic_tw_coord_plugin.png` (144×144)
- `app/src/main/res/drawable-xxxhdpi/ic_tw_coord_plugin.png` (192×192)

It parses the same silhouette XML, extracts each `<path>`'s polygon vertices (only `M`/`L`/`Z` are used by the file), and recolours by *path index*:

| Path indices in `ic_tw_coord.xml` | Family | Colour |
| --- | --- | --- |
| 0–3 | Corner brackets | `SAND_DIM` `#9D8C5F` |
| 4–6 | T (crossbar + upper stem + lower stem) | `SAND` `#C5B07A` |
| 7–10 | W (4 strokes) | `SAND` `#C5B07A` |

Polygons are then composited on top of an OD hexagonal plate (`OD #5B6B3A` fill, `OD_DARK #424E2A` 5 px outline). The native 192×192 master is downsampled with PIL's `LANCZOS` filter to the four lower densities.

**The index→family mapping is positional.** Any reorganisation of `<path>` order in `ic_tw_coord.xml` breaks the colour assignment. The script guards against silent drift with an explicit `if len(polys) != 11` check that aborts with a message telling you to update the mapping. **Update the mapping in lock-step with any XML restructuring**, otherwise the coloured icon will silently miscolour.

## Workflow for a future redesign

1. **Iterate on shape using throwaway preview scripts.** During the v1.0.2 design we wrote four one-shot scripts under `scripts/preview-*.py` to compare variants A/B/C/F/C-thin/tw-matched and produce contact sheets. These were deleted once the design was picked — they have no place in the committed pipeline.
2. **Encode the final geometry as `<path android:pathData="M … L … Z"/>` entries** in `app/src/main/res/drawable/ic_tw_coord.xml`. Keep the path order documented at the top of the file (corner brackets → T → W) so the colour-index mapping in `render-plugin-icon-png.py` stays valid.
3. **Run both render scripts:**
   ```sh
   python scripts/render-doc-icons.py
   python scripts/render-plugin-icon-png.py
   ```
4. **Sanity-check on device:** `./gradlew :app:assembleCivDebug && adb install -r …` and confirm both the Tools-menu silhouette and the Plugin Manager coloured tile read correctly. The same TW shape must appear in both surfaces.
5. **Commit** the XML edit, the regenerated doc preview, and the regenerated five `ic_tw_coord_plugin.png` files in a single commit so reviewers can see the chain of derivation in one diff.

## Tools

- **Python 3.9+** — standard library `xml.etree.ElementTree` for parsing; `re` for path tokenisation; `pathlib`/`os` for file IO.
- **Pillow (PIL)** — `Image`, `ImageDraw` for polygon/ellipse/line/resample primitives. No SVG renderer, no Cairo, no Inkscape; everything is plain raster compositing.
- **Optional dev-time iteration script** — one-off `scripts/preview-*.py` files for visual A/B testing. NOT committed once the chosen design lands.

No build-time dependency on the scripts — Android Studio / Gradle do not invoke them. Regeneration is a manual maintenance step triggered when the silhouette XML changes. This trade-off keeps the production build path free of Python / Pillow as transitive dependencies.

## Alternatives considered

- **Keep TTF font for the coloured PNG.** Rejected — the silhouette and coloured TW shapes drifted, and operators noticed at the v1.0.2 redesign moment. Reusing the polygon geometry as the single source eliminates the entire class of drift bugs.
- **Render the silhouette XML directly via `aapt2` / Android's `VectorDrawableCompat`.** Rejected for tooling-friction reasons — would require either a JVM tool chain in CI just to produce doc images, or building the APK and unzipping the rasterised assets. The PIL-based parser handles our path subset in ~150 LOC and runs anywhere Python is present.
- **Move both assets into pure SVG and convert with `rsvg-convert` / `inkscape`.** Rejected — those tools aren't installed on the dev machine, would force every contributor to install one of them, and the conversion still wouldn't sync the silhouette XML with the coloured PNG automatically.
- **Use a `LayerDrawable` XML that composites the silhouette glyph on top of a `<shape>` hex plate** to derive the coloured asset at render time. Rejected — works for the Plugin Manager surface but `LayerDrawable` doesn't downsample crisply on low-DPI devices, and we already commit per-density PNGs for sharp rendering across the install base. The PIL pipeline matches that 5-density expectation directly.

## Consequences

**Positive:**

- Single source of truth for TW geometry — no more silent drift between the two icon families.
- Both regeneration scripts are pure Python + Pillow, no JVM / Cairo / external SVG tooling required.
- The `! skipping unrecognised path:` warning in `render-doc-icons.py` flags any future XML extension that the parser doesn't handle, preventing silent visual regressions.
- The path-index colour mapping check in `render-plugin-icon-png.py` aborts loudly when the XML restructures, instead of producing wrong-coloured tiles.

**Negative:**

- The path-index mapping is positional — a contributor who reorders `<path>` elements in the XML for stylistic reasons will break the coloured render until they update the script. The mapping is documented in the file's docstring + this ADR, and the count-check aborts loudly, but the coupling is real.
- The SVG-path parser supports only `M`/`L`/`Z` + the "two-arc closed ellipse" idiom. Any new path command in the XML (curves, multi-segment arcs, relative commands) needs an `if/else` branch added to `render_path()`.
- Coloured PNG regeneration is a manual step, not a Gradle task. A future contributor changing the silhouette XML must remember to re-run `scripts/render-plugin-icon-png.py`. This ADR + the script's docstring are the primary mitigations; consider promoting it to a `./gradlew renderPluginIcons` task if the manual step is missed in practice.

## History

This pipeline replaces three prior generations:

1. **v1.0.0 — community demo cert + `tmp/icon-candidates/install.py`**: The coloured PNG was generated by a one-off Python script that used Arial Bold for the TW glyph. Silhouette XML was hand-edited polygons. Two different sources, drift waiting to happen.
2. **v1.0.0 polish — silhouette adoption**: The Tools-menu icons became silhouettes (white-only on transparent), still hand-edited polygons. Coloured PNG unchanged.
3. **v1.0.2 redesign**: Operator feedback flagged the W as illegible — original chunky shape with a shallow inner-V dip read as "Tn". Iteration produced variants A/B/C/F/C-thin/tw-matched; the picked design uses 4 thin diagonal W strokes with explicit V valleys at the bottom and the T slimmed to match. This commit ships that design AND inverts the asset pipeline so both icons derive from the silhouette XML.

## Links

- Silhouette source: [`app/src/main/res/drawable/ic_tw_coord.xml`](../../app/src/main/res/drawable/ic_tw_coord.xml)
- Companion silhouette: [`app/src/main/res/drawable/ic_tw_coord_goto.xml`](../../app/src/main/res/drawable/ic_tw_coord_goto.xml)
- Renderers: [`scripts/render-doc-icons.py`](../../scripts/render-doc-icons.py), [`scripts/render-plugin-icon-png.py`](../../scripts/render-plugin-icon-png.py)
- Doc surface: `docs/user-guide.md` §4 and `docs/user-guide_zh.md` §4
- Prior icon ADRs: none — icon work tracked inline in earlier `feat(003)` commits before this ADR was written.
