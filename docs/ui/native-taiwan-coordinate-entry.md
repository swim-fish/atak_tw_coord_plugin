# UI — Native Taiwan coordinate entry

**Features**: 011-native-coordinate-entry, 012-prefill-native-tabs,
013-native-address-entry

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

<p align="center">
<img src="../images/23a-native-address-full.png" alt="ATAK Enter Coordinate dialog showing Taiwan Address in single-field mode" width="900"><br>
<sub>Address single-field mode inside ATAK's native Enter Coordinate dialog.</sub>
</p>

<p align="center">
<img src="../images/23b-native-address-structured.png" alt="ATAK Enter Coordinate dialog showing Taiwan Address in four structured fields" width="900"><br>
<sub>The same draft projected into county, district, road, and address-tail fields.</sub>
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
│ Taipower         H7509 DB4016           │
│                  ────────────           │
│                  11 chars · main island │
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
│ [ Full address ] [ Structured ]        │
│ Full: [臺中市南屯區黎明路2段130號]       │
│ or: county · district · road · tail    │
│ [normalised/status] [Choose result]    │
└────────────────────────────────────────┘
ATAK-owned controls: Auto Fill · Clear · Copy · action/confirm
```

The pane owns one outer `ScrollView`; no nested vertical scroller competes with
ATAK's dialog. Its geometry mirrors ATAK's DD pane: compact horizontal
label/input/unit rows, native underline inputs at `wrap_content` height,
13 sp normal / 17 sp large title text, a 2 dp top inset, and system/zone
selectors whose outer and clickable heights both remain 48 dp. Their visual
vertical inset is drawable-owned, so it does not enlarge the pane. Empty status
text consumes no height, so the pane stays above ATAK's elevation and action
controls.

When ATAK opens the pane with a map-item or shared-dialog point, the plugin
prepares Taipower, TWD97, and TWD67 synchronously and starts an Address reverse
lookup from that exact WGS84 point. Switching tabs therefore reveals prepared
content without using Auto Fill. An unrepresentable system is cleared and
marked unavailable independently. Address completion never replaces the host
point with the nearest address-record point (the **reverse no-snap rule**).

## Coordinate systems

### Taipower

- Enter a 9-character (10 m) or 11-character (1 m) code. Auto Fill and Copy use
  the canonical 11-character form, for example `H7509 DB4016`.
- Coverage is the Taiwan main island. An outer-island Auto Fill clears the old
  draft and reports that the selected system cannot represent the supplied
  point.

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
- **Structured** provides four compact fields. County/city and district are
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

- **Auto Fill** calls the pane with ATAK's current point and replaces the active
  draft. It intentionally remains active-only; it is distinct from the
  all-system preparation performed when ATAK activates the pane with a point.
- **Clear** supplies no point and clears only the active Taiwan draft. With
  Address active it also cancels its pending lookup/candidates.
- **Copy** requests a canonical string without mutating the draft.
- The dialog's action consumes horizontal WGS84 metadata. The plugin does not
  invent altitude and does not move the map during parsing or formatting.

## Read-only and additional dialogs

ATAK may reuse the global pane in details or other location dialogs. When the
host supplies `editable=false`, fields, mode/system/zone selectors, and
candidate actions remain visible but disabled. Resolved content can still be
read and formatted; attempted edits do not change the controller result or
notify ATAK.

## Localisation and lifecycle

Strings are available in English, Taiwan Traditional Chinese, and Japanese.
When the plugin language changes while no native dialog is open, the registrar
replaces the pane immediately. If ATAK currently has the pane attached, refresh
waits for detach so an active host dialog is never mutated in place.

Registration failure, supported version skew, plugin unload, and stale queued
callbacks are contained by the registrar. ATAK's built-in panes remain usable.

## Compatibility

The plugin declares ATAK 5.5.0 as its minimum runtime. It compiles and is
currently validated with the ATAK-CIV 5.7.0.9 SDK. The checked-in exact ATAK
5.5 device matrix remains pending and is not implied by the successful SDK or
TPP build.
