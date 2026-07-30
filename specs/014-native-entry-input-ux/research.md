# Research: Native Taiwan Input UX

## R1 - Retain the existing public ATAK native-entry seam

**Decision**: Continue using the registered plugin-owned
`CoordinateEntryPane` view and the existing
`CoordinateEntryCapability.registerPane`/`unregisterPane` lifecycle. Make all
changes below the current `TaiwanCoordinateEntryPane`,
`TaiwanEntryController`, and plugin-resource boundary.

**Rationale**: ADR-0023 already establishes the controller/formatter/pane
separation, exact-instance unregistration, UI-thread lifecycle, single scroll
owner, and host-owned actions. The pinned ATAK-CIV 5.7.0.9
`main.jar` still exposes the recorded public signatures, and ATAK-CIV 5.5.1.1
source provides a stable minimum-runtime anchor. This feature needs only
Android view behavior and plugin-owned data; it does not need a new ATAK seam.

**Alternatives considered**:

- Reach into ATAK's `AlertDialog` or soft-input window policy: rejected because
  the public pane contract supplies a view, not host window ownership.
- Open a plugin Activity, Fragment, dialog, or keyboard: rejected because it
  would leave or replace Go To and add a second lifecycle/confirmation owner.
- Replace `CoordinateEntryPane` registration: rejected because the existing
  public seam already supports the required UI.

**Evidence**:

