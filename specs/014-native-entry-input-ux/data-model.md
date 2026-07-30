# Data Model: Native Taiwan Input UX

## Overview

Feature 014 adds no database entity or durable coordinate history. It replaces
the controller's single Taipower string with one in-memory, lossless draft and
persists only the selected Taipower presentation mode. Existing TWD97, TWD67,
Address, and host point models remain unchanged.

```text
TaiwanEntryController
└── TaipowerEntryDraft
    ├── active representation: raw or split
    ├── exact raw projection
    ├── four split parts
    ├── revision/projection provenance
    ├── validation and precision
    └── resolved Wgs84 only when valid

PreferenceStore
└── TaipowerInputMode (durable enum string)
```

## Entity: `TaipowerInputMode`

**Ownership**: `nativeentry` presentation state; serialized by
`PreferenceStore`.

| Value | Meaning |
|-------|---------|
| `SINGLE_FIELD` | One paste-friendly raw editor. This is the migration and corrupt-value fallback. |
| `SPLIT_FIELDS` | Four guided fields: region, subregion, 100 m cell, precision digits. |

Rules:

1. The mode does not contain coordinate content.
2. A mode change is a presentation action, not a coordinate edit.
3. A successful operator mode change writes the plugin preference but does not
   invoke ATAK's human-coordinate-change callback.
4. Missing, blank, or unknown serialized values deserialize as
   `SINGLE_FIELD`.
5. The key is independent of ATAK MGRS and the existing native-entry unit key.

## Entity: `TaipowerEntryDraft`

**Ownership**: One instance inside the Taipower draft held by
`TaiwanEntryController`.

Suggested immutable snapshot fields:

| Field | Type | Invariant |
|-------|------|-----------|
| `revision` | `long` | Monotonically increases only for accepted human/programmatic coordinate edits, not view switches or renders. |
| `source` | `RAW` or `SPLIT` | Identifies the representation changed most recently. |
| `rawText` | `String` | Exact UTF-16 text accepted by the raw editor; never implicitly trimmed, uppercased, spaced, or replaced. |
| `rawRevision` | `long` | Revision for which `rawText` is an exact or derived projection. |
| `splitParts` | `TaipowerSplitParts` | Four exact guided components. |
| `splitRevision` | `long` | Revision for which `splitParts` is an exact or derived projection. |
| `validation` | `TaiwanEntryController.Validation` | `EMPTY`, `INCOMPLETE`, `MALFORMED`, `OUT_OF_COVERAGE`, `VALID`, or lifecycle state as applicable. |
| `parseReason` | nullable `ParseResult.Reason` | Domain reason used to select precise local UI feedback. |
| `validationDetail` | nullable `TaipowerValidationDetail` | Presentation detail that distinguishes the east-west A-H and north-south A-E failures without adding a parser reason. |
| `precision` | `TaipowerPrecision` | `NONE`, `INCOMPLETE`, `TEN_METRE`, or `ONE_METRE`. |
| `resolved` | nullable `Wgs84` | Present only when validation is `VALID`. |
| `projectionFailure` | nullable `ProjectionFailure` | Why the requested alternate view cannot represent the current revision losslessly. |

The implementation may use a mutable controller-internal builder and expose an
immutable snapshot, but the invariants above must hold at every callback
boundary.

### Revision and losslessness

1. Editing raw text:
   - stores the new text exactly;
   - increments `revision`;
   - sets `source = RAW` and `rawRevision = revision`;
   - derives split parts only when normalization and positional projection are
     lossless;
   - never mutates raw text merely to make split projection possible.
2. Editing any split part:
   - stores the accepted part value;
   - increments `revision`;
   - sets `source = SPLIT` and `splitRevision = revision`;
   - derives a raw projection only for a contiguous, unambiguous prefix.
3. Switching mode does not increment `revision`.
4. If the operator switches raw → split → raw without editing the split
   fields, the original `rawText` is displayed byte-for-byte because
   `rawRevision` still matches the current revision.
