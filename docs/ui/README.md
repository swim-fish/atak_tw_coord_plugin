# UI Documentation

This directory holds the design record for every user-facing surface in
the plugin. Per Constitution Principle III, any change touching the UI
MUST be accompanied by an update here in the same change-set — not in a
later polish pass.

## What goes here

- `readout-widget.md` — the on-map readout overlay (anchor, layout,
  colour palette, state variants OK / OUT_OF_RANGE / NO_FIX /
  NO_PERMISSION), with screenshots in each of the three UI languages.
- `settings-fragment.md` — the preference fragment surfaced under
  ATAK's Tool Preferences, with screenshots in each of the three UI
  languages.

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
