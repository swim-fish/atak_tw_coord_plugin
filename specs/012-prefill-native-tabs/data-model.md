# Data Model: Prefill All Native Taiwan Tabs

## 1. HostActivationPoint

Canonical horizontal WGS84 supplied by ATAK for one pane activation.

| Field | Type | Rule |
|-------|------|------|
| `point` | optional `Wgs84` | Non-null means prepare all systems; null means native Clear |
| `editable` | boolean | Applies to every human control; does not restrict programmatic preparation |

Altitude is not retained or returned by this feature.

## 2. TaiwanSystemDraft

One independent session draft keyed by `CoordinateUnit`.

| Field | Type | Rule |
|-------|------|------|
| `unit` | `TAIPOWER`, `TWD97`, or `TWD67` | Immutable identity |
| `taipowerText` | string | Used only by Taipower; empty when unavailable/cleared |
| `eastingText` | string | Used only by TWD systems; ASCII integer metres |
| `northingText` | string | Used only by TWD systems; ASCII integer metres |
| `zone` | 119 or 121 | Used only by TWD systems; derived independently per datum |
| `validation` | `Validation` | Belongs to this draft, not globally to the controller |
| `resolved` | optional `Wgs84` | Present only when this draft is valid |
| `sourceGeneration` | monotonically increasing session number | Identifies the activation snapshot that produced the draft; not persisted |

### Invariants

1. `VALID` implies a non-null `resolved` point.
2. `UNREPRESENTABLE`, `EMPTY`, and `DISPOSED` imply no resolved point.
3. An unavailable draft contains no coordinate text from an earlier
   generation.
4. All drafts committed by one non-null activation share one generation and
   one source WGS84 point.
5. A human edit validates only the edited draft and does not copy state across
   units.

## 3. PreparationSnapshot

Immutable staged result for one non-null activation.

| Field | Type | Rule |
|-------|------|------|
| `source` | `Wgs84` | Exact point supplied by ATAK |
| `generation` | long | New value for each accepted non-null activation |
| `drafts` | exactly three `TaiwanSystemDraft` values | One for every unit |

The controller does not expose the snapshot until every ordinary conversion
attempt completes. `OutOfRange` is a successful attempt with an unavailable
draft. An unexpected failure produces a fully cleared failure snapshot for
the new generation before the exception reaches the pane safety boundary.

## 4. Controller Projection

`TaiwanEntryController` retains:

| Field | Rule |
|-------|------|
| `activeUnit` | Existing last-selected system; unchanged by preparation |
| `drafts` | Exactly one current draft per unit |
| `editable` | Host-supplied state |
| `disposed` | Monotonic; disposed state cannot return a valid result |

The public `validation()` and `resolvedOrNull()` methods project
`drafts[activeUnit]`. This preserves the pane's active-only contract while
allowing inactive drafts to retain correct availability.

## 5. State Transitions

| Event | Transition | Notification |
|-------|------------|--------------|
| `activate(non-null, editable)` | Stage three drafts, atomically replace all three, then project active draft | None |
| `activate(null, editable)` | Set editability and clear only active draft | None |
| Human system switch | Change active unit, project its existing draft, persist selection | Exactly once |
| Human field/zone edit | Update and validate target draft | Exactly once per logical edit |
| `autofill(point)` | Replace only active draft from point or with unavailable/empty state | None |
| Native Clear | Clear only active draft | None |
| Confirm/Copy | Read active valid draft only | None from controller |
| Unexpected all-system preparation failure | Replace every old draft with cleared failure state for new generation | None; pane logs/contains |
| Dispose | Mark all results unusable and detach listener | None |

## 6. Persistence and Migration

No draft, snapshot, source point, generation, validation, or availability
state is persisted. The existing `pref_native_entry_last_unit` format and all
custom GoTo state remain unchanged; no migration exists.
