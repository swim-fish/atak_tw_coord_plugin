# Contract: `Formatter`

**Package**: `com.atakmap.android.twcoord.coord`
**Module**: pure-Java (no Android dependency)
**Tested at**: `app/src/test/java/.../coord/FormatterTest.java`

Turns a `ConversionResult` plus a localised string-bundle into the
`DisplayLine` the widget paints. The widget itself does no string
construction beyond drawing the lines.

---

## API

```java
public final class Formatter {

    /** Strings the formatter needs, looked up once per locale change. */
    public interface Strings {
        String labelMap();          // e.g. "MAP", "地圖", "地図"
        String labelMe();           // e.g. "ME", "我", "私"
        String unitTagTaipower();   // e.g. "TPC", "台電", "TPC"
        String unitTagTwd97();      // e.g. "TWD97"
        String unitTagTwd67();      // e.g. "TWD67"
        String stateOutOfRange();   // e.g. "out of range", "超出範圍", "範囲外"
        String stateNoFix();        // e.g. "no fix", "無定位", "測位不可"
        String stateNoPermission(); // e.g. "no permission", "無權限", "権限なし"
    }

    /**
     * @param source       which readout this is (map-centre or self-marker)
     * @param result       what the converter produced (may be OutOfRange)
     * @param unit         the active unit (drives the tag label)
     * @param strings      localised labels
     * @return DisplayLine ready for the widget; never null
     */
    public DisplayLine format(
        Wgs84.Source source,
        ConversionResult result,
        CoordinateUnit unit,
        Strings strings
    );

    /**
     * @return the exact string a `Ok` `DisplayLine.value` carries —
     *         used as the clipboard payload on tap (FR-015).
     *         For non-Ok states, returns the entire human-readable line
     *         (label + state).
     */
    public String forClipboard(DisplayLine line);
}
```

---

## Format rules

### Taipower
- 9-char: `"<region><sub-4d> <hm-2L><tm-2d>"` — e.g. `"B7039 BD32"`.
  Single space between sub-region digits and 100-m letters.
- 11-char: `"<region><sub-4d> <hm-2L><tm-2d><om-2d>"` — e.g.
  `"B7039 BD3223"`.
  (11-char support is a future flag; default precision in v1 is 9.)

### TWD97 / TWD67
- `"<E>m <N>m"` — both numbers as plain decimal metres, **integer
  precision** (round half-up). Example: `"306963m 2769619m"`.
- Optional thousands separator follows the active locale (Locale.US
  groups, locale-specific grouping in zh-TW and ja-JP — Android handles
  this via `NumberFormat.getInstance(locale)`).

### Out-of-range
- Primary line: `"<labelPrefix> <unitTag>: <stateOutOfRange>"`.
- Fallback line: WGS84 to 6 decimals, comma-separated, in degrees.
  Example: `"(25.033611, 121.564472)"`.

### No fix
- Single line: `"<labelMe>: <stateNoFix>"`. No fallback line.

### No permission
- Single line: `"<labelMe>: <stateNoPermission>"`. The widget renders
  this with a subtle visual cue (e.g. tap to open settings); the
  formatter itself does not encode behaviour.

---

## Clipboard equality (FR-015 / SC-008)

`forClipboard(line)` MUST return a string that satisfies:
`displayedString.equals(clipboardString)` for every `Ok` line, across
all three units and all three UI locales. Tests MUST enumerate all 9
combinations and assert string equality.

---

## Performance

- Pure function. Locale-specific `NumberFormat` instances are cached
  inside the formatter; tests verify only one instance is created per
  locale.
