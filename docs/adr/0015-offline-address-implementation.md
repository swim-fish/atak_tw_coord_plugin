# ADR-0015: Offline Address — post-implementation pivots

**Status**: Accepted
**Date**: 2026-05-26
**Origin**: `/speckit-implement` US1+US2+US3+US4 delivery for feature 004 (commits `0fd3f0e` Phase 1+2 → `47d9404` Phase 7 docs polish → `03f910e` device fixes) plus on-device acceptance on Samsung Galaxy R52X908JF0W / Android 14 / ATAK-CIV 5.7.0.3.

This ADR captures the concrete pivots made during implementation after [ADR-0014](./0014-offline-address-reconnaissance.md) defined the architecture. ADR-0014 stays the reconnaissance/design record; this ADR records what actually shipped, with emphasis on the two device-only issues the reconnaissance didn't predict.

## Context

ADR-0014 defined ten reconnaissance points (R1–R10). Plan / contracts / tasks elaborated the design without contradicting them. Phases 1–7 landed mostly as designed. During the first on-device acceptance run two issues surfaced that neither the SDK reconnaissance (`javap` against `main.jar` + upstream source check) nor JVM/Robolectric unit tests caught:

1. The SAF picker handoff failed end-to-end on Android 14 + Samsung One UI, despite working under every theoretical Android model.
2. The runtime `places_rtree` query failed with `no such module: rtree` on the same device.

The fix path explored four dead-end commits chasing #1 before finding the correct shape. Once that landed, #2 surfaced 30 minutes later. Both root causes are device-platform realities that should be on the recon checklist for any future plugin doing storage / spatial work.

## Decisions

### D1 — File picker delegates to ATAK SDK's `ImportFileBrowserDialog` instead of a plugin-declared SAF Activity

**Spec impact**: ADR-0014 R7 (Tools-menu Activity vs DropDownReceiver) chose DropDownReceiver. Implementation kept that, but the picker behind the Import button was originally an Activity (`OfflineAddressFilePickerActivity`) that hosted `Intent.ACTION_OPEN_DOCUMENT` and broadcast the picked `content://` URI back to the receiver inside ATAK. That picker is now gone.

**Why**: Plugin-declared Activities run under the **plugin APK's own UID** (`10544` on the reference device), distinct from ATAK's host UID (`10515`). Crossing those UIDs with a broadcast on Android 14 + Samsung One UI proved impossible in practice. Four iterative fixes (squashed into `03f910e`) were ruled out by device evidence:

