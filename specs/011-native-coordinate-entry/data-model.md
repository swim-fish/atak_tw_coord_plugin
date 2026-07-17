# Data Model: Native Taiwan Coordinate Entry

The feature adds no database schema or file format. The model is an in-memory
UI/session state plus one durable enum preference.

## 1. TaiwanSystem

Represents the one active internal system.

| Value | Datum/grid | Fields | Zone rule |
|-------|------------|--------|-----------|
| `TAIPOWER` | Taipower grid derived from TWD67 | Normalised 9- or 11-character code | Implicit zone 121; main-island domain only |
| `TWD97` | TWD97 TM2 | Easting metres, northing metres | Explicit 121 or 119 |
| `TWD67` | TWD67 TM2 | Easting metres, northing metres | Explicit 121 or 119; zone-119 advisory |

The durable representation is the enum name. Unknown or missing values resolve
to `TAIPOWER`.

## 2. TaiwanCoordinateDraft

Session-only operator input. One draft object may retain per-system field text
while the operator switches systems, but only the active system is eligible for
validation or return to ATAK.

| Field | Type | Rules |
|-------|------|-------|
| `activeSystem` | `TaiwanSystem` | Exactly one; persisted on human selection |
| `taipowerText` | String | Raw edit text; parser performs existing case/spacing normalisation |
| `twd97EastingText` | String | Base-10 integer metres when present |
| `twd97NorthingText` | String | Base-10 integer metres when present |
| `twd97Zone` | integer | 121 or 119 only |
| `twd67EastingText` | String | Base-10 integer metres when present |
| `twd67NorthingText` | String | Base-10 integer metres when present |
| `twd67Zone` | integer | 121 or 119 only |
| `validation` | `ValidationState` | Derived; never trusted as a coordinate itself |
| `origin` | `DraftOrigin` | `EMPTY`, `HOST_POINT`, `AUTOFILL`, or `HUMAN_EDIT` |

### Invariants

1. Inactive fields never contribute to the returned point.
2. A TWD draft always displays its selected zone beside its numeric values.
3. Auto Fill replaces all fields for the active system before conversion; it
   never combines old and new values.
4. `onActivate(null, editable)` clears all visible active fields.
5. Programmatic population does not dispatch the human-change listener.

## 3. ValidationState

| State | Meaning | Host result allowed |
|-------|---------|---------------------|
| `EMPTY` | Required active fields are blank | No |
| `INCOMPLETE` | Only part of a TWD pair or Taipower code exists | No |
| `MALFORMED` | Non-numeric, bad length/letter, or unsupported code shape | No |
| `BAD_ZONE` | Zone is not 121/119; defensive state for corrupt/programmatic input | No |
| `OUT_OF_COVERAGE` | Numeric conversion succeeds outside the supported Taiwan box/grid | No |
| `UNREPRESENTABLE` | A supplied host point cannot be expressed in the active system | No |
| `VALID` | Parser produced a supported WGS84 point | Yes |
| `DISPOSED` | Pane has been unloaded but may still receive a late host callback | No |

Each non-valid state maps to one localised corrective message. Validation is
recomputed from field values; a previously valid cached point is discarded on
every human edit.

## 4. ResolvedCoordinate

Immutable successful conversion result.

| Field | Type | Meaning |
|-------|------|---------|
| `wgs84` | existing `Wgs84` | Canonical latitude/longitude returned to ATAK |
| `sourceSystem` | `TaiwanSystem` | Datum/grid provenance |
| `sourceInput` | existing `CoordinateInput` | Normalised Taipower or TM2 numbers and zone |
| `displayText` | String | Deterministic no-altitude representation |

Altitude is intentionally absent. `TaiwanCoordinateEntryPane` adapts `wgs84`
to a new horizontal `GeoPointMetaData`; ATAK owns elevation and user-entered
metadata in its result path.

## 5. NativeEntrySession

The controller state while ATAK is displaying or retaining the pane.

| Field | Type | Rules |
|-------|------|-------|
| `draft` | `TaiwanCoordinateDraft` | Non-null until disposed |
| `editable` | boolean | Human controls disabled when false |
| `currentHostPoint` | optional WGS84 | Last point supplied by `onActivate`; never a fallback result after invalid edit |
| `changedListener` | optional ATAK listener | At most one; human edits only |
| `suppressHumanEvents` | boolean | True during activation, Auto Fill, clear, locale refresh, and programmatic rendering |
| `disposed` | boolean | Monotonic false → true |

### Session transitions

| Event | Precondition | Transition | Side effect |
|-------|--------------|------------|-------------|
| `activate(point, editable)` | Not disposed | Set editability; clear or forward-convert point into active draft | No human notification |
| Human system switch | Editable | Change active system, persist enum, validate its draft | Notify once |
| Human field/zone edit | Editable | Update field, discard cached result, validate | Notify once per logical change |
| `autofill(point)` | Not disposed | Clear active draft, then forward-convert or set unrepresentable state | No human notification |
| Native Clear | Not disposed | Host invokes `onActivate(null, editable)`; active visible draft becomes empty | No human notification |
| Native Copy | Active draft valid | Host calls parse then pure `format(point)` | No draft mutation |
| Confirm/switch host pane | Active draft valid | Return new horizontal WGS84 metadata | Host owns subsequent action |
| Confirm invalid | Any invalid state | Throw contained `CoordinateException` | Host dialog remains open |
| Dispose | Any | Detach listeners and set `DISPOSED` | All later callbacks are inert/contained |

## 6. RegistrationState

Owned by `NativeCoordinateEntryRegistrar`.

| State | Meaning |
|-------|---------|
| `STOPPED` | No pane registered and no queued start |
| `START_PENDING` | UI-thread registration queued |
| `REGISTERED` | Exactly one live pane registered |
| `REFRESH_PENDING` | Locale refresh deferred until the pane view detaches |
| `STOP_PENDING` | UI-thread unregister queued; new start cannot reuse old pane |
| `FAILED` | Registration failed, rollback/disposal attempted; custom page unaffected |

`start()` and `stop()` are idempotent. A generation/token check makes stale
queued callbacks no-ops. Only `REGISTERED` exposes a live pane to ATAK.

## 7. TaiwanPanePreference

| Property | Value |
|----------|-------|
| Key | `pref_native_entry_last_unit` |
| Store owner | ATAK-process default `SharedPreferences` through `PreferenceStore` |
| Allowed values | `TAIPOWER`, `TWD97`, `TWD67` |
| Default/corrupt fallback | `TAIPOWER` |
| Write trigger | Human internal-system selection |
| Explicit non-effects | Does not notify on-map readout listeners and does not touch any `pref_goto_*` key |
