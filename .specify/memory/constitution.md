<!--
SYNC IMPACT REPORT
==================
Version change: (uninitialised template) → 1.0.0
Rationale: Initial ratification of the project constitution. All placeholder
tokens replaced with concrete principles, sections, and governance rules.
Bump type: MAJOR (initial baseline; semantic versioning starts at 1.0.0 once
the document carries enforceable rules).

Modified principles:
- [PRINCIPLE_1_NAME] → I. Code Quality & Formatting Discipline (NON-NEGOTIABLE)
- [PRINCIPLE_2_NAME] → II. Test-First Development (TDD) (NON-NEGOTIABLE)
- [PRINCIPLE_3_NAME] → III. User Experience Consistency
- [PRINCIPLE_4_NAME] → IV. Performance Requirements
- [PRINCIPLE_5_NAME] → V. Documentation & Knowledge Preservation

Added sections:
- Development Workflow & Quality Gates (covers Subagent delegation,
  formatter execution, ADR updates after /speckit-analyze and
  /speckit-implement, docs/ui updates on UI change).
- Governance (amendment procedure, versioning policy, compliance review).

Removed sections: none (template placeholders replaced in-place).

Templates requiring updates:
- ✅ .specify/templates/plan-template.md — Constitution Check section will be
  populated against principles I–V on the next /speckit-plan invocation; no
  structural change required now.
- ✅ .specify/templates/spec-template.md — aligns with the principles
  (Success Criteria already requires measurable outcomes that map to
  Performance and UX consistency).
- ✅ .specify/templates/tasks-template.md — already permits TDD ordering
  (tests before implementation) and includes Polish/Docs phase compatible
  with ADR and docs/ui updates.
- ⚠ docs/adr/ — directory does not yet exist; will be created on first
  /speckit-analyze or /speckit-implement run that triggers an ADR entry.
- ⚠ docs/ui/ — directory does not yet exist; will be created on the first
  UI-affecting change.

Follow-up TODOs: none. Ratification date set to today.
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
- **ADR cadence**: every `/speckit-analyze` and `/speckit-implement` run
  produces or updates an ADR under `docs/adr/` (see Principle V). The
  workflow is incomplete until the ADR exists and is committed.
- **UI docs cadence**: any change touching the user interface produces or
  updates a corresponding file under `docs/ui/` (see Principle III).
- **Definition of Done**: a task is complete only when all of the following
  hold — code formatted, static analysis clean, tests written and passing,
  ADR / UI docs updated where applicable, and performance budgets
  respected.
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

**Version**: 1.0.0 | **Ratified**: 2026-05-16 | **Last Amended**: 2026-05-16
