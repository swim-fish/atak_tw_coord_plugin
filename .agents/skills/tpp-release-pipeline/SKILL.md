---
name: tpp-release-pipeline
description: Prepare, audit, stage, or publish this ATAK plugin through the TAK Third Party Pipeline. Use for TPP source ZIPs, returned bundles, signer checks, release assets, signed tags, or GitHub Releases.
---

# TPP Release Pipeline

Read `docs/release/tpp-runbook.md`, ADR-0025, and the `release-readiness` skill.

## Prepare

Verify the version is already committed, run the TPP readiness gate, run
`:app:clean :app:assembleCivRelease`, then create the upload artifact with:

```powershell
python scripts/build-tpp-source-zip.py
```

Never use `--allow-dirty` for an upload candidate. The upload preparation does
not require `local.properties`, TAK repository credentials, a signing
keystore, or a locally signed APK.

## Submit

At `https://tak.gov/user_builds`, upload only
`build/atak_tw_coord_plugin-source-tpp-v<VERSION>.zip`. Never upload
`local.properties`, credentials, keystores, or the locally built APK. TAK.gov
builds and signs the returned plugin.

Uploading remains an explicit user-authorized external action. The
`--verify-build` option is only an optional authenticated diagnostic for an
operator who already has `artifacts.tak.gov` credentials; unavailable
credentials must not block source ZIP preparation or submission.

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
