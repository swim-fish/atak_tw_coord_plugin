# UI Documentation

This directory holds the design record for every user-facing surface in
the plugin. Per Constitution Principle III, any change touching the UI
MUST be accompanied by an update here in the same change-set — not in a
later polish pass.

## What goes here

- `readout-widget.md` — the on-map readout overlay: three coordinate
  rows (MAP / ME / TGT) plus an optional address row per anchor
  (feature 004 / US2), with state variants and screenshots in each of
  the three UI languages.
- `settings-fragment.md` — the preference fragment surfaced under
  ATAK's Tool Preferences. Covers the base coordinate-unit + UI-
  language rows (feature 001) plus the feature 004 "Offline Address"
  section (3 per-row toggles + dataset-status row).
- `input-page.md` — the TW Coord GoTo input page (feature 002 + the
  feature 003 ATAK-picker delegation button + the feature 010 / v1.3.2
  compact-stacked redesign).
- `offline-address-page.md` — the Offline Address `DropDownReceiver`
  (feature 004): State A (empty) / State B (active dataset), the
  Import / Replace / Remove flows, and the inline-error matrix; the
  feature-008 storage-dashboard redesign (usage bar/legend, ⋮ overflow,
  progress/error cards) and its localisation fix.
- `forward-search-page.md` — the TW Addr Search `DropDownReceiver`
  (feature 006): the county → 鄉鎮市區 → street → house-number funnel,
  the result-order toggle (feature 007), and the feature-008 redesign
  (segmented scope control + on-demand district / house-number dialogs,
  county-list missing-data ⚠, county-only chip).

## Contribution rules

- Each file MUST include at least one screenshot or wireframe when it
  documents a visual surface.
- When you rename, restructure, or remove a UI element, update the
  corresponding file in the same commit as the code change. Do not
  let the docs lag the code.
- All text in English (Constitution Principle V); screenshots may show
  the localised in-app text.
- Keep file size manageable: link to large mock-ups in a hosted
  location rather than committing multi-MB PNGs.
