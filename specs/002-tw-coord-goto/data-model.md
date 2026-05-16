# Phase 1 Data Model — Taiwan Coordinate Input ("GoTo") Page

**Date**: 2026-05-16 | **Feature**: 002-tw-coord-goto | **Plan**: [plan.md](./plan.md)

This document describes the runtime + persisted entities for the
input-page feature. All runtime entities live under
`app/src/main/java/com/atakmap/android/twcoord/goto/` and follow the
existing convention: immutable Java records / final classes, pure JVM,
zero ATAK dependencies in the converter / store layer (the
`DropDownReceiver` is the only class that touches the SDK directly).

Cross-reference: feature 001's data model
([../001-tw-coord-display/data-model.md](../001-tw-coord-display/data-model.md))
defines `Wgs84`, `Twd97Tm2`, `Twd67Tm2`, `TaipowerCode`,
`CoordinateUnit`, and `LanguageOverride`. This feature **reuses them
unchanged** and adds the entities below.

---

## 1. `CoordinateInput` (sealed-style hierarchy)

The structured input being edited on the page. Three variants — one
per unit — share a common interface but carry unit-specific fields.

```text
sealed interface CoordinateInput {
    CoordinateUnit unit();           // never null
    String displayString();          // for the "Recent" list rendering
}

record CoordinateInput.Taipower(String rawValue) implements CoordinateInput
    // rawValue: original operator string, post-normalisation
    //           (uppercase, single internal space, trimmed).

record CoordinateInput.Twd97(int easting, int northing, int zone)
    implements CoordinateInput
    // easting/northing: integer metres in TWD97 TM2.
    // zone: 121 or 119.

record CoordinateInput.Twd67(int easting, int northing, int zone)
    implements CoordinateInput
    // Same shape as Twd97 but the values flow through the 4-param
    // shift before re-projection.
```

**Construction rules**:
- Taipower `rawValue` MUST be 9 or 11 visible chars after stripping
  optional single internal space (`H7509 DB4016` → ok, `H7509DB4016`
  → ok). Letters must be `A`–`X` (`Y` / `Z` are reserved per
  ADR-0001).
- TWD97 / TWD67 easting MUST be 6 or 7 digits; northing MUST be 7
  digits. Zone MUST be `121` or `119`.
- Any value violating these rules is rejected at construction and the
  caller pivots to `ParseResult.Invalid` instead of attempting
  conversion.

**Lifecycle**: created either by `CoordinateParser` (operator-typed
input → `CoordinateInput`) or by `MapCenterAutoFillStream` (current
`Wgs84` → `CoordinateInput` formatted for the active tab). Never
mutated; used as the key for the `RecentEntryStore`.

---

## 2. `ParseResult` (sealed-ish wrapper)

Output of the inverse-converter pipeline. Mirrors feature 001's
`ConversionResult` but in the opposite direction.

```text
ParseResult = Ok(input, wgs84)
            | Invalid(unit, reason)
            | OutOfRange(unit, attemptedWgs84)
```

| Variant | Carries | Trigger |
|---|---|---|
| `Ok` | `CoordinateInput` + resolved `Wgs84` | Parsing + conversion succeeded inside Taiwan coverage. |
| `Invalid` | the unit, plus a localisable `reason` enum (`BAD_LENGTH`, `BAD_LETTER`, `RESERVED_LETTER_YZ`, `BAD_ZONE`, `EMPTY`, `NON_DIGIT`) | Operator input failed syntactic / lexical validation. |
| `OutOfRange` | the unit + the resolved `Wgs84` | Conversion succeeded numerically but the result is outside Taiwan's coverage box. |

**Why not throw**: same reason as feature 001's `ConversionResult` —
the UI needs to render *something* (an inline error, a disabled
submit button) in every case; control-flow exceptions would couple
the parser to the view.

---

## 3. `MapCenterFix` (immutable value class)

Snapshot of the current map-centre signal consumed by
`MapCenterAutoFillStream`.

```text
MapCenterFix
├── Wgs84 wgs84               // the map centre's WGS84 lat/lon
├── boolean insideCoverageBox // pre-computed; same predicate the widget uses
├── boolean taipowerOk        // wgs84 falls in zone 121 AND inside Taiwan
├── boolean twd97Ok           // wgs84 inside Taiwan (either zone)
└── boolean twd67Ok           // same as twd97Ok
```

**Lifecycle**: produced by the `MapCenterAutoFillStream` debouncer on
every `MAP_*` event, posted to a `LiveData<MapCenterFix>` observed by
the view. Stale fixes are simply replaced by the next emission; the
class is never persisted.

---

## 4. `DestinationMarker` (handle, not value)

Wraps the single in-flight ATAK `Marker` owned by the page.

```text
DestinationMarker
├── String uid                // stable UID, set once at first creation
├── Marker delegate           // null while no marker exists
├── CoordinateUnit lastUnit   // for the call-sign / icon hint
└── String lastInputDisplay   // for the call-sign

operations:
    moveOrCreate(Wgs84 target, CoordinateInput input)
    removeIfPresent()
```

**Identity rules** (FR-009):
- A single `uid` is allocated on first `moveOrCreate` and reused for
  the lifetime of the plugin process.
- Subsequent `moveOrCreate` calls update `delegate.setPoint(...)` and
  the call-sign metadata; they never call `mapView.addMapItem(...)`
  again.
- When the operator long-presses the marker and ATAK's standard
  affordance deletes it (`Marker.setRemovable(true)` is honoured),
  `delegate` is nulled and the next `moveOrCreate` starts a fresh
  marker (and reuses the same `uid` if the policy is in-process
  reuse, or allocates a new one if the plugin was restarted — see
  R5 in research.md).

