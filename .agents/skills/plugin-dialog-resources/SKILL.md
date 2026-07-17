---
name: plugin-dialog-resources
description: Rule + diagnosis for showing AlertDialogs (or any dialog) from this ATAK plugin. Use whenever adding/editing a button OnClickListener that opens a confirm/alert dialog, calling new AlertDialog.Builder(...), or debugging "the button does nothing on device", "the confirm dialog never appears", "刪除/移除/取代 按下沒反應". Covers the cross-context plugin-resource trap that fails silently.
---

# Showing dialogs from atak_tw_coord_plugin

A plugin runs with **two contexts**:

- **`pluginContext`** — owns the plugin APK's resources (`R.string.*`,
  `R.layout.*`, `R.drawable.*`).
- **The ATAK Activity context** — `getMapView().getContext()`. Owns a valid
  **window token**, so it is the one a dialog must be built with, but its
  `Resources` belong to the **ATAK host APK**, not the plugin.

These two are easy to mix up, and mixing them fails **silently** because every
host→plugin entry point here is wrapped in a swallow-all guard (`safeRun`,
`try/catch(Throwable)→Log.w`, Constitution VI). A thrown exception during dialog
construction is logged and discarded — the button just looks dead.

## The rule

Build the dialog with the **Activity** context, but resolve **every plugin
resource id to a value with `pluginContext` first**. Never pass a plugin
`R.*` id to a `Builder` method that resolves ids against the builder's context.

```java
// WRONG — setTitle(int) resolves R.string.x against the ATAK context's Resources,
// which don't contain the plugin id → Resources.NotFoundException → swallowed →
// dialog never shows, no crash, button "does nothing".
new AlertDialog.Builder(getMapView().getContext())
    .setTitle(R.string.offline_address_button_remove)   // plugin id, wrong context
    .setMessage(msg)
    .show();

// RIGHT — resolve plugin resources via pluginContext, pass the String.
new AlertDialog.Builder(getMapView().getContext())
    .setTitle(pluginContext.getString(R.string.offline_address_button_remove))
    .setMessage(msg)                                     // already a getString result
    .setPositiveButton(android.R.string.ok, ...)         // android.R.* is a framework
    .setNegativeButton(android.R.string.cancel, null)    // resource — fine either way
    .show();
```

What is safe vs not, on a builder made with the ATAK context:

- `android.R.string.*` (ok/cancel) — **safe**: framework resource, resolvable
  against any context.
- `setMessage(String)` / `setTitle(CharSequence)` with a pre-resolved value —
  **safe**.
- `setTitle(int)` / `setMessage(int)` / `setItems(int)` with a **plugin** id —
  **unsafe**: throws `Resources.NotFoundException`.

This is the same reason dialogs use `getMapView().getContext()` and not
`pluginContext` for the builder: `pluginContext` has no window token and
`show()` throws `BadTokenException`. One needs the Activity context for the
window, the other needs `pluginContext` for the resources — supply both.

## Fast diagnosis: "button does nothing / dialog never appears"

1. Confirm the press registers (ripple) but no dialog — that points at an
   exception during dialog construction, not a missing OnClickListener.
2. Grep the handler for a plugin id passed to a builder method:
   ```
   grep -nE "setTitle\(R\.|setMessage\(R\.|setItems\(R\." app/src/main/java/com/atakmap/android/twcoord/**/*.java
   ```
   Any hit on a `Builder` made with `getMapView().getContext()` is the bug.
3. Confirm from the device log — the swallowed throwable is logged at the
   receiver's TAG:
   ```
   adb -s <serial> logcat -s OfflineAddressReceiver:*
   ```
   Look for `safeRun threw … android.content.res.Resources$NotFoundException`.

The working reference in-repo is the Import dialog
(`OfflineAddressReceiver.launchPicker`): it already does
`dialog.setTitle(pluginContext.getString(R.string.offline_address_button_import))`.
Mirror it.

## Why tests miss this

Robolectric/JVM unit tests run dialogs against a single merged resource table, so
the cross-context split never appears — these dialogs only fail on a **real
device / real ATAK host**. Espresso on-device coverage for these confirm dialogs
has historically been deferred, so this class of bug ships unless checked by hand.

## Verify before claiming done

- No `setTitle(R.`/`setMessage(R.`/`setItems(R.` with a plugin id on any
  Activity-context builder (grep above returns nothing for plugin ids).
- Builder context is `getMapView().getContext()` (window token), resource values
  come from `pluginContext.getString(...)`.
- `./gradlew :app:assembleCivDebug`; `adb install -r`; then **disable→enable the
  plugin or restart ATAK** (a reinstall alone won't reload an already-loaded
  plugin) and tap the button on-device — the dialog must actually appear.
