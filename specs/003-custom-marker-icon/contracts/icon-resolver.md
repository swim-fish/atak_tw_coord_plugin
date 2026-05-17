# Contract: `IconResolver`

**Feature**: `003-custom-marker-icon` | **Phase 1**
**Package**: `com.atakmap.android.twcoord.gotopage`
**Type**: Public (within-plugin) facade — wraps the host's `UserIconDatabase` so the rest of the plugin never touches the SDK directly.

This is the only class in the plugin allowed to import `com.atakmap.android.icons.*`. Every other class (picker dialog, view controller, persistence layer, tests) consumes `IconResolver`'s typed API. Rationale: a single seam to mock in JVM unit tests + a single audit point for Constitution VI guards on SDK calls.

## Public API

```java
public final class IconResolver {

  /** Construct with the plugin context. Capable of being held for the plugin's lifetime. */
  public IconResolver(Context pluginContext);

  /**
   * Enumerate every iconset currently visible to the host's icon library.
   * Lightweight — does NOT load icons or bitmaps.
   * Caller MUST invoke this off the main thread per Constitution Principle IV.
   *
   * @return immutable list ordered alphabetically by iconset name, case-insensitive.
   *         Empty list (never null) if no iconsets are installed.
   */
  public List<IconsetSummary> listIconsets();

  /**
   * Enumerate every icon in one iconset.
   * Loads icon metadata but NOT bitmap blobs (use {@link #loadBitmap(int)} per-cell).
   * Caller MUST invoke this off the main thread.
   *
   * @param iconsetUid host-assigned UID of the iconset
   * @return immutable list ordered alphabetically by file name (extension-stripped),
   *         case-insensitive. Empty list if the iconset has no icons or has been removed.
   *         Rows whose metadata is invalid per {@link UserIcon#isValid()} are silently skipped.
   */
  public List<IconRow> listIcons(String iconsetUid);

  /**
   * Synchronously load a single icon's bitmap.
   * Caller MUST invoke off the main thread. Cache hits are sub-ms; cache misses
   * incur one indexed SQLite query + BitmapFactory.decodeByteArray.
   *
   * @param iconId UserIcon.getId()
   * @return decoded Bitmap, or null if the row's bitmap blob is missing or fails to decode
   *         (FR-010a "skip silently" behaviour — caller treats null as "exclude this row").
   */
  public Bitmap loadBitmap(int iconId);

  /**
   * Look up an icon by its persisted iconset path.
   * Used during page-bind to validate the persisted selection (FR-009 detection path).
   * Returns null if the path is malformed OR the path's iconset/icon no longer resolves.
   * MUST be called off the main thread if called from a hot UI handler.
   *
   * @param iconsetPath canonical "&lt;uid&gt;/&lt;group&gt;/&lt;filename&gt;" form
   * @return populated IconSelection (with iconsetName resolved) or null on miss/invalid
   */
  public IconSelection resolveSelection(String iconsetPath);

  /**
   * Cheap validity probe used during page-bind. Equivalent to
   * {@code resolveSelection(path) != null} but skips constructing an IconSelection.
   *
   * @return true if the path is well-formed AND the icon currently exists in the
   *         host's icon database.
   */
  public boolean isValidIconsetPath(String iconsetPath);
}
```

### `IconsetSummary` (value class)

| Field | Type | Source |
|---|---|---|
| `uid` | `String` | `UserIconSet.getUid()` |
| `name` | `String` | `UserIconSet.getName()` |
| `iconCount` | `int` | `UserIconSet.getIcons().size()` (after invalid-row filter) |

`equals` / `hashCode` keyed on `uid`.

### `IconRow` (value class)

| Field | Type | Source |
|---|---|---|
| `id` | `int` | `UserIcon.getId()` |
| `iconsetUid` | `String` | `UserIcon.getIconsetUid()` |
| `group` | `String` | `UserIcon.getGroup()` |
| `fileName` | `String` | `UserIcon.getFileName()` |
| `displayName` | `String` | `fileName` with last `.png`/`.jpg`/`.jpeg`/`.svg` suffix stripped (case-insensitive) |
| `iconsetPath` | `String` | `UserIcon.getIconsetPath()` (= `<uid>/<group>/<fileName>`) |

`equals` / `hashCode` keyed on `id`.

## Behavioural contract

| Behaviour | Required because |
|---|---|
| `listIconsets()` / `listIcons(...)` MUST sort alphabetically (case-insensitive) | [R13](../research.md#r13--iconseticon-ordering-inside-the-picker) |
| `listIconsets()` calls `getIconSets(true, false)` — icons loaded, bitmaps NOT | [R2](../research.md#r2--bitmap-fetch-strategy-at-picker-step-2) — keeps step-1 within SC-002 |
| `listIcons(uid)` MUST silently filter out rows where `UserIcon.isValid()` is false | FR-010a; defensive parsing |
| `loadBitmap(int)` MUST return null (never throw) on decode failure | FR-010a; caller relies on null = "skip silently" |
| Every public method MUST wrap its body in `try/catch (Throwable)`; on catch, log via `Log.w(TAG, ..., t)` and return safe default (empty list / null / false) | Constitution VI |
| Implementation MAY cache `listIconsets()` results for the lifetime of the `IconResolver` instance | Performance — picker re-open should not re-walk the iconsets table |
| Cache MUST be invalidated on `ICONSET_ADDED` / `ICONSET_REMOVED` broadcasts (broadcast handling lives in the view layer; view layer calls `IconResolver.invalidateCaches()`) | [R6](../research.md#r6--reacting-to-iconset-addremove-during-a-session) |

```java
/** Called by the view layer's ICONSET_ADDED / ICONSET_REMOVED listener. */
public void invalidateCaches();
```

## Threading model

| Method | Thread |
|---|---|
| Constructor | Any |
| `listIconsets`, `listIcons`, `loadBitmap`, `resolveSelection` | Worker (caller's responsibility — `IconResolver` does not own an executor) |
| `isValidIconsetPath` | Main thread permitted (single indexed SQL; sub-ms) |
| `invalidateCaches` | Main thread (broadcast callback) |

Callers (the view layer) own the executor introduced in [R10](../research.md#r10--off-main-thread-discipline) and dispatch back to the main thread via `View.post`.

## Test contract

Unit tests (JVM, no Android) MUST cover:

1. `listIconsets` returns the alphabetical order regardless of insertion order in the underlying DB.
2. `listIcons` skips invalid rows.
3. `loadBitmap` returns null on a row with `null` bitmap blob; returns Bitmap on a valid blob.
4. `resolveSelection` returns null for a malformed path, for a path whose iconset doesn't exist, and for a path whose icon doesn't exist; returns populated `IconSelection` for a fully-valid path.
5. `isValidIconsetPath` matches `resolveSelection() != null` for the same input.
6. Every public method survives an injected SDK exception (returns safe default; logs at WARN).

The SDK seam is mocked via constructor-injected `IconDatabaseFacade` interface so tests can run on the JVM without ATAK.
