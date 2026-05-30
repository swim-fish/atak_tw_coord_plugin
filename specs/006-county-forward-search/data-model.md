# Data Model: County-Scoped Forward Address Search

**Branch**: `006-county-forward-search` | **Date**: 2026-05-30

This feature reads two on-disk SQLite shapes (both already produced by the
generator, no schema change) and introduces in-memory model types. Nothing here
changes the generator data-contract.

---

## 1. On-disk inputs (read-only, generator-owned)

### 1.1 `townships.sqlite` (NEW consumer — was skipped in 005)

Per generator data-contract §3.2 (MOI shapefile source, release `1140318`):

```sql
CREATE TABLE townships (
    id INTEGER PRIMARY KEY,
    moi_code TEXT NOT NULL,        -- provenance; opaque to the plugin
    admin_level INTEGER NOT NULL,  -- 4=縣市, 7=直轄市區, 8=縣轄鄉鎮市
    name_zh TEXT NOT NULL,         -- bare district/county name, 臺→台 normalised
    name_en TEXT,
    county_zh TEXT,                -- parent 縣市 for level 7/8; NULL for level 4
    geometry_wkb BLOB NOT NULL     -- MultiPolygon, WGS84 lon/lat
);
CREATE INDEX idx_townships_level  ON townships(admin_level);
CREATE INDEX idx_townships_name   ON townships(name_zh);
CREATE INDEX idx_townships_county ON townships(county_zh);
CREATE VIRTUAL TABLE townships_rtree USING rtree(id, min_lat, max_lat, min_lon, max_lon);
CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL);
```

Plugin reads: `admin_level`, `name_zh`, `county_zh`, `geometry_wkb`, the R*Tree,
and `metadata` (`schema_version`, `source`, `boundary_release`). **Measured/verified
invariants** (the plugin relies on these; assert in tests, fail safe if violated):
- `county_zh` is non-null on every level-7/8 row (136/136 verified).
- `geometry_wkb` is little-endian OGC WKB, type 6 (MultiPolygon) or 3 (Polygon),
  WGS84 lon/lat.
- The R*Tree returns 1–3 candidates for a typical point (measured).

### 1.2 `places-<county>.sqlite` (existing — unchanged)

Per data-contract §3.1 (already consumed by 004/005). Forward search reads the
extra columns 004/005 don't use at lookup time:
- `street` — carries the `段` suffix (e.g. `中山路一段`); MUST be matched by
  substring/prefix, never `=`.
- `township` — district name (臺→台 normalised); the funnel's district filter key.
- `district_code` — MOI 7/8-digit; carried for exactness.
- `name`, `display_name`, `display_name_halfwidth`, `lat`, `lon` — for the
  candidate display + distance ranking (as 004/005 already use).

No schema change. No new index required (district-scoped `LIKE` + app-side rank).

---

## 2. In-memory model types (NEW)

### 2.1 `BoundaryGeometry` (`address/geo/`)

Parsed multipolygon + cached bbox.

| Field | Type | Notes |
|---|---|---|
| `polygons` | `List<Polygon>` | each = exterior ring + 0..N hole rings; ring = `double[] lon, double[] lat` (or `double[][]`) |
| `minLat/maxLat/minLon/maxLon` | `double` | cached bounds for a fast reject before PIP |

Methods: `boolean covers(double lat, double lon)` — bbox reject, then ray-cast
PIP on each polygon (inside exterior AND not inside any hole). Parser-agnostic:
internals may be swapped to JTS (research R1) behind this type.

### 2.2 `LocalityResult` (`address/boundary/`)

The outcome of `TownshipBoundaryFacade.localityAt`.

| Field | Type | Notes |
|---|---|---|
| `county` | `String` (nullable) | `null` ⇒ outside all county boundaries (offshore / not in installed bundle) |
| `district` | `String` (nullable) | `null` ⇒ county known but no covering district (clipped / coastal before snap) |
| `approx` | `boolean` | `true` ⇒ district was snapped within tolerance, not strictly covering |

States:
- **Full** — county≠null, district≠null, approx=false (the common case)
- **Snapped** — county≠null, district≠null, approx=true (coastal/reclaimed)
- **County-only** — county≠null, district=null (clipped area, no snap hit)
- **None** — county=null (offshore / outside installed data)

### 2.3 `CountySource` (`address/forward/`)

Enum + provenance for funnel stage ①.

| Value | Meaning | Default-seed rule |
|---|---|---|
| `MAP_CENTER` | county from map-centre coord | **default** when SELF≠MAP_CENTER (spec clarification) |
| `SELF` | county from self-marker GPS | one tap away from MAP_CENTER |
| `LIST` | operator picked from the level-4 list | list read from `townships.sqlite`, never hard-coded |

### 2.4 `ForwardSearchQuery` (`address/forward/`)

Accumulated funnel state. Immutable-ish value object; the controller produces a
new one per stage transition.

