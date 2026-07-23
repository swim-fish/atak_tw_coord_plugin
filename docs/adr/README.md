# Architecture Decision Records

This directory contains the durable record of every architecturally
significant decision made on this project, in the order they were taken.

ADRs exist because:

- **Decisions evaporate, code does not.** The code shows *what* exists;
  the ADR shows *why* it was chosen over the alternatives that did not
  survive.
- **Constitution Principle V requires decision traceability.** An ADR MUST be
  added when work selects or reverses an architecture, external contract,
  compatibility strategy, data format, privacy/security posture, or material
  operational trade-off. Routine command execution is not itself a decision.

ADRs record durable decisions; living command sequences belong in `docs/release/`
or `docs/contributing/`. When a procedure changes, add a successor ADR for the
decision and update the runbook instead of rewriting historical evidence.

## Filing convention

```
docs/adr/NNNN-kebab-case-title.md
```

- `NNNN` is a zero-padded sequence number starting at `0001`. Never
  reuse a number; never gap.
- The title is short and descriptive — present-tense if it names a
  decision (`use-proj4j-for-twd97`), past-tense if it records an event
  (`speckit-analyze-2026-05-16`).

## ADR template

Copy this skeleton when creating a new ADR:

```markdown
# ADR-NNNN: <Title>

**Status**: Accepted | Superseded by ADR-XXXX | Deprecated
**Date**: YYYY-MM-DD
**Origin**: /speckit-<command> on feature <branch>

## Context

What is the situation that called for a decision? Cite the spec FR
identifier, the plan section, or the analyze finding ID that surfaced
it. Be specific about the constraints (Constitution principle, SDK
limitation, time pressure, etc.).

## Decision

State the decision in one sentence. Then expand with the concrete
technical shape: classes, libraries, file layout, configuration
flags.

## Alternatives considered

Each rejected option with one or two sentences on *why* it lost.
Future maintainers will want to know whether you considered their
favourite idea and what made it lose.

## Consequences

Positive and negative. Operational impact, performance impact,
maintenance burden, security/privacy implications.

## Links

- Spec section / FR IDs
- Plan section
- Related ADRs (especially the one this supersedes, if any)
```

## Lifecycle

- **Accepted** — the decision is live and binding.
- **Superseded by ADR-XXXX** — keep the file; do not delete. The
  successor records the new decision and back-links here.
- **Deprecated** — the topic no longer applies (e.g., the feature was
  removed).

Do not edit a historical ADR's *Decision* or *Context* after it lands
except to fix typos. If you want to change the decision, write a new
ADR that supersedes it.

`/speckit-analyze` is strictly read-only and does not create an ADR merely
because analysis ran. If a later remediation accepts an architectural
decision, record that decision separately and link the relevant finding ID.
Likewise, `/speckit-implement` and `/speckit-converge` require an ADR only when
their resulting work meets the significance test above.

## Current supersession map

| ADR | Status | Relationship |
|-----|--------|--------------|
| ADR-0026 | Accepted | Native Address entry, one public Tools item, and bounded category-balanced candidate retrieval |
| ADR-0023 | Partially superseded by ADR-0026 | Taiwan pane retained; custom Go To fallback retired |
| ADR-0021 | Partially superseded by ADR-0026 | Standalone Go To UI retired |
| ADR-0020 | Partially superseded by ADR-0026 | Standalone search settings/navigation retired |
| ADR-0009 | Partially superseded by ADR-0026 | Custom Go To page retired |
