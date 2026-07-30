# TAK Third Party Pipeline Release Runbook

This is the living procedure for preparing a TAK Third Party Pipeline (TPP)
build and publishing its signed artifacts. ADR-0025 is the governing decision;
ADR-0013 is historical background.

## 1. Freeze the candidate

1. Select the new semantic version and update `ext.PLUGIN_VERSION` in
   `app/build.gradle`, `CHANGELOG.md`, and both user manuals.
2. Complete review and commit the candidate. Do not build the TPP archive from
   an uncommitted version bump.
3. Run:

   ```powershell
   python scripts/check-release-readiness.py --phase tpp
   ./gradlew :app:clean :app:assembleCivRelease
   python scripts/build-tpp-source-zip.py
   ```

The preflight must identify the exact full commit SHA. A Gradle or TPP build is
not evidence for a pending physical-device `[RELEASE-GATE]`. Source ZIP
preparation does not require `local.properties`, TAK repository credentials, a
signing keystore, or a locally signed APK.

## 2. Submit to TPP

At `https://tak.gov/user_builds`, upload only the generated
`build/atak_tw_coord_plugin-source-tpp-v<VERSION>.zip`. Do not upload
`local.properties`, credentials, keystores, or the locally built APK. TAK.gov
builds and signs the returned plugin.

Uploading and downloading are external actions and require the operator's
explicit instruction. Do not record the email-derived response filename in
committed documentation or logs.

`python scripts/build-tpp-source-zip.py --verify-build` is an optional
authenticated diagnostic for operators who already have `artifacts.tak.gov`
credentials. Missing credentials do not block source ZIP preparation or
submission through the user-build portal.

## 3. Stage returned artifacts

```powershell
python scripts/stage-tpp-release.py <TPP_BUNDLE> `
  --source-zip build/atak_tw_coord_plugin-source-tpp-v<VERSION>.zip
```

The default destination is `dist/release-v<VERSION>/`. The staging script
refuses a non-empty destination, missing or mismatched source provenance, or an
unexpected signer. It emits `provenance-v<VERSION>.json` with the source commit,
source-archive SHA-256, APK SHA-256, and signer fingerprint. Keep raw TPP
diagnostics outside Git; only the curated public assets are used for publication.

## 4. Clear the public-release gate

```powershell
python scripts/check-release-readiness.py --phase public
python scripts/check-doc-images.py
git diff --check
```

Every `[RELEASE-GATE]` must be completed or explicitly dispositioned in the
release notes. A waiver must name the unmet evidence, user impact, approver, and
follow-up version; it must never be represented as a PASS.

## 5. Tag and publish

Create a signed annotated, immutable tag only after the public gate passes:

```powershell
git tag -s v<VERSION> -m "Release v<VERSION>"
git verify-tag v<VERSION>
git push origin master v<VERSION>
gh release create v<VERSION> --verify-tag --notes-file <RELEASE_NOTES> <ASSETS>
```

Do not delete, recreate, or move a published tag. Corrections use a new version.
Pushing tags and creating the GitHub Release are external mutations and require
explicit operator authorization.

## 6. Verify publication

Compare the published APK SHA-256 with the staged value, confirm all assets are
present, and confirm the release target commit matches the signed tag. Record
only portable provenance: version, full commit SHA, tag, asset hashes, signer
fingerprint, verification date, and release-gate disposition.
