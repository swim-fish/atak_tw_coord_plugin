# Phase 1 Data Model — Taiwan Coordinate Display Plugin

**Date**: 2026-05-16 | **Feature**: 001-tw-coord-display | **Plan**: [plan.md](./plan.md)

This document captures the runtime data model: the value classes that
flow from the coordinate sources (map centre / self-marker) through the
conversion pipeline and into the on-map widget. All entities live under
`app/src/main/java/com/atakmap/android/twcoord/coord/` and are
immutable (Java records where possible) so they can be passed across
threads without locking.

---

## 1. `CoordinateUnit` (enum)

Represents the user's selected display unit.

| Value | Display label key | Underlying CRS | Notes |
|---|---|---|---|
| `TAIPOWER` | `unit.taipower` | Taipower grid over TWD67 TM2 z121 | Letters Y/Z rejected (out-of-coverage; main-island only). Default precision: **11-char (1 m)** per FR-011 (flipped from the original 9-char default during post-MVP iteration — see ADR-0008). 9-char (10 m) reserved for a future user-precision toggle. |
| `TWD97` | `unit.twd97` | EPSG:3826 (TWD97 / TM2 z121) | Easting/northing in metres, 1 m precision. |
| `TWD67` | `unit.twd67` | EPSG:3828-like (TWD67 / TM2 z121) but using the 4-parameter shift from TWD97 — see R8 | Easting/northing in metres, 1 m precision. ±3 m accuracy vs. official TWD67. |

**Persistence**: stored in `SharedPreferences` as the enum name string
(`TAIPOWER` / `TWD97` / `TWD67`) under key `pref_coord_unit`. Default
on first launch: `TWD97`.

**Lifecycle**: read at plugin startup; reread whenever the preference
change listener fires.

---

## 2. `LanguageOverride` (enum)

User-selected UI language override.

| Value | Resolves to | Notes |
|---|---|---|
| `SYSTEM` | Android system locale, mapped through `LocaleOverride` | Default on first launch. |
| `EN` | `en` resources | Forces English regardless of system locale. |
| `ZH_TW` | `zh-rTW` resources | Forces Traditional Chinese (Taiwan). |
| `JA` | `ja` resources | Forces Japanese. |

**Persistence**: stored in `SharedPreferences` as the enum name string
under key `pref_ui_language`. Default: `SYSTEM`.

**Lifecycle**: same as `CoordinateUnit`; the change listener
re-creates the configured `Context` and asks the widget to repaint.

---

## 3. `Wgs84` (immutable value class)

Source-of-truth latitude/longitude pair from either the map centre or
the self-marker.

```text
Wgs84
├── double latitudeDeg            // [-90.0, 90.0]
├── double longitudeDeg           // [-180.0, 180.0]
├── long   timestampEpochMs       // when the source produced this fix
└── Source source                 // MAP_CENTRE | DEVICE_LOCATION
```

**Validation rules**:
- `latitudeDeg ∈ [-90, 90]` and `longitudeDeg ∈ [-180, 180]` — invalid
  inputs throw `IllegalArgumentException` at construction.
- `timestampEpochMs > 0`.
- A `DEVICE_LOCATION` whose `timestampEpochMs` is older than the
  configured stale threshold (default 10 000 ms) is treated as "no
  fix" by the formatter (FR-010); the `Wgs84` itself is still well-
  formed.

**Lifecycle**: created once per inbound event from
`MapEventDispatcher`, passed to `CoordinateConverter`, never mutated.

---

## 4. `Twd97Tm2`, `Twd67Tm2`, `TaipowerCode` (immutable value classes)

Outputs of the conversion stages.

```text
Twd97Tm2
├── double eastingMetres
├── double northingMetres
└── int    zone   // 121 (main island) — 119 (Penghu) reserved for future

Twd67Tm2
├── double eastingMetres
├── double northingMetres
└── int    zone   // always 121 in v1

TaipowerCode
├── char   region          // 'A'..'X', excluding 'Y' and 'Z'
├── int    subRegion       // 0000..9999 (4-digit string when rendered)
├── char   hundredMeterE   // 'A'..'J'
├── char   hundredMeterN   // 'A'..'J'
├── int    tenMeterE       // 0..9
├── int    tenMeterN       // 0..9
├── Integer oneMeterE      // 0..9 or null (only when precision = 11)
└── Integer oneMeterN      // 0..9 or null
```

