<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan
at `specs/003-custom-marker-icon/plan.md` along with its companion docs
(`research.md`, `data-model.md`, `contracts/*.md`, `quickstart.md`).

Active feature: **003-custom-marker-icon** — adds a 9th "Custom Icon"
marker-mode option to the GoTo input page (shipped in feature 002),
backed by a two-step picker dialog that reads exclusively from ATAK's
existing `UserIconDatabase`. Marker placement reuses
`PlacePointTool.MarkerCreator.setIconPath(...)` so dropped markers are
indistinguishable from those placed via ATAK's own marker tools.

Builds on the shipped:
- **001-tw-coord-display** (`specs/001-tw-coord-display/`) — on-map
  readout widget, forward converters, settings page.
- **002-tw-coord-goto** (`specs/002-tw-coord-goto/`) — coordinate
  input page (Taipower / TWD97 / TWD67), Auto Fill, Recent list, the
  existing 8 marker-mode radios this feature extends.

SDK reconnaissance for this feature is recorded in
`docs/adr/0010-custom-marker-icon-picker.md`. The Plan-phase code
anchoring discipline (cite both `javap -public` against
`ATAK-CIV-5.7.0.3-SDK/main.jar` AND upstream permalinks on
`github.com/TAK-Product-Center/atak-civ`) is captured in the user-level
memory `feedback-plan-phase-code-anchoring.md`.
<!-- SPECKIT END -->
