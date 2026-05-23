# Contract: `CoordinateConverter`

**Package**: `com.atakmap.android.twcoord.coord`
**Module**: pure-Java (no Android dependency)
**Tested at**: `app/src/test/java/.../coord/CoordinateConverterTest.java`

The single entry point used by the widget to turn a `Wgs84` source
fix into a renderable `ConversionResult`. All other classes in the
package (`Projections`, `DatumShiftTwd67`, `TaipowerGrid`) are
implementation details.

---

## API

```java
public final class CoordinateConverter {

    /**
     * Convert a WGS84 fix into the requested unit.
     * Pure function; no I/O, no logging.
     *
     * @param fix    a well-formed Wgs84 (validated at construction)
     * @param unit   the target coordinate unit
     * @return       Ok(...) inside the unit's valid domain;
     *               OutOfRange(fix) otherwise.
     *               Never returns NoFix — that is the caller's
     *               responsibility based on staleness.
     * @throws NullPointerException if either argument is null
     */
    public ConversionResult convert(Wgs84 fix, CoordinateUnit unit);
}
```

---

## Behaviour matrix

| Unit | In-range condition | Out-of-range trigger |
|---|---|---|
| `TWD97` | `120.0 ≤ lon ≤ 122.5` AND `21.5 ≤ lat ≤ 25.5` (Taiwan main island), enforced before calling proj4 | Anything else → `OutOfRange(fix)` |
| `TWD67` | Same window as TWD97 (it is computed by post-shifting the TWD97 result) | Same as TWD97 |
| `TAIPOWER` | TWD67 easting ∈ [90 000, 410 000) AND northing ∈ [2 400 000, 2 800 000), AND the resulting (row, col) cell holds a non-blank letter (excludes `I` underwater and `S`/`X`/`Y`/`Z` offshore) | Anything else → `OutOfRange(fix)` |

The 120.0 / 122.5 / 21.5 / 25.5 window is the published valid domain
for TM2 z121 over the Taiwan main island; it deliberately excludes
Penghu (Zone 119) and other outer islands until ADR adds them.

---

## Golden test vectors

The unit-test class MUST include these four cases (from
`research.md` R8) and assert each unit to its tolerance:

| Location | WGS84 in | TWD97 expect (±0.1 m) | TWD67 expect (±3 m) | Taipower-9 expect (±10 m) |
|---|---|---|---|---|
| Taipei 101 | 25.033611, 121.564472 | 306962.887, 2769619.124 | 306132.271, 2769822.821 | B7039 BD32 |
| Kaohsiung 85 | 22.61225, 120.2867 | 176669.456, 2501522.988 | 175842.607, 2501731.687 | Q0703 CC43 |
| Taichung CH | 24.1416, 120.6437 | 213789.087, 2670751.115 | 212960.559, 2670956.951 | G5341 FE65 |
| Hualien Stn | 23.9932, 121.6012 | 311171.020, 2654400.548 | 310341.091, 2654606.002 | H7509 DB40 |
| Hualien inland (L) | 23.9217588, 121.0492519 | 255013.996, 2646359.053 | 254185.000, 2646565.000 | L0593 BA86 |

Tolerance numbers come straight from the pwa_map reference (see
`research.md` R8). The Kaohsiung 85 letter was corrected from `P` to
`Q` and a fifth `L`-region vector was added on 2026-05-23 — see
ADR-0001 follow-up note for the letter-table correction.

---

## Negative cases the tests MUST cover

| Input | All three units MUST return |
|---|---|
| WGS84 (40.0, 121.0) — outside Taiwan north | `OutOfRange(fix)` |
| WGS84 (22.0, 100.0) — outside Taiwan west | `OutOfRange(fix)` |
| WGS84 (23.5, 119.6) — Penghu (Zone 119) | `OutOfRange(fix)` for TWD97/TWD67 (until Zone 119 ADR); `OutOfRange(fix)` for Taipower (Y/Z) |

`null` for either argument MUST raise `NullPointerException` (Java
language convention; no need for a `@Nullable` allowance).

---

## Threading & performance

- **Thread-safe**: stateless and pure; safe to call from any thread.
- **Performance**: each `convert(...)` MUST complete in ≤ 50 μs on the
  reference device (measured by a JMH micro-bench in
  `app/src/test/`). This leaves the full 100 ms SC-002 budget for
  event dispatch + widget repaint, which is where the actual UI
  latency lives.
