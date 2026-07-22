# Native Address Entry and Tools Consolidation

**Status:** Planning input

**Date:** 2026-07-22

**Target:** A new Spec Kit feature after the shipped native coordinate-entry work

## 1. Decision Summary

The Taiwan pane registered with ATAK's native `CoordinateEntryCapability` will grow from three
internal tabs to four:

1. Taipower
2. TWD97
3. TWD67
4. Address

The Address tab will provide a single full-address field and an optional structured-entry mode.
The user switches between the two modes with a plugin-owned button following the interaction
pattern of ATAK's MGRS pane. Both modes describe the same address draft and must remain
synchronized.

The plugin's public Tools entries will be consolidated at the same time:

| Current Tools entry | Target state | Rationale |
|---|---|---|
| `TW Coordinates` | Keep | The only public plugin Tools entry; owns settings and offline-data navigation. |
| `TW Coord GoTo` | Remove | Native ATAK Go To and Convert Coordinate now host Taiwan coordinate entry. |
| `TW Addr Search` | Remove | Forward address search moves into the native Taiwan Address tab. |
| `TW Offline Addr` | Remove its Tools icon only | Dataset import, status, replacement, and removal remain available inside `TW Coordinates`. |

This is a user-interface consolidation, not removal of offline address capability.

## 2. Target Information Architecture

```text
ATAK Tools
└── TW Coordinates
    ├── Coordinate display settings
    ├── Address search settings
    ├── Offline dataset status
    └── Manage offline address data
        └── Existing internal import/status/replacement/removal page

ATAK native Go To / Convert Coordinate
└── Taiwan
    ├── Taipower
    ├── TWD97
    ├── TWD67
    └── Address
        ├── Full-address mode
        ├── Structured-entry mode
        └── Candidate selection when required
```

`OfflineAddressReceiver` remains an internal page. The `TW Coordinates` dataset-status row is the
primary entry point. Embedding the complete importer and dataset manager directly in the
Preference screen is out of scope because that page already owns file picking, progress,
replacement, and removal lifecycle.

## 3. Address Tab UI

### 3.1 Full-address mode

