# ADR-0007: Mimic ATAK native widget styling via SDK bytecode reverse-engineering

**Status**: Accepted
**Date**: 2026-05-16
**Origin**: On-device iteration with the user, following deployment of `739c712`.

## Context

After installing the plugin on a Galaxy Tab S10+ with ATAK-CIV 5.7.0.3, the
on-map readout widget was functional but visually mismatched against ATAK's
native bottom-corner readouts (Eye Alt, MGRS, scale bar). Our text was
larger, the box padding looked different, and our widget drifted away from
the screen edge differently than the natives.

The user's ask was concrete:

> 剩下對齊框框 與系統原本的座標顯示的框框 一樣排好
> 有辦法知道 左下角 Eye Alt: 系統排版的方式嗎 可以仿照一樣的方式嗎?

The ATAK SDK ships compiled `main.jar` without source. Public Javadoc covers
the abstract `TextWidget` / `LinearLayoutWidget` classes but does **not**
document the exact construction parameters ATAK uses for its own native
overlay widgets. The visible source samples (`meshtastic_atak`,
`helloworld`, etc.) use ad-hoc styling — none replicate the EyeAlt look.

## Decision

**Reverse-engineer the SDK's own widget-construction sequence by decompiling
the relevant class from `main.jar`, then replicate the exact parameters in
our widget.**

The discovery procedure that worked for this case (and is the recommended
recipe for any future "match the native look" task):

1. **Locate a string that appears in the native widget's text.** Here:
   `"Eye Alt"` — a literal that should appear in whichever class builds the
   widget.

2. **Brute-force search the SDK jar for the literal.** Two-step:
   ```bash
   cd /tmp && unzip -q <SDK>/main.jar -d dump
   grep -lr "Eye Alt" dump/   # -> com/atakmap/android/navigation/widgets/NavWidgetsMapComponent.class
   ```
   This pinpoints the class file. It costs ~30 seconds and a few hundred
   megabytes of disk space.

3. **Decompile that class to bytecode (`javap -c`)**, then grep for the
   structural markers around the literal. Bytecode is enough — no need to
   reach for jadx/cfr. The constructor calls are visible as `invokespecial`
   and field assignments as `putfield`:
   ```bash
   javap -p -c com/atakmap/android/navigation/widgets/NavWidgetsMapComponent.class \
       | grep -B 2 -A 2 -E "TextWidget|MapTextFormat|setMargins|setPadding"
   ```
   The output sequence shows:
   ```
   getstatic     Field android/graphics/Typeface.DEFAULT_BOLD
   bipush        -2
   invokestatic  Method com/atakmap/android/maps/MapView.getTextFormat:(Landroid/graphics/Typeface;I)Lcom/atakmap/android/maps/MapTextFormat;
   ...
   new           class com/atakmap/android/widgets/TextWidget
   dup
   ldc           String                                  // empty
   iconst_2                                              // background style 2
   invokespecial Method TextWidget."<init>":(Ljava/lang/String;I)V
   ...
   ldc           String EyeAltTextWidget
   invokevirtual Method TextWidget.setName
   ...
   ldc           float 16.0f
   ldc           float 16.0f
   fconst_0
   ldc           float 16.0f
   invokevirtual Method TextWidget.setMargins:(FFFF)V
   ```
   The whole construction story is right there.

4. **Translate bytecode back to Java in our source**, with a comment
   pointing at the SDK class so the next maintainer can re-derive it.

For the EyeAlt widget specifically, the parameters extracted are:

| Parameter | Value | Note |
|---|---|---|
| `MapTextFormat` | `MapView.getTextFormat(Typeface.DEFAULT_BOLD, -2)` | Shared bold font, "default − 2" px |
| `TextWidget` background | `2` | NOT `TextWidget.TRANSLUCENT_BLACK` (which is `0`); style `2` is the native semi-translucent box |
| `setMargins(L, T, R, B)` | `(16, 16, 0, 16)` | Right = 0 since EyeAlt sits flush to the right side of the BOTTOM_LEFT anchor |
| `setPadding(...)` | not called | Defaults are correct |

