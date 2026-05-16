# ADR-0004: `/speckit-analyze` 2026-05-16 — findings and remediation

**Status**: Accepted
**Date**: 2026-05-16
**Origin**: `/speckit-analyze` on feature `001-tw-coord-display`

## Context

Per Constitution Principle V, every `/speckit-analyze` run that
produces a non-trivial outcome MUST be captured as an ADR. The run on
2026-05-16 produced 10 findings (0 CRITICAL, 2 HIGH, 4 MEDIUM, 4 LOW)
across the spec ↔ plan ↔ tasks triplet. The user authorised
remediation for HIGH and MEDIUM findings only; LOW findings were
explicitly deferred.

## Decisions

### Findings remediated this cycle

| ID | Severity | Decision |
|---|---|---|
| F1 | HIGH | Relaxed `spec.md` FR-011 wording to "Taipower 10 m default (9-char); 1 m precision optional behind a future flag". Aligns with `data-model.md` §1 and pwa_map default; rationale: typical GPS-fix accuracy is 3-5 m, so 10 m is the right default and matches the reference implementation. |
| F4 | HIGH | Added new Polish task **T061** — an instrumented `FpsImpactTest` (or scripted `dumpsys gfxinfo` bench) that compares ATAK frame rate with/without the plugin loaded and asserts ≤ 1 fps median drop per SC-007. |
| F2 | MEDIUM | Re-located docs/ui authoring into the relevant story phases: **T053 → T036a** (end of US1) and **T054 → T048a** (end of US3). Honours Constitution III's "MUST be accompanied by" wording — docs/ui no longer trails by months. |
| F3 | MEDIUM | Authored the three deferred design ADRs *now* (this cycle): **ADR-0001** (coordinate-math-source), **ADR-0002** (no-TDAL-integration), **ADR-0003** (locale-override-mechanism). Task IDs T055/T056/T057 closed in `tasks.md`. Honours Constitution V's cadence rule — ADRs accompany decisions, not project completion. |
| F5 | MEDIUM | Augmented **T059** (manual acceptance) to explicitly stopwatch cold-launch → first `ME` readout and assert ≤ 5 s per SC-001. |
| F6 | MEDIUM | Augmented **T050** (`WidgetRenderTest`) with an SC-003 next-frame assertion using `Choreographer.FrameCallback` + `CountDownLatch`. |

### Findings explicitly deferred this cycle

| ID | Severity | Decision |
|---|---|---|
| F7 | LOW | Spec FR-014 ↔ FR-019 overlap not consolidated. The two requirements are not contradictory; user judged the redundancy acceptable for now. Revisit if FR list growth makes it harder to audit. |
| F8 | LOW | FR-007 wording mismatch with SC-002 (median vs 95th percentile) not changed. Acceptance tests run against SC-002 (the stricter SC); FR-007 reads as a sub-criterion. Revisit if test reports surface a distribution that satisfies FR-007 but fails SC-002. |
| F9 | LOW | JMH source-set placement is a `/speckit-implement` concern (advisory note in T032); no spec/plan/tasks change needed. |
| F10 | LOW | T060's conditional wording mostly addressed (reworded to "verify" instead of "update if changed"). Counted as resolved. |

## Files changed by this cycle

```
spec.md                                            (1 edit: FR-011 wording)
tasks.md                                           (5 edits: T007 mark done, T036a inserted,
                                                    T048a inserted, T050 augmented,
                                                    T053-T057 marked done/moved,
                                                    T059 augmented, T060 reworded,
                                                    T061 added, parallel example updated,
                                                    format-validation block rewritten,
                                                    changelog appended)
docs/adr/README.md                                 (new — ADR filing template)
docs/adr/0001-coordinate-math-source.md            (new)
docs/adr/0002-no-tdal-integration.md               (new)
docs/adr/0003-locale-override-mechanism.md        (new)
docs/adr/0004-speckit-analyze-2026-05-16.md       (new — this file)
```

No production code touched. Constitution Principle I (formatter) does
not apply this cycle (no `.java` / `.dart` changes).

## Alternatives considered

- **Defer all six findings to `/speckit-implement`.** Rejected by the
  user (explicit "Fix HIGH MEDIUM"). The cost of fixing them now is
  trivial; the cost of fixing them mid-implementation is rework on
  test files that would already exist.
- **Renumber all tasks after deletions to keep IDs contiguous.**
  Rejected — task IDs are append-only by convention; renumbering
  breaks references in commit messages and contracts. Closed tasks
  use strikethrough notation; new tasks (T036a, T048a, T061) extend
  the sequence.
- **Author one giant "constitution alignment" ADR instead of three
  retroactive design ADRs.** Rejected — each design decision has a
  distinct context and rationale; bundling them would make future
  supersedes-by-ADR-XXXX links awkward.

## Consequences

**Positive:**

- Spec ↔ plan ↔ tasks now consistent on Taipower precision (F1).
- SC-007 is now testable (F4) — previously the constitution
  Principle IV gate could not be satisfied at acceptance time.
- Constitution III and V cadence drift fixed at the
  task-organisation level — no need to remember "do the docs at the
  end".
- Decision provenance for the entire plan now sits in `docs/adr/`
  (ADR-0001..0003), satisfying Principle V retroactively.
- Future contributors reading `docs/adr/` get a complete narrative
  without trawling git history.

**Negative:**

- Two tasks gained sub-IDs (T036a, T048a); future readers must know
  the convention.
- ADRs 0001-0003 were filed *after* the decisions were taken, not
  *with* them. Cost: small loss of fidelity in "what we thought at
  the moment of decision" — partially mitigated by these ADRs
  quoting the corresponding sections of `research.md`. Going
  forward, ADRs should be authored at decision time per
  Constitution V's wording.

## Links

- `/speckit-analyze` report (in-conversation, not committed)
- `spec.md` FR-011 (post-edit)
- `tasks.md` T036a, T048a, T050, T059, T060, T061 (post-edit)
- ADR-0001, ADR-0002, ADR-0003 (filed this cycle)
- Constitution Principles III and V
