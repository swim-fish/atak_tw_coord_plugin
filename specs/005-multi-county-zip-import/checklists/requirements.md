# Specification Quality Checklist: Multi-County + ZIP Bundle Import

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-05-26
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) — spec references file/dir layouts in **WHAT** terms; concrete class names from 004 (e.g. `AddressBundleImporter`, `AddressSubsystem`) appear only as continuity markers, not as design prescriptions
- [x] Focused on user value and business needs — every user story leads with operator goal
- [x] Written for non-technical stakeholders — technical readers can use the data-contract link for depth; the spec body reads at operator-manual level
- [x] All mandatory sections completed — Context, User Scenarios & Testing, Requirements, Success Criteria, Assumptions present

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain — all gaps resolved by informed guess + Assumptions section
- [x] Requirements are testable and unambiguous — every FR is observable on-device (e.g. FR-006 "MUST NOT touch any other county's files" is testable by hashing other county dirs before/after a Remove)
- [x] Success criteria are measurable — every SC has a unit (s / ms / bytes / counts) and a budget
- [x] Success criteria are technology-agnostic — no class / library / framework names in SC text
- [x] All acceptance scenarios are defined — each user story has 3–4 Given/When/Then scenarios
- [x] Edge cases are identified — 9 edge cases covering ZIP malformed, partial failures, external tampering, schema-mix, migration failure, etc.
- [x] Scope is clearly bounded — Context "explicitly does NOT ship" list, Assumption §2 reaffirms feature 006+ deferrals
- [x] Dependencies and assumptions identified — 10 assumptions including the generator data-contract v2 dependency and the v1.0.5 upgrade path

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria — each FR is exercised by at least one acceptance scenario or edge case
- [x] User scenarios cover primary flows — US1 ZIP import, US2 per-county lifecycle, US3 multi-county resolve, US4 v1.0.5 migration
- [x] Feature meets measurable outcomes defined in Success Criteria — SC-001 through SC-007 cover the primary user goal (≤ 90 s setup), continuity (SC-002 non-regression), independence (SC-003 / SC-004), resource bounds (SC-005), and upgrade safety (SC-006 / SC-007)
- [x] No implementation details leak into specification — class names from 004 used only as "what 004 ships today, 005 builds on" continuity references; no `class …`, no `interface …`, no method signatures, no library names

## Notes

- This spec deliberately defers Tier-1 (township polygon-in) and Tier-2 (nearest-road) reverse-geocoding to feature 006 to keep 005 scope tight. The user's stated requirements ("多檔 / ZIP / 個別更新 / 優先 R*Tree") map cleanly to 005 without touching Tier-1/Tier-2.
- The plan-phase decision deferred to plan.md: whether the active root path stays at `tools/twcoord/offline-address/active/` (consistent with 004 deployments in the wild) or moves to `tools/twcoord/data/` (consistent with the generator data-contract §2). Either honours FR-005's per-county isolation; the trade-off is migration scope vs forward-compat. **Recommended in plan.md**: stay at `active/` for 005, schedule the rename for feature 006 when townships / roads land at `data/` per generator contract.
- FR-017 / Assumption §11 lock in **A primary + B fallback** for the R*Tree continuity guarantee (user-decided 2026-05-26): ATAK SDK native SQLite is the always-on path (zero APK size cost), bundled portable SQLite is initialised only when the primary path fails to open the dataset. Plan-phase picks the concrete fallback library (Requery `sqlite-android` is the leading candidate) and the detection algorithm (probe with a no-op `SELECT 1 FROM places_rtree LIMIT 0` on every fresh-county open; on `SQLITE_ERROR no such module: rtree` swap the facade to the fallback runtime for that county only).
