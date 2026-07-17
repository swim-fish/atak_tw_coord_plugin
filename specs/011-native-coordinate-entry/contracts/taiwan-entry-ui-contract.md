# Contract: Taiwan Native Entry UI

## Layout

Resource: `res/layout/taiwan_coordinate_entry_pane.xml`

The root is one vertical `ScrollView` with one vertical child. ATAK owns the
pane frame, tab strips, and host controls but does not vertically scroll the
active pane, so this is the only content scroll owner. No nested scroll
container is allowed, and at most one coordinate field group is visible.

| Control role | Required behaviour |
|--------------|--------------------|
| System selector | Three mutually exclusive 52 dp-high choices: Taipower, TWD97, TWD67; active choice always visible |
| Taipower group | One 20 sp text field with 14 dp vertical padding and format hint; supports paste, case, and spacing accepted by the existing parser |
| TWD97 group | Two 20 sp ASCII integer-metre fields with 13 dp padding and a 10 dp gap plus explicit 121/119 selector |
| TWD67 group | Two 20 sp ASCII integer-metre fields with 13 dp padding and a 10 dp gap plus explicit 121/119 selector |
| Zone selector | Two 50 dp-high values labelled with central-meridian/area meaning; selected zone visible in editable and read-only states |
| Advisory | TWD67 zone 119 shows the existing outer-island accuracy advisory inline |
| Error/status | One live-region-capable localised text area; visible only for corrective/advisory state; never overlays a field |

The pane must not add its own Go/Confirm, Auto Fill, Clear, Copy, elevation,
marker, or affiliation controls. ATAK owns those controls.

The vertical content inset is 12 dp. These dimensions intentionally match
`res/layout/tw_coord_goto.xml`. On every compatibility-matrix device,
orientation, and font scale, equivalent native controls must be no smaller and
no less reachable than the custom GoTo controls under the same configuration.

## Visual and interaction states

| State | Fields | Selectors | Message |
|-------|--------|-----------|---------|
| Empty/editable | Enabled | Enabled | Optional format guidance only |
| Valid/editable | Enabled | Enabled | No error; zone advisory remains when applicable |
| Invalid/editable | Enabled; offending group remains visible | Enabled | Specific corrective message |
| Read-only | Disabled but legible/selectable for accessibility reading | Disabled | No editing affordance; zone remains visible |
| Auto Fill unrepresentable | Cleared | System selector remains as allowed by editability | Localised unsupported-area message |
| Disposed | Disabled | Disabled | No new result/action |

## Validation timing

- Pane activation/rendering and human edits update local state within 100 ms.
- Blank or partially typed input may show guidance without throwing a dialog.
- ATAK confirmation/Copy invokes authoritative parse and produces a checked
  error when invalid.
- Switching systems never interprets one system's text as another system.
- Programmatic activation/Auto Fill clears an old error before rendering the
  new result.

## Accessibility

- Every field has a persistent visible label or an accessibility label that
  names datum, axis, and metre unit.
- The system and zone choices expose selected/disabled state to accessibility
  services.
- Touch targets are at least 48 dp high; visible focus order follows system,
  fields, zone, advisory/error.
- Error/status changes use an appropriate polite announcement mechanism and do
  not steal keyboard focus.
- Colour is not the only indication of active system, zone, invalid state, or
  read-only state.

## Localisation

All introduced strings exist with matching format arguments in:

- `res/values/strings.xml` (English)
- `res/values-zh-rTW/strings.xml` (Traditional Chinese, Taiwan)
- `res/values-ja/strings.xml` (Japanese)

Numeric parsing is locale-safe: coordinate field storage is ASCII decimal
integer metres, while display grouping must not introduce characters the
parser cannot read. Taipower normalisation remains defined by the existing
parser.

## Coexistence

The UI does not expose custom-page marker mode, ATAK icon palette, Recent list,
or saved custom drafts. Native selection writes only
`pref_native_entry_last_unit`. Opening or using this pane must leave the custom
page's controls and preferences byte-for-byte unchanged.
