<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan
at `specs/004-offline-address/plan.md` along with its companion docs
(`research.md`, `data-model.md`, `contracts/*.md`, `quickstart.md`).

Active feature: **004-offline-address** — adds an offline reverse-address
lookup feature. New Tools-menu entry **Offline Address** lets operators
side-load a `places-<county>.sqlite` file produced by the companion
generator `atak-tw-address-generator` (sibling repo at
`C:\Users\hhhnr\source\tak\atak_vns_offline_routing\atak-tw-address-generator`).
The plugin validates the file against the generator's schema
(`places`, `places_fts`, `metadata`), builds a `places_rtree` spatial
index at import time, and atomically activates the dataset. Three
independent Settings toggles (`pref_address_row_me`,
`pref_address_row_target`, `pref_address_row_map`) gate whether the
address row appears under each existing coordinate row in
`TwCoordWidget` (ME / TGT / MAP). All defaults are off — zero visual
change on upgrade until opt-in.

Builds on the shipped:
- **001-tw-coord-display** (`specs/001-tw-coord-display/`) — on-map
  readout widget (`TwCoordWidget`) this feature extends, forward
  converters, settings page.
- **002-tw-coord-goto** (`specs/002-tw-coord-goto/`) — coordinate
  input page; introduced the `DropDownReceiver` pattern this feature
  reuses for the Offline Address page.
- **003-custom-marker-icon** (`specs/003-custom-marker-icon/`) — 9th
  Custom Icon marker-mode on the GoTo page.

SDK reconnaissance and Phase 0 decisions are in
`specs/004-offline-address/research.md`; ADR-0014-reconnaissance
captures the same in the ADR canon. The Plan-phase code anchoring
discipline (cite both `javap -public` against
`ATAK-CIV-5.7.0.3-SDK/main.jar` AND upstream permalinks on
`github.com/TAK-Product-Center/atak-civ`) is captured in the user-level
memory `feedback-plan-phase-code-anchoring.md`.
<!-- SPECKIT END -->