5. If split input changes, the previous raw spelling/spacing no longer
   represents the current coordinate revision; a canonical contiguous raw
   projection is generated only when safe.
6. A projection failure retains the current source, current mode, exact
   content, precision, validation, and resolved point.

## Value Object: `TaipowerSplitParts`

| Part | Stored form | Accepted edit prefix | Complete form |
|------|-------------|----------------------|---------------|
| `region` | Uppercase ASCII string | empty or one character | One supported main-island region letter |
| `subregion` | ASCII digit string | 0-4 digits | Exactly four digits |
| `subgrid` | Uppercase ASCII string | 0-2 characters | Exactly two letters, first A-H and second A-E |
| `precisionDigits` | ASCII digit string | 0-4 digits | Exactly two digits for 10 m or four for 1 m |

Rules:

1. UI filters reject extra characters without altering already accepted
   content.
2. UI filters may guide character class/range, but parser and value-object
   validation remain authoritative.
3. A field may be reached with a preceding field incomplete. Such a draft is
   preserved but cannot form a raw projection across a positional gap.
4. Lowercase letter input is accepted and displayed uppercase in split mode.
5. The `precisionDigits` states are:
   - 0 digits with no preceding content: empty draft;
   - 0, 1, or 3 digits after preceding content: incomplete;
   - 2 digits: complete 9-character / 10 m code;
   - 4 digits: complete 11-character / 1 m code;
   - more than 4: rejected by the edit boundary.
6. Concatenation is:

   ```text
   region + subregion + subgrid + precisionDigits
   ```

   It is available only when every preceding part is complete or the result is
   a contiguous positional prefix.

## Derived Enum: `TaipowerPrecision`

| Value | Derivation |
|-------|------------|
| `NONE` | All coordinate content is empty. |
| `INCOMPLETE` | Content exists but is not exactly a valid 9- or 11-character structure. |
| `TEN_METRE` | Complete canonical structure with two final digits (9 characters total). |
| `ONE_METRE` | Complete canonical structure with four final digits (11 characters total). |

Precision is derived from the authoritative representation and is never
rounded or changed by switching layouts.

## Value Object: `ProjectionFailure`

| Value | Meaning |
|-------|---------|
| `RAW_NOT_POSITIONAL` | Raw content cannot be normalized to the supported complete code or a safe positional prefix. |
| `SPLIT_HAS_GAP` | A later split component contains content while an earlier required component is incomplete. |
| `SPLIT_INVALID_CHARACTER` | Existing split content cannot be represented by the raw Taipower grammar. |

This is presentation feedback, not a new `ParseResult` domain reason. A
complete malformed Taipower code still uses the authoritative parser reason.

## Value Object: `TaipowerValidationDetail`

| Value | Meaning |
|-------|---------|
| `EW_SUBGRID_OUT_OF_RANGE` | The first 100 m letter is outside A-H; render the localized A-H correction message. |
| `NS_SUBGRID_OUT_OF_RANGE` | The second 100 m letter is outside A-E; render the localized A-E correction message. |

This detail is derived from the normalized positional draft when
`parseReason == BAD_LETTER`. Editors accept uppercase A-Z within their character
class and length limits so an out-of-range attempt remains visible for
correction; the parser and draft validation remain authoritative.

## Existing Entity: `TaiwanEntryController.Validation`

Feature 014 retains the existing validation vocabulary:

| Value | Taipower interpretation |
|-------|--------------------------|
| `EMPTY` | No coordinate content. |
| `INCOMPLETE` | A supported prefix or split draft lacks required characters, including final lengths 0/1/3 after preceding content. |
| `MALFORMED` | Wrong character/order/range, including east-west I/J or north-south F-J. |
| `OUT_OF_COVERAGE` | Structurally valid code resolves outside supported main-island coverage. |
| `VALID` | Parser returns a WGS84 point for a 9- or 11-character code. |
| `UNREPRESENTABLE` | Host point cannot be formatted as Taipower; used by activation/Auto Fill rather than an operator projection refusal. |
| `DISPOSED` | Pane/controller no longer accepts callbacks. |

