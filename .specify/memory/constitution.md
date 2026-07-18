<!--
SYNC IMPACT REPORT
==================
Version change: 1.2.0 -> 2.1.0
Rationale: Replace inherited Flutter-centric and tool-runtime-specific rules
with enforceable Android/ATAK plugin governance; add explicit ATAK SDK and
geospatial correctness principles; clarify the four independent Android/ATAK
compatibility versions; and add a safe Git-hook policy. Bump type: MAJOR
because existing contributor obligations are materially redefined. Version
2.1.0 includes the compatible workflow guidance added during the same
unreleased amendment.

Modified principles:
- I. Code Quality & Formatting Discipline -> Code Quality & Build Discipline.
- II. Test-First Development -> Test-First Development & Verification.
- III. User Experience Consistency -> UX, Accessibility & Localisation.
- IV. Performance Requirements -> Performance & Offline Operation.
- V. Documentation & Knowledge Preservation -> Documentation & Decision
  Traceability.
- VI. Host-Process Isolation -> Host-Process Isolation with boundary-oriented
  exception handling and corrected Android resource rules.
- Development Workflow & Quality Gates: add optional post-plan checklist
  guidance, safe commit-hook rules, and an explicit implement/converge loop.

Added principles:
- VII. ATAK SDK Compatibility, including separate Android compile/minimum SDK
  and ATAK compile SDK/minimum runtime contracts.
- VIII. Geospatial Correctness & Provenance.

Removed or replaced rules:
- Removed mandatory subagent delegation from project governance.
- Replaced Flutter/Dart formatter and analysis commands with Android/Gradle
  gates used by this repository.
- Replaced the universal outer ScrollView mandate with one vertical scroll
  owner, allowing RecyclerView or another bounded scrolling container.
- Removed host cold/warm-start targets that the plugin cannot control.
- Removed mandatory ADR creation after every read-only speckit-analyze run.
- Replaced blanket Throwable swallowing, per-call SDK try/catch, and universal
  AtomicBoolean guards with boundary-scoped, failure-aware requirements.
- Replaced ambiguous "compile SDK" wording with explicit Android and ATAK
  version axes.
- Disabled optional auto-commit hooks; mandatory repository initialization and
  feature-branch creation hooks remain enabled.

Templates and guidance updated:
- [x] .specify/templates/plan-template.md
- [x] .specify/templates/spec-template.md
- [x] .specify/templates/tasks-template.md
- [x] AGENTS.md and CLAUDE.md
- [x] README.md
- [x] docs/adr/README.md
- [x] ADR-0022 filename aligned to docs/adr/NNNN-title.md
- [x] .specify/workflows/speckit/workflow.yml and workflow-registry.json
- [x] .specify/extensions.yml
- [x] Spec Kit Codex skills aligned to the installed PowerShell scripts
- [x] .specify/init-options.json and .specify/integration.json
- [x] .specify/scripts/powershell/check-prerequisites.ps1

Follow-up TODOs:
- Before any future `specify integration upgrade --force`, preserve and
  reapply the project-specific skill, script, and template overrides. The
  managed-files-modified integration warning is intentional.
-->

# atak_tw_power_plugin Constitution

## Core Principles

### I. Code Quality & Build Discipline (NON-NEGOTIABLE)

Every source or Android resource change MUST pass the repository's applicable
quality gates before it is merged or declared complete:

- Java and Android XML MUST follow the repository formatter configuration.
  Run `./gradlew :app:spotlessApply` after modifying formatted sources and
  `./gradlew :app:spotlessCheck` before completion.
- `./gradlew :app:lint` MUST complete without errors. New warnings MUST be
  fixed or explicitly justified in the feature plan or pull request.
- Behavioural code changes MUST pass `./gradlew :app:testCivDebugUnitTest`.
  Plugin packaging or resource changes MUST also pass
  `./gradlew :app:assembleCivDebug`.
- Comments MUST explain non-obvious constraints, invariants, provenance, or
  workarounds rather than narrate the code.
- Dead code, commented-out implementations, generated build artefacts, and
  TODOs without an owner or tracking reference MUST NOT be merged.
- Documentation-only changes MAY use proportionate validation, but
  `git diff --check` and any format or link checks relevant to the changed
  files remain mandatory.

**Rationale**: These are the actual Android/Gradle gates used by this plugin.
Keeping the rule executable prevents governance from drifting into commands
that do not exist in the repository.

### II. Test-First Development & Verification (NON-NEGOTIABLE)

