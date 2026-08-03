# UI — Native Taiwan coordinate entry

**Features**: 011-native-coordinate-entry, 012-prefill-native-tabs,
013-native-address-entry, 014-native-entry-input-ux,
015-compact-address-layout

**Source**: `app/src/main/res/layout/taiwan_coordinate_entry_pane.xml` and
`app/src/main/java/com/atakmap/android/twcoord/nativeentry/`

This pane adds Taiwan coordinates and offline Taiwan address lookup to ATAK's
shared coordinate-entry dialog. It is the only public coordinate/address entry
surface provided by the plugin; ATAK still owns Go To, Convert Coordinate,
Auto Fill, Clear, Copy, and confirmation.

## Choose the appropriate workflow

| Goal | Use |
|---|---|
| Enter, Auto Fill, Clear, or Copy a Taiwan coordinate | ATAK **Go To** → **Taiwan** |
| Find an imported Taiwan street address | ATAK **Go To** → **Taiwan** → **Address** |
| Inspect every representation of a map item | Tap its Coordinate value → **Taiwan** |
| Import, replace, or remove offline datasets | Tools → **TW Coordinates** (opens the dataset manager directly) |

## Host entry paths

### Enter Coordinate / Go To

Open ATAK's standard coordinate-entry dialog, choose **Taiwan**, then choose
Taipower, TWD97, TWD67, or Address. The pane remains compact enough to leave the
ATAK-owned elevation, Auto Fill, Clear, Copy, and confirmation controls
reachable.

Every editable Taiwan field requests the inline keyboard presentation with
fullscreen/extract mode disabled. **Next** moves only to the next visible,
enabled plugin editor. **Done** and **Search** dismiss the keyboard without
invoking ATAK confirmation. ATAK still owns the final action.

<p align="center">
<img src="../images/26a-native-taipower-single.png" alt="ATAK Enter Coordinate dialog showing the Taiwan Taipower single-field layout and its Guided fields action at the far right" width="900"><br>
<sub>Single-field presentation keeps paste-friendly input on the left and names the alternate Guided fields layout at the far right; the coordinate is redacted.</sub>
</p>

<p align="center">
<img src="../images/26b-native-taipower-split.png" alt="ATAK Enter Coordinate dialog showing four guided Taipower fields and its Single field action at the far right" width="900"><br>
<sub>Guided presentation shows the 1/4/2/2-or-4 groups and names Single field as the return action; coordinate values are redacted.</sub>
</p>

<p align="center">
<img src="../images/23a-native-address-full.png" alt="ATAK Enter Coordinate dialog showing Taiwan Address single-field mode with its mode action at the upper right" width="900"><br>
<sub>Address single-field mode keeps its mode action at the upper right; address content is redacted.</sub>
</p>

<p align="center">
<img src="../images/27-native-address-structured.png" alt="ATAK Enter Coordinate dialog showing the compact Taiwan Address structured layout in two equal-width rows" width="900"><br>
<sub>The first 1:1 row pairs county/city with district/township; the second pairs road/locality with house-number/floor. ATAK-owned controls remain reachable, and address values are redacted.</sub>
</p>

<p align="center">
<img src="../images/25a-native-address-county-selector.png" alt="Native Taiwan Address county chooser showing only active imported counties with the map-centre county first" width="740"><br>
<sub>The immutable active-data chooser promotes one strictly contained map-centre county without making unimported postal rows searchable.</sub>
</p>

<p align="center">
<img src="../images/25b-native-address-district-selector.png" alt="Native Taiwan Address district chooser showing imported districts in map-centre and postal order" width="740"><br>
<sub>The district snapshot promotes one strict map-centre match, then preserves the selected county's postal baseline.</sub>
</p>

### Map item / Convert Coordinate

Open a map item's details and tap its **Coordinate** value. ATAK opens
**Convert Coordinate**, where **Taiwan** appears beside the built-in coordinate
pane. Selecting Taiwan projects the same source point into all representable
Taiwan drafts before rendering, so switching systems does not require Auto
Fill.

<p align="center">
<img src="../images/20-atak-point-detail-coordinate.jpg" alt="ATAK point details with the Coordinate field highlighted" width="420"><br>
<sub>The map-item Coordinate value is the Convert Coordinate entry point.</sub>
</p>

<p align="center">
<img src="../images/21-atak-convert-coordinate.jpg" alt="ATAK Convert Coordinate dialog with the Taiwan pane beside MGRS" width="900"><br>
<sub>Select Taiwan in Convert Coordinate to inspect the prepared Taiwan representations.</sub>
</p>

## Anatomy