| Attempt | What failed |
|---|---|
| `exported=true` on the Activity | Cross-UID `startActivity()` worked, but the receive side still didn't fire. |
| Switch picker to `android.util.Log` + `Context.sendBroadcast` (it ran in plugin's own UID without ATAK SDK on classpath) | Activity started cleanly, broadcast was sent, receiver never fired. |
| `setData(uri) + FLAG_GRANT_READ_URI_PERMISSION` + `addDataScheme("content")` + `RECEIVER_EXPORTED` + register receiver in ctor (not in `onDropDownVisible`, since SAF takeover would unregister us before the result arrived) | Five layered fixes, still no `onReceive`. |
| `setPackage("com.atakmap.app.civ") + <queries><package name="com.atakmap.app.civ"/>` to make the broadcast explicit and the receiver visible | Still no `onReceive`. `adb shell dumpsys activity broadcasts history` showed `enqueueClockTime` set but `dispatchClockTime = 1970-01-01 08:00:00.000` — ActivityManager finished the broadcast without dispatching. A bare `adb shell am broadcast -p com.atakmap.app.civ -a … -d content://…` from the host had the same outcome despite the dumpsys confirming the receiver was registered with matching action + scheme. |

Rejected alternatives explored in the layered fixes:

- `grantUriPermission(ATAK_PACKAGE, uri, …)` from the picker — the picker Activity isn't the URI owner, only SAF is.
- Copy bytes into `getCacheDir()` and pass a `file://` URI — plugin's app-private cache is mode 0700, ATAK's UID can't read.
- Stuff a `ParcelFileDescriptor` into broadcast extras — Android refuses with `Not allowed to write file descriptors here`.

Chose instead: **`com.atakmap.android.gui.ImportFileBrowserDialog`** from the ATAK SDK. It is an in-process file picker that returns a `java.io.File` synchronously via `DialogDismissed.onFileSelected`. No cross-UID handoff, no broadcasts, no extra Activity, no manifest declaration. Pattern lifted from `ATAK-CIV-5.7.0.3-SDK/samples/helloworld/.../HelloWorldDropDownReceiver.java:3656` (the `sampleFileBrowser()` method).

**Cost**: net `-70 / +60` lines vs the failing SAF design, plus removal of the manifest's `<activity>` and `<queries>` entries, plus removal of `ACTION_PICK_FILE_RESULT` and `EXTRA_PICKED_URI` from `OfflineAddressIntents`. ATAK SDK's dialog has no zh-TW / ja translation by itself, but its labels are short and largely operator-recognised (file/folder, OK, Cancel) — acceptable for a v1 ship.

**Reusable lesson**: when an ATAK plugin needs UI that the SDK already provides (file picker, color picker, alert dialog, list dialog), prefer the SDK class over a self-declared Activity. The SDK class always runs in ATAK's process, sidestepping the cross-UID broadcast graveyard entirely.

### D2 — Production runtime SQLite uses ATAK's native `com.atakmap.database.Databases` instead of `android.database.sqlite.SQLiteDatabase`

**Spec impact**: ADR-0014 R3 chose R*Tree as the spatial index strategy. The implementation honoured that (the v2 generator now ships `places_rtree` pre-built — see commit `71fa3d7`). What ADR-0014 did **not** anticipate was that Android's stock `android.database.sqlite.SQLiteDatabase` on **Samsung One UI** ships without the R*Tree extension.

**Evidence**: on the reference device, the bbox-join compile failed with:

```
SQLiteException: no such module: rtree (code 1 SQLITE_ERROR[1]):
SELECT p.lat, p.lon, … FROM places_rtree r JOIN places p ON r.id = p.id
WHERE r.min_lat <= ? AND r.max_lat >= ? AND r.min_lon <= ? AND r.max_lon >= ?
```

Robolectric (xerial sqlite-jdbc) has R*Tree, so all 6 `AddressDatabaseFacadeTest` cases plus the 8 `AddressResolverTest` cases passed on JVM and masked the gap until the device run.

**Why this choice**: ATAK itself runs heavy spatial work (mapdata layers, raster tile indexes, KML / GRG / RouteKit features) and depends on R*Tree. Its embedded native SQLite (`com.atakmap.database.Databases.openDatabase`, returning a `DatabaseIface` + `CursorIface`) is a full-featured build with R*Tree, FTS, JSON1, and the SpatiaLite extensions on. Routing the plugin's reads through the ATAK native runtime piggy-backs on a guarantee the host already underwrites, costs nothing in APK size (the binaries are inside ATAK), and keeps the SQL string in `AtakDatabasesAddressDatabase` byte-identical to what JVM tests exercise.

Rejected alternatives:

- **Requery's `sqlite-android`** (`com.github.requery:sqlite-android`) — bundles a portable SQLite with R*Tree. Adds 1–2 MB per ABI to the APK, fragments which SQLite the plugin uses vs ATAK, and risks classloader conflicts inside the ATAK process.
- **Drop R*Tree, use `(lat, lon)` B-tree + WHERE BETWEEN** — would work on 1.3 M Taichung-scale rows in ~50–200 ms (still inside the SC-002 budget), but loses the generator's pre-built `places_rtree` work and forces a schema change on the data contract.

Chose ATAK native. Implementation: `AtakDatabasesAddressDatabase implements AddressDatabaseFacade`, mirrors `SqliteAddressDatabase`'s SQL and haversine refine logic 1-for-1 with one swap: `db.query(…)` returns `CursorIface` instead of `Cursor`, both expose `moveToNext / getDouble / getString / isNull / close`. `SqliteAddressDatabase` stays in the tree as the test fixture (Robolectric + xerial; the facade interface stays unchanged so the two impls are interchangeable). `TwCoordMapComponent` wires `AtakDatabasesAddressDatabase.Factory` in production.

**Reusable lesson**: any plugin work touching SQLite extensions (R*Tree, FTS, JSON1, SpatiaLite, ICU collations) should use `com.atakmap.database.Databases.openDatabase`, not the Android platform SQLite. Stock Android SQLite's extension surface varies wildly per OEM build. Update ADR-0014 R3 implicitly: "R*Tree only available reliably through ATAK native SQLite, not platform SQLite."

### D3 — `SqliteAddressDatabase` survives D2 as a JVM/Robolectric test fixture

The straightforward path after D2 would be to delete `SqliteAddressDatabase` entirely. Kept it because:

- `AddressDatabaseFacadeTest` (6 Robolectric cases) uses real SQLite to verify the bbox-cos-latitude SQL is correct against xerial sqlite-jdbc (which has R*Tree). Killing it would force these tests to mock `CursorIface`, losing one of the few places we exercise real SQL.
- `AddressResolverTest` (8 cases) uses a stub facade; not affected, but it benefits from `AddressDatabaseFacadeTest` being a real regression net for SQL string changes.
- ATAK's `DatabaseIface` doesn't have a JVM-side implementation that we can spin up under unit tests — it's a native binding into ATAK's runtime, only valid inside a running ATAK process.

So the facade `AddressDatabaseFacade` interface stays as the seam, and there are two concrete impls: `SqliteAddressDatabase` (tests, xerial-backed) and `AtakDatabasesAddressDatabase` (production, ATAK native). The interface signature was unchanged through D2.

### D4 — `formatFailure(...)` initial `args` is a 1-element `{details}` array to satisfy lint StringFormatMatches

Tiny but worth recording. `formatFailure` originally initialised `Object[] args = new Object[0]` and only the four cases needing `%1$s` (MISSING_REQUIRED_METADATA_KEY, UNSUPPORTED_SCHEMA_VERSION, UNEXPECTED_PLACES_COLUMNS, IO_ERROR/CANCELLED default) reassigned it. `lintCivDebug` flagged `R.string.offline_address_error_io` (which contains `%1$s`) as "Wrong argument count: format string … requires 1 but format call supplies 0" — even though the CANCELLED/IO_ERROR/default fall-through always assigns `args = new Object[] {fail.details()}` before the `getString` call.

Lint's flow analysis was conservative around the case fall-through. Switched the initial value to `new Object[] {details}` (with `details = fail.details() != null ? fail.details() : ""` to guard the null case) and removed the per-case reassignments. Resource strings without a `%s` placeholder simply ignore the extra arg; `Resources.getString → String.format` is tolerant of trailing args.

Net: 3 lint errors gone, zero behaviour change, slightly smaller switch.

### D5 — Diagnostic `Log.i` lines in `TwCoordMapComponent.onCreate` and `OfflineAddressReceiver` ctor are kept, not pruned

The session that found D1+D2 was made possible by two `Log.i` lines added during the dead-end SAF debugging: "Feature 004 init: building AddressBundleImporter + OfflineAddressReceiver in pid=… uid=…" and "ctor — registering pick-result receiver in pid=… uid=…". They confirmed that `OfflineAddressReceiver` was instantiated in ATAK's process (uid 10515) and not the plugin process (uid 10544), which is the critical wiring detail Constitution VI testing alone can't verify.

These two lines stay. They're cheap (one `Log.i` per ATAK launch each), they answer the most expensive debugging question on a device-only platform issue ("is the receiver even alive?"), and they pay for themselves whenever this plugin or another in the same hosts process needs the same kind of cross-process diagnostic.

### D6 — T031 (Flow A import) and Flow B partial (MAP toggle, Taichung-centre map) verified by manual operator run, not Espresso scripted

**Spec impact**: tasks.md T023 / T041 / T045 ship Espresso skeletons; T031 / T044 / T048 were the device-acceptance runs.

T031 / Flow A passed by manual run 2026-05-26: pick `places-taichung.sqlite` (599 MB, v2) → progress → State B render with `imported.manifest.txt.rtree_built=false` (v2 generator's pre-built index correctly detected) → no SqliteException. T044 partial: MAP toggle on + map centred on 24.137°N / 120.685°E (Taichung station) renders the address row under the MAP coord readout.

Espresso scripted versions still nice-to-have but not blocking. The remaining T044 sub-flows (ME toggle, TGT toggle, out-of-region empty state, Replace with `places-changhua.sqlite`, Remove → State A) and T048 (delete active dir mid-run, push zero-byte fixture) are device-operator runs documented in [quickstart §3-§5](../../specs/004-offline-address/quickstart.md#3-acceptance-flow-a--us1-import-a-bundle).

### D7 — `<queries>` Manifest entry not needed

Briefly added (then removed) during the dead-end SAF chase. The final design (D1) has the dialog running in ATAK's process; there is no cross-UID broadcast or `startActivity` that needs package visibility. Manifest is clean of `<queries>` for v1.0.5.

## Performance numbers (T057 — to be filled by device run)

Placeholder until T057 quickstart §6 smoke tests run on the reference device. Expected entries:

| SC | What | Budget | Measured |
|---|---|---|---|
| SC-002 | Median address-row update across 100 pans | ≤ 1000 ms (p95 ≤ 2000 ms) | TBD |
| SC-003 | Import duration for Taichung-scale (~600 MB) | ≤ 180 s (R*Tree pre-built path) | TBD |
| SC-004 | Footprint vs v1.0.4 baseline when all toggles off | 0 bytes net | TBD |
| SC-005 | Recovery from missing files | ≤ 2000 ms | TBD |
| SC-006 | Non-empty resolve rate across 1000 scripted lookups | ≥ 95 % | TBD |

Once measured, update this table and the matching cells in [tasks.md T057](../../specs/004-offline-address/tasks.md). Any miss is a regression — investigate before declaring done. If SC-003 measures comfortably under 180 s (the v2 generator's pre-built `places_rtree` makes this likely), tighten the spec SC at the same time.

## Constitution VI audit result (T056)

Inherited from commit `47d9404`. All 11 host-callable entry points added in feature 004 wrap `Throwable` per Principle VI:

- `OfflineAddressTool` (TwCoordMapComponent register path) — wrapped.
- `OfflineAddressReceiver.onReceive` + 4 click listeners (`launchPicker`, `confirmReplace`, `confirmRemove` × 2 dialog-OK callbacks) + `onDropDownClose` + `onFileSelected` + `onDialogClosed` — wrapped via `safeRun(...)` + explicit try/catch.
- `pickResultReceiver` BroadcastReceiver entry — removed under D1 (no longer exists).
- `AddressSubsystem` listener registration + `onCoord` + scheduled-lookup body — wrapped.
- `AtakDatabasesAddressDatabase.readMetadata` + `nearestWithin` + `close` — every `Throwable` caught and converted to a safe default per facade contract.

Constitution VI audit passes for feature 004.

## Cross-references

- [ADR-0014 — Offline Address reconnaissance](./0014-offline-address-reconnaissance.md): the design/reconnaissance phase that preceded this implementation.
- [specs/004-offline-address/research.md](../../specs/004-offline-address/research.md): R1–R10 reconnaissance findings.
- [specs/004-offline-address/quickstart.md](../../specs/004-offline-address/quickstart.md): acceptance flows + performance smoke tests.
- [ADR-0011 — Custom Icon picker implementation](./0011-custom-marker-icon-implementation.md): the template this ADR follows.
- Commit `03f910e fix(offline-address): use ATAK SDK file picker + native SQLite`: the implementation of D1 + D2 + D3 + D4 + D5 + D7.
