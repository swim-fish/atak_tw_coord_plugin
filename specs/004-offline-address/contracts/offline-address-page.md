# Contract: `OfflineAddressReceiver` (DropDownReceiver)

**Package**: `com.atakmap.android.twcoord.address`

**Source of truth for**: the Tools-menu page that lets the operator inspect / import / remove
the active dataset.

## Action constants

```java
public final class OfflineAddressIntents {
    public static final String ACTION_SHOW_OFFLINE_ADDRESS =
        "com.atakmap.android.twcoord.SHOW_OFFLINE_ADDRESS";
    public static final String ACTION_PICK_FILE_RESULT =
        "com.atakmap.android.twcoord.OFFLINE_ADDRESS_PICK_FILE_RESULT";
    private OfflineAddressIntents() {}
}
```

The receiver registers for `ACTION_SHOW_OFFLINE_ADDRESS` via `AtakBroadcast` in
`TwCoordMapComponent.onCreate`, the same way `TwCoordGotoReceiver` does today.

## Page layout (`offline_address_page.xml`)

Two visual states, swapped at runtime:

### State A — no dataset active

```text
┌─────────────────────────────────────────────────┐
│ Offline Address                                  │
│                                                  │
│  No address dataset installed.                   │
│                                                  │
│  Use the address bundle produced by the          │
│  atak-tw-address-generator project (one          │
│  `places-<county>.sqlite` per Taiwan county).    │
│                                                  │
│  [   Import…   ]                                 │
└─────────────────────────────────────────────────┘
```

### State B — dataset active

```text
┌─────────────────────────────────────────────────┐
│ Offline Address                                  │
│                                                  │
│  County:        台中市                            │
│  Data date:     115-01                           │
│  Source:        tgos                              │
│  Rows:          1,316,674                         │
│  CSV SHA-256:   abc123…  (tap to expand)         │
│                                                  │
│  Imported:      2026-05-24 15:30 UTC             │
│  File SHA-256:  def456…  (tap to expand)         │
│  R*Tree built:  yes                              │
│                                                  │
│  [  Replace…  ]   [  Remove  ]                   │
└─────────────────────────────────────────────────┘
```

The header text is from `R.string.offline_address_page_title`. Field labels come from
localised string resources (en / zh-rTW / ja).

## Behaviour

### Import flow

1. Operator taps **Import…**.
2. Receiver launches SAF `ACTION_OPEN_DOCUMENT` with MIME `application/octet-stream` (the
   filter is non-strict — operators may rename files). Trampolined through a per-receiver
   `Activity`-shim that broadcasts `ACTION_PICK_FILE_RESULT` with the picked `content://` URI
   in the extras. (Required because `DropDownReceiver` is not an `Activity` and cannot host
   `ActivityResultLauncher` directly; the project does not yet have this shim — to be added
   alongside this feature.)
3. Receiver opens the URI's `InputStream` via `ContentResolver.openInputStream(...)`.
4. On the background executor, calls `AddressBundleImporter.importFrom(stream, listener)`.
5. `ProgressListener` callbacks update an in-page progress chip ("Copying… 42%", "Building
   index… 78%", "Activating…").
6. On `Success(dataset)`: refreshes the page to State B; broadcasts a plugin-internal
   `ACTION_DATASET_CHANGED` so `TwCoordMapComponent` / `AddressSubsystem` re-open the facade.
7. On `Failure(reason, details)`: shows the error text inline (NOT a toast — the error stays
   visible until the operator acts).

### Replace flow

Same as Import flow, preceded by a confirmation dialog ("Replace the active <county>
dataset?"). On cancel, no state change.

### Remove flow

Tapping **Remove** opens a confirmation dialog ("Remove the active <county> dataset?"). On
confirm:

1. Receiver calls `AddressBundleImporter.removeActive()` (idempotent).
2. Broadcasts `ACTION_DATASET_CHANGED`.
3. Refreshes the page to State A.

### Lifecycle entry points (Constitution VI — must wrap)

| Method | Wrap location |
|---|---|
| `onReceive(Context, Intent)` | outer body of the dispatch (already inherited from base, but re-wrap defensively) |
| `onDropDownVisible(boolean)` | outer body |
| `onDropDownClose()` | outer body |
| `onDropDownSizeChanged(double, double)` | outer body |
| The SAF-result `BroadcastReceiver.onReceive` (registered in `onDropDownVisible`, unregistered in `onDropDownClose`) | outer body |

## Test plan (`OfflineAddressReceiverTest`, Robolectric)

| # | Test name | What it asserts |
|---|---|---|
| 1 | `firstOpen_showsStateAWhenNoDataset` | The page binds the State A layout when `importer.activeOrNull() == null`. |
| 2 | `firstOpen_showsStateBWhenDatasetActive` | The page binds State B with the dataset's metadata fields populated from the fixture. |
| 3 | `import_invokesImporterWithPickedStream` | The SAF result handler reads the URI via `ContentResolver` and feeds the stream to `AddressBundleImporter.importFrom(...)`. |
| 4 | `import_progressUpdatesUpdateTheProgressChip` | `ProgressListener` calls flow into the chip text. |
| 5 | `import_failureShowsInlineErrorNotToast` | A `Failure(UNSUPPORTED_SCHEMA_VERSION, ...)` is displayed inline; no `Toast.makeText` is called. |
| 6 | `replace_promptsConfirmThenImports` | Tapping Replace shows a confirm dialog; cancel does nothing; confirm runs Import. |
| 7 | `remove_promptsConfirmThenRemoves` | Tapping Remove shows a confirm dialog; confirm calls `removeActive()` and refreshes to State A. |
| 8 | `closeUnregistersSafResultReceiver` | After `onDropDownClose()`, the SAF result receiver is no longer registered with `AtakBroadcast`. |