- [ATAK-CIV 5.5.1.1 `CoordinateEntryCapability`](https://github.com/TAK-Product-Center/atak-civ/blob/5.5.1.1/atak/ATAK/app/src/main/java/com/atakmap/android/gui/coordinateentry/CoordinateEntryCapability.java)
- [ADR-0023: Native Taiwan Coordinate Entry](../../docs/adr/0023-native-taiwan-coordinate-entry.md)
- [ADR-0024: ATAK 5.7.0.9 Compile SDK](../../docs/adr/0024-use-atak-5-7-0-9-compile-sdk.md)

## R2 - Request inline IME presentation on every editable field

**Decision**: Keep every editor single-line and configure it with both
`IME_FLAG_NO_FULLSCREEN` and `IME_FLAG_NO_EXTRACT_UI`. Add
`IME_FLAG_FORCE_ASCII` only to Taipower letter/raw editors. Never apply the
ASCII restriction to Taiwan Address editors.

**Rationale**:

- `IME_FLAG_NO_FULLSCREEN` is Android's direct request to prevent a compliant
  IME from replacing the application with a full-screen editor.
- `IME_FLAG_NO_EXTRACT_UI` matches the compatibility behavior used by ATAK
  5.5's native MGRS letter fields and provides additional protection for older
  extract editors.
- Both APIs predate Android API 26 and add no minimum-SDK or ATAK API seam.
- Android documents the flag as a request, not a guarantee, so device/IME
  behavior remains a release gate.

**Field contract selected**:

| Field | Input type | Action | Additional flags/limits |
|-------|------------|--------|-------------------------|
| Taipower single | Uppercase text, no suggestions | Done | Force ASCII; no restrictive length because supported paste may contain whitespace/parentheses |
| Split region | Uppercase text, no suggestions | Next | Force ASCII; length 1 |
| Split subregion | Number | Next | Length 4 |
| Split 100 m letters | Uppercase text, no suggestions | Next | Force ASCII; length 2 |
| Split precision digits | Number | Done | Length 4 |
| TWD97/TWD67 easting | Number/decimal as currently supported | Next | Existing numeric validation retained |
| TWD97/TWD67 northing | Number/decimal as currently supported | Done | Existing numeric validation retained |
| Address full | Postal address, Chinese-capable | Search | Existing parsing retained |
| Address road | Postal address, Chinese-capable | Next | Existing parsing retained |
| Address tail | Postal address, Chinese-capable | Search | Existing parsing retained |

County and district remain non-focusable selector controls and must not open an
IME session.

**Alternatives considered**:

- Copy ATAK's MGRS XML exactly: rejected because native MGRS applies
  `NO_EXTRACT_UI` only to letter fields and does not cover every Taiwan editor.
- Apply `FORCE_ASCII` everywhere: rejected because Address entry must accept
  Chinese and Japanese IMEs.
- Rely on the keyboard vendor's default presentation: rejected because it
  reproduces the current full-screen extract-editor problem.

**Evidence**:

- [Android `imeOptions`](https://developer.android.com/reference/android/R.attr#imeOptions)
- [Android `IME_FLAG_NO_FULLSCREEN`](https://developer.android.com/reference/android/view/inputmethod/EditorInfo#IME_FLAG_NO_FULLSCREEN)
- [Android `IME_FLAG_NO_EXTRACT_UI`](https://developer.android.com/reference/android/view/inputmethod/EditorInfo#IME_FLAG_NO_EXTRACT_UI)
- [ATAK-CIV 5.5.1.1 native MGRS layout](https://github.com/TAK-Product-Center/atak-civ/blob/5.5.1.1/atak/ATAK/app/src/main/res/layout/coordinate_pane_mgrs.xml)

## R3 - Handle editor actions locally without submitting the host dialog

**Decision**: Give consecutive plugin editors explicit `nextFocusForward` and
`nextFocusDown` IDs and install a guarded `OnEditorActionListener`.

- Next focuses the next visible, enabled plugin editor.
- Done/Search consumes the action, clears editor focus, and requests keyboard
  dismissal.
- `IME_NULL` plus Enter performs one equivalent action for physical keyboards,
  with key phases de-duplicated.
- No editor action calls `getGeoPointMetaData()`, activates ATAK's positive
  button, or emits a host-change notification.
- Fixed split groups auto-advance at lengths 1, 4, and 2. The final group does
  not auto-finish at two digits because the operator may extend it to four.
- A mode-switch focus transfer occurs only when a Taipower editor already owns
  focus, and every posted transfer checks lifecycle generation, disposal,
  visibility, and editability.

**Rationale**: Explicit local focus is deterministic when alternate-layout
fields are `GONE`; vendor-default traversal is not. ATAK retains confirmation,
elevation, marker, and validation ownership.

**Alternatives considered**:

- XML actions without listeners: rejected because hidden alternate editors
  make implicit focus order ambiguous across IMEs.
- Treat Done/Search as Go To confirmation: rejected because it bypasses host
  ownership.
- Focus an ATAK-owned elevation field: rejected because that would couple the
  plugin to a host resource ID not exposed by the pane contract.

**Evidence**:

- [ATAK MGRS focus and auto-advance behavior](https://github.com/TAK-Product-Center/atak-civ/blob/5.5.1.1/atak/ATAK/app/src/main/java/com/atakmap/android/gui/coordinateentry/MGRSPane.java)
- [Android `OnEditorActionListener`](https://developer.android.com/reference/android/widget/TextView.OnEditorActionListener)
- [Android forward-focus attribute](https://developer.android.com/reference/android/view/View#attr_android:nextFocusForward)

## R4 - Provide exactly two Taipower projections over one draft

**Decision**: Implement `SINGLE_FIELD` and `SPLIT_FIELDS`, using a single
`TaipowerEntryDraft` as the authority. The split projection is exactly:

```text
[region: 1 letter] [subregion: 4 digits]
[100 m cell: 2 letters] [precision: 2 or 4 digits]
```

Single mode preserves the original raw value byte-for-byte until parsing can
project it safely. Split mode preserves each field independently. Switching
views updates only presentation and the mode preference. If the requested
projection cannot represent the current draft losslessly, the current view
remains active and reports a localized projection error.

**Rationale**: A shared draft prevents mode drift and lets activation, Auto
Fill, Clear, validation, and host change reporting retain one authority. It
also matches the useful aspect of ATAK's native MGRS swap behavior without
copying its third partially formatted mode, which is not required here.

**Alternatives considered**:

- Keep separate raw and split strings: rejected because they can diverge and
  make host notifications/order dependent.
- Normalize an unprojectable raw draft during a switch: rejected because that
  silently changes operator input.
- Add ATAK MGRS's third partially formatted mode: rejected because FR-004
  requires exactly two layouts.
- Disable mode switching whenever input is incomplete: rejected because
  representable prefixes can be moved losslessly and are explicitly covered by
  the success criteria.

**Evidence**:

- [ATAK MGRS mode/persistence implementation](https://github.com/TAK-Product-Center/atak-civ/blob/5.5.1.1/atak/ATAK/app/src/main/java/com/atakmap/android/gui/coordinateentry/MGRSPane.java)
- [Taipower entry contract](./contracts/taipower-entry-contract.md)

## R5 - Persist only the plugin-owned Taipower input mode

**Decision**: Store the selected mode as a stable string enum in a new
plugin-owned `SharedPreferences` key. Missing, blank, or unknown values fall
back to `SINGLE_FIELD`. Do not read or write ATAK's
`coordview.formattedMGRS`, the native last-tab key, or retired plugin workflow
preferences.

**Rationale**: Existing installations had only a single Taipower field, so
single mode is the non-surprising migration default. Drafts remain pane-local
and should not become durable coordinate history.

**Alternatives considered**:

- Reuse ATAK's MGRS preference: rejected because the concepts have different
  values and ownership.
- Persist both projections/draft text: rejected because the requirement covers
  only mode and the current native pane owns drafts in memory.
- Use a boolean: rejected because stable enum strings are self-describing and
  fail safely if future modes are added.

## R6 - Enforce canonical A-H/A-E ranges at the domain boundary

**Decision**: Enforce A-H for the east-west 100 m subgrid letter and A-E for
the north-south letter in both `TaipowerParser` and `TaipowerCode`. UI filters
provide guidance but are not authoritative. Invalid positions retain the
existing `BAD_LETTER` result.

**Rationale**: Each 800 m by 500 m chart is divided into forty 100 m by 100 m
cells:

- `800 / 100 = 8` east-west indices `0..7`, therefore A-H.
- `500 / 100 = 5` north-south indices `0..4`, therefore A-E.

The current encoder already produces only those indices; only parsing,
constructor validation, comments/clamps, tests, and documentation are
over-permissive. Existing canonical output and golden vectors do not change.
Former A-I/A-J and A-F/A-J inputs are noncanonical aliases into neighboring
subregions and must fail rather than be silently canonicalized.

**Provenance**:

- [Taipower Journal: pole coordinate structure](https://service.taipower.com.tw/tpcjournal/article/7441)
- [Taipower Heritage: power-coordinate introduction](https://service.taipower.com.tw/Collection/2009/2025/7769/blogPost)
- [OSGeo: Taiwan Power Company grid](https://wiki.osgeo.org/wiki/Taiwan_Power_Company_grid)
- [Jidanni `taipowergrid` reference implementation](https://www.jidanni.org/geo/taipower/programs/taipowergrid)
- [Sunriver: 800 m by 500 m, A-H/A-E subdivision](https://www.sunriver.com.tw/grid_taipower.htm)

**Alternatives considered**:

- Preserve A-J/A-J for compatibility: rejected because it accepts impossible
  cells and neighboring-subregion aliases.
- Accept and carry overflow into the next subregion: rejected because it
  silently replaces the operator's supplied coordinate and is ambiguous at
  region boundaries.
- Enforce only in the UI: rejected because paste, controller, and other parser
  callers must be safe.
- Change only the parser: rejected because invalid `TaipowerCode` value objects
  would remain constructible.

**Decision record**: Add ADR-0028, partially superseding only ADR-0001's 100 m
letter-range statement. Retain the datum, region table, anchors, precision,
and conversion decisions. Historical Feature 001/002 artifacts remain
historical; current reference and user documentation is corrected.

## R7 - Verify the grid correction with provenance and exhaustive boundaries

**Decision**: Preserve all existing golden expectations and add:

- Parser acceptance for every A-H east-west and every A-E north-south value.
- Parser `BAD_LETTER` rejection for I/J east-west and F-J north-south.
- Direct `TaipowerCode` constructor acceptance of A/A and H/E and rejection of
  both invalid ranges.
- Both 9-character and 11-character forms at AA and HE.
- Encoder wrap vectors that prove H/E maxima and the next 800 m/500 m
  subdivision without ever emitting I or F.
- Output-shape assertions of `[A-H][A-E]` rather than `[A-J]{2}`.
- Both UI layouts keeping invalid complete drafts unresolved and unable to
  report a point.

**Provenance-backed vectors**:

| Code | Expected TWD67 |
|------|----------------|
| `G8150 HD7812` | `(235571, 2675382)` |
| `W9999 HE9999` | `(329999, 2449999)` |

**Encoder boundary vectors**:

| TWD67 | Expected 11-character code |
|-------|----------------------------|
| `(258000, 2655000)` | `H1010 AA0000` |
| `(258799, 2655499)` | `H1010 HE9999` |
| `(258800, 2655000)` | `H1110 AA0000` |
| `(258000, 2655500)` | `H1011 AA0000` |

## R8 - Center a 36 dp visual track inside the existing 48 dp targets

**Decision**: Keep all three selector `RadioGroup`s and every `RadioButton` at
a real 48 dp layout/hit height. Inset the group track and every option-state
drawable by 6 dp at the top and bottom, producing an exact centered 36 dp
visible track. Keep vertical padding at zero and children at `match_parent`.
Use named dimensions for the 48 dp target, 36 dp track, and 6 dp inset.

**Rationale**: Drawable insets reduce visual weight without changing hit,
focus, pane-height, row-ownership, or native radio semantics. Both track and
selected-option drawables must change; otherwise the current 44 dp selected
fill would protrude beyond the new track.

**Alternatives considered**:

- Make controls 36 dp with `TouchDelegate`: rejected because delegated space
  would overlap adjacent rows and add dispatch/accessibility lifecycle.
- Wrap a 36 dp control in a 48 dp clickable container: rejected because it
  adds duplicate/delegated semantics without changing the layout footprint.
- Use margins: rejected because margins are not clickable.
- Add group padding: rejected because it measures `match_parent` children
  below 48 dp and repeats the Feature 012 regression.
- Reduce the full row to 36 dp: rejected because a non-overlapping 48 dp target
  cannot fit inside it.

**Evidence**:

- [Android view accessibility target guidance](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views)
- [Selector presentation contract](./contracts/selector-presentation-contract.md)

## R9 - Preserve selector semantics and disabled-selected visibility

**Decision**:

- Scope compaction only to the system, TWD97 zone, and TWD67 zone selectors.
- A tap anywhere in an option's 48 dp rectangle, including transparent bands,
  selects exactly that option and emits one human-change notification.
- Programmatic render remains silent.
- Add a disabled-and-checked drawable/text state before generic disabled so
  the selected system/zone remains visible in read-only mode.
- Retain native `RadioGroup`/`RadioButton` checkable, selected, enabled, name,
  and traversal semantics with no overlay or duplicate focus node.
- Validate labels at font scales 1.0 and 2.0 for English, Traditional Chinese
  (Taiwan), and Japanese at the smallest supported pane width.

**Rationale**: A smaller drawing must not reduce accessibility or erase the
current selection in read-only Convert Coordinate. Android 14 supports 200%
nonlinear font scaling, making maximum-font device validation material.

**Evidence**:

- [Android 14 nonlinear font scaling](https://developer.android.com/about/versions/14/features#accessibility)

## R10 - Split automated proof from device-only claims

**Decision**: Use JVM/Robolectric tests for parser/draft logic, resource flags,
focus actions, geometry, touch dispatch, preference behavior, lifecycle guards,
and locale parity. Use physical/runtime evidence for actual keyboard
presentation, ATAK dialog visibility/reachability, TalkBack, real font
rendering, timing, and reload behavior.

**Rationale**: Robolectric can prove plugin configuration and callbacks but
cannot prove a real IME honors flags or that an ATAK-hosted dialog remains
visible.

**Device release gates**:

- Exact ATAK 5.5.x and ATAK 5.7.0.9 runtimes.
- Record device, Android build, default IME package/version/subtype, pane
  width, density, orientation, locale, and font scale.
- Twenty focus attempts per editable field in portrait and landscape.
- Both Taipower modes, fixed-field auto-advance, manual Next, final Done, and
  continued entry from two to four final digits.
- A sanitized screenshot measurement of 36 dp visible tracks and accessibility
  bounds of at least 48 dp.
- Top/bottom transparent-band taps, read-only selected state, TalkBack/Switch
  Access order/names, and no clipped labels.
- Locale replacement and 20 plugin reload/dispose cycles while a field owns
  focus.
- Focus feedback within 500 ms p95 and mode/validation updates within 100 ms
  p95 on the current reference device.
- Third-party IME safe degradation, plus hardware keyboard, DeX, floating
  keyboard, and multi-window smoke checks when available.

The current build and current-device run never substitute for exact
minimum-runtime evidence.

## R11 - Treat non-null Auto Fill as all-page host-point preparation

**Decision**: Route every non-null Auto Fill point through the controller's
atomic all-coordinate staging path, then start Address reverse lookup from the
same WGS84 point. Keep null Clear active-page-only.

**Rationale**: Activation and Auto Fill both mean that ATAK supplied one host
point. Preparing only the selected page leaves other Taiwan pages stale and
forces repeated operator actions. Reusing the established activation staging
path keeps Taipower/TWD97/TWD67 coherent without adding an ATAK seam, while
the Address controller retains its asynchronous no-snap contract.

Auto Fill does not switch pages, change the persisted Taipower presentation
mode, notify ATAK of a human edit, invoke confirmation, or wait for Address
lookup. If a supplied point is outside Taipower coverage, Taipower alone is
unavailable while both TWD drafts and Address remain prepared. When a TWD
conversion is unrepresentable, its previous explicit zone selection is kept
for corrective continuity.

**Alternatives considered**:

- Active-page-only Auto Fill: rejected because inactive pages can describe an
  older point.
- Four sequential operator actions: rejected because it is repetitive and
  can mix host points.
- All-page Clear: rejected because clearing the visible draft is a distinct,
  destructive editing action.

**Decision record**: ADR-0029 partially supersedes ADR-0023 only for Auto Fill
scope.
