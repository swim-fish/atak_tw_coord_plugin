# Contract: Taipower Entry

## Scope

This contract governs Taipower coordinate content shared by
`TaiwanEntryController`, `TaiwanCoordinateEntryPane`, `TaipowerParser`,
`TaipowerCode`, `TaipowerGrid`, and `PreferenceStore`. It does not change
TWD97, TWD67, Address, or ATAK confirmation ownership.

## Grammar

### Canonical complete forms

```text
9-character  = REGION DIGIT{4} EW_LETTER NS_LETTER DIGIT{2}
11-character = REGION DIGIT{4} EW_LETTER NS_LETTER DIGIT{4}
```

Where:

- `REGION` is an existing supported main-island Taipower region letter.
- `EW_LETTER` is A-H.
- `NS_LETTER` is A-E.
- The 9-character form represents 10 m precision.
- The 11-character form represents 1 m precision.
- Datum/projection remains TWD67 TM2 zone 121.

### Supported single-field normalization

The existing parser behavior remains:

- case-insensitive letters;
- no-space and whitespace-separated forms;
- CR/LF removal for pasted content;
- one supported surrounding-parentheses pair;
- locale-independent uppercase normalization.

The exact raw editor value remains available to the draft. Parser
normalization is used for validation/projection and does not silently rewrite
the operator's raw field.

### Guided split form

```text
[REGION:1] [SUBREGION:4] [EW+NS:2] [PRECISION:2|4]
```

The final group:

- is empty, one digit, or three digits after prior content: `INCOMPLETE`;
- has two digits: validate as 10 m;
- has four digits: validate as 1 m;
- receives a fifth digit: reject that edit and preserve the first four.

### Layout action

- The Taipower pane uses one mode action in the far-right action column,
  matching the Address pane's 8:2 content/action structure.
- The button text names the alternate layout: guided while single-field
  content is visible, and single while guided content is visible.
- It replaces the earlier full-width segmented mode row; there is no second
  mode action or separate Taipower mode tab strip.
- Its target remains at least 48 dp, stays available for a lossless read-only
  projection, and becomes inert when the pane is disposed.

## Authoritative validation

1. `TaipowerParser` and `TaipowerCode` enforce A-H/A-E. UI filters are
   supplementary.
2. Invalid east-west I/J and north-south F-J return the existing
   `ParseResult.Reason.BAD_LETTER`.
3. The raw and split letter editors accept uppercase A-Z within their character
   class and maximum length. They preserve an out-of-range attempt instead of
   silently discarding it, then display localized position-specific feedback:
   east-west must be A-H or north-south must be A-E.
4. Malformed, incomplete, and out-of-coverage values expose no resolved point.
5. ATAK confirmation, map movement, and marker placement receive no point from
   an unresolved draft.
6. `TaipowerGrid` emits only A-H/A-E and treats an impossible encoder index as
   an invariant error rather than clamping/carrying it.
7. Existing canonical golden vectors and round-trip budgets remain unchanged.

## Draft projection

There is one coordinate revision and two views.

1. Editing one view updates the shared draft and derives the other view only
   when the projection is lossless.
2. Switching views does not create a coordinate revision.
3. Raw → split failure retains single mode and exact raw text and exposes
   localized projection feedback.
4. Split → raw failure retains split mode and every component and exposes
   localized projection feedback.
5. Raw → split → raw without an intervening coordinate edit restores the
   original raw text byte-for-byte.
6. Mode switching alone does not:
   - emit the controller's human-change callback;
   - change validation or precision;
   - replace a resolved point;
   - move the map;
   - invoke host confirmation.

## Controller-facing operations

Names may be adapted to project style, but behavior must be equivalent:

```java
TaipowerInputMode taipowerInputMode();
TaipowerEntryDraft taipowerDraft();
boolean selectTaipowerInputMode(TaipowerInputMode mode, boolean human);
void setTaipowerRawText(String text, boolean human);
void setTaipowerRegion(String text, boolean human);
void setTaipowerSubregion(String text, boolean human);
void setTaipowerSubgrid(String text, boolean human);
void setTaipowerPrecisionDigits(String text, boolean human);
```

- The mode-selection result reports whether the lossless projection succeeded.
- A successful human mode selection persists the mode but is not a
  human-coordinate change.
- Coordinate-edit methods perform at most one human-change notification per
  accepted edit callback.
- Programmatic render, activation, Auto Fill, and projection are silent.

## Preference contract

- Key ownership: plugin `PreferenceStore`.
- Serialized values: stable enum names or explicit stable strings.
- Default/fallback: `SINGLE_FIELD`.
- Write timing: successful operator mode selection.
- Independent from:
  - ATAK `coordview.formattedMGRS`;
  - the plugin's last native-entry coordinate unit;
  - retired custom Go To preferences.
- Coordinate text is not persisted.

## Host activation, Auto Fill, and Clear

1. Activation stages Taipower, TWD97, TWD67, and Address before publishing the
   pane, as established by the native-entry contract.
2. A non-null Auto Fill stages Taipower, TWD97, and TWD67 atomically and starts
   Address reverse lookup from the same exact WGS84 point without switching
   the selected page.
3. A representable host point creates canonical 11-character raw and split
   Taipower projections at the same revision.
4. An unrepresentable Taipower host point leaves Taipower unavailable while
   both TWD drafts and Address remain prepared from the same point.
5. Active-page Clear empties only the selected draft. For Taipower it clears
   both projections and retains the selected mode; for Address it also cancels
   pending lookup and candidates.
6. Address reverse completion retains the exact host WGS84 and never snaps to
   the nearest address-record point.
7. None of these programmatic projections emits a human change.

## Read-only and lifecycle

- Read-only mode prohibits coordinate text and precision edits.
- It may switch layouts as a non-mutating view only if a lossless projection
  exists.
- A successful operator-selected read-only projection persists the
  presentation mode under the same preference contract; it still emits no
  coordinate change.
- Disposed controllers/panes ignore field, mode, validation, focus, and IME
  callbacks.
- Locale replacement restores the durable mode, not stale coordinate text.

## Localization

English, Traditional Chinese (Taiwan), and Japanese resources must align for:

- single-field and split-field mode names;
- four split-field hints/labels;
- projection-failure feedback;
- incomplete feedback;
- position-specific east-west A-H and north-south A-E invalid-letter feedback;
- accessibility names for the mode switch and fields.

No source-language fallback is accepted in the supported locale matrix.

## Verification obligations

- 100 raw → split → raw round trips covering complete and safely
  representable partial drafts with zero unintended content/state changes.
- Every A-H east-west and A-E north-south boundary accepted.
- Every I/J east-west and F-J north-south boundary rejected.
- Identical resolved WGS84 for single/split 9- and 11-character fixtures.
- Default, saved, corrupt, reload, read-only, activation, Auto Fill, Clear,
  locale replacement, and dispose tests.
- Exact ATAK 5.5.x and 5.7.0.9 device acceptance before a public compatibility
  claim.
