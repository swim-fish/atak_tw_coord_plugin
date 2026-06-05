# Contract: Cross-Context Dialog & Menu Reliability (FR-017)

The user's explicit constraint ("must follow the SDK samples or you debug for a
long time") encoded as a verifiable reliability contract. Applies to every new
`AlertDialog` and `PopupMenu` added by this feature.

## C-DLG-1 Activity context for window-bearing UI
- Every `AlertDialog.Builder(...)` and `PopupMenu(...)` MUST be constructed with
  the host ATAK Activity context obtained from `getMapView().getContext()`
  (or an anchor view whose context is that Activity).
- Building with `pluginContext` is prohibited (it lacks a window token and
  raises `BadTokenException` at `show()`).

## C-DLG-2 Plugin context for resources
- All `getString(R.string.*)`, `getDrawable(R.drawable.*)`, and view inflation
  for dialog content MUST resolve against `pluginContext` (the localized plugin
  resources, ADR-0003).
- Resolving plugin `R.*` ids against the ATAK Activity context is prohibited
  (raises `Resources.NotFoundException`).

## C-DLG-3 Sample/shipped parity
- The construction MUST match the proven references:
  - shipped `OfflineAddressReceiver` (L722/737/796/815):
    `new AlertDialog.Builder(getMapView().getContext())` + `pluginContext.getString(...)`.
  - SDK `helloworld` `HelloWorldDropDownReceiver:2187`:
    `new AlertDialog.Builder(mapView.getContext())`.
  - SDK `meshtastic_atak`: `MapView.getMapView().getContext()` for all
    Activity-scoped surfaces.

## C-DLG-4 Reliable appearance (SC-007)
- Every new dialog/menu MUST appear on device on first invocation (0 silent
  failures in smoke testing). A dialog/menu raised from an invalid context MUST
  NOT crash the host (Constitution VI) — listeners are wrapped and the build is
  guarded.

## C-DLG-5 Localised across three locales (FR-018, SC-008)
- Every new operator-facing string used in a dialog/menu MUST exist in
  `values-zh-rTW` (primary), `values` (en base), and `values-ja`, with no
  missing-resource fallback.

## Verification
- Code review asserts each new `AlertDialog.Builder` / `PopupMenu` call site uses
  `getMapView().getContext()` and `pluginContext.getString(...)` (greppable).
- On-device smoke test (quickstart) opens each new dialog/menu once and confirms
  it appears and dismisses without crash in all three locales.
