---
name: add-tools-menu-page
description: Wiring checklist for adding (or debugging a missing) ATAK Tools-menu page in this plugin. Use whenever you add a new DropDownReceiver page, a new AbstractPluginTool, a toolbar icon, or when a tool/page "exists in code but the Tools-menu button never appears" / "the drop-down never opens". Covers the four seams that must all be wired or the icon silently won't show.
---

# Adding a Tools-menu page to atak_tw_power_plugin

This plugin surfaces each page (TW Coord, GoTo, Offline Address, Forward Search)
as a **Tools-menu icon → broadcast intent → DropDownReceiver**. The icon and the
receiver are wired in **two different files**, and a working page needs **four
seams**. Missing any one fails *silently* — most commonly the icon never appears
in the Tools menu because the tool was created but never added to the toolbar
array.

## The four seams (ALL required)

For a page called `Foo`, with action `FooIntents.ACTION_SHOW_FOO`:

1. **Tool class** — `plugin/FooTool.java` extends `AbstractPluginTool`, fires
   `ACTION_SHOW_FOO` on tap. Mirror `ForwardSearchTool` / `OfflineAddressTool`
   (ctor takes a `Context`, passes label + desc strings + drawable + action).

2. **Toolbar registration** ← *the one that gets forgotten* —
   `plugin/TwCoordLifecycle.java`: add `new FooTool(...pluginContext...)` to the
   `IToolbarItem[]` passed to `super(...)`. **If it is not in this array, NO icon
   appears in the Tools menu**, even though the receiver below is registered and
   listening. This was the feature-006 bug: `ForwardSearchTool` existed but was
   absent from the array.

3. **Receiver registration** — `TwCoordMapComponent.java` (`onCreate`-ish body):
   construct the `FooReceiver`, build a `DocumentedIntentFilter` with
   `addAction(FooIntents.ACTION_SHOW_FOO)`, and
   `AtakBroadcast.getInstance().registerReceiver(fooReceiver, filter)`.

4. **Teardown** — `TwCoordMapComponent.onDestroyImpl` (~L640+): null-guard,
   `unregisterReceiver(fooReceiver)` (catch `IllegalArgumentException`),
   `fooReceiver.dispose()`, set field to null. Mirror the
   `forwardSearchReceiver` block.

Plus the resources the tool class references: `R.drawable.ic_foo`
(`res/drawable/ic_foo.xml`) and `tool_foo_label` / `tool_foo_desc` strings in
**all** `values*/strings.xml` (default + `values-zh-rTW` + `values-ja`).

## Fast diagnosis: "Tools button doesn't appear"

The icon is built **only** from the `IToolbarItem[]` in `TwCoordLifecycle`. If a
page's receiver is registered (it listens) but no icon shows, seam #2 is missing.
Check first:

```
grep -n "IToolbarItem\[\]" -A12 app/src/main/java/com/atakmap/android/twcoord/plugin/TwCoordLifecycle.java
```

Every `*Tool` under `plugin/` must appear in that array. A `FooTool.java` that
exists but isn't listed there is the smoking gun.

## After fixing: the device won't refresh on reinstall alone

ATAK builds the Tools menu when the **plugin loads**. A plain `adb install -r`
usually does **not** rebuild an already-loaded toolbar. Tell the user to either
**disable→enable** the plugin (Settings → Tool Preferences → Plugins) or **fully
restart ATAK**. Otherwise they reinstall, see no change, and assume the fix
failed.

## Verify before claiming done

- `TwCoordLifecycle`'s array length == number of `*Tool` classes under `plugin/`.
- The new tool's action string matches the receiver's `addAction(...)` and the
  receiver's `onReceive` action guard exactly (same constant).
- Build: `./gradlew :app:assembleCivDebug`; install; then prompt the
  plugin-reload / ATAK-restart step above.