`BAD_ZONE` remains relevant to TWD entry and not to Taipower.

## Domain Value Object: `TaipowerCode`

The existing value object gains corrected invariants:

| Property | Invariant |
|----------|-----------|
| `region` | Existing main-island region table only |
| `subRegion` | Existing four-digit range |
| `hmE` | A-H (`0..7`) |
| `hmN` | A-E (`0..4`) |
| `tenE`, `tenN` | Existing decimal digit rules |
| `oneE`, `oneN` | Existing optional 1 m digit rules |

The encoder must never clamp an out-of-range 100 m index. Its geometry must
produce only `0..7` and `0..4`; an impossible index is an invariant failure.

## Presentation State: `InlineEntrySession`

This is pane-local behavior, not durable data.

| Field | Meaning |
|-------|---------|
| `focusedEditorId` | Current plugin-owned editor or none |
| `nextEditorId` | Next visible, enabled plugin editor or none |
| `editable` | Mirrors host-provided pane editability |
| `lifecycleGeneration` | Invalidates posted focus/keyboard callbacks after replacement/dispose |
| `rendering` | Suppresses watchers and auto-advance during programmatic projection |

Rules:

- No session exists for county/district selectors or read-only editors.
- Editor actions never confirm a host point.
- A disposed/generation-mismatched callback is ignored.
- Mode switching transfers focus only if a Taipower editor owned it before the
  switch.

## Presentation State: `SelectorPresentation`

| Property | Value |
|----------|-------|
| Layout/touch height | 48 dp |
| Visible track/fill height | 36 dp |
| Transparent inset | 6 dp top + 6 dp bottom |
| Vertical control padding | 0 dp |
| Semantic control | Existing native `RadioButton` |

The transparent bands are inside the actual view bounds and are therefore
clickable. No `TouchDelegate`, overlay, negative margin, or overlapping target
is part of the model.

## State Transitions

### Raw edit

```text
receive exact text
→ increment revision and make RAW authoritative
→ classify empty / safe prefix / complete / malformed
→ derive split projection when lossless
→ parse only complete 9/11 forms
→ publish validation and resolved point
→ emit one human-change callback
```

### Split edit

```text
filter only the edited component
→ increment revision and make SPLIT authoritative
→ preserve every component
→ derive raw projection only for a safe contiguous prefix
→ classify precision and validate complete 9/11 forms
→ emit one human-change callback
```

### Mode switch

```text
request alternate mode
→ verify projection for current revision
→ if unavailable: retain mode/content and show localized projection failure
→ if available: change visible projection and persist mode
→ never change revision/resolved point or emit host change
```

### Host activation / Auto Fill

```text
receive Wgs84
→ stage Taipower, TWD97, and TWD67 drafts atomically
→ format canonical 11-character Taipower when representable
→ populate raw and split projections at the same revision
→ start Address reverse lookup from the exact same Wgs84
→ retain the selected Taiwan page
→ retain selected Taipower mode
→ emit no human change
```

### Active Clear

```text
receive no point
→ clear only the active coordinate or Address draft
→ if Taipower: clear raw and split projections and retain selected mode
→ if Address: cancel pending reverse lookup and candidates
→ leave every inactive draft unchanged
→ emit no human change
```

### Read-only and dispose

```text
read-only: retain selected projection and validation, disable every editor
dispose: increment/invalidate lifecycle generation, remove callbacks,
         dismiss pane-owned focus safely, replace drafts with DISPOSED
```

## Persistence Migration

No schema migration is required:

- The new key is absent on existing installations and defaults to
  `SINGLE_FIELD`.
- No Taipower draft text is currently persisted.
- Previously accepted noncanonical A-I/A-J or A-F/A-J strings are not stored by
  native entry and are intentionally rejected when re-entered.
- Unknown future/corrupt mode values fail closed to the established workflow.