**Lifecycle**: process-scoped; not persisted across plugin restarts.

---

## 5. `RecentEntry` (immutable value class)

One historical successful submission.

```text
RecentEntry
├── CoordinateUnit unit            // Taipower / TWD97 / TWD67
├── String rawValue                // operator's original input, normalised
├── int eastingOrNull              // present for TWD97/TWD67, null for Taipower
├── int northingOrNull             // present for TWD97/TWD67, null for Taipower
├── int zone                       // 121 or 119; 0 for Taipower (sentinel)
└── long timestampEpochMs          // monotonic in practice; for ordering and dedup
```

**Constraints**:
- For `unit == TAIPOWER`, easting / northing fields are `null` (or
  zero sentinels in the JSON serialisation) and the parser
  reconstructs the WGS84 from `rawValue`.
- For `unit == TWD97 | TWD67`, `rawValue` is a display string built
  from the numeric fields (e.g. `"302912 / 2770905"`); the numerics
  remain the source of truth.

**Lifecycle**: stored in `RecentEntryStore`; capacity 10 (R10); FIFO
eviction by `timestampEpochMs` after deduplicating on
`(unit, rawValue)`.

---

## 6. `RecentEntryStore` (singleton, JSON-backed)

A small repository on top of `SharedPreferences`.

```text
RecentEntryStore
operations:
    void   append(RecentEntry)        // deduplicate by (unit, rawValue), trim to 10
    List<RecentEntry> getAll()        // newest-first
    void   clear()
    void   registerListener(OnChange) // for the view to refresh the "Recent" list
```

**Persistence**:
- Single SharedPreferences key `pref_goto_recent_json`.
- Value: a JSON array of objects with fields
  `{unit, rawValue, easting, northing, zone, timestampEpochMs}`.
- Capacity enforced at `append()` time (R10).
- Listener interface is the same `OnSharedPreferenceChangeListener`
  the existing `PreferenceStore` exposes; no new framework.

**Lifecycle**: created once at `TwCoordMapComponent.onCreate`, lives
as long as the plugin. Cleared on `clear()`; not cleared on plugin
disable/enable cycles (the operator's intent is "I want my history
back next session").

---

## 7. `InputPageState` (in-session, not persisted)

Ephemeral state held by the `DropDownReceiver` so that closing and
reopening the page within the same ATAK session preserves the
operator's in-progress edits.

```text
InputPageState
├── CoordinateUnit activeTab
├── String taipowerDraft       // empty if nothing typed yet
├── String twd97EastingDraft   // empty if nothing typed yet
├── String twd97NorthingDraft  // empty if nothing typed yet
├── int    twd97Zone           // 121 default
├── String twd67EastingDraft
├── String twd67NorthingDraft
└── int    twd67Zone           // 121 default
```

**Lifecycle**: created on first open (defaults sourced from
`PreferenceStore.getGotoLast*` keys per FR-003); persisted only
in-memory across DropDown open/close cycles within one ATAK process;
discarded on plugin teardown.

---

## 8. Extended `PreferenceStore` keys (persisted aggregate)

The existing `PreferenceStore` (feature 001) gains new keys for the
last-submitted tuple. No new SharedPreferences file.

```text
pref_goto_last_unit                // string: "TAIPOWER" | "TWD97" | "TWD67"
pref_goto_last_taipower            // string: e.g. "H7509 DB4016", empty if none
pref_goto_last_twd97_e             // int (0 = none)
pref_goto_last_twd97_n             // int
pref_goto_last_twd97_zone          // int: 121 (default) or 119
pref_goto_last_twd67_e             // int
pref_goto_last_twd67_n             // int
pref_goto_last_twd67_zone          // int
pref_goto_recent_json              // string JSON array; see §6
```

**Defaults on first read**: `pref_goto_last_unit` → `TAIPOWER`
(FR-003), all numeric keys → 0 sentinel (treated as "no previous
value"), `pref_goto_recent_json` → `"[]"`.

---

## Entity relationship summary

```text
   ┌─────────────────────┐       ┌────────────────────────┐
   │ EditText keystroke  │       │ MAP_* event (existing) │
   └──────────┬──────────┘       └──────────┬─────────────┘
              │                              │
              ▼                              ▼
       CoordinateParser            MapCenterAutoFillStream
       (string → ParseResult)      (MapEvent → MapCenterFix)
              │                              │
              ├──► Invalid / OutOfRange ─►   │ updates LiveData
              │                              │
              ▼                              ▼
         CoordinateInput ◄──── feeds ──── Auto Fill button
              │                       (enables when *Ok for active tab)
              │
              ▼
       Submit handler in TwCoordGotoView
              │
              ├──► RecentEntryStore.append(RecentEntry)
              │
              └──► DestinationMarker.moveOrCreate(wgs84, input)
                          │
                          ▼
                  ATAK MapView pan + marker render
```

State transitions:
- A `CoordinateInput.*` is **immutable**; the operator's edits
  produce a new `InputPageState` snapshot each keystroke (debounced
  to the validator).
- `DestinationMarker` has only two reachable states: *no marker* and
  *marker placed*; transitions are `moveOrCreate(...)` and
  `removeIfPresent(...)`.
- `RecentEntryStore` is purely append + dedup + FIFO-trim; the
  `clear()` operation is allowed but not exposed to the
  Tools-menu-icon affordance (only via the Recent list's own delete
  control, US4 acceptance 3).
