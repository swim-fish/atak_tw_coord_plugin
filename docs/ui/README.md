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
- `input-page.md` — historical design record for the retired TW Coord GoTo
  page. Current coordinate entry is documented in
  `native-taiwan-coordinate-entry.md`.
- `native-taiwan-coordinate-entry.md` — the current four-tab Taiwan pane
  registered in ATAK's shared coordinate-entry dialog, including Address,
  candidate selection, host controls, read-only state, and locale lifecycle.
- `offline-address-page.md` — the internal Offline Address manager reached
  through TW Coordinates (feature 004): State A (empty) / State B (active dataset), the
  Import / Replace / Remove flows, and the inline-error matrix; the
  feature-008 storage-dashboard redesign (usage bar/legend, ⋮ overflow,
  progress/error cards) and its localisation fix.
- `forward-search-page.md` — historical design record for the retired TW Addr
  Search page. Current forward lookup is the Address tab described in
  `native-taiwan-coordinate-entry.md`; dataset management remains in
  `offline-address-page.md`.

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
