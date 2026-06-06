<!--
SYNC IMPACT REPORT
==================
Version change: 1.1.0 → 1.2.0
Rationale: Add a "Scrollable by default" rule to Principle III (User
Experience Consistency) in response to a real layout defect: the TW Offline
Addr page used a non-scrolling LinearLayout root with the Import button placed
below an unweighted wrap_content inner ScrollView, so a long county list could
push the Import button (and the boundary row) off the bottom edge with no way
to reach them. The rule makes tool pages scrollable by default and forbids
placing fixed actions below an unbounded inner scroller. Bump type: MINOR
(new normative rule added under an existing principle; no removals or
incompatible redefinitions).

Prior amendment (1.0.0 → 1.1.0): added Principle VI "Host-Process Isolation
(NON-NEGOTIABLE)" after a Resources.NotFoundException in a view-rendering path
crashed the whole ATAK-CIV process.

Modified principles:
- III. User Experience Consistency — new "Scrollable by default" bullet.

Added sections: none (bullet added to an existing principle).

Removed sections: none.

Templates requiring updates:
- ✅ .specify/templates/plan-template.md — Constitution Check should evaluate
  the scrollability rule for any feature that adds/edits a tool page layout;
  no structural template change required.
- ✅ .specify/templates/spec-template.md — no schema change.
- ✅ .specify/templates/tasks-template.md — no schema change; a layout
  "scrollability check" may be added to the Polish phase for UI features.
- ⚠ Existing layouts: only newly added/modified tool pages must comply;
  shipped pages are brought into compliance when next touched (the TW Offline
  Addr fix in v1.3.x is the first application).

Follow-up TODOs: none.
-->

# atak_tw_power_plugin Constitution

## Core Principles

### I. Code Quality & Formatting Discipline (NON-NEGOTIABLE)

Every code change MUST satisfy the following before it can be merged or
declared "done":

- Source is auto-formatted with the language-native formatter on every change
  (Dart sources via `dart format .`; other languages via their canonical
  formatter, e.g. `dart format`, `clang-format`, `prettier`). No commit may
  contain unformatted code.
- Static analysis (`flutter analyze` / `dart analyze`) MUST pass with zero
  errors and zero new warnings introduced by the change.
- Code MUST be self-explanatory; comments are reserved for non-obvious WHY
  (constraints, invariants, workarounds), never WHAT.
- Dead code, commented-out blocks, and TODOs without an owner or tracking
  reference MUST NOT be merged.

**Rationale**: Consistent formatting and analysis eliminate review noise,
make diffs reviewable, and prevent latent defects from drifting into the
codebase. The formatter is a contract — not a suggestion.

### II. Test-First Development (TDD) (NON-NEGOTIABLE)

Test-Driven Development is the default workflow for every change that
modifies behaviour:

- Tests MUST be authored before the production code that satisfies them.
- The Red → Green → Refactor cycle MUST be observable in commit history (or
  in a single commit that demonstrably contains both failing-test
  introduction and the implementation that makes them pass).
- Each public API, business rule, or bug fix MUST be covered by at least one
  automated test at the appropriate level (unit, widget, or integration).
- Integration tests are REQUIRED for: new external contracts, cross-module
  communication, persistence boundaries, and any platform-channel surface.
- Pure refactors (no behaviour change) MUST keep the existing test suite
  green without modification of test expectations.

**Rationale**: TDD locks behaviour into executable specifications, prevents
regressions, and forces a design that is testable from day one. Skipping
tests is technical debt with compounding interest.

### III. User Experience Consistency

Every user-facing change MUST preserve a coherent, predictable experience:

- Visual language (spacing, typography, color tokens, iconography) MUST
  follow the shared design system; ad-hoc styling is prohibited.
- Interaction patterns (navigation, gestures, feedback, error states,
  loading states, empty states) MUST mirror existing flows. New patterns
  require an explicit design decision recorded under `docs/ui/`.
- **Scrollable by default.** When adding or modifying a tool page / DropDown
  view, the page MUST be wrapped in a single outer `ScrollView` so no control
  can be clipped or pushed off-screen on short panes or small devices. A
  non-scrolling root (`LinearLayout` etc.) is permitted ONLY when the content
  is provably short and fixed — a small, bounded set of fixed-height widgets
  that cannot grow. Any variable-length content (per-item lists, optional
  cards / banners, localised text that may wrap) makes the page scrollable.
  Fixed actions (Submit, Import, etc.) MUST NOT be placed below an unweighted
  `wrap_content` inner scroller, where a long list eats their space and pushes
  them past the bottom edge. Avoid nested vertical scrollers — prefer one
  outer `ScrollView` with plain inner containers.
- Accessibility minimums MUST be met: semantic labels for interactive
  widgets, sufficient contrast, scalable text, and keyboard / screen-reader
  reachability where the platform supports it.
