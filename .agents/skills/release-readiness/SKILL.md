---
name: release-readiness
description: Determine whether this plugin is TPP-ready or public-release-ready. Use before TPP upload, tagging, GitHub release publication, or whenever a successful build might be mistaken for release completion.
---

# Release Readiness

Read `.specify/memory/constitution.md`, `.specify/feature.json`, the active
feature's `tasks.md`, and `docs/contributing/release-readiness.md`.

1. Choose `tpp` or `public` from the requested action.
2. Run `python scripts/check-release-readiness.py --phase <phase>`.
3. Report the exact source SHA, version synchronization, branch/tree state, and
   every unchecked `[RELEASE-GATE]`.
4. For `tpp`, open release gates are warnings; never describe them as passed.
5. For `public`, any open gate blocks publication unless the user supplies an
   explicit documented disposition that satisfies the constitution.
6. Do not upload, tag, push, or publish unless the user explicitly requests the
   corresponding external mutation.

A successful Gradle or TPP build is build evidence only. It cannot prove exact
ATAK 5.5 device compatibility, interactive acceptance, device timing, or signer
identity.
