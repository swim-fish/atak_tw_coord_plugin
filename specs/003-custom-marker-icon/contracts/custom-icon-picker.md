# Contract: `CustomIconPickerDialog`

**Feature**: `003-custom-marker-icon` | **Phase 1**
**Package**: `com.atakmap.android.twcoord.gotopage`
**Type**: UI contract — modal `android.app.AlertDialog` wrapped by a small controller class.

The dialog is the two-step picker described in FR-003. It is owned by `TwCoordGotoView`, constructed lazily on first open, and re-used across opens within a session. State across opens is reset per [data-model §1.4](../data-model.md#14-pickerdialogstate-transient-not-persisted).

## Construction

```java
public CustomIconPickerDialog(
    Context themedContext,        // plugin context wrapped with the operator's UI-language override
    IconResolver iconResolver,    // injected; see contracts/icon-resolver.md
    ExecutorService worker,       // shared with the view layer (R10)
    Handler mainThreadHandler,    // for post-back-to-UI dispatch
    Listener listener);

public interface Listener {
    /** Operator picked an icon at step 2. */
    void onIconPicked(IconSelection selection);

    /** Operator dismissed the dialog via back/outside-tap. No selection change. */
    void onCancelled();
}
```

`themedContext` MUST be the same `LocaleOverride.contextFor(...)`-wrapped context the rest of `TwCoordGotoView` uses for string lookups, so dialog text honours the operator's UI-language override (FR-013).

## Public methods

```java
/**
 * Open the dialog. Idempotent — if the dialog is already showing, the call is a no-op.
 *
 * @param current null = open at step 1 (iconset list).
 *                non-null = open at step 2 of current.iconsetUid, with the current
 *                icon visually highlighted. If current.iconsetUid no longer resolves,
 *                falls back to step 1.
 */
public void show(IconSelection current);

/**
 * Force-dismiss the dialog if it's showing. Called on DropDownReceiver.onDropDownClose()
 * so the dialog never outlives its host page.
 */
public void dismissIfShowing();

/**
 * Called by the view layer when an ICONSET_ADDED or ICONSET_REMOVED broadcast fires.
 * The dialog invalidates its in-memory caches and refreshes whichever step is visible.
 * If the dialog is at step 2 and the showing iconset has just been removed, it
 * automatically transitions back to step 1.
 */
public void onIconsetsChanged();
```

## Behavioural contract

### Re-open rule (FR-003 + clarification Q1)

| Inputs to `show(current)` | Resulting step |
|---|---|
| `current == null` | Step 1 (iconset list) |
| `current != null && current.iconsetUid still resolves` | Step 2 of `current.iconsetUid` |
| `current != null && current.iconsetUid no longer resolves` | Step 1 (iconset list); listener NOT notified of the loss — that's the view layer's FR-009 path |

### Step 1 (iconset list)

- Title: `R.string.goto_custom_icon_dialog_title_iconsets`
- Body: `ListView` over `List<IconsetSummary>` from `IconResolver.listIconsets()` (worker-thread fetch + main-thread bind).
- Each row: iconset name + icon count (`"<name> (<n>)"`).
- Tap → transition to step 2 with that iconset's `uid`.
- Empty list → empty-state row `R.string.goto_custom_icon_empty_iconsets`. Dialog stays open; operator can cancel.
- Title bar contains no back button (step 1 is the root).

### Step 2 (icon list)

- Title: `R.string.goto_custom_icon_dialog_title_icons` with the iconset name appended.
- Body: `GridView` (3–4 columns at 64 dp thumbnails) over `List<IconRow>` from `IconResolver.listIcons(uid)`.
- Each cell: 48 dp thumbnail centred, filename underneath in 12 sp text, max 2 lines, ellipsised.
- Thumbnail load is per-cell via `IconResolver.loadBitmap(row.id)` on the worker. Cells that fail to decode (loadBitmap returns null) are filtered out of the adapter, not shown — FR-010a.
- Tap → `listener.onIconPicked(IconSelection.from(row, iconset))` then `dismiss()`.
- Empty grid (every row filtered out, or iconset is genuinely empty) → empty-state row `R.string.goto_custom_icon_empty_icons`. Operator can use the back button to return to step 1.
- Title bar contains a back button (`R.string.goto_custom_icon_back`) that returns to step 1.

### Cancel paths

- System back gesture → `dismiss() + listener.onCancelled()`.
- Outside tap (dialog's standard `setCanceledOnTouchOutside(true)`) → same.
- View layer calling `dismissIfShowing()` (e.g. on `onDropDownClose`) → dismiss WITHOUT notifying `listener.onCancelled()` (no operator action occurred).

### Threading

- `show(...)` / `dismissIfShowing()` / `onIconsetsChanged()` MUST be called on the main thread.
- Every data fetch (`listIconsets`, `listIcons`, `loadBitmap`) MUST dispatch to the injected `worker` ExecutorService.
- Result binding MUST post back via `mainThreadHandler.post(...)`.

### Constitution VI

Every callback the dialog exposes to its host:

- `ListView.OnItemClickListener` (step 1)
- `GridView.OnItemClickListener` (step 2)
- Back-button `View.OnClickListener` (step 2)
- `DialogInterface.OnCancelListener`
- Adapter `BaseAdapter.getView` (called by the framework, can throw on bad rows)

MUST wrap the callback body in `try/catch (Throwable)`, log via `Log.w(TAG, ..., t)`, and return without re-throw.

Worker task bodies (the `Runnable`s posted to `worker.submit(...)`) MUST also be `try/catch (Throwable)`-wrapped. A worker death due to uncaught exception is a silent failure — wrapping makes the failure visible in logcat without crashing the host.

## Layout files (Phase 2 — Implementation)

Sketch for tasks.md:

- `res/layout/custom_icon_picker_dialog.xml` — outer container: title TextView + back-button ImageButton (gone at step 1) + content FrameLayout that swaps between `iconset_list` and `icon_grid`.
- `res/layout/custom_icon_picker_iconset_row.xml` — iconset list row.
- `res/layout/custom_icon_picker_icon_cell.xml` — icon grid cell.
- `res/layout/custom_icon_picker_empty_row.xml` — empty-state row shared by both steps.

All XML uses the plain `android` widget set (no AppCompat, no Material) per ADR-0009 D6.

## Test contract

JVM unit tests (against an injected mock `IconResolver`) cover:

1. `show(null)` opens at step 1.
2. `show(currentValid)` opens at step 2 with the icon highlighted.
3. `show(currentInvalid)` opens at step 1 (no notification to listener).
4. Step 1 → step 2 transition on item click.
5. Step 2 → step 1 transition on back button.
6. Icon pick fires `listener.onIconPicked(...)` with the correct `IconSelection`.
7. System back / outside tap fires `listener.onCancelled()` once.
8. `dismissIfShowing()` does NOT fire `listener.onCancelled()`.
9. `onIconsetsChanged()` while at step 2 of an iconset that has just been removed transitions back to step 1.
10. `onIconsetsChanged()` while at step 1 re-fetches and re-renders.
11. Worker task throws → dialog state remains consistent (empty-state shown, no crash).
12. **Corrupt-bitmap silent skip (FR-010a)**: given an iconset whose 5 `IconRow`s include 2 rows for which the mocked `IconResolver.loadBitmap(id)` returns `null`, the rendered grid adapter MUST report `getCount() == 3` and MUST NOT call `getView()` for the 2 skipped rows. No exception, no placeholder drawable, no toast.

Espresso instrumented tests cover items 4/5/6/7 plus an end-to-end "open page → pick CUSTOM_ICON → pick iconset → pick icon → Submit" flow.