```text
┌────────────────────────────────────────┐
│ [ Taipower ] [ TWD97 ] [ TWD67 ] [Address]│
│                                        │
│ Single: H7509 DB4016    [Guided fields] │
│ Guided: [H][7509][DB][4016] [Single field]│
│          1    4     2    2 or 4 chars   │
│                                        │
│ — when TWD97 or TWD67 is selected —    │
│ Easting          306963             m   │
│                  ──────                 │
│ Northing         2769619            m   │
│                  ───────                │
│ TM2 zone         [ 121 ] [ 119 ]        │
│ [119 accuracy advisory when applicable]│
│ [validation status]                    │
│                                        │
│ — when Address is selected —           │
│ Full: [臺中市南屯區黎明路2段130號] [Structured]│
│ Structured: [county/city] [district/township] [Single]│
│             [road/locality] [house/floor]             │
│                                  [Choose result]│
│ [normalised/status]                    │
└────────────────────────────────────────┘
ATAK-owned controls: Auto Fill · Clear · Copy · action/confirm
```

The pane owns one outer `ScrollView`; no nested vertical scroller competes with
ATAK's dialog. The root shrink-wraps compact Taipower, TWD97, and TWD67 content
and caps tall Address content at a 216 dp viewport so structured fields scroll
before reaching ATAK-owned elevation and action controls. Its geometry mirrors
ATAK's DD pane: compact horizontal label/input/unit rows, native underline
inputs at `wrap_content` height, 13 sp normal / 17 sp large title text, a 2 dp
top inset, and system/zone selectors whose outer and clickable heights both
remain 48 dp. The selector track and checked fill are centered at 36 dp by 6 dp
transparent top/bottom drawable insets; those bands remain part of each native
`RadioButton` target. System-tab labels use a dedicated 12 sp normal / 15 sp
large font to reduce visual weight without reducing the touch target. Empty
status text consumes no height. Like ATAK's built-in ADDR pane, Address entry
keeps input content on the left and its mode/candidate actions in a top-aligned
right column, so the mode control is not placed below the structured fields.
Structured mode places county/city and district/township in the first row, then
road/locality and house-number/floor in the second. Each field group receives
half of the content-column width while preserving the existing 3:7 label/input
proportion and row-major focus order. Both rows remain inside the pane's single
outer scroll owner.
Taipower uses the same 8:2 content/action structure: a single far-right action
names the alternate layout instead of consuming a full-width segmented row.

When ATAK opens the pane with a map-item or shared-dialog point, the plugin
prepares Taipower, TWD97, and TWD67 synchronously and starts an Address reverse
lookup from that exact WGS84 point. Switching tabs therefore reveals prepared
content without using Auto Fill. An unrepresentable system is cleared and
marked unavailable independently. Address completion never replaces the host
point with the nearest address-record point (the **reverse no-snap rule**).

## Coordinate systems

### Taipower

- **Single field** preserves exact paste/type content and accepts a
  9-character (10 m) or 11-character (1 m) code.
- **Guided fields** show the same draft as region letter (1), subregion digits
  (4), east-west/north-south 100 m letters (2), and precision digits (2 or 4).
  The first three completed groups advance focus; the two-digit final group
  remains focused so two more digits can extend 10 m input to 1 m.
- The far-right mode action shows **Guided fields** while single entry is
  active and **Single field** while guided entry is active.
- Switching layouts changes presentation only. A lossless round trip restores
  exact raw spacing/case; an unprojectable partial remains in its current
  layout with corrective feedback.
- The first 100 m letter is A-H and the second is A-E. Invalid A-Z attempts
  remain visible but expose no point.
- Auto Fill and Copy use the canonical 11-character form, for example
  `H7509 DB4016`.
- Coverage is the Taiwan main island. An outer-island Auto Fill clears the old
  Taipower draft and reports that the system cannot represent the supplied
  point while refreshing TWD97, TWD67, and Address from the same point.

### TWD97 and TWD67

- Enter integer easting and northing values in metres.
- Choose zone **121** for the main island or **119** for outer islands.
- Auto Fill determines the zone from the supplied point and replaces both
  fields atomically.
- TWD67 zone 119 shows an accuracy advisory because the available datum shift
  has a wider error budget there.

### Address

- **Full address** provides one field. Normalisation accepts common Taiwan
  variants such as `台`/`臺`, full-width digits, spacing, and Chinese numerals
  adjacent to address units.
- **Structured** provides four compact fields in two equal-column rows:
  county/city with district/township first, then road/locality with the
  remaining house-number/floor text. County/city and district/township are
  selectors; road/locality and remaining address stay editable. A county list
  contains only active imported county datasets. After a county is selected,
  its district list contains only distinct non-empty `places.township` values
  present in that active dataset.
