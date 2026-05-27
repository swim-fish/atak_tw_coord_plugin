<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan
at `specs/005-multi-county-zip-import/plan.md` along with its companion
docs (`research.md`, `data-model.md`, `contracts/*.md`, `quickstart.md`).

Active feature: **005-multi-county-zip-import** — extends the
just-shipped offline-address subsystem from a single active dataset
into N independently-updatable per-county datasets, and adds ZIP
bundle import on top of the bare-`.sqlite` flow. Aligned with the
companion generator's data-contract v2 §2 (per-county zip + flat
`places-*.sqlite` layout). User-facing surfaces extended:
- Tools → 離線地址 page lists every active county, each with its
  own Replace / Remove buttons; the page has a chained-picker UX
  (Q1) so operators can keep adding files / ZIPs to a batch session.
- Settings shows one status row per active county under the
  existing per-row toggle group; the fragment is scrollable when
  the county count exceeds the visible area (Q2).
- Mid-batch reentrancy: while an import is in flight the operator
  may keep adding picks, which enqueue onto the same single-thread
  executor (Q3).
- v1.0.5 → v1.0.6 auto-migrate of the single `active/places.sqlite`
  into the new `active/<county>/places.sqlite` layout runs once at
  plugin onCreate, atomic by design.

Builds on the shipped:
- **004-offline-address** (`specs/004-offline-address/`) — the
  single-active-dataset flow this feature lifts. ADR-0014 (recon)
  + ADR-0015 (implementation) record the SDK + on-device pivots
  feature 005 carries forward (ImportFileBrowserDialog, ATAK native
  SQLite for R*Tree, AlertDialog Activity context).
- **001-tw-coord-display**, **002-tw-coord-goto**,
  **003-custom-marker-icon** — earlier features whose patterns
  (TwCoordWidget, DropDownReceiver) feature 005 still composes
  unchanged.

Sibling generator project: `atak-tw-address-generator` at
`C:\Users\hhhnr\source\tak\atak_vns_offline_routing\atak-tw-address-generator`.
Its [data-contract.md v2](file:///c/Users/hhhnr/source/tak/atak_vns_offline_routing/atak-tw-address-generator/docs/data-contract.md)
defines the per-county ZIP shape feature 005 consumes — no generator
changes required by this feature.

Plan-phase Phase 0 decisions live in
`specs/005-multi-county-zip-import/research.md` (R1–R10, including
the FR-017 fallback library choice in R5 and the R*Tree probe
algorithm). The Plan-phase code anchoring discipline (cite both
`javap -public` against `ATAK-CIV-5.7.0.3-SDK/main.jar` AND upstream
permalinks on `github.com/TAK-Product-Center/atak-civ`) is captured
in the user-level memory `feedback-plan-phase-code-anchoring.md`.
<!-- SPECKIT END -->
