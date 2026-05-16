# Contract — `RecentEntryStore` (persistence schema)

**Feature**: 002-tw-coord-goto | **Java package**: `com.atakmap.android.twcoord.gotopage`

`RecentEntryStore` is a small repository that persists up to 10
successful coordinate submissions in
`SharedPreferences("tw_coord_settings")`. It is consumed by
`TwCoordGotoView` to render the "Recent" section (US4) and by the
submit path to record each new entry.

---

## Public surface (Java)

```java
public final class RecentEntryStore {

    public RecentEntryStore(SharedPreferences prefs);

    /** Append a successful submission. Deduplicates on (unit, rawValue):
     *  if a row already matches, it is removed before insertion so the
     *  new (newer-timestamp) row floats to the head. Capacity-10 FIFO
     *  eviction is then applied. */
    public void append(RecentEntry entry);

    /** Returns the persisted list, newest-first. Never null; empty list
     *  on first call. */
    public List<RecentEntry> getAll();

    /** Removes the entry at the given index (0 = newest). No-op if
     *  out-of-range. */
    public void removeAt(int index);

    /** Empties the store. */
    public void clear();

    /** Registers a listener fired on every mutation (append / remove /
     *  clear). Implementation: wraps a SharedPreferences change
     *  listener. */
    public void registerListener(OnChange listener);
    public void unregisterListener(OnChange listener);

    public interface OnChange {
        void onRecentEntriesChanged(List<RecentEntry> newList);
    }
}
```

The class is **thread-safe**: all writes serialise through a single
`synchronized` block; reads use the immutable snapshot last written
to the in-memory cache.

---

## Persistence layout

Single SharedPreferences key:

```text
pref_goto_recent_json
  ←→ JSON: array of objects, capacity 10, newest-first
```

### JSON object schema

```json
{
  "unit": "TAIPOWER" | "TWD97" | "TWD67",
  "rawValue": "H7509 DB4016" | "302912 / 2770905" | …,
  "easting": 0 | <int>,
  "northing": 0 | <int>,
  "zone": 0 | 121 | 119,
  "timestampEpochMs": 1747353600000
}
```

| Field | Notes |
|---|---|
| `unit` | One of three exact strings; rejected at parse time if any other value. |
| `rawValue` | The display string the operator sees in the Recent list. For Taipower, this is the operator's normalised input; for TWD97/TWD67, a synthesised `"E / N"` string. |
| `easting` / `northing` / `zone` | Present only for TWD97 / TWD67; `0` sentinel for Taipower entries. The 0-sentinel choice over JSON `null` keeps the schema flat and the deserialiser branchless. |
| `timestampEpochMs` | Required; used for ordering + dedup arbitration. |

### Serialisation / deserialisation

Encode with `org.json.JSONArray` / `JSONObject` (built into Android,
no new dependency). On read, malformed JSON is treated as "empty
list" and the corrupted key is overwritten with `"[]"` on the next
`append` — no crash, no silent data loss propagation.

---

## Invariants

| Invariant | Where enforced |
|---|---|
| `size <= 10` | `append` trims after dedup. |
| No two entries share `(unit, rawValue)` | `append` removes the existing row before insertion. |
| Ordering: index 0 = newest by `timestampEpochMs` | `getAll()` sorts before returning. |
| All entries have valid `unit` values | Deserialiser drops any entry whose `unit` is not one of the three legal strings (best-effort recovery for forward-compat). |
| Persistence is atomic per mutation | Writes use `prefs.edit().putString(...).apply()`; SharedPreferences serialises this. |

---

## Test contract (JUnit 4 + AssertJ)

```java
@Test public void append_storesEntry()
@Test public void append_dedupesOn_unitAndRawValue()
@Test public void append_evictsOldest_whenCapacityExceeded()
@Test public void getAll_returnsNewestFirst()
@Test public void getAll_returnsEmptyList_onFirstRead()
@Test public void removeAt_removesByIndex_andShiftsSubsequent()
@Test public void clear_emptiesTheStore()
@Test public void deserialiser_recoversFromCorruptedJson()
@Test public void deserialiser_skipsEntriesWithUnknownUnitString()
@Test public void registerListener_isInvokedOn_append_remove_clear()
@Test public void roundTrip_acrossInstanceReconstruction_preservesEntries()
```

The fixture uses `androidx.test.core.app.ApplicationProvider` to
obtain a real `SharedPreferences` instance backed by an
`InMemorySharedPreferences` (or a temp-file-backed one in instrumented
tests). No mocking required for the store itself.

---

## Performance contract

| Operation | Reference device target |
|---|---|
| `append(...)` (including JSON encode + `apply`) | < **5 ms** typical |
| `getAll()` (cache hit) | < **0.1 ms** |
| `getAll()` (cache miss, parse 10 entries) | < **2 ms** |

The store is not on any hot path (UI redraw, MAP_* events); these
budgets exist to bound worst-case behaviour on cold starts only.
