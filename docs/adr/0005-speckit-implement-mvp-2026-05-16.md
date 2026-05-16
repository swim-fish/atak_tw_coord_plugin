# ADR-0005: First `/speckit-implement` pass — MVP through US1

**Status**: Accepted
**Date**: 2026-05-16
**Origin**: `/speckit-implement` on feature `001-tw-coord-display`

## Context

Per Constitution Principle V, every `/speckit-implement` run that
results in a non-trivial change MUST be recorded as an ADR. This
first pass produced a working, signed civ-debug APK; that is non-
trivial.

The scope chosen for this pass was the MVP slice: Phase 1 Setup,
Phase 2 Foundational, and Phase 3 US1 (map-centre readout). Phases
4-6 (US2 own-position, US3 settings, Polish) are deferred to a later
`/speckit-implement` continuation — the natural pause point is on-
device verification of US1's three acceptance scenarios.

## Decisions and discoveries

### D1 — proj4j must be constructed with explicit parameter strings, not EPSG-name lookup

`Projections.java` builds both the WGS84 and TWD97_Z121 CRS via
`CRSFactory.createFromParameters(name, projString)`. The original
draft used `createFromName("EPSG:4326")` for WGS84, which failed at
runtime with `IllegalStateException: Unable to access CRS file:
proj4/nad/epsg` — the Android Gradle plugin does not merge proj4j's
classpath EPSG database into the JVM unit-test classpath.

The explicit parameter-string approach removes the EPSG database
dependency entirely and is more transparent for code review. Both
strings (WGS84 and TWD97_Z121) are documented inline.

### D2 — `sdk.path` is the magic property the takdev plugin needs

The atak-takdev Gradle plugin discovers the ATAK SDK location from
the `sdk.path` property in `local.properties`. Setting only
`takdev.plugin` (the plugin jar path) is *not* sufficient — the
plugin compiles fine without ATAK SDK classes on the classpath
otherwise, producing baffling "cannot find symbol" errors for
`AbstractPlugin`, `MapView`, etc.

This is now documented in `local.properties` and the gotcha is worth
calling out in `quickstart.md` (follow-up task).

### D3 — Widget uses `LinearLayoutWidget` + 2× `TextWidget`

`TwCoordWidget` was originally drafted as extending `MapWidget` and
self-anchoring. The final shape is simpler: it owns a vertically
oriented `LinearLayoutWidget` container with two `TextWidget` rows.
The container is added to `RootLayoutWidget.TOP_RIGHT` once on
`attach()` and removed on `detach()`. Per-row text and colour update
through `setText(...)` / `setColor(...)` from `render(...)`.

This matches `TextWidget`'s API as exposed by ATAK-CIV 5.7.0.3 SDK
(`unzip -l main.jar`), and it gives us the cleanest path for the
out-of-range fallback rendering (multi-line via `\n`).

### D4 — `MapEvent.MAP_MOVED` + `MAP_SCALE` (not `MAP_BOUNDS_CHANGED`)

Research R3 originally suggested listening to
`MapEvent.MAP_BOUNDS_CHANGED`. During implementation, inspection of
the SDK's `MapEvent` constants showed `MAP_MOVED` and `MAP_SCALE`
are the canonical pan + zoom signals; `MAP_BOUNDS_CHANGED` may exist
in a newer SDK but is not the documented pair for 5.7.0.3. Both
listeners are registered; either firing triggers a render.

This is a minor research-doc drift that should be folded back into
`research.md` R3 on the next analyze cycle.

### D5 — Number formatting uses locale-aware grouping

`Formatter.intMetres(...)` uses `NumberFormat.getIntegerInstance
(locale)` which groups digits with the locale's grouping separator.
Under `Locale.ROOT` this produces `306,963m` rather than `306963m`.
The original `FormatterTest` asserted on the un-grouped form; it was
updated to match the contract wording ("locale-aware grouping").

This matches `contracts/coordinate-formatter.md` and is the right
default — Taiwanese, Japanese, and English readers all expect
thousands separators in 6-7 digit numbers.

### D6 — JMH micro-bench (T032) deferred this pass

T032 calls for a JMH benchmark asserting
`CoordinateConverter.convert(...) ≤ 50 µs`. Analyze finding F9
already flagged that JMH wants its own source-set; rather than
introduce that complication into the first build, the bench is
deferred to a later pass. The build still has the property assert in
`contracts/coordinate-converter.md`; it just is not yet machine-
verified.

## Files added / changed (this pass)

- 40 files added across `/`, `app/`, `docs/ui/`, `gradle/`.
- 1 ADR added (this file).
- `tasks.md` annotated with a "Implementation status (2026-05-16)"
  block and changelog row.

No spec / plan / contract content was modified — the design held
intact through to a green build.

## Consequences

**Positive:**

- Working civ-debug APK in 18 s (incremental) from a clean Gradle
  cache.
- 22 JVM unit tests covering all four pwa_map golden vectors for
  TWD97, TWD67, Taipower 9-char, the converter facade's in/out-of-
  range branches, and the formatter's clipboard-equality contract.
- Constitution Principle II (TDD) honoured: every behavioural Java
  class was authored against a RED test that turned GREEN before the
  next class began.
- Constitution Principle III (UX consistency): `docs/ui/readout-
  widget.md` ships in the same change-set as the widget code.
- Constitution Principle V (ADR cadence): this file records the
  decisions; the spec ADRs from `/speckit-analyze` (0001-0003) and
  the analyze record (0004) already exist.

**Negative:**

- US1 has not been verified on a real ATAK device. The acceptance
  scenarios in `quickstart.md` §7 are the gating step before
  claiming US1 done.
- US2 and US3 entirely untouched — the plugin currently has *no* own-
  position readout and *no* settings page. The widget shows only the
  map-centre row.
- T032 (JMH bench) is debt; the converter-latency budget is
  asserted in the contract but not enforced by an automated check.
- Research-doc drift: `research.md` R3 mentions
  `MAP_BOUNDS_CHANGED` while the implementation uses
  `MAP_MOVED + MAP_SCALE`. Worth aligning on the next `/speckit-
  analyze`.

## Links

- Commit: `b9cfd2b` (Phase 1+2+3 math) followed by the commit
  carrying this ADR.
- APK: `app/build/outputs/apk/civ/debug/ATAK-Plugin-atak_tw_coord_
  plugin-1.0.0-b9cfd2bb-5.7.0.3-civ-debug.apk`.
- ADR-0001..0004 (prior).
- `tasks.md` "Implementation status (2026-05-16)" block.