We apply identical values for our MAP widget (BOTTOM_LEFT) and mirror
left/right margins for our ME widget (BOTTOM_RIGHT). The TGT widget at
TOP_RIGHT also uses the mirrored variant.

## Alternatives considered

- **Eyeballing values until they look right.** What we tried first
  (`MapTextFormat(Typeface.MONOSPACE, false, 14)`, padding 8, margins 8).
  The result was visibly off — bigger font, taller box, wrong corner offset
  — and not stable across the SDK upgrade cycle. Rejected.

- **Asking on the ATAK Discord / SDK forum.** Slower turnaround than
  spending five minutes with `javap`. Rejected for this round; useful as a
  fallback if the SDK changes its layout in a future version.

- **Decompiling to high-level Java with jadx or cfr.** Cleaner output but
  the bytecode form was already legible enough for our needs. We did not
  introduce another tool dependency.

## What we found about the *other* corners (BOTTOM-RIGHT and TOP-RIGHT)

When the user asked us to apply the same technique to mirror ATAK's
self-callsign card (BOTTOM-RIGHT) and the cursor-on-target callout
(TOP-RIGHT), the same bytecode-grep recipe pointed at:

- BOTTOM-RIGHT self-callsign card →
  `com.atakmap.android.navigation.SelfCoordBottomBar`.
  This class extends `android.widget.FrameLayout` and is rendered with
  Android `TextView`s inflated from an XML layout — **not** with the
  `MapWidget` family.

- TOP-RIGHT cursor-on-target callout → similarly Android-view based
  (search did not yield a `MapWidget` constructor for it).

This rules out a "copy the constants" mirror for those two corners: our
plugin's overlay lives on the GL rendering layer (`MapWidget` →
`TextWidget`), and ATAK's right-side widgets live on the Android view
hierarchy. The two render pipelines have no shared geometry or
typography.

**Decision for the right-side rows**: keep them in the `MapWidget`
family (consistent with our BOTTOM-LEFT row and with ADR's "single
in-plugin renderer" stance) and accept that they will look visually
distinct from ATAK's own neighbouring Android widgets. EyeAlt-style is
our consistent visual language; the cost of trying to match the
Android-view neighbours is rewriting the entire plugin to inject into
ATAK's view tree, which is well out of scope for v1.

If a future requirement demands pixel-perfect parity, the route is:
inflate an Android view (custom layout XML) and add it directly to
ATAK's view hierarchy via the same hook `SelfCoordBottomBar` uses.
That's a separate ADR if it ever comes up.

## Consequences

**Positive:**

- Our widget visually merges with ATAK's native overlay. Users no longer
  see two different "looks" in the same corner.
- The discovery recipe (string-literal → grep → `javap -p -c`) is now a
  documented technique we will reuse for the next "match the native look"
  question (likely candidates: the self-callsign card in BOTTOM_RIGHT,
  the cursor-on-target callout in TOP_RIGHT).
- Background style `2` is now in our vocabulary; future widgets we create
  can opt for it (matches native) vs. `TRANSLUCENT_BLACK` (our previous
  default, more obvious overlay look).

**Negative:**

- Our styling now silently depends on internal ATAK constants
  (`-2` size hint, background style `2`). If the SDK changes their meaning
  in a future release, we regress without a build error. Mitigation: the
  ADR cites the exact SDK class so re-derivation is fast.
- The technique relies on the SDK jar shipping with debugging-friendly
  bytecode. If a future SDK enables aggressive optimization on its own
  jar, the literal-grep step might miss the target.

## Links

- SDK class:
  `<ATAK-SDK>/main.jar :: com.atakmap.android.navigation.widgets.NavWidgetsMapComponent`
- Spec: FR-001, FR-012 (visual identification).
- Constitution Principle III (UX Consistency) — this is the canonical way
  to honour it for plugin UI sitting next to ATAK natives.
- Related: `docs/ui/readout-widget.md` lists the on-device colour palette
  and per-state styling; this ADR covers the typography / box shape.