| Field | Type | Notes |
|---|---|---|
| `county` | `String` (nullable until ① done) | the selected county |
| `countySource` | `CountySource` | drives whether ② pre-highlights a district |
| `district` | `String` (nullable until ② done) | selected 鄉鎮市區 |
| `streetFragment` | `String` (nullable until ③) | raw operator input (pre-fold) |
| `houseNumber` | `String` (nullable, optional ④) | optional final disambiguation |
| `anchorLat/anchorLon` | `double` | distance-ranking anchor (map centre or self) |

### 2.5 `AddressCandidate` (`address/forward/`)

One street-matched row, the unit shown in the list and handed to GoTo.

| Field | Type | Notes |
|---|---|---|
| `lat/lon` | `double` | WGS84; the GoTo target |
| `displayName` | `String` | `display_name` (fullwidth, full hierarchy) for the row |
| `displayNameHalfwidth` | `String` | for matching echo / accessibility |
| `street` | `String` | the matched street (may carry `段`) |
| `distanceMeters` | `double` | haversine from the query anchor; the rank key |

---

## 3. Funnel state machine (stage transitions)

```
      ┌─────────────┐  pick/confirm county   ┌──────────────┐  pick district  ┌────────────┐
  →   │ ① COUNTY    │ ─────────────────────► │ ② DISTRICT   │ ──────────────► │ ③ STREET   │
      │ (seed:      │ ◄───────────── change   │ (pre-highlight│ ◄──── change    │ (fold+     │
      │  MAP_CENTER)│                          │  if SELF/MC) │                 │  substring)│
      └─────────────┘                          └──────────────┘                 └─────┬──────┘
            ▲                                                                          │ candidates
            │ change county (any stage)                                                ▼
            │                                                          ┌────────────────────────┐
            └──────────────────────────────────────────────────────── │ ④ PIN (house# or       │
                                                                       │   distance) → confirm  │
                                                                       │   → GoTo (no auto-pan)  │
                                                                       └────────────────────────┘
```

Rules:
- Stage ① seeds from `MAP_CENTER` locality; `SELF` and `LIST` are one tap away.
- Stages ① and ② are **tap-only** (no keyboard). ② pre-highlights the operator's
  own district when county came from SELF/MAP_CENTER.
- Stage ③ opens the selected county's place DB **for the first time** (FR-008);
  not before. Fragment is folded (R5) and matched as substring incl. `段`.
- Stage ④ house number via numeric keypad; blank ⇒ nearest-by-distance.
- GoTo only on explicit confirm; never auto-pan (FR-013).
- "Change county/district" returns to the relevant stage without losing the
  rest where it still applies.

---

## 4. Reverse-path scoping (modifies 005 behaviour, not data)

`AddressSubsystem.runLookup` with a bound boundary facade:

```
locality = boundaryFacade.localityAt(lat, lon, snapMeters=~1000)
if locality.county != null and registry has that county:
    result = thatCountyFacade.nearestWithin(lat, lon, R)     # ONE county
elif locality.county != null:                                 # county detected, no dataset
    result = LocalityOnly(county, district)                   # FR-015 best-effort
else:
    result = lookupAcrossAllCounties(lat, lon)                # fallback (no boundary / offshore)
```

Invariant (FR-014): for a point inside an active county, the single-county result
equals the old globally-nearest result (the nearest record lies in the county
that contains the point). Tested by `AddressSubsystemReverseScopingTest`.

---

## 5. Validation & failure rules (Constitution VI defensive-validation)

| Input | Rule | Failure recovery |
|---|---|---|
| `geometry_wkb` blob | parse as little-endian WKB type 3/6 | malformed ⇒ skip that polygon / return "no locality"; never throw |
| `localityAt` candidates | bbox-overlap then `covers` | none cover + snap off ⇒ County-only or None; never throw |
| street fragment | fold then non-empty after trim | empty ⇒ no query, empty-state |
| district key | must match a `townships.name_zh` for the county | mismatch ⇒ empty candidate list + log |
| boundary DB absent | facade is null | forward search shows "import base data"; reverse falls back to 005 fan-out (FR-017) |
| county detected, place DB absent | registry miss | show locality only (FR-015) |
| house number | optional; digits + 之/- | unparseable ⇒ treat as blank (distance pin) |

All boundary/forward worker entry points wrap `Throwable` → `Log.w` and degrade
to a safe state (Constitution VI). The WKB parser specifically treats its input
as untrusted (the DB could be tampered/truncated on disk).

---

## 6. What this feature does NOT model

- No `roads.sqlite` geometry (Tier-2) — deferred.
- No `places-osm.sqlite` landmarks — deferred.
- No global FTS index / no-anchor search — deferred.
- No generator schema change — `townships.sqlite` and `places-*.sqlite` are read
  exactly as shipped.
- No active-root migration to `data/` — the boundary DB sits under the existing
  005 root (`active/_boundary/`); migration is a separate future step.
