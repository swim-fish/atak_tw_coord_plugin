---
name: tpp-release-pipeline
description: Prepare, audit, stage, or publish this ATAK plugin through the TAK Third Party Pipeline. Use for TPP source ZIPs, returned bundles, signer checks, release assets, signed tags, or GitHub Releases.
---

# TPP Release Pipeline

Read `docs/release/tpp-runbook.md`, ADR-0025, and the `release-readiness` skill.

## Prepare

Verify the version is already committed, run the TPP readiness gate, run
`:app:clean :app:assembleCivRelease`, then run
`python scripts/build-tpp-source-zip.py --verify-build`. Never use
`--allow-dirty` for an upload candidate.

## Stage

Run `python scripts/stage-tpp-release.py <TPP_BUNDLE>`. The script must stage to
`dist/`, require the source archive, verify version alignment, and reject an
unexpected or unverifiable signer. Do not echo or commit the email-derived raw
bundle name.

## Publish

Run the public readiness gate and image check first. Create a signed annotated
tag with `git tag -s`, verify it, and treat it as immutable. Uploading to TPP,
pushing a tag, and creating or changing a GitHub Release require explicit user
authorization. Corrections after publication receive a new version.