- Localisation: user-visible strings MUST be externalised and translation-
  ready; hard-coded English strings in production widgets are prohibited.
- Any UI change (new screen, modified widget, altered flow) MUST be
  accompanied by a corresponding update under `docs/ui/` describing the
  change, the rationale, and screenshots or wireframes where helpful.

**Rationale**: Consistency reduces cognitive load for users and prevents
the codebase from accumulating divergent UI dialects. Documenting UI in
`docs/ui/` keeps designers, reviewers, and downstream contributors aligned.

### IV. Performance Requirements

Performance is a first-class acceptance criterion, not an after-the-fact
optimisation:

- UI MUST maintain 60 fps (≤ 16 ms frame budget) under representative load
  on the lowest supported target device; jank above this threshold is a
  defect.
- Cold start to first interactive frame MUST be ≤ 2 s on the reference
  device; warm start MUST be ≤ 1 s.
- Memory: steady-state footprint MUST stay within the budget recorded in the
  feature plan; regressions > 10 % require an explicit justification entry
  in `docs/adr/`.
- Network and platform-channel calls MUST be batched, debounced, or
  cancellable where appropriate; never perform blocking I/O on the UI
  isolate.
- Performance-critical paths MUST be measured (timeline, devtools, or
  benchmark harness) before optimisation; commits MUST cite the measurement
  that motivated the change.

**Rationale**: Power-user tooling lives or dies by responsiveness. Defining
budgets up front turns "feels slow" into a falsifiable claim.

### V. Documentation & Knowledge Preservation

The project's institutional memory MUST be captured in English and kept
current:

- All committed documentation (specs, ADRs, READMEs, code comments, commit
  messages, PR descriptions) MUST be written in English. Bilingual content
  is permitted only where English is also present and primary.
- An Architecture Decision Record (ADR) MUST be appended under
  `docs/adr/NNNN-title.md` after every successful `/speckit-analyze` and
  every `/speckit-implement` run that resulted in a non-trivial change.
  The ADR MUST capture: context, decision, alternatives considered,
  consequences, and links to the originating spec / plan / tasks.
- UI changes MUST additionally update `docs/ui/` (see Principle III).
- README, quickstart, and contributor-facing docs MUST be updated in the
  same change set that introduces breaking or surface-level changes — never
  in a follow-up.

**Rationale**: Decisions evaporate; documentation persists. ADRs anchored
to the spec-kit workflow give future contributors (and future you) a
traceable narrative of *why* the system looks the way it does.

### VI. Host-Process Isolation (NON-NEGOTIABLE)

The plugin runs hosted inside ATAK-CIV's process. Any uncaught exception
that escapes a plugin entry point crashes the entire ATAK application —
not just the plugin. Every plugin code path MUST be designed so a fault
within the plugin can NEVER take down the host.

Mandatory rules:

- **Wrap every plugin entry point.** Every callback the host calls into
  the plugin — `BroadcastReceiver.onReceive`, `MapEventDispatcher.MapEventDispatchListener.onMapEvent`,
  `OnClickListener.onClick`, `OnPointChangedListener.onPointChanged`,
  preference change listeners, `AbstractMapComponent.onCreate` /
  `onDestroyImpl`, `DropDownReceiver.onReceive` / `onDropDownClose` /
  `onDropDownSizeChanged` and every other host→plugin boundary — MUST
  catch `Throwable` (or at minimum `Exception`) at the entry-point
  body's outer scope, log the exception via `com.atakmap.coremap.log.Log.w`,
  and return without re-throwing. Native crashes are out of scope for this
  rule but everything reachable from a JVM stack frame is in.
- **Listener bodies short-circuit on listener-side faults.** When the
  plugin fans an event out to its own listeners (e.g. `RecentEntryStore`
  → `RecentEntry.Listener`), each listener invocation MUST be in its own
  `try`/`catch` so a single buggy listener cannot abort the dispatch loop
  or propagate up to the host. This rule is the reason
  `RecentEntryStore.persist` already wraps each listener call;
  *every* listener fan-out the plugin owns MUST do the same.
- **View rendering paths degrade gracefully.** Code that constructs or
  binds Android `View` instances MUST tolerate `Resources.NotFoundException`,
  `NullPointerException` from `findViewById`, and inflate failures by
  falling back to a "minimum viable" rendering (empty list, hidden
  section, plain TextView) rather than letting the exception bubble.
- **No attribute-id vs resource-id confusion.** APIs that consume a
  drawable resource ID (`setBackgroundResource`, `setImageResource`,
  `getDrawable`, etc.) MUST NOT be called with an `android.R.attr.*`
  attribute id directly; attribute ids MUST be resolved via
  `Context.getTheme().resolveAttribute(...)` first, OR the call site
  MUST avoid the attribute altogether and use a concrete drawable.
