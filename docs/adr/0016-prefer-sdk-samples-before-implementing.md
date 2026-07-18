# ADR-0016: Prefer SDK samples before self-implementing host-callable surfaces

**Status**: Accepted

**Date**: 2026-05-26

**Origin**: Lessons earned across feature 004 (commits `2ca5643` → `14f43bf` → `d80d8bc` → `c0c493d`, eventually squashed into `03f910e`) and feature 005 (clean from day one).

## Context

Feature 004 spent **five layered debugging commits** trying to make a self-built SAF (Storage Access Framework) file picker work for the Offline Address page. Each iteration fixed one cross-UID broadcast layer (exported Activity, `setData` + `FLAG_GRANT_READ_URI_PERMISSION`, `addDataScheme("content")`, `RECEIVER_EXPORTED`, ctor-bound receiver lifecycle) only to discover the next one. On Android 14 + Samsung One UI ActivityManager silently dropped the broadcast no matter the flags. The fix that actually shipped — `com.atakmap.android.gui.ImportFileBrowserDialog` — was lifted **verbatim** from the SDK's `helloworld` sample at `ATAK-CIV-5.7.0.3-SDK/samples/helloworld/app/src/main/java/com/atakmap/android/helloworld/HelloWorldDropDownReceiver.java` line **3656** (the `sampleFileBrowser()` method).

Feature 005 then opened day one already using `ImportFileBrowserDialog` — zero file-picker debugging time spent. Same surface area, completely different cost profile, because the sample was the reference.

The five-layer debugging path was avoidable. The SDK samples directory was always there; nothing prevented checking it first.

## Decision

**Before implementing any host-callable surface in an ATAK plugin, scan the SDK samples for an existing pattern.**

Concretely:

1. **Plan-phase reconnaissance MUST include a sample sweep.** When `/speckit-plan` writes `research.md`, the SDK-reconnaissance section MUST include grep / `Glob` results for the surface area being implemented across `../ATAK-CIV-5.7.0.3-SDK/samples/**`. Treat sample hits as authoritative until proven otherwise.

2. **Specifically scan these samples first** (ordered by demonstrated coverage):
   - `helloworld/HelloWorldDropDownReceiver.java` — file picker, dialog, map markers, drop-down lifecycle, overlay rendering, broadcast wiring.
   - `plugintemplate/` and `plugintemplate-compose/` — minimum-viable plugin skeleton.
   - `importexportexample/` — `ImportResolver`, `ImportFileTask`, `MarshalManager` for dataset import flows.
   - `customtiles/`, `selfmarkerdata/`, `windprovider/`, `windconsumer/` — surface-specific examples for tiles / location streams / preference plugins / inter-plugin IPC.
   - `dsmmanager/`, `lassotoolexpansiondemo/`, `radialmenudemo/` — Tools-menu, lasso, radial menu integrations.

3. **Cite the sample in plan-phase docs.** Every research.md decision that references a sample MUST cite the file path AND line range, so future maintainers (and `/speckit-implement`) can trace the pattern back to its origin.

4. **Prefer SDK-provided UI primitives over custom Android components for in-plugin UI**:
   - File picker → `com.atakmap.android.gui.ImportFileBrowserDialog`, not `Intent.ACTION_OPEN_DOCUMENT`.
   - Alert / confirm dialog → `new AlertDialog.Builder(getMapView().getContext())`, not the plugin Context (ADR-0015 D8).
   - Coordinate input → `EnterLocationDropDownReceiver` and related receivers when available (ADR-0011 D8 precedent).
   - Toolbar / Tools-menu entry → `AbstractPluginTool` subclass, mirrored after existing tools.
   - SQLite reads → `com.atakmap.database.Databases.openDatabase` (ATAK native, has R*Tree / FTS5 / JSON1 — ADR-0015 D2), not `android.database.sqlite.SQLiteDatabase`.

5. **When no sample covers the surface, mark it loudly in research.md.** A "no sample exists" finding is itself a decision the team must make consciously — it usually signals either a novel feature (rare) or a wrong abstraction choice (common). Either way it should be documented before coding starts, not after the fifth debugging round.

## Alternatives considered

- **Self-implement first, look at samples on failure.** This is the path feature 004 took for the file picker. Cost: ~3 days of debugging across the 5-layer SAF cross-UID death spiral. Rejected explicitly.

- **Add this as a Constitution principle.** Rejected — Constitution principles are NON-NEGOTIABLE invariants that the build process enforces (formatting, TDD, host-process isolation). This is a methodology preference, valuable for design speed but not enforceable by tooling. ADRs are the right home for methodology lessons.

- **Codify a separate "SDK reconnaissance" checklist file.** Rejected — duplicates research.md's role. Updating research.md template guidance + this ADR achieves the same outcome with less surface area.

## Consequences

**Positive**:

- Plan-phase research.md gains a "SDK sample sweep" sub-section. Cost: ~10 minutes of `Glob` + `Grep` at the start of every new feature.
- Feature implementations skip entire classes of platform / cross-UID / window-token pitfalls that the SDK's samples have already solved.
- Onboarding new contributors becomes easier — the samples directory is the canonical "how-to" reference.

**Negative / risks**:

- The samples directory is not exhaustive — some surfaces (`ServiceLifecycle`, custom protocols, native renderer extensions) have no canonical sample. For those, the recon section still has to mark "no sample exists" and accept the design risk consciously.
- Samples can drift from current SDK practice; cross-check signatures against `javap -public ATAK-CIV-SDK/main.jar` (per `feedback-plan-phase-code-anchoring` memory) when the sample's pattern looks dated.

**Process changes triggered by this ADR**:

- `/speckit-plan` research.md template implicitly gets a "SDK sample reconnaissance" expectation; existing feature 004 research.md (R7 — Tools-menu wiring) already does this; feature 005 research.md R3 explicitly cites the helloworld sample. Future features MUST follow suit.
- The user-level memory `feedback-prefer-sdk-samples-before-implementing.md` mirrors this ADR's Decision section for cross-session persistence; both should stay in sync if the decision is ever revised.

## Links

- [ADR-0015 § D1](./0015-offline-address-implementation.md) — the SAF cross-UID broadcast death spiral and the in-process `ImportFileBrowserDialog` fix that motivated this ADR.
- [ADR-0015 § D2](./0015-offline-address-implementation.md) — the ATAK-native SQLite swap that came from the same methodology (look at what ATAK already does before assuming the platform path works).
- [ADR-0015 § D8](./0015-offline-address-implementation.md) — `getMapView().getContext()` for AlertDialog window tokens; another SDK-pattern lesson.
- [ADR-0014 § R10](./0014-offline-address-reconnaissance.md) — Constitution VI entry-point audit, also driven by reading what existing ATAK callbacks expect.
- `feedback-plan-phase-code-anchoring` (user-level memory) — companion discipline: cite both `javap -public` against `main.jar` AND upstream permalinks.
- `feedback-prefer-sdk-samples-before-implementing` (user-level memory) — companion to this ADR, cross-session.
- SDK samples directory: `<ATAK_SDK_5_7_0_3>/samples/` — local mirror; the upstream is `github.com/TAK-Product-Center/atak-civ`.
