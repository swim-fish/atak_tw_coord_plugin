# Contract: `AddressDatabaseFacade`

**Package**: `com.atakmap.android.twcoord.address`

**Source of truth for**: the SDK seam between the rest of the plugin and Android's
`SQLiteDatabase`. The interface is mockable on the JVM with no Android dependency.

## Type signature

```java
public interface AddressDatabaseFacade extends AutoCloseable {
    /** Read the `metadata` key/value table verbatim. Used by the Offline Address page. */
    GeneratorMetadata readMetadata();

    /** Find the single nearest address record within `radiusMeters` of (lat, lon).
     *  Returns null if no record falls inside the radius. */
    AddressRecord nearestWithin(double lat, double lon, double radiusMeters);

    @Override
    void close();
}
```

The production implementation `SqliteAddressDatabase implements AddressDatabaseFacade` opens
the DB read-only with `OPEN_READONLY | NO_LOCALIZED_COLLATORS`. The implementation MUST NOT
throw out of any public method — IO / SQL failures return `null` from `nearestWithin` and a
`GeneratorMetadata` with empty defaults from `readMetadata`, after logging at `Log.w`.

## Behaviour: `nearestWithin(lat, lon, radius)`

Implementation algorithm (cf. [research.md R4](../research.md#r4--reverse-lookup-algorithm-bbox--haversine-plus-display-name-pick)):

```java
double latRad = Math.toRadians(lat);
double dLat = radiusMeters / 111_320.0;             // 1° lat ≈ 111.32 km
double dLon = radiusMeters / (111_320.0 * Math.cos(latRad));

// Stage 1 — R*Tree bbox.
Cursor c = db.rawQuery(
    "SELECT p.lat, p.lon, p.display_name, p.display_name_halfwidth"
  + "  FROM places_rtree r JOIN places p ON r.id = p.id"
  + " WHERE r.min_lat <= ? AND r.max_lat >= ?"
  + "   AND r.min_lon <= ? AND r.max_lon >= ?",
    new String[]{ str(lat + dLat), str(lat - dLat),
                  str(lon + dLon), str(lon - dLon) });

// Stage 2 — haversine refine.
double best = radiusMeters;
AddressRecord winner = null;
while (c.moveToNext()) {
    double rLat = c.getDouble(0);
    double rLon = c.getDouble(1);
    double d = haversine(lat, lon, rLat, rLon);
    if (d < best) {
        best = d;
        winner = new AddressRecord(rLat, rLon, c.getString(2), c.getString(3));
    }
}
c.close();
return winner;
```

Default `radiusMeters = 500` (per R4). The contract does not expose configuration in v1; the
default is hard-coded in `AddressResolver`.

## Test plan (`AddressDatabaseFacadeTest`, JVM)

The production class is named `SqliteAddressDatabase`. JVM tests use Robolectric (already on
the project's test classpath) to spin up an in-memory SQLite under
`org.robolectric.shadows.ShadowSQLiteConnection` (or, alternatively, depend on
`org.xerial:sqlite-jdbc` — to be decided at /speckit-tasks time; the contract is independent of
that choice). For pure unit tests of the algorithm we test `AddressResolver` against a mock
`AddressDatabaseFacade`.

| # | Test name | What it asserts |
|---|---|---|
| 1 | `readMetadata_returnsAllKeysVerbatim` | Given a `metadata` table with `schema_version=1, county=台中市, data_date=115-01, ...`, the returned `GeneratorMetadata` carries all keys including unknown ones in `raw`. |
| 2 | `readMetadata_missingTableReturnsEmpty` | A DB with no `metadata` table returns `GeneratorMetadata` with all defaults; no throw. |
| 3 | `nearestWithin_returnsNearestRecordInRadius` | Fixture: 3 rows at known distances 50 m, 200 m, 800 m from query point; with radius 500 m, returns the 50 m row. |
| 4 | `nearestWithin_returnsNullIfNoneInRadius` | Same fixture, radius 30 m — returns null. |
| 5 | `nearestWithin_respectsCosLatitudeBboxCorrection` | At 25° N, a 500 m radius produces a longitude delta wider than the latitude delta; record at +0.0055° lon, +0.0045° lat passes the bbox. |
| 6 | `nearestWithin_handlesEmptyRtree` | DB with `places` populated but `places_rtree` empty — returns null cleanly (no SQL exception escapes). |
