# Contract — `CoordinateParser` (inverse-converter facade)

**Feature**: 002-tw-coord-goto | **Java package**: `com.atakmap.android.twcoord.gotopage`

`CoordinateParser` is the single entry point that converts a
user-typed coordinate input into a `Wgs84`. It is the *inverse* of
feature 001's `CoordinateConverter` and runs entirely off-device
(pure JVM, no Android, no ATAK).

This contract is the source of truth for the JVM unit tests under
`app/src/test/java/com/atakmap/android/twcoord/gotopage/`. The
implementation lands after the tests, per Constitution Principle II.

---

## Public surface (Java)

```java
public final class CoordinateParser {

    /** Parses a Taipower grid string (9 or 11 chars, with or without a
     *  single internal space, case-insensitive). */
    public ParseResult parseTaipower(String rawValue);

    /** Parses TWD97 TM2 easting/northing in metres. */
    public ParseResult parseTwd97(int easting, int northing, int zone);

    /** Parses TWD67 TM2 easting/northing in metres. The 4-parameter
     *  shift constants are inherited from DatumShiftTwd67 (feature 001). */
    public ParseResult parseTwd67(int easting, int northing, int zone);

    /** Dispatch helper used by the page's submit button. */
    public ParseResult parse(CoordinateInput input);
}
```

The class is **stateless** and **thread-safe** (only references the
shared, immutable `proj4j` `CoordinateTransform` instances; matches the
existing `Projections` facade).

---

## Validation rules (cross-references to spec FRs)

### Taipower (FR-004, FR-005)

| Input shape | Validation | Result |
|---|---|---|
| Length 9 or 11 visible chars (post-normalisation) | OK | proceed |
| Any other length | reject | `Invalid(BAD_LENGTH)` |
| Region letter (1st char) `Y` or `Z` | reject | `Invalid(RESERVED_LETTER_YZ)` |
| Region letter outside `A..X` | reject | `Invalid(BAD_LETTER)` |
| Hundred-metre letters (positions 6 & 7) outside `A..J` | reject | `Invalid(BAD_LETTER)` |
| Any non-digit in a digit slot | reject | `Invalid(NON_DIGIT)` |
| All letters / digits valid, conversion to WGS84 returns outside Taiwan box | reject | `OutOfRange(attemptedWgs84)` |
| All checks pass | accept | `Ok(input, wgs84)` |

Normalisation (applied before validation):
- `toUpperCase(Locale.ROOT)`
- `trim()`
- collapse consecutive ASCII whitespace runs to a single space; strip
  parentheses; remove embedded `\r` / `\n`.

### TWD97 / TWD67 (FR-006)

| Input shape | Validation | Result |
|---|---|---|
| `easting` not in `100_000..1_000_000` (6 or 7 digits) | reject | `Invalid(BAD_LENGTH)` |
| `northing` not in `1_000_000..10_000_000` (7 digits) | reject | `Invalid(BAD_LENGTH)` |
| `zone` not in `{121, 119}` | reject | `Invalid(BAD_ZONE)` |
| Numeric conversion to WGS84 succeeds, result outside Taiwan box | reject | `OutOfRange(attemptedWgs84)` |
| All checks pass | accept | `Ok(input, wgs84)` |

The Taiwan box is the same one feature 001's `CoordinateConverter`
uses (LAT 21.5–26.5, LON 118.0–122.5); the constant lives in a
shared location to keep the forward + inverse paths in sync.

---

## Conversion chain

```text
Taipower string
  └─► TaipowerGrid.fromCode(rawValue)  (existing — wider symbol from feature 001)
        └─► Twd67Tm2
              └─► DatumShiftTwd67.twd67ToTwd97(Twd67Tm2)  (existing)
                    └─► Twd97Tm2
                          └─► Projections.twd97ToWgs84(Twd97Tm2)  (existing
                                                                  inverse direction)
                                └─► Wgs84

TWD97 (easting, northing, zone)
  └─► Twd97Tm2.of(easting, northing, zone)
        └─► Projections.twd97ToWgs84  (inverse direction of the same
                                       CoordinateTransform used by the widget)
              └─► Wgs84

TWD67 (easting, northing, zone)
  └─► Twd67Tm2.of(easting, northing, zone)
        └─► DatumShiftTwd67.twd67ToTwd97  (negated 4-param shift; small
                                            iteration cycle to converge to
                                            sub-metre stability)
              └─► Twd97Tm2
                    └─► Projections.twd97ToWgs84
                          └─► Wgs84
```

All three chains terminate at a `Wgs84`; the Taiwan-box check happens
once at the bottom and is the only out-of-range gate.

---

## Test contract (JUnit 4 + AssertJ)

### Required test methods

For each of the 22 entries in `test-data/taiwan_cities_coords.csv`,
the test fixture MUST exercise:

```java
@Test public void taipower_roundtrip_<city>()
    // Forward: city.wgs84 → CoordinateConverter → TaipowerCode → string
    // Inverse: string → CoordinateParser.parseTaipower → Wgs84
    // Assert: inverseWgs84 within 5 m (haversine) of city.wgs84 on main island,
    //         within 20 m on outer islands.

@Test public void twd97_roundtrip_<city>()
    // Forward: city.wgs84 → Twd97Tm2
    // Inverse: parseTwd97(city.twd97_e, city.twd97_n, city.zone)
    // Assert: inverseWgs84 within 0.5 m (TWD97 is the conformal native CRS).

@Test public void twd67_roundtrip_<city>()
    // Forward: city.wgs84 → Twd67Tm2
    // Inverse: parseTwd67(city.twd67_e, city.twd67_n, city.zone)
    // Assert: inverseWgs84 within 5 m on main island, within 20 m on outer
    //         islands (matches the forward-direction tolerance bands).
```

### Required negative-path tests

```java
@Test public void parseTaipower_rejects_zReservedLetter()
@Test public void parseTaipower_rejects_yReservedLetter()
@Test public void parseTaipower_rejects_8charLength()
@Test public void parseTaipower_rejects_12charLength()
@Test public void parseTaipower_normalises_lowercase()
@Test public void parseTaipower_normalises_missingSpace()
@Test public void parseTaipower_normalises_doubleSpace()
@Test public void parseTaipower_normalises_surroundingParens()
@Test public void parseTwd97_rejects_zoneNot121or119()
@Test public void parseTwd97_rejects_5digitEasting()
@Test public void parseTwd97_rejects_8digitNorthing()
@Test public void parseTwd97_returnsOutOfRange_forCoordinateInPhilippines()
@Test public void parseTwd67_returnsOutOfRange_forCoordinateInOkinawa()
```

### Round-trip stability

```java
@Test public void roundTrip_isStable_forAllAuthoritativeCities()
    // For every CSV row, forward → inverse → forward MUST return the
    // original Twd97Tm2 within the per-unit tolerance — guards against
    // drift in DatumShiftTwd67's iterative inverse.
```

---

## Performance contract

| Operation | Reference device target |
|---|---|
| `parseTaipower(rawValue)` | < **1 ms** median (JVM-only) |
| `parseTwd97(...)` | < **1 ms** median |
| `parseTwd67(...)` | < **1 ms** median (includes one inverse-shift iteration) |
| 22-city round-trip suite | < **150 ms** total wall-clock |

These targets keep `CoordinateParser` comfortably under the spec's
SC-004 100 ms validation budget; the remaining headroom belongs to
`EditText` reflow and the debouncer post-back to the UI thread.