- **Resource lookups are nullable.** `findViewById`, `getDrawable`,
  `getString` and friends MAY return null in deferred-inflation or
  themed-context corner cases; code MUST null-check before dereferencing
  rather than assume presence.
- **External SDK calls are best-effort.** Calls into ATAK SDK classes
  (`mapView.getRenderer3()`, `CameraController.panTo`, `Marker.setPoint`,
  `AtakBroadcast.sendBroadcast`, etc.) MUST be in a `try`/`catch` because
  the SDK is a moving target and version-skew faults must not propagate.
- **Defensive validation at boundaries.** Inputs that originate from
  outside the plugin (Intent extras, persisted preferences, JSON
  payloads, file contents) MUST be validated before use. Corrupt input
  MUST recover to a safe default (e.g. empty list, default unit)
  rather than throw.
- **`AtomicBoolean` guards for re-entrant click handlers.** Submit /
  Auto Fill / Recent-row tap handlers MUST be guarded against rapid
  double-tap re-entry (compare-and-set pattern) so a click in flight
  cannot fire a second copy of the same code path.

**Rationale**: A plugin that crashes ATAK destroys operator trust faster
than any feature gain restores it. This principle was added in response
to a real 2026-05-16 incident where a single
`Resources.NotFoundException` from a misused `android.R.attr.*` in a
view-rendering path killed ATAK-CIV on a Galaxy Tab S10+ — caught only
because the user was running with logcat open. The cost of a `try`/`catch`
around every entry point is one line per callback; the cost of an ATAK
crash in the field is a mission failure. Always wrap.

**Definition of Done extension**: a task is incomplete if it adds a new
plugin entry point (BroadcastReceiver action, listener, MapEvent
subscriber, view binding, etc.) without the corresponding outer `Throwable`
guard. Code review and `/speckit-analyze` MUST flag any unguarded entry
point as a CRITICAL finding.

## Development Workflow & Quality Gates

The following workflow rules apply to every contributor (human or agent)
working in this repository:

- **Subagent delegation**: long-running searches, multi-file audits,
  cross-cutting consistency checks, and other parallelisable read-heavy
  tasks MUST be delegated to subagents (e.g. `Explore`, `general-purpose`,
  `Plan`) so the main conversation context is preserved. The orchestrating
  agent reads only the subagent's summary, not its intermediate output.
- **Formatter execution**: `dart format .` (or the language-equivalent
  formatter) MUST be run after every code modification, before staging.
  Pre-commit hooks or CI MUST enforce this; manual reliance is not
  sufficient.
- **Crash isolation (Principle VI)**: every code change that adds or
  modifies a plugin entry point MUST include the outer `Throwable` guard.
  Code review MUST refuse PRs that add unguarded host-callable callbacks.
- **ADR cadence**: every `/speckit-analyze` and `/speckit-implement` run
  produces or updates an ADR under `docs/adr/` (see Principle V). The
  workflow is incomplete until the ADR exists and is committed.
- **UI docs cadence**: any change touching the user interface produces or
  updates a corresponding file under `docs/ui/` (see Principle III).
- **Definition of Done**: a task is complete only when all of the following
  hold — code formatted, static analysis clean, tests written and passing,
  ADR / UI docs updated where applicable, performance budgets respected,
  and every new plugin entry point wrapped per Principle VI.
- **Gate sequencing**: `/speckit-plan` runs a Constitution Check before
  Phase 0 and again after Phase 1; violations MUST either be eliminated or
  justified in the plan's Complexity Tracking table with a Simpler
  Alternative Rejected reason.

## Governance

This constitution supersedes ad-hoc conventions, individual preferences,
and undocumented tribal knowledge. Where this document and any other
guidance conflict, this document wins until amended.

- **Amendment procedure**: amendments are proposed by editing
  `.specify/memory/constitution.md` via `/speckit-constitution`. Each
  amendment MUST include a Sync Impact Report at the top of the file, a
  version bump, and updated dependent templates / docs in the same change
  set.
- **Versioning policy** (semantic):
  - MAJOR — backward-incompatible governance changes, principle removals,
    or redefinitions that alter the contract with contributors.
  - MINOR — new principle or materially expanded guidance added.
  - PATCH — wording clarifications, typo fixes, non-semantic refinements.
- **Compliance review**: every pull request MUST self-attest constitution
  compliance in its description (a one-line checklist is sufficient).
  Reviewers MUST verify the attestation against the diff; unjustified
  violations block merge.
- **Runtime guidance**: agent-facing runtime instructions live in
  `CLAUDE.md` and the per-feature plan; they MUST NOT contradict this
  constitution. When they drift, the constitution is the source of truth.

**Version**: 1.2.0 | **Ratified**: 2026-05-16 | **Last Amended**: 2026-06-06