```text
┌──────────────────────────────────────────────────────────────┐
│ Address                                            [ split ] │
│ [臺中市南屯區黎明路2段130號6樓之4________________________] │
│ Normalized: 臺中市南屯區黎明路2段130號6樓之4              │
│ [candidate or validation status]                            │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 Structured-entry mode

```text
┌──────────────────────────────────────────────────────────────┐
│ Address                                             [ join ] │
│ [County/City________] [District___________]                  │
│ [Road/Locality____________________________]                  │
│ [Lane/Alley/Number/Floor___________________]                  │
│ Combined: 臺中市南屯區黎明路2段130號6樓之4                  │
│ [candidate or validation status]                            │
└──────────────────────────────────────────────────────────────┘
```

The four structured logical fields are:

- County/city
- District/township
- Road, street, section, or locality
- Lane, alley, house number, floor, room, and any preserved tail

The fields should use the same density, text size, minimum height, and content-width constraints as
the native DD pane and the existing corrected Taiwan coordinate pane. The mode-switch control is a
plugin drawable and must not depend on ATAK-internal resource identifiers.

### 3.3 Interaction rules

- Switching modes never clears the draft.
- Full-address input is normalized and split into structured fields when possible.
- Structured fields are combined into the full-address representation after edits.
- Parser uncertainty is preserved in `unparsed`; text is never silently discarded.
- A unique exact candidate becomes the resolved point.
- Multiple credible candidates require an explicit candidate-selection dialog.
- No candidate, missing dataset, or unsupported area keeps the pane unresolved and explains the
  problem inline.
- Address lookup must finish before host `OK` can return a point because
  `CoordinateEntryPane.getGeoPointMetaData()` is synchronous.
- Host `OK` remains the only action that performs the native Go To or Convert Coordinate result.
- `Auto Fill` performs reverse lookup lazily for the Address tab. Coordinate tabs continue to fill
  immediately.
- `Clear` clears only the active internal tab.
- Read-only and disposed panes must not start new lookup work or mutate state.

## 4. Address Parsing Model

A single large regular expression is not sufficient for Taiwan addresses. Parsing should use this
pipeline:

```text
raw text
→ Unicode NFKC and punctuation normalization
→ 台/臺 equivalence folding
→ county/city longest-prefix dictionary match
→ district/township longest-prefix dictionary match
→ road/locality longest-prefix dictionary match
→ regex parsing for section/lane/alley/number/floor/room tail
→ candidate lookup and ranking
```

The model must retain:

- `rawAddress`
- `normalizedAddress`
- structured components
- `unparsed`
- selected candidate identity and resolved WGS84 point

Chinese numerals should be converted only when adjacent to an address unit. Names such as
`八德路` must not be changed, while `黎明路二段` may normalize to `黎明路2段`.

For the first implementation, structured input is an editing aid rather than a promise that every
Taiwan address can be losslessly decomposed. Ambiguous locality names and unsupported tails remain
visible and recoverable.

## 5. Architecture Direction

### 5.1 Native pane composition

Do not add `ADDRESS` to the existing `CoordinateUnit` enum. Coordinate conversion code currently
iterates that enum and assumes every value is a coordinate system. Introduce a UI-level tab model,
for example:

```text
NativeEntryTab
├── TAIPOWER
├── TWD97
├── TWD67
└── ADDRESS
```

Keep `TaiwanEntryController` responsible for the three coordinate systems and introduce an
`AddressEntryController` for address drafts, mode switching, asynchronous lookup, candidate
selection, reverse lookup, and disposal.

### 5.2 Shared address service

Extract a reusable, UI-independent address lookup service before deleting the old forward-search
page. It should reuse the existing dataset registry, database facade, text normalizer, candidate
model, and ranking logic. The service contract should support:

- cancellation or stale-result rejection by request generation;
- county-scoped and inferred-county queries;
- deterministic candidate ranking;
- exact/ambiguous/empty/error outcomes;
- WGS84 output for the native pane;
- no Android View or `DropDownReceiver` dependency.

The native entry registrar currently starts before the offline address subsystem is fully created.
Implementation must either initialize the shared address service before pane registration or inject
an availability-aware provider after construction. Disposal order must stop new pane requests,
unregister the pane, cancel address work, then close database and executor resources.

### 5.3 Offline dataset management

Keep these capabilities even though the public `TW Offline Addr` icon is removed:

- dataset file selection and import;
- validation and progress reporting;
- county import and replacement;
- dataset status and provenance display;
- dataset removal;
- registry and database lifecycle.

The existing `pref_address_dataset_status` row in `TW Coordinates` should be relabeled as needed and
continue to open the internal management page. The Address tab may also offer a compact `Manage
offline data` link when no usable dataset exists, provided the hosted-dialog/context rules are
followed.

## 6. Migration and Removal Matrix

Removal is intentionally phased. A class may be deleted only after all reusable logic has moved to
a neutral package and native Address-tab tests cover it.

| Area | Remove or change | Preserve or extract first |
|---|---|---|
| Tools lifecycle | Remove construction of `TwCoordGotoTool`, `ForwardSearchTool`, and `OfflineAddressTool`; leave only `TwCoordTool`. | `TwCoordTool` and the settings fragment. |
| Custom Go To | Remove `TwCoordGotoReceiver`, `TwCoordGotoView`, `TwCoordGotoTool`, intents, layouts, icon-palette/marker-mode/recent-history UI, and UI-only tests/resources. | Move `CoordinateParser`, `ParseResult`, Taipower parser/value types, and shared tests out of `gotopage` because the native controller already imports them. |
| Forward search | Remove `ForwardSearchTool`, `ForwardSearchReceiver`, forward-search intents/layout, and receiver-only UI code/tests. | Extract candidate models, normalization, query, ranking, and database access into the shared address lookup service used by the native Address tab. |
| Offline address | Remove `OfflineAddressTool` and its public Tools registration only. | Keep `OfflineAddressReceiver`, importer, coordinator, dataset registry, storage, intents, resources, and tests as an internal management page. |
| Settings | Remove or replace the custom `pref_open_goto` action. Update offline-data wording so it no longer names a removed Tools entry. | Keep the dataset-status navigation and address settings. Any direct launch of ATAK native Go To requires a verified ATAK 5.5-compatible public seam. |
| Documentation | Remove the four-icon workflow and custom Go To/forward-search instructions. | Replace with native Taiwan Address-tab and single-icon navigation; retain offline dataset instructions under `TW Coordinates`. |
| Localization and assets | Remove strings/drawables used only by retired Tools entries and pages after reference checks. | Keep reusable parser messages, dataset strings, and the one `TW Coordinates` icon. |

Accepted behavior losses from retiring the custom Go To page are its marker-affiliation picker,
ATAK icon palette integration, and recent-entry list. They should not be rebuilt inside the native
coordinate-entry pane unless a later requirement explicitly restores them.

## 7. Phased Delivery Plan

### Phase A — Shared foundations

1. Record an ADR for native address integration and Tools consolidation.
2. Move coordinate parser/value classes out of the legacy `gotopage` package without behavior
   changes.
3. Extract the UI-independent asynchronous address lookup service.
4. Add normalization, parsing, ranking, stale-result, cancellation, and lifecycle tests.

### Phase B — Native Address tab

1. Add `NativeEntryTab.ADDRESS` without changing `CoordinateUnit` semantics.
2. Implement the full/structured UI and mode synchronization.
3. Integrate forward and reverse offline lookup.
4. Add candidate selection and missing-dataset navigation.
5. Verify Go To and Convert Coordinate host behaviors on ATAK 5.5 minimum runtime and the pinned
   compile SDK.

### Phase C — Retire duplicate workflows

1. Remove the custom Go To Tools item, receiver, page, and UI-only resources.
2. Remove the forward-search Tools item, receiver, page, and UI-only resources.
3. Remove only the offline-address Tools item while retaining the internal page.
4. Reduce `TwCoordLifecycle` to one public `TwCoordTool`.
5. Remove orphaned registrations and verify teardown symmetry.

### Phase D — Settings, documentation, and release evidence

1. Make `TW Coordinates` the only documented Tools entry.
2. Update the settings and offline-data navigation wording.
3. Replace user-guide screenshots with the one-icon workflow and four-tab native pane.
4. Check screenshot metadata, numbering, Git LFS state, and sensitive local-path leakage.
5. Complete device acceptance, ATAK 5.5 compatibility evidence, clean release build, and TPP
   readiness checks.

## 8. Acceptance Criteria for the Future Specification

- ATAK Tools displays exactly one plugin entry: `TW Coordinates`.
- Native Taiwan coordinate entry displays Taipower, TWD97, TWD67, and Address internal tabs.
- Full and structured address modes preserve equivalent user input when switching.
- Exact offline address results resolve to WGS84; ambiguous results require user selection.
- Out-of-coverage and missing-dataset states do not break the three coordinate tabs.
- Convert Coordinate fills all coordinate tabs and lazily fills Address when data is available.
- Offline dataset management remains reachable from `TW Coordinates` without a separate Tools icon.
- Custom Go To and forward-search receivers are no longer registered.
- Shared coordinate parsers and address lookup logic remain covered by unit tests after package
  migration.
- Registration, lookup, receiver, executor, and database lifecycles have symmetric teardown.
- The APK builds against the pinned ATAK SDK and device evidence covers the ATAK 5.5 minimum
  runtime contract.

## 9. Spec Kit Handoff

The next Spec Kit feature should treat this document as research input and create new artifacts
rather than editing the shipped native-prefill feature. The specification should explicitly cover:

- Address-tab input and candidate-resolution user stories;
- one-icon Tools information architecture;
- legacy UI retirement and preserved shared capabilities;
- initialization and disposal order;
- offline-only behavior and dataset absence;
- ATAK 5.5 compatibility evidence;
- documentation and screenshot migration;
- an ADR task before architecture-changing implementation.
