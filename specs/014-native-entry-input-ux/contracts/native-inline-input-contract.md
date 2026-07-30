# Contract: Native Inline Input

## Purpose

Keyboard-editable Taiwan fields request an inline software-keyboard
presentation that keeps ATAK's current Go To dialog visible. The plugin
controls only its editors and local focus; ATAK retains dialog and confirmation
ownership.

## Editor configuration

Every editable `EditText` below is:

- single-line;
- configured with `IME_FLAG_NO_FULLSCREEN`;
- configured with `IME_FLAG_NO_EXTRACT_UI`;
- assigned an explicit logical action and plugin-owned focus target where
  applicable.

| Editor | Action | Force ASCII | Maximum length |
|--------|--------|-------------|----------------|
| Taipower raw | Done | Yes | None beyond existing parser/input safety |
| Taipower region | Next | Yes | 1 |
| Taipower subregion digits | Next | Not applicable (numeric) | 4 |
| Taipower subgrid letters | Next | Yes | 2 |
| Taipower precision digits | Done | Not applicable (numeric) | 4 |
| TWD97 easting | Next | No | Existing validation |
| TWD97 northing | Done | No | Existing validation |
| TWD67 easting | Next | No | Existing validation |
| TWD67 northing | Done | No | Existing validation |
| Address full | Search | No | Existing validation |
| Address road/locality | Next | No | Existing validation |
| Address tail | Search | No | Existing validation |

County and district selectors remain non-focusable and do not start an IME.

`IME_FLAG_FORCE_ASCII` is prohibited on Address editors.

## Focus and action behavior

1. Next moves to the next visible, enabled, plugin-owned editor in the active
   layout.
2. Done/Search is consumed locally, clears editor focus, and requests keyboard
   dismissal without invoking ATAK confirmation.
3. `IME_NULL` plus physical Enter maps to one equivalent logical action. Down
   and up phases do not cause duplicate behavior.
4. Fixed split Taipower groups auto-advance when accepted content first reaches
   lengths 1, 4, and 2.
5. The final Taipower group remains focused after two digits and accepts two
   more digits.
6. Programmatic render, activation, Auto Fill, Clear, locale replacement, and
   mode projection never auto-advance or steal focus.
7. A Taipower mode switch transfers focus only when:
   - a Taipower editor owned focus before the switch;
   - the projection succeeds;
   - the target editor is visible, enabled, and editable;
   - the lifecycle generation is still current.
8. All focus targets use plugin resource IDs. No ATAK internal elevation,
   button, or dialog resource ID is referenced.

## Host ownership

An editor action must never:

- call `CoordinateEntryPane.getGeoPointMetaData()` as submission;
- click or imitate ATAK's positive/confirm button;
- mutate elevation or marker state;
- invoke a second Clear/Auto Fill/Copy action;
- move the map;
- emit a duplicate coordinate-change callback.

ATAK calls the pane contract when it needs validation or confirmation.

## Read-only behavior

- All text editors are disabled/non-editable.
- Tapping displayed content starts no editable keyboard session.
- Copy/display behavior owned by ATAK remains available.
- A non-mutating Taipower layout projection may change visible fields only as
  allowed by the Taipower entry contract.

## Lifecycle safety

- Watchers, editor-action listeners, focus listeners, and pending focus
  transfers check the pane's disposed flag and lifecycle generation.
- `dispose()` removes plugin listeners/watchers and leaves the view inert.
- A late IME/key/focus callback cannot restore a disposed editor, mutate a
  replacement pane, or notify ATAK.
- A locale replacement may restore the saved mode but does not expose the old
  pane's draft.

## Keyboard compatibility boundary

Android documents the no-fullscreen option as an IME request. Therefore:

- Automated tests prove the editor flags and plugin action behavior.
- A real supported default keyboard proves the Go To dialog stays visible.
- An unsupported third-party IME that ignores the request must not lose the
  draft, crash ATAK, or duplicate a host action.
- Public compatibility claims name the tested device, Android/ATAK build, IME
  package/version/subtype, orientation, and locale.

## Automated contract tests

1. Every editable field has the expected single-line/input/action/flag matrix.
2. Only Taipower alphanumeric fields have force-ASCII.
3. Split lengths are exactly 1/4/2/4.
4. Next follows only visible plugin editors.
5. Auto-advance occurs only at fixed groups 1-3.
6. Final length two remains editable to length four.
7. Done/Search and physical Enter are consumed once without host submission.
8. Render, mode switch without focus, activation, Auto Fill, and Clear do not
   steal focus or emit human changes.
9. Read-only fields do not start input.
10. Late/post-dispose editor callbacks are inert.

Robolectric success is not evidence of actual IME presentation.

## Device acceptance

On exact ATAK 5.5.x and ATAK 5.7.0.9:

- exercise every editable field in portrait and landscape;
- repeat focus 20 times per field;
- record default IME identity;
- verify Go To, active editor context, elevation, Auto Fill, Clear, Copy,
  marker, and confirmation remain reachable;
- verify both Taipower modes, auto-advance, manual Next, Done/Search, and
  two-to-four final-digit continuation;
- verify read-only Convert Coordinate starts no keyboard;
- switch mode, locale, and plugin lifecycle while focus is active;
- measure focus/keyboard feedback against the 500 ms p95 budget.
