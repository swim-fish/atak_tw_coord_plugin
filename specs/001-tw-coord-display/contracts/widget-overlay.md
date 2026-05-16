# Contract: `TwCoordWidget` (on-map readout overlay)

**Package**: `com.atakmap.android.twcoord`
**Module**: Android (depends on ATAK SDK `MapWidget`)
**Tested at**: `app/src/androidTest/java/.../WidgetRenderTest.java`,
                 `app/src/androidTest/java/.../ClipboardCopyTest.java`

The widget owns no business logic. It is a pure renderer driven by
two `DisplayLine` inputs (one map-centre, one self-marker) and exposes
a single tap callback for clipboard copy.

---

## Lifecycle

```java
public final class TwCoordWidget extends MapWidget {

    public TwCoordWidget(
        MapView mapView,
        ClipboardManager clipboard,
        ToastCallback toastCallback
    );

    /** Attach to the standard ATAK root layout (top-right anchor). */
    public void attach();

    /** Detach and free resources. */
    public void detach();

    /**
     * Update what is shown. Both arguments may be the previous values
     * — the widget MUST invalidate only when at least one differs
     * field-by-field.
     */
    public void render(DisplayLine mapCentreLine, DisplayLine selfLine);

    public interface ToastCallback {
        void showCopiedToast(String labelKey);
    }
}
```

---

## Rendering rules

- Two text rows, top-aligned within the widget, monospace font for
  numeric readability, 14 dp text size by default.
- The label prefix and unit tag come from `DisplayLine` exactly as
  given; the widget does NOT format or transform.
- A 2 dp inner padding separates rows; total widget height adjusts to
  fit the second (fallback) line when present.
- Background: 60 % opaque dark grey, 4 dp corner radius. Foreground
  text colour: white (`OK` state) or amber (`OUT_OF_RANGE`) or grey
  (`NO_FIX` / `NO_PERMISSION`).
- Anchor: `RootLayoutWidget.TOP_RIGHT` by default.

---

## Tap behaviour (FR-015 / SC-008)

- Two independent tap targets, one per row. Tap on a row:
  1. Calls `Formatter.forClipboard(displayLine)` to obtain the exact
     string the user sees.
  2. Writes the string to the system `ClipboardManager` under a
     stable label (e.g. `"tw-coord"`).
  3. Invokes `ToastCallback.showCopiedToast(labelKey)`. The labelKey
     is a localised string resource; the widget MUST NOT hard-code
     "Copied".
- Confirmation MUST appear ≤ 200 ms after the tap (SC-008).

---

## Locale repaint contract (FR-018)

- The widget does NOT cache `Strings`. The caller (MapComponent) is
  responsible for passing freshly-resolved `Strings` (from the
  locale-wrapped `Context`) into `Formatter` on every render.
- When the language override changes, the MapComponent MUST call
  `render(...)` again with rebuilt `DisplayLine`s. The widget itself
  has no notion of locale.

---

## Threading

- All public methods MUST be called from the ATAK main thread (UI
  thread). The widget asserts this in debug builds.

---

## Negative cases the tests MUST cover

- `render(null, null)` → no-op (renders empty); no crash.
- Tap with `Ok` state → clipboard exact-string equality check.
- Tap with `OutOfRange` state → clipboard receives the multi-line
  composite string (`"MAP TWD97: out of range\n(25.0, 121.0)"` style)
  unchanged.
- Rapid taps (5 in 200 ms) → exactly 5 toasts queued; no duplicate
  clipboard writes within the same animation frame.
