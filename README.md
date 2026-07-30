# Taiwan Coordinates for ATAK (`atak_tw_coord_plugin`)

TW Coordinates is an offline
[ATAK-CIV](https://tak.gov/products/atak-civ) plugin for Taiwan coordinate
display, native coordinate and address entry, and county-scoped offline
address data.

## Core capabilities

- Displays MAP, ME, and TGT coordinates as on-map readouts.
- Adds one native **Taiwan** pane to ATAK Go To and Convert Coordinate with
  inline single/guided Taipower entry, TWD97, TWD67, and offline Address tabs.
- Imports, replaces, and removes offline county address datasets.
- Exposes one public Tools item, **TW Coordinates**, which opens the offline
  data manager and links to plugin settings.
- Supports English, Taiwan Traditional Chinese, and Japanese.
- Operates without the Android `INTERNET` permission or telemetry.

See the [project overview](docs/overview.md) for workflows, screenshots,
limitations, and the complete capability summary.

## Start here

| Goal | Documentation |
|---|---|
| Install and use the plugin | [User guide](docs/user-guide.md) / [繁中使用手冊](docs/user-guide_zh.md) |
| Enter an offline Taiwan address | [Native Taiwan Address](docs/tw-addr-search.md) / [繁中](docs/tw-addr-search_zh.md) |
| Import or manage offline address data | [Offline address data](docs/tw-offline-addr.md) / [繁中](docs/tw-offline-addr_zh.md) |
| Check coordinate coverage and accuracy | [Coordinate systems reference](docs/reference/coordinate-systems.md) |
| Build or contribute | [Development guide](docs/contributing/development.md) |
| Prepare a release | [Release readiness](docs/contributing/release-readiness.md) |

## Compatibility

| Axis | Current value |
|---|---|
| Android compile / minimum SDK | 36 / 26 |
| ATAK compile SDK | ATAK-CIV 5.7.0.9 |
| Minimum declared ATAK runtime | ATAK-CIV 5.5.0 |

Physical ATAK 5.5 acceptance remains a separate release gate; a successful
5.7.0.9 build does not close it.

## Build

After configuring the Android and ATAK SDK paths described in the
[development guide](docs/contributing/development.md):

```powershell
.\gradlew.bat :app:testCivDebugUnitTest
.\gradlew.bat :app:assembleCivDebug
```

The debug APK is written under `app/build/outputs/apk/civ/debug/`.

## Documentation index

The complete sectioned index is [`docs/README.md`](docs/README.md).

### Users

- [Project overview](docs/overview.md).
- [English user guide](docs/user-guide.md).
- [Taiwan Traditional Chinese user guide](docs/user-guide_zh.md).
- [Feature guides](docs/README.md#1-start-here).

### Contributors

- [Development guide](docs/contributing/development.md).
- [UI documentation](docs/ui/README.md).
- [Documentation image workflow](docs/images/README.md).
- [Feature specifications](specs/).

### Maintainers and release

- [Architecture Decision Records](docs/adr/README.md).
- [Project constitution](.specify/memory/constitution.md).
- [Release readiness](docs/contributing/release-readiness.md).
- [TPP release runbook](docs/release/tpp-runbook.md).
- [Change log](CHANGELOG.md).

## License

Released under the [MIT License](LICENSE). Copyright (c) 2026 Shihyu.