Every behaviour change MUST be designed with executable verification:

- Tests MUST be authored before or alongside the production change, and the
  Red -> Green -> Refactor sequence MUST be recorded in the task notes, pull
  request, or other review evidence. Separate red and green commits are not
  required.
- Pure coordinate maths, parsing, ranking, persistence rules, and other
  host-independent logic MUST have JVM unit tests.
- New public contracts, persistence boundaries, cross-module communication,
  and ATAK SDK adapter seams MUST have contract or integration coverage at the
  closest practical boundary.
- Android resource, window-context, plugin lifecycle, and ATAK host behaviour
  that cannot be represented faithfully on the JVM MUST have explicit
  on-device acceptance steps in `quickstart.md` and `tasks.md`.
- Bug fixes MUST include a regression test unless the failure is demonstrably
  device-only. Device-only exceptions MUST include reproducible steps,
  expected log evidence, and a reason automation is not practical.
- Pure refactors MUST keep existing expectations green. Test changes that
  alter behaviour require corresponding specification changes.

**Rationale**: ATAK plugins need both fast host-independent tests and real-host
validation. Treating either layer as sufficient by itself leaves material
failures uncovered.

### III. UX, Accessibility & Localisation

Every user-facing change MUST preserve a coherent, field-usable experience:

- Visual language, navigation, feedback, loading, empty, and error states MUST
  follow the existing plugin pages unless a new pattern is recorded under
  `docs/ui/`.
- A tool page MUST have exactly one primary vertical scroll owner whenever
  content can grow or localised text can wrap. That owner MAY be a ScrollView,
  RecyclerView, or another suitable bounded container. Nested unbounded
  vertical scrollers and fixed actions placed below content that can consume
  unlimited height are prohibited.
- Interactive targets SHOULD be at least 48 dp and MUST remain reachable on
  the supported ATAK drop-down sizes. Any exception MUST be justified by an
  existing ATAK interaction pattern.
- Interactive controls MUST have meaningful labels, sufficient contrast,
  scalable text, and keyboard or screen-reader reachability where Android and
  ATAK expose those capabilities.
- User-visible strings MUST be externalised. New or materially changed strings
  MUST be supplied in the default English resources and kept aligned with the
  Traditional Chinese (Taiwan) and Japanese resource sets.
- Material screen or workflow changes MUST update the corresponding
  `docs/ui/` document. Small copy-only corrections MAY update the user guide
  or changelog without creating a new UI design document.

**Rationale**: Operators use ATAK on small panes, tablets, and field hardware.
One clear scroll owner and consistent, localised interaction patterns prevent
controls from becoming unreachable without forbidding appropriate list
containers.

### IV. Performance & Offline Operation

Performance requirements MUST cover behaviour the plugin actually controls:

- Feature plans MUST define measurable budgets for performance-critical paths
  they introduce, such as opening a drop-down, converting coordinates,
  resolving an address, importing a dataset, or updating a map overlay.
- UI work MUST avoid blocking file, database, archive, or network I/O on the
  Android main thread. Long-running work MUST be cancellable or isolated from
  host callbacks where practical.
- Interactive UI SHOULD sustain the device refresh target under representative
  plugin load. Claims about frame rate or latency MUST be backed by a named
  device, dataset, and measurement method.
- Memory-sensitive features MUST record a budget in the feature plan.
  Regressions greater than 10 percent on the same scenario require an
  explanation and, when architectural, an ADR.
- The plugin MUST remain offline-capable and MUST NOT request Android INTERNET
  permission or add telemetry unless a specification explicitly requires it
  and an ADR records the privacy, operational, and fallback consequences.
- ATAK process cold-start and warm-start times are host-owned and MUST NOT be
  used as plugin acceptance criteria unless the feature demonstrably changes
  plugin load time and isolates that contribution.

**Rationale**: Plugin-owned, measured budgets are actionable. Host-wide targets
that cannot be isolated create false gates and hide the paths the plugin can
actually improve.

### V. Documentation & Decision Traceability

The project's institutional memory MUST remain current and reviewable:

- Technical documentation, specifications, plans, tasks, ADRs, code comments,
  commit messages, and pull request descriptions MUST be written in English.
  Localised user documentation is permitted when a canonical English
  counterpart exists and both are updated together.
- Architecturally significant decisions MUST be recorded as
  `docs/adr/NNNN-kebab-case-title.md`. An ADR is required when a change
  selects or reverses an architecture, external contract, compatibility
  strategy, data format, security or privacy posture, or material operational
  trade-off.