**Validation rules**:
- All fields lie within the ranges shown.
- `oneMeterE` and `oneMeterN` are either *both* present or *both*
  absent; mixing is rejected at construction.

**Lifecycle**: created by `CoordinateConverter` per inbound `Wgs84`,
discarded after the widget reads them. Never persisted.

---

## 5. `ConversionResult` (sealed-ish wrapper)

A small ADT mirroring the spec's three readout states (FR-009, FR-010).

```text
ConversionResult = Ok(value) | OutOfRange(wgs84) | NoFix
```

| Variant | Carries | Trigger |
|---|---|---|
| `Ok` | The unit-specific value class (`Twd97Tm2` / `Twd67Tm2` / `TaipowerCode`) | Conversion succeeded inside the valid domain. |
| `OutOfRange` | The original `Wgs84` (for the fallback line) | Coordinate is outside the unit's valid domain (e.g., outside Taiwan; Y/Z letter for Taipower). |
| `NoFix` | nothing | Only emitted for `DEVICE_LOCATION` source when the fix is stale or absent. |

**Why not just throw**: the widget needs to render *something* in
every case; exceptions would couple the renderer to control flow.

---

## 6. `DisplayLine` (value class consumed by `TwCoordWidget`)

What the widget actually renders, one per source.

```text
DisplayLine
├── String labelPrefix       // localised "MAP" / "ME" / equivalent
├── String unitTag           // "TWD97" / "TWD67" / "台電" (localised)
├── String value             // formatted coordinate value, or "—"
├── String fallback          // optional WGS84 lat/lon when state == OUT_OF_RANGE
└── State  state             // OK | OUT_OF_RANGE | NO_FIX | NO_PERMISSION
```

**Rendering rules**:
- `OK`: `"{labelPrefix} {unitTag}: {value}"`.
- `OUT_OF_RANGE`: `"{labelPrefix} {unitTag}: out of range"` plus a
  second line `"({fallback})"` containing WGS84 lat/lon to 6 decimals.
- `NO_FIX`: `"{labelPrefix}: no fix"`.
- `NO_PERMISSION`: `"{labelPrefix}: no permission"` plus a tap target
  that opens Android app-settings.

The exact strings come from `strings.xml`; this table fixes the slots,
not the words.

---

## 7. `UserPreference` (persisted aggregate)

Materialised view of `SharedPreferences`.

```text
UserPreference
├── CoordinateUnit  coordUnit       // pref_coord_unit
├── LanguageOverride uiLanguage     // pref_ui_language
└── long staleFixThresholdMs        // pref_stale_fix_threshold_ms, default 10000
```

**Persistence**: Android `SharedPreferences`. Keys are constants on
`PreferenceStore`. The store exposes typed read/write methods and a
`registerOnChange(listener)` method that re-dispatches the platform
listener with strongly-typed enum values.

**Lifecycle**: read once at `MapComponent.onCreate`; mutated via the
preference fragment; change events fan out to the widget and to the
self-marker debouncer.

---

## Entity relationship summary

```
       ┌──────────────────┐        ┌──────────────────────┐
       │ MapEvent         │        │ Self-marker          │
       │ MAP_BOUNDS_      │        │ ITEM_CHANGED         │
       │ CHANGED          │        │ (debounced to 1 Hz)  │
       └────────┬─────────┘        └──────────┬───────────┘
                │                              │
                ▼                              ▼
              Wgs84 (source=MAP_CENTRE)    Wgs84 (source=DEVICE_LOCATION)
                │                              │
                ▼                              ▼
          ┌───────────────────────────────────────────┐
          │  CoordinateConverter.format(wgs84,        │
          │      userPreference.coordUnit) →           │
          │      ConversionResult                      │
          └────────────────────┬──────────────────────┘
                               │
                               ▼
                       DisplayLine (one per source)
                               │
                               ▼
                       TwCoordWidget.render(...)
```

State transitions on the widget are driven entirely by inbound events
and preference changes; the widget itself is a pure renderer.
