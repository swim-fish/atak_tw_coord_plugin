# ADR-0025: Separate Release Readiness from TPP Staging

**Status**: Accepted
**Date**: 2026-07-18
**Origin**: Post-v1.4.2 release-process audit

## Context

ADR-0013 joined source packaging, TPP submission, artifact staging, version
selection, tagging, and publication into one sequence. Experience through
v1.4.2 exposed three unsafe couplings: the version could be changed after the
TPP source archive was created; release assets were staged below `build/` and
could be deleted by Gradle `clean`; and a successful TPP build could be mistaken
for completion of unresolved compatibility or device-acceptance evidence.

The active native coordinate-entry feature deliberately retains ATAK 5.5
physical-device evidence and an activation-timing measurement as incomplete.
Neither source inspection, an ATAK 5.7.0.9 install, nor a TPP build proves those
release gates.

## Decision

1. Freeze and commit `PLUGIN_VERSION` before creating the TPP source archive.
   The archive, TPP bundle, APK, source commit, and release tag must identify the
   same version and source candidate.
2. Use app-scoped cleanup (`:app:clean`) for release verification. Stage durable
   public assets below `dist/release-v<VERSION>/`, never below Gradle's
   disposable `build/` tree.
3. Treat TPP readiness and public-release readiness as separate states. Tasks
   that require physical devices, external verification, or explicit operator
   disposition carry `[RELEASE-GATE]`. TPP submission may proceed with reported
   open gates; publication may not silently infer them complete.
4. Verify source ref, version, SHA-256, expected TPP signer, and gate disposition
   before publication. Published tags are signed annotated tags and immutable;
   follow-up changes receive a new version and tag.
5. Keep deterministic checks in repository scripts. Project skills orchestrate
   those scripts and explain operator decisions, but do not duplicate policy or
   mutate external systems without explicit authorization.
6. Keep raw TPP response names, workstation paths, device-owner identifiers,
   and image metadata out of committed artifacts.

## Alternatives considered

- **Keep ADR-0013 as a mutable runbook.** Rejected because editing historical
  decisions obscures why old releases differ and mixes policy with commands.
- **Block TPP submission on every device gate.** Rejected because TPP build and
  scan turnaround can run while device evidence is collected; only public
  publication needs the final disposition.
- **Continue moving an existing tag for docs-only fixes.** Rejected because a
  published tag is a provenance boundary. A new immutable tag is easier to
  audit and verify.

## Consequences

- Release preparation has an explicit intermediate state and may report
  `TPP_READY` while public publication remains `BLOCKED`.
- Maintainers must allocate a new version for any post-tag change.
- `dist/` is disposable locally but isolated from Gradle cleanup.
- Existing releases and ADR-0013 remain historical evidence; they are not
  rewritten to match the new policy.

## Links

- `.specify/memory/constitution.md`, Principle IX
- `docs/release/tpp-runbook.md`
- `docs/contributing/release-readiness.md`
- ADR-0013 (partially superseded)
- ADR-0024 (ATAK compile/runtime compatibility)
