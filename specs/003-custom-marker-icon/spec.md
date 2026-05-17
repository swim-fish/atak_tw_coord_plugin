# Feature Specification: Custom Marker Icon on the GoTo Page

**Feature Branch**: `003-custom-marker-icon`

**Created**: 2026-05-17

**Status**: Draft

**Input**: User description: "在 GoTo page 加一顆「Custom Icon」radio + 二段式 picker dialog"
("Add a Custom Icon radio plus a two-step picker dialog to the GoTo page.")

## Context

Feature 002 (`specs/002-tw-coord-goto/`) shipped the Taiwan-coordinate input ("GoTo") page with an 8-option marker-mode picker: **Move only**, **Waypoint**, **GoTo Pin**, **Point of Interest**, **Friendly**, **Hostile**, **Neutral**, **Unknown**. Each option places (or doesn't place) a marker at the resolved coordinate using a fixed CoT type; the on-map icon is whatever the host application derives from that type code.

Operators have asked for the ability to drop a **custom** icon at the resolved coordinate — for example, a responder-style pin, a wildfire incident glyph, or any icon their iconset library already contains — without giving up the existing marker modes. The host application already manages a library of installable icon collections (ships several out of the box, lets users self-load more), so the missing piece is a way to surface that library inside the GoTo page and to apply the operator's pick to the placed marker.

ADR-0010 records the pre-implementation reconnaissance: the host's icon library is exposed as a public read API, and the host's marker-placement builder accepts an "icon path" string at placement time. The plugin will consume those APIs; it will not bundle, ship, or maintain its own icon collection.

## Clarifications

### Session 2026-05-17

- Q: When the operator picks **Custom Icon** but has not yet chosen an iconset/icon (first time, or after a previously-picked icon's iconset was uninstalled), what should Submit do?
  → A: **Block Submit** until a valid icon is selected. The Submit button becomes disabled (matching the existing input-validation pattern that disables Submit on invalid coordinates), and the picker preview shows an "Pick an icon" placeholder.
- Q: When the operator's previously-picked icon's iconset has been removed (between sessions, or while the page is open), should the page restore the next-best icon from the same family, prompt the operator, or silently fall back?
  → A: **Silently fall back to Move only**, clear the persisted icon path, and surface a single-line empty-state hint on the picker preview ("Selected icon no longer installed. Pick again."). No modal, no toast — the operator notices via the picker preview when they next look.
- Q: Should the in-page picker preview show a small thumbnail of the currently-selected icon, or only a text label?
  → A: **Thumbnail + iconset-name label.** A ~32 dp thumbnail (square) and the iconset display name underneath. Matches the visual weight of the existing 8 marker-mode radios so the new option doesn't read as second-class.
- Q: Should the operator's Custom-Icon selection persist across plugin restarts (different from the existing 8 modes, which reset to **Move only** on every restart)?
  → A: **Yes — persist both the marker-mode and the icon path.** Restarting ATAK with **Custom Icon + responder/fire_truck.png** selected re-opens the page with the same selection pre-filled. Rationale: the operator who curates a specific icon does not want to repeat the picker every session; the "Move only is the safe default" property is preserved because Move only is still the install-time default until the operator first changes it.
- Q: Is the picker scoped to icons in the iconsets the operator already has installed, or does the plugin add anything of its own?
  → A: **Operator-installed only.** The plugin contributes zero image assets. Every icon the picker offers comes from the host's existing icon-library read APIs. Iconsets the operator installs/removes through the host's own iconset manager are automatically reflected on the next picker open.
- Q: After the operator has picked an icon at least once, when they tap the picker preview to change it, where should the picker dialog open — step 1 (iconset list), step 2 of the iconset containing the current selection, or step 2 of the most-recently-viewed iconset?
  → A: **Step 2 of the iconset containing the current selection.** Re-opening lands on icons of the iconset the operator's current pick belongs to (the dominant "tweak my current pin" workflow). A back affordance on the dialog returns to step 1 (iconset list) when the operator wants to pick from a different iconset. If the current selection's iconset no longer exists (FR-009 fallback already triggered), the dialog opens at step 1.
- Q: When the operator picks an icon at picker step 2, does the act of picking auto-fire Submit (immediate marker placement) or only enable Submit?
  → A: **Only enable Submit.** Picking closes the picker, updates the preview thumbnail, and enables Submit; the operator must then tap Submit separately to commit. Matches feature 002's contract that **Submit is the only mutating action** (pan + persist + recent-list append + marker placement all happen there, and nowhere else). Picking an icon is reversible (switch to another icon, switch to a different marker mode, switch to Move only) up until Submit is tapped; this preserves the operator's ability to recover from a mis-tap.
- Q: If an iconset contains a row whose bitmap fails to decode (corrupt asset, partial install), how should the picker handle that row?
  → A: **Skip silently.** Rows that fail bitmap decode are filtered out of the icon grid at step 2 and never shown to the operator. The operator only sees icons that will render correctly at marker-placement time. Each skip is logged at WARN with the iconset UID and icon name for diagnostics, but no toast / dialog / placeholder is shown — corrupt rows are a host-DB integrity problem, not the operator's problem.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Pick a custom icon and drop it at the resolved coordinate (Priority: P1)

A field operator on the GoTo page has resolved a Taipower / TWD97 / TWD67 coordinate. They want to mark it with a specific iconography their team uses (e.g. a fire-truck icon for an incident, a numbered pin from their custom iconset) rather than one of the eight built-in marker types. They tap the new **Custom Icon** radio, see the picker open, choose an iconset, choose an icon within it, dismiss the picker, see a thumbnail preview confirming their pick, and tap Submit. The map pans to the resolved coordinate and drops a marker showing exactly the icon they chose.

**Why this priority**: This is the entire feature in one sentence — the request was "let me drop a custom icon at the GoTo destination". Shipping just this story already delivers all the user value; everything else (persistence, fallback handling, validation behaviour) refines the same flow.

**Independent Test**: With the plugin installed and at least one iconset present in the host (out-of-the-box state already satisfies this), open the GoTo page, enter a valid Taiwan coordinate, tap **Custom Icon**, pick any iconset, pick any icon, tap Submit. A marker MUST appear at the resolved coordinate showing the picked icon. Long-pressing the marker MUST open the host's standard marker radial menu (i.e. the marker behaves as a normal user-placed marker, not as a plugin-specific artefact).

**Acceptance Scenarios**:

1. **Given** the GoTo page is open with a valid coordinate entered and the host has at least one iconset installed, **When** the operator taps **Custom Icon**, **Then** the marker-mode area updates to show the **Custom Icon** option as selected and a picker preview area appears.
2. **Given** **Custom Icon** is selected and no icon has yet been picked, **When** the operator taps the picker preview area, **Then** a picker dialog opens showing a list of available iconsets.
3. **Given** the picker dialog is open at the iconset list, **When** the operator selects one iconset, **Then** the dialog transitions to show every icon contained in that iconset.
4. **Given** the picker dialog is showing icons within a chosen iconset, **When** the operator taps one icon, **Then** the dialog closes, the picker preview shows a thumbnail of the chosen icon plus the iconset name, and Submit becomes enabled (assuming the coordinate is also valid).
5. **Given** **Custom Icon** is selected with a valid icon picked and a valid coordinate entered, **When** the operator taps Submit, **Then** the map pans to the resolved coordinate and a marker showing the picked icon is dropped at that point.
6. **Given** the operator has dropped a custom-icon marker, **When** they long-press it, **Then** the host's standard marker radial menu opens with all normal options (edit, delete, route, etc.) — the marker behaves identically to one placed via the host's own marker tools.

---

### User Story 2 — Picker preview blocks Submit until a valid icon is selected (Priority: P1)

The operator picks **Custom Icon** but has not yet chosen any iconset/icon. The Submit button stays disabled and the picker preview prompts them to pick an icon. This prevents Submit from silently falling back to a default and matches the page's existing "Submit is disabled on invalid input" behaviour from feature 002.

**Why this priority**: Without this gate, picking **Custom Icon** and tapping Submit by accident would have undefined behaviour. The validation gate is part of the P1 flow's correctness, not a P2 polish item, so it ships together with US1.

**Independent Test**: Open the GoTo page, enter a valid coordinate (Submit becomes enabled with **Move only** selected). Switch to **Custom Icon**. Submit MUST become disabled and the picker preview MUST show a "Pick an icon" empty-state message. Pick an icon. Submit MUST become enabled.

**Acceptance Scenarios**:

1. **Given** a valid coordinate is entered and **Move only** is selected, **When** the operator switches to **Custom Icon** without a previously-persisted icon, **Then** the Submit button becomes disabled and the picker preview shows a "Pick an icon" empty-state.
2. **Given** **Custom Icon** is selected with no icon picked, **When** the operator dismisses the picker dialog without picking, **Then** Submit remains disabled and the picker preview continues to show the empty-state.
3. **Given** **Custom Icon** is selected with an icon picked, **When** the operator switches the coordinate to an invalid value, **Then** Submit becomes disabled (same rule as for the other marker modes — coordinate validity dominates).

---

### User Story 3 — Selection persists across plugin restarts (Priority: P2)

The operator has curated a specific iconset+icon pair they use repeatedly (e.g. their team's incident pin). They close ATAK at end-of-shift, re-open it next shift, open the GoTo page — and find **Custom Icon** already selected with the same icon pre-loaded. They can submit immediately without re-navigating the picker.

**Why this priority**: A real productivity win for repeat users, but the feature is usable without it (US1 alone delivers the value). P2 because shipping it later is fine; shipping never is not.

**Independent Test**: Open the page, pick **Custom Icon** + any specific iconset/icon. Force-stop ATAK. Re-open ATAK, open the GoTo page. The marker-mode area MUST show **Custom Icon** still selected, the picker preview MUST show the same thumbnail + iconset name, and Submit MUST be enabled (assuming a valid persisted coordinate from feature 002's existing persistence is also present).

**Acceptance Scenarios**:

1. **Given** **Custom Icon** with a specific icon is currently selected, **When** the page closes (drop-down dismissed) and re-opens, **Then** the same marker-mode and icon are restored.
2. **Given** **Custom Icon** with a specific icon was last selected before plugin shutdown, **When** ATAK is restarted and the GoTo page is opened, **Then** the same marker-mode and icon are restored.
3. **Given** the operator has never picked **Custom Icon**, **When** the GoTo page opens for the first time, **Then** the marker-mode defaults to **Move only** (matching feature 002's install-time default).

---

### User Story 4 — Graceful fallback when a persisted icon's iconset is removed (Priority: P3)

Between sessions, the operator removes (or upgrades-and-renames) the iconset their persisted Custom-Icon pick belonged to. On next page open, the plugin notices the picked icon no longer resolves, silently falls back to **Move only**, clears the stale persisted icon path, and surfaces a single-line hint on the picker preview ("Selected icon no longer installed. Pick again."). No modal, no toast — the operator notices when they next look at the picker.

**Why this priority**: A correctness/robustness story. Useful but rare; the P1+P2 stories deliver the everyday value.

**Independent Test**: Pick **Custom Icon** + any specific icon, dismiss the page. Outside the plugin, remove the iconset that contains that icon via the host's iconset manager. Re-open the GoTo page. Marker-mode MUST be **Move only** (not **Custom Icon**), the persisted icon-path preference MUST be cleared, and if the operator switches to **Custom Icon**, the picker preview MUST show the "Selected icon no longer installed. Pick again." hint exactly once (not on every subsequent open).

**Acceptance Scenarios**:

1. **Given** a persisted Custom-Icon selection whose iconset no longer exists, **When** the GoTo page opens, **Then** the marker-mode displays **Move only** and the persisted icon-path preference is cleared.
2. **Given** the above fallback has occurred, **When** the operator taps **Custom Icon** for the first time after the fallback, **Then** the picker preview shows the "Selected icon no longer installed. Pick again." hint.
3. **Given** the operator has picked a new icon after the fallback, **When** they switch to a different marker-mode and back to **Custom Icon**, **Then** the hint is no longer shown (it was a one-shot notification about the lost selection, not a persistent error).

---

### Edge Cases

- **No iconsets installed**: The host ships ~5 iconsets and a seed database out of the box, so this is unlikely in practice. If it does happen, the picker dialog's iconset-list step MUST show an empty-state ("No iconsets installed. Add iconsets via the host's iconset manager.") instead of a blank list. Submit remains disabled in this case.
- **Iconset with zero icons**: After picking an iconset that turns out to be empty, the picker's second step MUST show an empty-state row and let the operator back out to the iconset list without picking.
- **Iconset with corrupt-bitmap rows**: Rows whose bitmap fails to decode are silently filtered out (FR-010a). If every row in the iconset fails to decode, step 2 collapses to the same empty-state as for a genuinely empty iconset, with no extra messaging — the operator's recovery action is identical (back out, pick a different iconset).
- **Very large iconsets**: Some operator-installed iconsets contain hundreds of icons. The icon-list step MUST scroll smoothly and MUST NOT block the UI thread while loading thumbnails.
- **Iconset with deeply nested groups**: The host's model allows iconsets to organise icons into named groups. The picker MUST flatten the group dimension at the icon-list step — either show all icons in one scrollable grid regardless of group, or show a group sub-header — so the operator never has to dig through more than two levels (iconset → icon).
- **Operator dismisses picker via system back gesture or tapping outside**: Treated as cancel. The picker preview reverts to whatever state it was in before the dialog opened (e.g. still empty-state if nothing was picked yet, still showing the previous selection if one was already in place).
- **Switching marker-mode away from Custom Icon and back**: The previously-picked icon MUST remain associated with the **Custom Icon** mode. Switching to **Friendly** then back to **Custom Icon** MUST NOT clear the picker preview.
- **Submit fails to place the marker** (host SDK error): Per Constitution Principle VI (host-process isolation), the failure MUST be logged but MUST NOT crash the plugin or cancel the camera pan / persistence / toast / page close. The operator sees the pan happen and the toast, but no marker — same recovery posture as feature 002's existing marker-placement path.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The GoTo page MUST display **Custom Icon** as a ninth marker-mode option positioned alongside the eight existing modes (Move only, Waypoint, GoTo Pin, Point of Interest, Friendly, Hostile, Neutral, Unknown). The visual weight (thumbnail size, padding, selected-state styling) of the new option MUST match the existing eight.
- **FR-002**: Selecting **Custom Icon** MUST reveal a picker preview area on the page. The preview MUST show one of three states: (a) a "Pick an icon" empty-state with a tappable affordance to open the picker, (b) a "Selected icon no longer installed. Pick again." one-shot hint after a fallback, or (c) a thumbnail of the currently-selected icon plus the iconset name underneath.
- **FR-003**: Tapping the picker preview MUST open a picker dialog. The dialog MUST present a two-step flow: step 1 lists every iconset available from the host's icon library; step 2 (after the operator picks an iconset) lists every icon in that iconset. On re-open with an icon already selected, the dialog MUST open at step 2 of the iconset containing the current selection (with a back affordance returning to step 1); on re-open with no current selection, or with a current selection whose iconset has since been removed (FR-009), the dialog MUST open at step 1.
- **FR-004**: The dialog MUST source iconsets and icons exclusively from the host's existing icon-library read APIs. The plugin MUST NOT bundle, ship, or maintain its own iconset.
- **FR-005**: Picking an icon at step 2 MUST close the dialog, update the picker preview to show the chosen icon's thumbnail and iconset name, persist the operator's selection, and enable Submit (assuming the coordinate is also valid). Picking MUST NOT auto-fire Submit — marker placement happens only when the operator explicitly taps Submit.
- **FR-006**: When **Custom Icon** is selected but no valid icon is picked, Submit MUST be disabled. When a valid icon is picked AND the coordinate is also valid, Submit MUST be enabled.
- **FR-007**: Submitting with **Custom Icon** selected MUST drop a marker at the resolved coordinate using the host's standard marker-placement API such that the dropped marker (a) displays the operator's picked icon and (b) behaves identically to a marker the operator placed via the host's own marker tools (long-press radial menu, edit, delete, route-add, etc.).
- **FR-008**: The marker-mode selection AND the picked icon's identifier MUST persist across page closes and plugin restarts. On page open, the persisted values MUST be restored.
- **FR-009**: If the persisted icon identifier no longer resolves to a real icon (because the iconset was removed or renamed), the page MUST silently fall back to **Move only**, clear the stale persisted identifier, and queue a one-shot "Selected icon no longer installed. Pick again." hint to surface on the picker preview the next time the operator switches to **Custom Icon**. No modal dialog, no toast.
- **FR-010**: The picker dialog MUST handle empty states gracefully — an empty iconset list and an empty icon list within an iconset MUST each show an explanatory empty-state row, not a blank screen.
- **FR-010a**: Icons whose bitmap fails to decode MUST be silently filtered out of the step-2 grid (not shown, not selectable, no placeholder). Each skip MUST be logged at WARN with the iconset UID and icon name; no operator-visible notification is surfaced. If filtering produces a fully-empty grid, the empty-state row from FR-010 applies.
- **FR-011**: Marker placement failures (host SDK exceptions during the place-point call) MUST be caught and logged, MUST NOT crash the plugin or the host process, and MUST NOT abort the camera-pan / persistence / confirmation-toast / page-close sequence that runs alongside marker placement.
- **FR-012**: The CoT type assigned to the dropped marker when **Custom Icon** is the selected mode MUST be one that the host treats as a generic user-placed pin (so the marker carries no spurious affiliation semantics like "friendly ground unit" simply because the operator picked a non-MIL-STD-2525 icon).
- **FR-013**: All visible strings introduced by this feature (Custom Icon label, picker dialog title, empty-state messages, fallback hint) MUST be available in every locale the plugin already ships (English, Traditional Chinese, Japanese) and MUST honour the plugin's existing UI-language override (i.e. read through the same locale-override pathway as the other GoTo strings).
- **FR-014**: The Recent entries list (feature 002 US4) MUST be unaffected by this feature — it tracks coordinate inputs, not marker-mode selections, and that contract is preserved unchanged.

### Key Entities *(include if feature involves data)*

- **Iconset reference**: The operator's selected iconset, identified by the iconset's host-assigned unique identifier (not by name, since names can change across versions).
- **Icon path**: A string identifier that uniquely names one icon within an iconset, in the canonical form the host's marker-placement API accepts. Carries the iconset reference, the group (if any), and the icon's file name.
- **Marker-mode preference**: Which of the nine marker modes is currently selected on the GoTo page (Move only, Waypoint, GoTo Pin, Point of Interest, Friendly, Hostile, Neutral, Unknown, **Custom Icon**). Persisted across sessions.
- **Persisted icon path**: The icon path the operator most recently picked while in **Custom Icon** mode. Persisted across sessions. Cleared on graceful fallback (FR-009).
- **Picker preview state**: A derived UI state — one of {empty, fallback-hint, populated-with-thumbnail} — that drives what the picker preview area renders. Not persisted; recomputed on every page open.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An operator can go from "GoTo page open with valid coordinate" to "marker dropped at coordinate showing a chosen custom icon" in no more than **three taps** beyond what feature 002 requires for Submit (one tap on Custom Icon, one tap to open the picker preview, one tap on an iconset, one tap on an icon — totalling four extra taps including the iconset step; we target three for the steady-state where the iconset choice can be a single tap).
- **SC-002**: Opening the picker dialog (from tapping the preview) takes no more than **300 ms** at the median on the reference device (the same target feature 002 set for its Submit path).
- **SC-003**: Loading the icon list for an iconset of up to **500 icons** completes in no more than **500 ms** at the median on the reference device, with thumbnails rendering progressively (no blank grid for more than 100 ms).
- **SC-004**: After picking an icon, Submit becomes enabled within **one UI frame** (≤ 16 ms) — operators must not perceive a lag between picking and being able to submit.
- **SC-005**: An operator who has previously picked an icon, restarted the plugin, and re-opens the page sees the same picker preview state in **0 additional taps** beyond opening the page. (Persistence works on first try.)
- **SC-006**: Across **100 consecutive Submit operations** with **Custom Icon** selected on the reference device, the dropped marker MUST show the operator's chosen icon in 100 % of cases (zero "default-icon" fallbacks except where the host's icon-library has lost the icon mid-session).
- **SC-007**: After the host's iconset manager removes an iconset that the operator's persisted pick belonged to, opening the GoTo page MUST recover to **Move only** without prompting, without crashing, and without leaving stale state in the persistence layer — verified by re-opening the page a second time and observing no residual hint, no residual selection.

## Assumptions

- The host application (ATAK-CIV) is configured normally — its built-in icon library is initialised at startup and at least one iconset is present. The host's out-of-the-box bundle satisfies this (five iconsets plus a seed-database entry), so this is the default state on any first install.
- Operators who want a specific custom iconset (e.g. their team's branded pins) install it through the host's existing iconset manager. The plugin does not provide a separate iconset installation path.
- The CoT type chosen for **Custom Icon** placements (FR-012) does not need to carry team / affiliation semantics. Operators who want affiliation-coded markers continue to use the existing eight marker modes; **Custom Icon** is for cases where the operator's intent is "drop this iconography" rather than "drop a friendly/hostile/neutral".
- The picker dialog is a transient overlay; it does not need to remain open across page closes. State persistence (FR-008) is per-selection, not per-dialog-position.
- Marker rotation, color, and styling beyond the icon itself are out of scope for this feature. Custom-Icon markers inherit whatever default rotation/color the host assigns to user-placed markers; the operator can adjust those via the host's standard marker-edit flow after placement.
- This feature inherits feature 002's offline-capable, zero-telemetry posture. No network access is added; the picker reads only from local host APIs.
- This feature inherits feature 001's accuracy posture for coordinate resolution — the coordinate-to-WGS84 pipeline is unchanged; only the marker-placement step gains a new icon-source.