- The map-centre county or district is promoted to the first row only when
  strict township-boundary containment resolves it and the same locality is
  active. Remaining counties follow the bundled Chunghwa Post selector order;
  remaining districts follow three-digit postal order. The open dialog is an
  immutable snapshot and never reorders while the operator is choosing.
- The postal catalog controls order only. It cannot make a missing county or
  district searchable. Imported values absent from the catalog remain
  selectable in deterministic name order.
- Switching modes projects one canonical draft, including unclassified text,
  so repeated switches are lossless. A pasted locality that is not currently
  selectable remains visible for correction instead of being discarded.
- Selecting another county clears an incompatible district and stale address
  result while preserving road/locality and remaining-address text. Selecting
  a district preserves both editable fields.
- A unique exact result becomes the resolved host point. Multiple credible
  results remain unresolved until the operator taps **Choose result** and
  selects a row with county/district/road context. Selection alone never pans
  the map; ATAK's confirmation performs the host action.
- Village/neighbourhood text may be omitted. A unique county/district,
  street/section, and address-tail match resolves automatically; identical
  matches in multiple villages remain unresolved for explicit selection.
- Candidate retrieval is bounded before display. Exact matches are shown
  exclusively. Otherwise the 20-row shortlist initially reserves six
  text-prefix, eight numeric-nearest, four current-map-distance, and two
  fallback rows, then deduplicates and backfills in that semantic order.
  Distance rows are omitted when ATAK has no valid map-centre anchor.
- A direct-road query without `巷` or `弄` ranks direct-road numbers ahead of
  lane/alley addresses. For example, a `臺灣大道三段9` draft prefers matching
  prefixes and nearby direct numbers instead of filling the dialog with
  unrelated `...巷...弄9號` rows.
- A missing applicable county dataset leaves the three coordinate systems
  usable and shows guidance to open **TW Coordinates** for data management.
- Forward and reverse lookup are local-only. Editing, mode changes, pane
  replacement, or plugin unload fence stale callbacks and candidate dialogs.

### Locality-order provenance and updates

`app/src/main/assets/address/chunghwa_post_postal_localities.json` is a
versioned, offline ordering reference derived from the Chunghwa Post county
selector and published three-digit locality data. Its authority URLs,
retrieval/effective dates, and source hashes are stored in the asset. The
reproducible refresh command is documented in
`specs/013-native-address-entry/quickstart.md`; refreshes must pass the
22-county/371-locality schema, uniqueness, coordinate, and representative
prefix tests before review. See ADR-0027 for the authority boundary.

## ATAK-owned controls

The surrounding dialog owns its buttons and resulting action:

- **Auto Fill** calls the pane with ATAK's current point, atomically refreshes
  Taipower/TWD97/TWD67, and starts Address reverse lookup from that same exact
  WGS84 point. It retains the selected page and emits no human-change callback.
- **Clear** supplies no point and clears only the active Taiwan draft. With
  Address active it also cancels its pending lookup/candidates.
- **Copy** requests a canonical string without mutating the draft.
- The dialog's action consumes horizontal WGS84 metadata. The plugin does not
  invent altitude and does not move the map during parsing or formatting.
- Keyboard editor actions never call that dialog action.

## Read-only and additional dialogs

ATAK may reuse the global pane in details or other location dialogs. When the
host supplies `editable=false`, coordinate/address editors, system/zone
selectors, and candidate actions remain visible but disabled. The Taipower and
Address layout selectors may still switch between lossless read-only
projections. Resolved content can be read and formatted; attempted coordinate
edits do not change the controller result or notify ATAK.

## Localisation and lifecycle

Strings are available in English, Taiwan Traditional Chinese, and Japanese.
When the plugin language changes while no native dialog is open, the registrar
replaces the pane immediately. If ATAK currently has the pane attached, refresh
waits for detach so an active host dialog is never mutated in place.

Registration failure, supported version skew, plugin unload, and stale queued
callbacks are contained by the registrar. Programmatic render does not steal
editor focus; read-only transition dismisses pane-owned input; disposal removes
editor/mode listeners and invalidates posted focus/render work. ATAK's built-in
panes remain usable.

## Compatibility

The plugin declares ATAK 5.5.0 as its minimum runtime. It compiles and is
currently validated with the ATAK-CIV 5.7.0.9 SDK. The checked-in exact ATAK
5.5 device matrix remains pending and is not implied by the successful SDK or
TPP build.
