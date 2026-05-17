# Contract: `MarkerMode` v2 (extension of feature 002's enum)

**Feature**: `003-custom-marker-icon` | **Phase 1**
**File**: `app/src/main/java/com/atakmap/android/twcoord/gotopage/MarkerMode.java`
**Type**: Additive enum change.

This contract specifies the v2 shape of `MarkerMode` and the behavioural rules around its new `CUSTOM_ICON` value. The eight existing values are unchanged.

## v2 enum surface

```java
public enum MarkerMode {
    MOVE_ONLY(null),
    WAYPOINT("b-m-p-s-m"),
    FRIENDLY("a-f-G"),
    HOSTILE("a-h-G"),
    NEUTRAL("a-n-G"),
    UNKNOWN("a-u-G"),
    SPI("b-m-p-s-p-i"),
    MISSION_POINT("b-m-p-w-GOTO"),

    /** NEW in v2 — generic Spot Map pin with operator-picked iconset path. */
    CUSTOM_ICON("b-m-p-s-m");

    private final String cotType;
    MarkerMode(String cotType) { this.cotType = cotType; }

    public String cotType() { return cotType; }
    public boolean dropsMarker() { return cotType != null; }

    /** NEW — true only for CUSTOM_ICON. Used by submit flow to decide whether to call setIconPath. */
    public boolean requiresIconPath() { return this == CUSTOM_ICON; }

    /** NEW — convenience alias for switch arms / readability. */
    public boolean isCustomIcon() { return this == CUSTOM_ICON; }
}
```

## Compatibility

- Source-compatible with v1. No existing call sites change.
- Binary-compatible with v1 — adding an enum value at the end of the list does not break callers that switch on the enum (they fall through to `default`).
- Persistence-compatible: `pref_goto_marker_mode` uses `enum.name()`, which is `"CUSTOM_ICON"` for the new value and unchanged strings for the eight existing values.

## CoT type rationale (FR-012)

`CUSTOM_ICON.cotType() = "b-m-p-s-m"` — the Spot Map generic user-placed pin. Same type as `WAYPOINT`. Rationale captured in [research R7](../research.md#r7--cot-type-for-the-custom-icon-marker-mode-fr-012) and [ADR-0010 D4](../../../docs/adr/0010-custom-marker-icon-picker.md):

- The marker's identity is fully expressed by the icon path (via `MarkerCreator.setIconPath`), so the CoT type's role is reduced to "what group should this marker live in" — and `PlacePointTool` automatically routes any marker with a valid iconset path into the User Icons MapGroup regardless of CoT type.
- `b-m-p-s-m` carries no affiliation semantics (unlike `a-f-G`, `a-h-G`, …). Operators who want affiliation-coded markers continue to use the existing 4 affiliation modes.

## Submit-path branching contract

Callers (concretely: `TwCoordGotoView.submitOk`) MUST branch as follows when constructing the `MarkerCreator`:

```java
PlacePointTool.MarkerCreator b = new PlacePointTool.MarkerCreator(dest)
    .setUid(UUID.randomUUID().toString())
    .setType(markerMode.cotType())
    .setCallsign(callsign);
if (markerMode.requiresIconPath() && currentSelection != null) {
    b.setIconPath(currentSelection.iconsetPath());
}
b.placePoint();
```

Three guarantees this gives:

1. `setIconPath` is called only when the marker mode actually needs one — defensive against future enum additions.
2. A null `currentSelection` (FR-009 mid-flight clear, or operator picked CUSTOM_ICON and never picked an icon) does NOT crash; `b.setIconPath(null)` is rejected by [`PlacePointTool.java:215`](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/ATAK/app/src/main/java/com/atakmap/android/user/PlacePointTool.java#L215) (no-op on null/empty), but Submit should be disabled in this case anyway per FR-006 — defence in depth.
3. The eight non-custom modes go through the v1 builder chain unchanged.

## Submit-enabled rule (FR-006 contract on the view layer)

`Submit.enabled` = `(activeTabParseIsOk) AND validMarkerSelection`, where:

```java
boolean validMarkerSelection() {
    if (!markerMode.requiresIconPath()) return true;       // 8 v1 modes always pass
    return currentSelection != null;                        // CUSTOM_ICON needs an icon
}
```

This MUST be re-evaluated on:

- Marker-mode change (radio click)
- Picker dialog `onIconPicked`
- Picker dialog `onCancelled` (no change but cheap to re-eval)
- FR-009 fallback firing during page bind

## Test contract

Unit tests on `MarkerMode` (pure JVM, no Android):

1. `CUSTOM_ICON.cotType().equals("b-m-p-s-m")`.
2. `CUSTOM_ICON.dropsMarker() == true`.
3. `CUSTOM_ICON.requiresIconPath() == true`; all other modes return false.
4. `CUSTOM_ICON.isCustomIcon() == true`; all other modes return false.
5. `MarkerMode.valueOf("CUSTOM_ICON") == CUSTOM_ICON` (persistence round-trip).
6. `Arrays.stream(MarkerMode.values()).filter(MarkerMode::dropsMarker).count() == 8` (was 7 in v1; now 8 because CUSTOM_ICON joins).
