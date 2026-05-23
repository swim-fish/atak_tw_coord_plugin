# Adding Buttons to ATAK's Radial Menu with `MapMenuHandler`

A vendor-neutral developer reference for ATAK-CIV plugins that need to add
options to the existing radial menu that pops up around a `MapItem` — without
replacing the menu wholesale.

- **SDK baseline**: `ATAK-CIV-5.7.0.3-SDK/main.jar` (TAK-Product-Center
  release 5.7.0.3, 2024)
- **Upstream source**: <https://github.com/TAK-Product-Center/atak-civ>
  (default branch `main`; permalinks below pin commit
  [`9f6893d`](https://github.com/TAK-Product-Center/atak-civ/tree/9f6893dd657feacc35ec5de03dad721c2e44170e))
- **Audience**: any ATAK-CIV plugin that wants additive radial-menu
  contributions (a single "Copy as X coords", "Send to Y", "Toggle Z" button,
  etc.). The same APIs work for custom apps that link `main.jar` directly.

If you need to **replace** the menu rather than augment it, use the sibling
interface `MapMenuFactory` — see [§ Choosing between factory and
handler](#choosing-between-factory-and-handler).

---

## TL;DR

```java
// 1. Implement the interface.
public final class MyMenuHandler implements MapMenuHandler {
    @Override
    public void updateMenu(MapItem item, MapMenuWidget itemMenu) {
        try {
            if (!isMyItem(item)) return;          // no-op when not interested
            MapMenuButtonWidget btn = buildButton(item);
            itemMenu.addChildWidget(btn);         // append at the tail
        } catch (Throwable t) {                   // host-process isolation
            Log.w(TAG, "updateMenu failed", t);
        }
    }
}

// 2. Register once per plugin lifetime, typically from MapComponent.onCreate.
MapMenuReceiver.getInstance()
    .registerMapMenuHandler(new MyMenuHandler(), /* priority = */ 100);

// 3. Unregister on shutdown.
MapMenuReceiver.getInstance().unregisterMapMenuHandler(myHandler);
```

The handler runs **immediately before** the menu is shown, on the same
`MapMenuWidget` ATAK built for the item. Mutating it (adding, removing,
re-ordering buttons; setting `setShowSubmenu`, etc.) feeds directly into the
render pipeline.

---

## Choosing between factory and handler

ATAK exposes two extension points in `com.atakmap.android.menu`:

| API | When to use | Behaviour |
|---|---|---|
| `MapMenuFactory.create(MapItem)` | You want to **replace** the menu for a specific class of `MapItem` (e.g. a custom marker type that needs its own layout). | Factories are queried in registration order. The first non-`null` return wins. Return `null` to defer to the next factory. |
| `MapMenuHandler.updateMenu(MapItem, MapMenuWidget)` | You want to **add**, **remove**, or **tweak** buttons on whatever menu ATAK (or another plugin's factory) already produced. | Every registered handler is invoked in priority order on the final widget. No short-circuit. |

Prefer `MapMenuHandler` for single-button contributions. It cooperates with
ATAK's defaults and with other plugins, and is the lowest-blast-radius option.

Evidence:

- `MapMenuFactory` interface — single method `create(MapItem) -> MapMenuWidget`.
  - SDK `javap -public main.jar com.atakmap.android.menu.MapMenuFactory`:
    ```text
    public interface com.atakmap.android.menu.MapMenuFactory {
      public abstract com.atakmap.android.menu.MapMenuWidget create(com.atakmap.android.maps.MapItem);
    }
    ```
  - Upstream:
    [`MapMenuFactory.java`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/MapMenuFactory.java)
- `MapMenuHandler` interface — single method
  `updateMenu(MapItem, MapMenuWidget)`.
  - SDK `javap -public`:
    ```text
    public interface com.atakmap.android.menu.MapMenuHandler {
      public abstract void updateMenu(com.atakmap.android.maps.MapItem,
                                      com.atakmap.android.menu.MapMenuWidget);
    }
    ```
  - Upstream:
    [`MapMenuHandler.java`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/MapMenuHandler.java)
- The upstream `MapMenuHandler` Javadoc explicitly states: *"This method
  should be a no-op if the specified `MapItem` is not supported/not of
  interest."* That contract — **silent skip, no exception** — is mandatory.

---

## Registration

### Where to register

Register from your plugin's `AbstractMapComponent.onCreate(...)` (or
equivalent app startup hook) and unregister from `onDestroyImpl(...)`. The
singleton `MapMenuReceiver` is the entry point.

```java
@Override
public void onCreate(Context pluginContext, Intent intent, MapView view) {
    super.onCreate(pluginContext, intent, view);
    handler = new MyMenuHandler(pluginContext);
    MapMenuReceiver.getInstance().registerMapMenuHandler(handler, 100);
}

@Override
protected void onDestroyImpl(Context context, MapView view) {
    MapMenuReceiver.getInstance().unregisterMapMenuHandler(handler);
}
```

### Registration API

Four methods on `MapMenuReceiver`:

| Signature | Notes |
|---|---|
| `boolean registerMapMenuFactory(MapMenuFactory)` | Adds a factory at the tail of the factory chain. |
| `boolean unregisterMapMenuFactory(MapMenuFactory)` | Removes the same instance. Returns `true` if it was registered. |
| `boolean registerMapMenuHandler(MapMenuHandler)` | Convenience overload — registers with **priority 0**. |
| `boolean registerMapMenuHandler(MapMenuHandler, int priority)` | Registers with explicit priority. |
| `boolean unregisterMapMenuHandler(MapMenuHandler)` | Removes the same instance. |

Evidence (SDK `javap -public com.atakmap.android.menu.MapMenuReceiver`):

```text
public boolean registerMapMenuFactory(com.atakmap.android.menu.MapMenuFactory);
public boolean registerMapMenuHandler(com.atakmap.android.menu.MapMenuHandler);
public boolean registerMapMenuHandler(com.atakmap.android.menu.MapMenuHandler, int);
public boolean unregisterMapMenuFactory(com.atakmap.android.menu.MapMenuFactory);
public boolean unregisterMapMenuHandler(com.atakmap.android.menu.MapMenuHandler);
public static com.atakmap.android.menu.MapMenuReceiver getInstance();
```

Upstream:
[`MapMenuReceiver.java#L319-L364`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/MapMenuReceiver.java#L319-L364)

### Priority semantics — read this before picking a number

From the upstream Javadoc on `registerMapMenuHandler(handler, priority)`:

> Registered handlers are invoked in priority order or LILO on priority
> collision. Handlers with numerically smaller priorities are evaluated
> before handlers with numerically larger priorities. **All core
> `MapMenuHandler` instances will be registered with a priority
> less-than-or-equal-to `0`.**

Source:
[`MapMenuReceiver.java#L337-L350`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/MapMenuReceiver.java#L337-L350)

Practical guidance:

- **Use a positive priority** (e.g. `100`). Your handler then runs *after*
  every ATAK-core handler has finished, so the `MapMenuWidget` you receive
  reflects the final core layout. Mutations stick.
- The convenience overload `registerMapMenuHandler(handler)` registers at
  priority `0` — the same lane as core. That is rarely what you want from a
  plugin. Prefer the explicit-priority overload.
- If two plugins both register at the same priority, the order is
  last-in-last-out (LILO) by registration time. Do not rely on it for
  correctness.

### Intent-based registration (declarative alternative)

For static per-type menus that do not need code-driven logic, `MapMenuReceiver`
also exposes intent actions:

```text
public static final String SHOW_MENU      = "com.atakmap.android.maps.SHOW_MENU";
public static final String HIDE_MENU      = "com.atakmap.android.maps.HIDE_MENU";
public static final String REGISTER_MENU  = "com.atakmap.android.maps.REGISTER_MENU";
public static final String UNREGISTER_MENU = "com.atakmap.android.maps.UNREGISTER_MENU";
```

Upstream:
[`MapMenuReceiver.java#L36-L39`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/MapMenuReceiver.java#L36-L39)

This route registers a full XML menu against a `MapItem` type string. It is
out of scope for this document — `MapMenuHandler` is the right tool when you
want to add buttons rather than declare a fixed layout.

---

## Mutating the menu inside `updateMenu(...)`

`MapMenuWidget` extends `LayoutWidget`, which in turn extends
`AbstractParentWidget`. The parent-widget API is how you add and remove
buttons:

```text
// com.atakmap.android.widgets.AbstractParentWidget (javap -public)
public void addChildWidget(gov.tak.api.widgets.IMapWidget);
public void addChildWidgetAt(int, gov.tak.api.widgets.IMapWidget);
public gov.tak.api.widgets.IMapWidget removeChildWidgetAt(int);
public boolean removeChildWidget(gov.tak.api.widgets.IMapWidget);
public int getChildCount();
public com.atakmap.android.widgets.MapWidget getChildAt(int);
public java.util.List<gov.tak.api.widgets.IMapWidget> getChildren();
```

`MapMenuWidget` itself adds radial-layout knobs:

```text
public float getCoveredAngle();          public void setCoveredAngle(float);
public float getStartAngle();            public void setStartAngle(float);
public float getInnerRadius();           public void setInnerRadius(float);
public float getButtonWidth();           public void setButtonWidth(float);
public boolean isClockwiseWinding();     public void setClockwiseWinding(boolean);
public float getButtonSpan();
```

Upstream:
[`MapMenuWidget.java`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/MapMenuWidget.java)

When adding buttons, leave the radial geometry to ATAK — `MapMenuWidget`
re-flows children based on `coveredAngle` / `buttonWidth` automatically. The
button-level knob you usually want is `setLayoutWeight(float)`, which lets a
single button occupy more or less of the arc than its peers.

### Two ways to build a `MapMenuButtonWidget`

#### Option A — load XML from your plugin (recommended for non-trivial menus)

`PluginMenuParser` is the explicit plugin-facing helper for reading menu XML
out of your plugin's own asset bundle:

```text
public class com.atakmap.android.menu.PluginMenuParser {
  public static java.lang.String getMenu(android.content.Context pluginContext,
                                         java.lang.String name);
  public static java.lang.String getItem(android.content.Context pluginContext,
                                         java.lang.String name);
}
```

Upstream:
[`PluginMenuParser.java#L53`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/PluginMenuParser.java#L53)
and
[`#L179`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/PluginMenuParser.java#L179)

The presence of the `Plugin` prefix in the class name (and the documented
`Context pluginContext` parameter) makes this the supported entry point for
plugin-shipped menu XML. Place your menu file under your plugin's
`assets/menus/<name>.xml` and load it from inside `updateMenu(...)`. From
there you can use ATAK's standard XML-driven `MapMenuButtonWidget.Factory` to
inflate the parsed string into widget instances.

#### Option B — build a button in code

```java
MapMenuButtonWidget btn = new MapMenuButtonWidget(pluginContext);
btn.setWidgetIcon(myIcon);                            // gov.tak.api.commons.graphics.IIcon
btn.setLayoutWeight(1f);
btn.setOnButtonClickHandler(new OnButtonClickHandler() {
    @Override public boolean isSupported(Object opaque) {
        return opaque instanceof MapItem;
    }
    @Override public void performAction(Object opaque) {
        MapItem item = (MapItem) opaque;
        doMyAction(item);
    }
});
itemMenu.addChildWidget(btn);
```

The constructor and key setters (SDK `javap -public
com.atakmap.android.menu.MapMenuButtonWidget`):

```text
public com.atakmap.android.menu.MapMenuButtonWidget(android.content.Context);
public void setWidgetIcon(gov.tak.api.commons.graphics.IIcon);
public void setLayoutWeight(float);
public void setOnButtonClickHandler(gov.tak.api.widgets.IMapMenuButtonWidget$OnButtonClickHandler);
public void setSubmenu(gov.tak.api.widgets.IMapMenuWidget);
public void setShowSubmenu(boolean);
public void setDisabled(boolean);
public void setSelectable(boolean);
```

Upstream:
[`MapMenuButtonWidget.java#L63`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/MapMenuButtonWidget.java#L63)
(constructor) and
[`#L204`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/MapMenuButtonWidget.java#L204)
(`setOnButtonClickHandler`).

### Click handlers — `OnButtonClickHandler` (modern) vs `setOnClickAction` (deprecated)

Two ways to wire a click action exist; only the first is current:

| API | Status | Reason |
|---|---|---|
| `IMapMenuButtonWidget.OnButtonClickHandler` via `setOnButtonClickHandler(...)` | **Current.** | Two-method contract: `boolean isSupported(Object)` and `void performAction(Object)`. The `opaque` argument is the focus item (typically a `MapItem`). |
| `MapAction` via `MapMenuButtonWidget.setOnClickAction(MapAction)` | **Deprecated.** | Upstream Javadoc: *"@deprecated Use `IMapMenuButtonWidget#setOnButtonClickHandler(OnButtonClickHandler)`"* — see [`MapMenuButtonWidget.java#L222-L227`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/MapMenuButtonWidget.java#L222-L227). The setter still works because ATAK wraps the action in a `MapActionAdapter` internally. New code should not depend on it. |

`OnButtonClickHandler` contract:

```text
// takkernel/shared/src/main/java/gov/tak/api/widgets/IMapMenuButtonWidget.java
interface OnButtonClickHandler {
    boolean isSupported(Object opaque);
    void    performAction(Object opaque);
}
```

Upstream:
[`IMapMenuButtonWidget.java`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/takkernel/shared/src/main/java/gov/tak/api/widgets/IMapMenuButtonWidget.java)

Return `false` from `isSupported(opaque)` if your handler cannot do anything
useful for the supplied focus object; the button will refuse the click rather
than fire an empty action.

---

## Observability — `MapMenuEventListener`

If you only need to know when a menu opens or closes (for analytics, logging,
or temporary styling) and do not need to mutate it, use the lighter-weight
listener:

```text
public interface com.atakmap.android.menu.MapMenuEventListener {
  public abstract boolean onShowMenu(com.atakmap.android.maps.MapItem);
  public abstract void    onHideMenu(com.atakmap.android.maps.MapItem);
}

// Register / unregister
public synchronized void addEventListener(MapMenuEventListener);
public synchronized void removeEventListener(MapMenuEventListener);
```

Upstream:
[`MapMenuEventListener.java`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/MapMenuEventListener.java)

`onShowMenu` returning `true` suppresses the default menu display — useful
for "intercept and route elsewhere" flows, but a foot-gun if used by accident.
Return `false` unless you specifically intend to swallow the event.

---

## Crash isolation (non-negotiable for plugins)

A plugin runs inside the ATAK host process. Any exception that escapes
`updateMenu(...)`, `create(...)`, or an `OnButtonClickHandler` callback
propagates out of an ATAK call stack and **crashes the host application**, not
just the plugin.

Rules:

- The body of every `MapMenuHandler.updateMenu(...)`, `MapMenuFactory.create(...)`,
  `OnButtonClickHandler.isSupported(...)`, and
  `OnButtonClickHandler.performAction(...)` MUST be wrapped in a
  `try { ... } catch (Throwable t) { Log.w(TAG, "...", t); /* return safely */ }`.
- Resource-loading paths (`Resources#getDrawable`, `Resources#getString`,
  `findViewById`, `Context#getResources`) MUST tolerate `NotFoundException`
  and `NullPointerException`. Fall back to a no-op or a minimum-viable
  rendering rather than re-throwing.
- Do not pass an `android.R.attr.*` attribute id to APIs that expect a
  drawable/resource id (`setBackgroundResource`, `setImageResource`,
  `getDrawable`). Resolve the attribute through
  `Context#getTheme().resolveAttribute(...)` first, or use a concrete
  resource id from your own plugin package.
- SDK calls (`mapView.getRenderer3()`, `Marker.setPoint`, etc.) are
  best-effort — wrap them. The SDK is a moving target across ATAK versions
  and version-skew faults must not propagate.

The `try { ... } catch (Throwable) { ... }` discipline is one line per
callback. The cost of an ATAK crash in the field is a mission failure. Always
wrap.

---

## Common pitfalls

1. **Registering at priority 0 by accident.** The convenience overload
   `registerMapMenuHandler(handler)` defaults to priority `0` — the same
   lane as ATAK core. Core handlers may mutate the menu *after* yours runs,
   undoing your additions. Pass an explicit positive priority unless you
   have a reason not to.
2. **Holding a `MapView` or `MapItem` reference past `onDestroyImpl`.**
   `MapMenuHandler` instances are kept alive by `MenuLayoutWidget` until
   explicitly unregistered. Failing to call `unregisterMapMenuHandler` on
   plugin teardown leaks both the handler and any context it captured.
3. **Using the deprecated `setOnClickAction(MapAction)` in new code.** It
   still works because ATAK wraps it in a package-private
   `MapActionAdapter`, but new plugins should target
   `setOnButtonClickHandler(OnButtonClickHandler)` directly.
4. **Returning a non-trivial result without guarding.** The interface
   Javadoc explicitly says `updateMenu` should be a no-op when the item is
   not of interest. A handler that throws on unfamiliar items will crash
   ATAK every time the user opens a radial menu on anything it does not
   recognise.
5. **Assuming `getDrawable(...)` returned non-null.** In themed-context or
   deferred-inflation corner cases, resource lookups can return `null`.
   Null-check before dereferencing.
6. **Calling `addChildWidget` from a background thread.** Widget mutation
   must happen on the UI thread. `updateMenu` is already invoked on the UI
   thread, so this only bites if you defer the work onto an executor.

---

## Full evidence table

Every public symbol cited above is reachable from the SDK jar **and** the
upstream source tree. The SDK jar is the build-time contract; the upstream
source is the implementation. If the two disagree, the SDK jar wins for
binary compatibility.

| Symbol | SDK `javap` location | Upstream permalink |
|---|---|---|
| `MapMenuHandler` | `com.atakmap.android.menu.MapMenuHandler` | [`MapMenuHandler.java`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/MapMenuHandler.java) |
| `MapMenuFactory` | `com.atakmap.android.menu.MapMenuFactory` | [`MapMenuFactory.java`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/MapMenuFactory.java) |
| `MapMenuReceiver.getInstance()` | `com.atakmap.android.menu.MapMenuReceiver` | [`MapMenuReceiver.java`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/MapMenuReceiver.java) |
| `registerMapMenuHandler(handler)` | ↑ | [`#L333`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/MapMenuReceiver.java#L333) |
| `registerMapMenuHandler(handler, priority)` | ↑ | [`#L350`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/MapMenuReceiver.java#L350) |
| `unregisterMapMenuHandler(handler)` | ↑ | [`#L363`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/MapMenuReceiver.java#L363) |
| `SHOW_MENU` / `HIDE_MENU` / `REGISTER_MENU` / `UNREGISTER_MENU` | ↑ | [`#L36-L39`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/MapMenuReceiver.java#L36-L39) |
| `MapMenuWidget` | `com.atakmap.android.menu.MapMenuWidget` | [`MapMenuWidget.java`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/MapMenuWidget.java) |
| `MapMenuButtonWidget` ctor | `com.atakmap.android.menu.MapMenuButtonWidget` | [`#L63`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/MapMenuButtonWidget.java#L63) |
| `setOnButtonClickHandler(...)` | ↑ | [`#L204`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/MapMenuButtonWidget.java#L204) |
| `setOnClickAction(...)` *(deprecated)* | ↑ | [`#L222-L227`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/MapMenuButtonWidget.java#L222-L227) |
| `IMapMenuButtonWidget.OnButtonClickHandler` | `gov.tak.api.widgets.IMapMenuButtonWidget$OnButtonClickHandler` | [`IMapMenuButtonWidget.java`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/takkernel/shared/src/main/java/gov/tak/api/widgets/IMapMenuButtonWidget.java) |
| `PluginMenuParser.getMenu` / `getItem` | `com.atakmap.android.menu.PluginMenuParser` | [`#L53`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/PluginMenuParser.java#L53), [`#L179`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/PluginMenuParser.java#L179) |
| `MapMenuEventListener` | `com.atakmap.android.menu.MapMenuEventListener` | [`MapMenuEventListener.java`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/menu/MapMenuEventListener.java) |
| `AbstractParentWidget.addChildWidget(...)` | `com.atakmap.android.widgets.AbstractParentWidget` | (widget tree; cross-checked via `javap` on the bundled SDK) |

To reproduce the `javap` lines locally:

```bash
javap -public -classpath path/to/ATAK-CIV-5.7.0.3-SDK/main.jar \
    com.atakmap.android.menu.MapMenuHandler \
    com.atakmap.android.menu.MapMenuFactory \
    com.atakmap.android.menu.MapMenuReceiver \
    com.atakmap.android.menu.MapMenuWidget \
    com.atakmap.android.menu.MapMenuButtonWidget \
    com.atakmap.android.menu.MapMenuEventListener \
    com.atakmap.android.menu.PluginMenuParser \
    gov.tak.api.widgets.IMapMenuButtonWidget
```

---

## Document provenance

- Researched against ATAK-CIV 5.7.0.3 SDK (`main.jar`) and upstream
  `TAK-Product-Center/atak-civ` at commit
  `9f6893dd657feacc35ec5de03dad721c2e44170e` (default branch `main`,
  pushed 2026-02-06).
- Permalinks above are pinned to that commit. If the upstream tree moves,
  signatures verified via `javap` against the SDK jar remain authoritative
  for the 5.7.0.3 contract.
- Vendor-neutral: this document references no application-specific code.
