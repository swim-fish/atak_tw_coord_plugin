# Release Readiness

Release readiness is evidence, not a synonym for a successful build.

## States

- **Development complete**: the implementation satisfies its spec, plan, and
  tests. `speckit-converge` may report this state.
- **TPP ready**: the version is committed, the tree is clean, deterministic
  checks pass, and the exact source candidate can be archived. Open device gates
  are reported but do not prevent TPP build/scan work.
- **Public-release ready**: required TPP artifacts and signer provenance are
  verified, documentation is synchronized, and every `[RELEASE-GATE]` is
  complete or explicitly dispositioned.

## Release-gate tasks

Use `[RELEASE-GATE]` for evidence that automation cannot legitimately infer,
including exact ATAK-version device compatibility, interactive UX acceptance,
measured device performance, signer verification, and external compliance
review. Such a task stays unchecked until its evidence exists.

An implementation task may be complete while a related release gate remains
open. Do not change the checkbox based on compilation, source inspection, a
different ATAK version, or TPP success.

## Required provenance

The release record must identify the version, full source commit SHA, signed
tag, APK SHA-256, expected signer fingerprint, compatibility evidence, and any
approved waiver. Do not commit raw workstation paths, usernames, device serials,
email-derived TPP filenames, credentials, or sensitive image metadata.
