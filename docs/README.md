# Documentation

This index separates current operator guidance from contributor references,
architecture records, research, and historical material. Start with the task
you need to complete; use the design and planning records only when maintaining
or changing the plugin.

## 1. Start here

| Goal | English | Taiwan Traditional Chinese |
|---|---|---|
| Understand the project and its current surfaces | [Project overview](overview.md) | [使用手冊](user-guide_zh.md) |
| Install and operate the plugin | [User guide](user-guide.md) | [使用手冊](user-guide_zh.md) |
| Enter a Taiwan address in ATAK Go To | [Native Taiwan Address](tw-addr-search.md) | [原生 Taiwan Address](tw-addr-search_zh.md) |
| Import, replace, or remove offline address data | [Offline address data](tw-offline-addr.md) | [離線地址資料](tw-offline-addr_zh.md) |
| Check supported coordinate systems and accuracy | [Coordinate systems and accuracy](reference/coordinate-systems.md) | — |

## 2. Current user interfaces

- [Native Taiwan coordinate entry](ui/native-taiwan-coordinate-entry.md) —
  the current inline Taipower (single/guided), TWD97, TWD67, and Address pane
  under ATAK Go To and Convert Coordinate.
- [Offline address manager](ui/offline-address-page.md) — the page opened by
  the plugin's only public Tools item, **TW Coordinates**.
- [On-map readout widget](ui/readout-widget.md) — MAP, ME, and TGT coordinate
  and optional address rows.
- [Settings](ui/settings-fragment.md) — display, language, address, and dataset
  preferences.
- [UI documentation policy and index](ui/README.md).

## 3. Reference

- [Coordinate systems, coverage, and accuracy](reference/coordinate-systems.md).
- [ADR-0028 Taipower A-H/A-E range decision](adr/0028-correct-taipower-subgrid-letter-ranges.md).
- [ADR-0029 all-page native Auto Fill decision](adr/0029-fill-all-native-taiwan-pages.md).
- [ATAK radial-menu integration](reference/map-menu-handler.md).
- [Change log](../CHANGELOG.md).
- [Documentation image workflow](images/README.md).

## 4. Development and contribution

- [Build, test, repository layout, and Spec Kit workflow](contributing/development.md).
- [Release readiness](contributing/release-readiness.md).
- [Project constitution](../.specify/memory/constitution.md).
- [Active feature plan](../specs/014-native-entry-input-ux/plan.md).
- [Feature specifications](../specs/) — requirements, plans, tasks, contracts,
  research, and acceptance evidence for each feature.

## 5. Architecture and release

- [Architecture Decision Records](adr/README.md) — durable decisions and the
  current supersession map.
- [TPP release runbook](release/tpp-runbook.md).
- [Third Party Pipeline reference](pipe/Third%20Party%20Pipeline.md).
- [Third Party Pipeline participation terms](pipe/Third%20Party%20Pipeline%20Participation%20Terms.md).
- [Release signing notes](../keystore/README.md).

## 6. ATAK import research

- [Data Package UID collision analysis](import-data-package/README.md) and its
  [Taiwan Traditional Chinese version](import-data-package/README.zh-TW.md).
- [File-format import flows](import-data-package/file-format-flows.md) and its
  [Taiwan Traditional Chinese version](import-data-package/file-format-flows.zh-TW.md).
- [Extended file-format import flows](import-data-package/file-format-flows-extended.md)
  and its
  [Taiwan Traditional Chinese version](import-data-package/file-format-flows-extended.zh-TW.md).

## 7. Research and reviews

The files under [`research/`](research/) are feasibility and architecture
studies. The files under [`reviews/`](reviews/) are dated review evidence.
Neither directory is the primary source for current operator behavior.

Key current research:

- [Native Address entry and Tools consolidation](research/native-address-entry-tools-consolidation.md).
- [Address plugin architecture study](research/address-atak-plugin-study.md).
- [Offline forward and fuzzy address search](research/forward-fuzzy-address-search.md).

## 8. Historical UI records

These pages describe retired standalone workflows and are retained for
traceability. They are not current operator instructions:

- [Retired TW Coord GoTo page](ui/input-page.md).
- [Retired forward-search page](ui/forward-search-page.md).
- [`design/search_settings/`](design/search_settings/) implementation mock-ups
  and change notes for earlier standalone pages.
