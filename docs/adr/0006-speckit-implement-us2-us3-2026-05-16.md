# ADR-0006: Second `/speckit-implement` pass — US2 + US3 complete

**Status**: Accepted
**Date**: 2026-05-16
**Origin**: `/speckit-implement` on feature `001-tw-coord-display`

## Context

Continuation of the first MVP pass (ADR-0005). This pass picks up at
T037 and lands US2 (own-position readout, FR-002 / FR-008 / FR-010)
and US3 (settings fragment + live unit / language switching,
FR-004…FR-006 / FR-017 / FR-018) in a single change-set. Per
Constitution Principle V every `/speckit-implement` run with a non-
trivial change requires an ADR; this is the entry for this pass.

The acceptance plan (`quickstart.md` §7) covers all three user
stories now; on-device verification is the gating step before
declaring v1 feature-complete.

## Decisions and discoveries

### D1 — `ToolsPreferenceFragment` lives in `com.atakmap.app.preferences`, not `com.atakmap.android.tools`

Subagent A's R5 finding cited the import as
`com.atakmap.android.tools.ToolsPreferenceFragment`, mirroring older
docs. Compilation failed; `unzip -l main.jar | grep
ToolsPreferenceFragment` showed the class is actually at
`com.atakmap.app.preferences.ToolsPreferenceFragment` in ATAK-CIV
5.7.0.3.

Documentation drift: `research.md` R5 should be updated on the next
`/speckit-analyze`.

### D2 — `MapEvent.ITEM_REFRESH` (not `ITEM_CHANGED`) for self-marker position

Research R4 said to subscribe to `MapEvent.ITEM_CHANGED` filtered on
`MapView.getSelfMarker().getUID()`. The current SDK exposes
`MapEvent.ITEM_REFRESH` for marker-property updates including
position; `ITEM_CHANGED` may exist as a constant but is not the
canonical signal in 5.7.0.3.

Documentation drift: `research.md` R4 should be folded back at next
analyze. The shape of the listener (UID filter on self-marker) is
unchanged.

### D3 — `SelfMarkerSubscriber` first-event init must NOT use `Long.MIN_VALUE` sentinel

The original draft initialised `lastEmittedAt = Long.MIN_VALUE` and
guarded the first emit on `now - lastEmittedAt >= debounceMs`. For
`now = 0`, that subtraction silently overflows to a negative number
in Java's two's-complement arithmetic, so the guard always failed
and `onFreshFix` never fired — three of the four unit tests went red
on first run.

Fix: use an explicit `haveEmitted` boolean. Same pattern applied to
`lastReceivedAt` / `haveReceived` for the stale-check path.
Constitution Principle II (TDD) earned its keep here — the bug was
caught by the RED tests authored just before the implementation.

### D4 — Permission check uses `Context.checkSelfPermission` (no `ContextCompat`)

T040 calls for
`ContextCompat.checkSelfPermission(ACCESS_FINE_LOCATION)`. We excluded
`androidx.core` from the compile classpath in
`app/build.gradle` to match ATAK's bundled AndroidX versions. Since
the plugin's `minSdk` is 26 and `Context.checkSelfPermission(String)`
has been API-23+, the framework method is sufficient — no
`ContextCompat` needed.

### D5 — Locale-listener-before-widget-listener guarantee via single combined listener

The contract (`contracts/preference-store.md`) requires that the
locale-rebuild fire BEFORE the widget repaints, to avoid a window
where the widget reads stale `Strings`. We implement this with a
single combined `PreferenceStore.Listener` in `TwCoordMapComponent`
that does locale-rebuild then render in sequence. Simpler than two
listeners with order constraints, and idiomatic Java.

### D6 — PreferenceStore uses `commit()` not `apply()`

The contract demands synchronous on-UI-thread dispatch (subscribers
must see the change before the setter returns). Android's
`SharedPreferences.Editor.commit()` is synchronous; `apply()` is
async. Since the payload is two small enum strings the `commit()`
disk write is fast enough on the UI thread.

### D7 — `tap-to-open-settings` for NO_PERMISSION row deferred to T051

T040 calls for a tap handler that opens
`Settings.ACTION_APPLICATION_DETAILS_SETTINGS` when the row is in
the NO_PERMISSION state. The widget currently *renders* the
NO_PERMISSION state correctly (grey "no permission" text) but does
not have a tap-to-settings handler — that work overlaps with the
clipboard-copy tap handler in T051 (Polish phase) and is cleaner to
bundle together.

## Files added / changed this pass

```
app/src/main/java/com/atakmap/android/twcoord/
  SelfMarkerSubscriber.java                     (new — T038)
  TwCoordPreferenceFragment.java                (new — T046)
  TwCoordMapComponent.java                      (rewritten — T039/T040/T047/T048)
  i18n/LocaleOverride.java                      (new — T044)
  prefs/PreferenceStore.java                    (new — T043)
app/src/main/res/
  xml/preferences.xml                           (new — T045)
  values/arrays.xml                             (new — supports T045)
app/src/test/java/com/atakmap/android/twcoord/
  SelfMarkerSubscriberTest.java                 (new — T037)
  i18n/LocaleOverrideTest.java                  (new — T042)
docs/ui/settings-fragment.md                    (new — T048a)
docs/adr/0006-speckit-implement-us2-us3-2026-05-16.md  (this file)
tasks.md                                        (status table updated)
```

T041 (instrumented `PreferenceStoreTest`) is NOT included in this pass
— it requires a connected Android device and lives in
`androidTest/`. It will land in the next pass alongside T049
(`ClipboardCopyTest`) and T050 (`WidgetRenderTest`).

## Consequences

**Positive:**

- Full feature surface (US1 + US2 + US3) now in code. The plugin
  shows both readouts, supports unit switching, supports language
  override.
- All four `pwa_map` golden vectors continue to pass; SelfMarker
  debouncer + stale detector pass their RED-then-GREEN tests; locale
  fallback chain covered by 12 explicit cases including the weird
  `zh-Hans-SG`, `zh-Hant-HK`, `ko-KR`, `fr-FR` paths.
- 31 JVM unit tests, 0 failures. APK assembles to 172 KB.
- Constitution Principle III honoured: `docs/ui/settings-fragment.md`
  ships in the same commit as the fragment code.
- Constitution Principle V honoured: this ADR.

**Negative:**

- No instrumented tests yet (T041, T049, T050, T052).
- No JMH micro-bench yet (T032).
- No fps-impact benchmark yet (T061 / SC-007).
- On-device verification (T059) still pending — until then we cannot
  claim US1/US2/US3 acceptance scenarios pass on real ATAK.
- Two minor research-doc drifts (D1 ToolsPreferenceFragment package,
  D2 ITEM_REFRESH event) are unfolded here; folding back is a follow-
  up for the next `/speckit-analyze`.

## Links

- Commit immediately following this ADR's write.
- Prior commits: `b9cfd2b` (Phase 1+2+3 math), `739c712` (US1 widget).
- ADR-0005 (first implement pass).
- `tasks.md` — Implementation status block updated for this pass.
