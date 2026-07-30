<!--
  Current end-user guide for offline forward address lookup.
  The filename is retained so historical links continue to work.
-->

# Native Taiwan Address — feature guide

Offline forward address lookup now lives in ATAK's native coordinate-entry
dialog. The retired **TW Addr Search** Tools page is no longer registered.

## Before searching

Address data is not bundled with the plugin. Open **TW Coordinates**, select the
dataset-status/management row, and import the boundary layer plus the applicable
county SQLite or supported ZIP bundle. See [Offline address data](tw-offline-addr.md).

All lookup and normalisation are local. The plugin does not request Android's
`INTERNET` permission.

## Open Address

1. Open ATAK **Go To**.
2. Select **Taiwan**.
3. Select **Address**, the fourth internal tab.

The initial mode is one full-address input. Use the mode selector to expose four
structured fields when administrative parts need to be entered or corrected
separately.

## Full-address mode

Enter the address as normally written, for example:

```text
臺中市南屯區黎明路二段130號6樓之4
```

The canonical draft normalises common Taiwan input variants:

- `台` and `臺`;
- full-width digits, whitespace, and punctuation;
- Chinese numerals immediately associated with address units;
- house, lane, alley, floor, room, and subnumber forms supported by the local
  parser.

City, district, and road/locality recognition use dataset-backed longest-prefix
matching rather than a single regular expression. This prevents names such as
`臺南市新市區` or `八德路` from being split at the wrong character.

## Structured mode

Structured mode exposes:

1. county/city;
2. district/township;
3. road or named locality;
4. remaining address (section, lane, alley, house number, floor, room, and any
   unclassified suffix).

Both modes are projections of one `AddressDraft`. Switching modes repeatedly
must not discard or duplicate unclassified text. Edits invalidate older lookup
requests and candidate lists, so a slow result cannot overwrite a newer draft.

County/city and district/township use selection dialogs rather than
unrestricted text entry. The county/city dialog contains only datasets that are
currently imported and active. After a county/city is selected, the
district/township dialog contains only distinct, non-empty district values
available in that dataset.

The map-centre county/city or district is promoted to the first row only when
the exact locality is available in the active offline data. The remaining
county/city choices follow the bundled Chunghwa Post locality order, and
district choices follow postal-code order. Imported values that are not present
in the bundled ordering catalog remain selectable at the end of the list. Each
open dialog is a stable snapshot; importing, activating, or removing data takes
effect the next time the dialog opens.

Changing the county/city clears an incompatible district and any resolved
coordinate, while preserving the road and remaining-address text. Changing the
district also clears the resolved coordinate without discarding the remaining
draft. If a pasted or parsed locality is not currently selectable, it remains
visible so the user can correct it instead of losing input.

<img src="images/25a-native-address-county-selector.png"
     alt="Native Taiwan address county and city selector showing active imported datasets"
     width="740">

*County/city choices are limited to active imported datasets; an available
map-centre match is promoted to the first row.*

<img src="images/25b-native-address-district-selector.png"
     alt="Native Taiwan address district selector showing imported districts"
     width="740">

*District/township choices come from the selected dataset and follow postal
ordering after an available map-centre match.*

## Results and candidate selection

- A unique exact record resolves the point for ATAK confirmation.
- Multiple credible records remain unresolved. Tap **Choose result** and compare
  county, district, road/locality, house number, and other distinguishing
  context.
- The dialog displays at most 20 candidates. Exact matches are exclusive.
  Otherwise it initially reserves six text-prefix, eight numeric-nearest, four
  current-map-distance, and two fallback rows, removes duplicates, and
  backfills unused capacity in that order.
- Distance candidates use ATAK's valid current map centre. If no valid anchor
  exists, that category is skipped and the other categories fill the list.
- When the typed address does not include `巷` or `弄`, direct-road house
  numbers rank ahead of lane/alley records. Selecting a row updates the
  draft/result but does not pan the map; ATAK's normal confirmation performs
  Go To.
- An invalid or unmatched address reports a localised status without retaining
  a stale point.
- If no applicable county dataset is active, use the displayed **TW Coordinates**
  data-management guidance. Coordinate tabs remain usable.

The Settings **Address search result order** preference chooses distance or best
text match for candidate ordering.

## Convert Coordinate and reverse lookup

From a map item's details, tap **Coordinate**, open **Taiwan**, then select
**Address**. The address is resolved asynchronously while Taipower/TWD97/TWD67
remain immediately usable. The displayed nearest record is descriptive only:
the plugin preserves the exact ATAK-supplied WGS84 point and never snaps it to
the record.

The same no-snap rule applies when one **Auto Fill** refreshes all four Taiwan
pages. **Clear** clears only the active Address draft and cancels pending
candidates. In a read-only host flow, the address can be displayed but cannot
be edited or selected.

## Troubleshooting

**No dataset / no match:** Open **TW Coordinates** and verify boundary data and
the applicable county are active.

**Several similar records:** Use **Choose result** and compare administrative
context; the plugin intentionally does not guess. The list is a bounded,
category-balanced shortlist rather than an exhaustive dump of every address on
a dense road.

**A recent edit seems to return an older result:** close and reopen the pane if
needed. The lifecycle fence rejects stale callbacks, and the older result must
not become confirmable.

**Want to manage disk usage or update a county?** See
[Offline address data](tw-offline-addr.md).