- `/speckit-analyze` is strictly read-only and MUST NOT create an ADR merely
  because analysis ran. If the team accepts a remediation that introduces an
  architectural decision, that decision MUST be recorded in a separate change.
- `/speckit-implement` or `/speckit-converge` requires an ADR only when the
  resulting work meets the significance test above.
- Breaking changes and user-visible workflow changes MUST update README,
  quickstart, changelog, and user/UI documentation as applicable in the same
  change set.
- Specs, plans, tasks, code, tests, and ADRs MUST link to stable requirement or
  decision identifiers where those identifiers exist.

**Rationale**: Decision-based ADRs preserve important context without turning
read-only analysis or routine maintenance into noisy event logs.

### VI. Host-Process Isolation (NON-NEGOTIABLE)

The plugin shares ATAK-CIV's process. Failures at host-to-plugin boundaries
MUST be contained without hiding fatal runtime conditions:

- Every host-callable entry point MUST route through an outer safety boundary
  that logs ordinary plugin failures and returns a safe result. Shared helpers
  are preferred over repeated per-call wrappers.
- Safety boundaries MAY catch `Throwable` only when they immediately rethrow
  fatal conditions such as `VirtualMachineError` and `ThreadDeath`.
  Otherwise they MUST catch `Exception` plus only the specific `LinkageError`
  subclasses needed for documented ATAK version-skew handling.
- Listener fan-out owned by the plugin MUST isolate each listener so one
  listener cannot prevent later listeners from running or propagate into ATAK.
- Android dialogs MUST use an ATAK Activity context for the window while
  plugin resource values are resolved through the plugin context before being
  passed to the dialog. Plugin resource IDs MUST NOT be resolved by the host
  resource table.
- `findViewById` results and optional SDK objects MUST be null-checked.
  Resource APIs such as `getString` that throw on invalid IDs MUST be guarded
  at the rendering boundary; they MUST NOT be documented as nullable.
- APIs expecting drawable resource IDs MUST NOT receive
  `android.R.attr.*` values directly. Theme attributes MUST be resolved first
  or replaced with concrete resources.
- ATAK SDK interaction MUST be concentrated behind lifecycle, adapter, or
  host-boundary seams where practical. It is not necessary to wrap every SDK
  method call independently when an enclosing boundary already contains it.
- Intent extras, preferences, JSON, database rows, archives, and imported
  files MUST be validated before use and recover to a documented safe state.
- Non-idempotent, asynchronous, or multi-step actions MUST prevent duplicate
  execution. Simple synchronous selection controls do not require an
  `AtomicBoolean` solely because they are click handlers.

**Rationale**: Boundary-oriented containment protects ATAK while preserving
fatal JVM semantics and keeping the code auditable instead of burying every SDK
call in an unrelated catch block.

### VII. ATAK SDK Compatibility (NON-NEGOTIABLE)

The declared runtime range is a contract:

- The minimum supported runtime is ATAK-CIV 5.5.0 unless superseded by an ADR
  and matching manifest, README, changelog, and release-tooling changes.
- Every feature plan that changes Android or ATAK compatibility MUST record
  four independent values: Android compile SDK, Android minimum SDK, ATAK
  compile SDK, and ATAK minimum runtime. A plan MAY mark an unchanged value as
  inherited, but MUST NOT collapse Android and ATAK versions into one field.
- New ATAK integrations MUST use public SDK APIs. Evidence MUST include
  `javap -public` output from the pinned ATAK compile SDK and source or API
  evidence for the ATAK minimum runtime. Repository research MUST cite stable
  upstream source links when available.
- A feature MUST NOT assume that compiling against the current SDK proves
  compatibility with ATAK 5.5. API additions, method signatures, lifecycle
  ordering, resource ownership, and unregister/dispose behaviour MUST be
  checked explicitly.
- Features that add or change ATAK SDK seams MUST include a compatibility
  matrix covering ATAK 5.5 and the current ATAK runtime corresponding to the
  pinned ATAK compile SDK. Required device checks MAY remain an explicit
  incomplete task until hardware is available; they MUST NOT be silently
  marked complete.
- Reflection or private/internal ATAK APIs require an ADR documenting the
  public alternative considered, version-skew failure mode, and removal plan.

**Rationale**: The project currently uses Android compile SDK 36 and minimum
SDK 26, while compiling ATAK APIs against ATAK-CIV 5.7.0.3 and supporting
ATAK-CIV 5.5.0 at runtime. Explicit naming prevents Android API levels from
being confused with ATAK API compatibility and prevents a successful build
from masking a NoSuchMethodError or lifecycle incompatibility.

### VIII. Geospatial Correctness & Provenance (NON-NEGOTIABLE)

Coordinate and boundary behaviour is safety-relevant domain logic:

- WGS84 latitude/longitude MUST remain the canonical internal interchange
  representation at ATAK boundaries. Projected and grid coordinates MUST be
  converted at explicit adapter boundaries.
- TWD97, TWD67, and Taipower conversions MUST define datum, TM2 zone, axis
  order, units, valid coverage, normalisation, and out-of-range behaviour.
- Conversion changes MUST include authoritative or provenance-recorded golden
  vectors, forward/inverse round-trip tests, zone 119 and 121 coverage where
  applicable, and boundary or out-of-range cases.
- Accuracy claims MUST state the reference source, transformation model,
  representative locations, and error budget. A lower-accuracy fallback MUST
  be visible in user documentation and must not be presented as equivalent to
  the authoritative transform.
- Imported boundary or address datasets MUST record source release, schema or
  contract version, checksum where available, and generator provenance.
- Coordinate formatting and parsing MUST be locale-safe, deterministic, and
  round-trip compatible at the precision promised to the user.

**Rationale**: Incorrect zone, datum, axis, or precision assumptions can move a
marker by metres or kilometres while still producing plausible numbers.
Provenance and golden vectors make those errors detectable.

## Development Workflow & Quality Gates

The required feature workflow is:

`specify -> clarify -> plan -> checklist (optional) -> tasks -> analyze -> implement -> converge`

- Feature branches SHOULD use `codex/NNN-short-name` for Codex work or
  `NNN-short-name` for other integrations so Spec Kit can resolve the feature
  number consistently.
- `/speckit-plan` MUST run the Constitution Check before Phase 0 and again
  after Phase 1. Any unresolved non-negotiable violation blocks implementation.
  Other justified complexity belongs in the plan's Complexity Tracking table.
- `/speckit-checklist` MAY run after planning when requirements quality needs
  an additional review. It validates requirement wording and coverage; it does
  not replace executable tests or the Constitution Check.
- `/speckit-tasks` MUST create test tasks before the corresponding behaviour
  tasks and MUST include applicable Gradle, device, compatibility,
  documentation, and ADR decision checks.
- `/speckit-analyze` remains read-only. It reports coverage and constitution
  conflicts but does not change artifacts.
- `/speckit-converge` MAY append a convergence phase after implementation.
  If it appends tasks, `/speckit-implement` and convergence validation repeat
  until no actionable gaps remain.
- Mandatory initialization and feature-branch hooks MAY run automatically.
  Hooks that stage or commit changes MUST remain disabled by default and MUST
  NOT run around read-only commands such as `/speckit-analyze`. When a commit
  command is explicitly requested, it MUST preserve unrelated work and stage
  only the reviewed change scope; blanket `git add .` is prohibited in a dirty
  worktree.
- Parallel work MAY be used when the active runtime supports it and task/file
  dependencies permit it. Project governance MUST NOT require a particular
  agent orchestration capability.
- A task is complete only when applicable formatting, lint, automated tests,
  package build, on-device acceptance, compatibility evidence, documentation,
  and decision records are complete or explicitly marked as blocked.

## Governance

This constitution supersedes ad-hoc conventions and conflicting runtime
guidance until it is amended.

- Amendments MUST use `/speckit-constitution`, include a Sync Impact Report,
  update the semantic version and amendment date, and propagate changes to
  dependent templates and guidance in the same change set.
- Versioning follows semantic governance:
  - MAJOR: backward-incompatible rule changes, principle removals, or material
    redefinitions of contributor obligations.
  - MINOR: a new principle or materially expanded compatible guidance.
  - PATCH: non-semantic clarification, typo, or wording correction.
- Pull requests MUST state constitution compliance and identify any remaining
  device-only or compatibility tasks. Reviewers MUST compare that statement to
  the diff and active feature artifacts.
- Agent-facing runtime guidance lives in `AGENTS.md`, `CLAUDE.md`,
  `.specify/feature.json`, and the active feature plan. Those files MUST
  resolve the same active feature and MUST NOT contradict this constitution.
- Historical ADR decisions are immutable except for typo or metadata fixes.
  Reversals require a superseding ADR.

**Version**: 2.1.0 | **Ratified**: 2026-05-16 | **Last Amended**: 2026-07-17
