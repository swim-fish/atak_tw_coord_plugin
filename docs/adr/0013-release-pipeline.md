# ADR-0013: Release pipeline — TAK TPP submission → GitHub Releases

**Status**: Accepted
**Date**: 2026-05-17
**Origin**: Four end-to-end TPP submissions (v1.0.0 × 2, v1.0.1, v1.0.3 — v1.0.2 was committed + tagged but rolled into v1.0.3 before reaching TPP). Captures the keystore migration (`chore/release-keystore` branch, commit `2aceae6`), the source-zip preflight script that lived through three iterations, and the GitHub Releases distribution conventions.

This ADR is the canonical reference for cutting a new release. Anyone shipping a new version should be able to follow it end-to-end without consulting this conversation history.

## Context

The plugin ships under TAK-CIV's third-party-plugin model. Two distribution routes are available:

| Route | Signed by | End user gets | Maintainer needs |
| --- | --- | --- | --- |
| **A — TAK Third Party Pipeline** | `CN=TAK Product Center ATAK Untrusted Plugin Release`, issued by TAK Government | Government-signed APK with the "third-party signed" UI badge | `tak.gov` account + `artifacts.tak.gov` Maven credentials |
| **B — self-signed + GitHub** | Whatever the maintainer's own keystore is | Self-signed APK (still triggers the third-party badge in ATAK) | Just a working dev box; same trust prompt either way |

Both routes produce a third-party-badged plugin in ATAK; the only operational difference is who holds the signing cert. We picked **Route A** because:

- The maintainer already has a `tak.gov` account.
- The Government-cert chain is a small but real trust signal for end users — they know who issued the cert without having to verify a community fingerprint themselves.
- TPP runs an OWASP dependency-check + Fortify SAST pass on every submission. We get those scan reports "for free" and can ship them as release attachments.
- Hosting the binary on `github.com/swim-fish/atak_tw_coord_plugin/releases` keeps the user-facing download surface familiar — `tak.gov`'s user-build dashboard is for the maintainer only.

Route B (self-signed + GitHub) is documented as the fallback in `docs/pipe/Third Party Pipeline.md`; we don't use it but the build.gradle signing config supports it (the keystore migration below was originally about making Route B viable; in practice the keystore is now only used as a no-op local-signing step before TPP re-signs downstream).

## Decision

The release pipeline has six stages:

1. **Local verification** — `./gradlew assembleCivRelease` must succeed against the dev keystore.
2. **Source-archive generation** — `scripts/build-tpp-source-zip.py` produces a TPP-ready zip with preflight checks.
3. **TPP submission** — drag-drop the zip into `https://tak.gov/user_builds`. TPP builds + scans + signs + returns a result bundle.
4. **Release-artifact staging** — rename TPP output to the public naming convention; copy in the source zip + scan reports.
5. **Tag + push** — bump `PLUGIN_VERSION`, tag `vX.Y.Z`, push master + tag to `origin`.
6. **GitHub Release** — `gh release create` with the five-asset bundle + release-notes markdown.

Each stage's specifics are documented below.

### Stage 1 — local verification

- Source: this repo on the chosen branch (typically `master` after merging any fix/feature branches).
- Command: `./gradlew clean :app:assembleCivRelease`
- Expected: BUILD SUCCESSFUL, output APK in `app/build/outputs/apk/civ/release/`.
- Signed locally using the keystore wired through `local.properties` (see [§Keystore](#keystore)). The local signature is not what end users ever see — TPP re-signs downstream — but the build must succeed signed because the release variant has `signingConfig signingConfigs.release` and an unsigned variant would fail certain release-only Gradle tasks.

### Stage 2 — source archive

`scripts/build-tpp-source-zip.py` is the canonical generator. Default behaviour:

- Runs `git archive HEAD --format=zip --prefix=<rootProject.name>/` so only committed files land in the zip.
- Strips developer-tooling overhead that TPP doesn't need: `.claude/`, `.specify/`, `CLAUDE.md`, `docs/images/` (added v1.0.3 — the 11 user-guide screenshots were inflating the zip from 470 KB to 4.2 MB with zero TPP value).
- Reads `PLUGIN_VERSION` from `app/build.gradle` and emits the zip as `build/<rootProject.name>-source-tpp-v<VERSION>.zip` (added v1.0.3 — versioned filenames prevent re-uploading a stale zip).

Static preflight checks (mirror the `Source Archive Requirements` from `docs/pipe/Third Party Pipeline.md`):

| Check | Failure mode |
| --- | --- |
| `git working tree clean` | WARN — `git archive` snapshots HEAD only; uncommitted edits silently won't ship. |
| `AndroidManifest plugin activity` | FAIL — without `<activity android:name="com.atakmap.app.component">` + matching `<intent-filter><action…/>` ATAK won't even scan the package. |
| `proguard -repackageclasses customised` | FAIL — if the value still reads `atakplugin.PluginTemplate`, crash logs from this plugin would be indistinguishable from the upstream plugintemplate. |
| `atak-gradle-takdev applied` | FAIL — TPP requires this Gradle plugin to fetch the ATAK SDK from `artifacts.tak.gov`. |
| `assembleCivRelease target defined` | FAIL — Gradle would not have a target to invoke. |
| `gradle version vs TPP FAQ` | WARN — FAQ pins Gradle 6.9.1 but TPP env actually runs modern Gradle (Gradle 8.14.3 confirmed via real submission v1.0.0 onwards). FAQ is stale; warning preserved as a "verify on submission" reminder. |
| `NDK version (if pinned)` | FAIL — if `ndkVersion` is set, it must be one of TPP's preinstalled versions. Not pinned today (pure-Java plugin) so the check trivially passes. |

Archive shape checks (run after the zip is written):

| Check | Failure mode |
| --- | --- |
| `single root folder` | FAIL — TPP rejects multi-root zips; it names the output APK after the root folder. |
| `gradle-wrapper.jar present` | FAIL — added after the v1.0.0 first submission, which failed with `Could not find or load main class org.gradle.wrapper.GradleWrapperMain` because the global `.gitignore`'s `*.jar` rule had swept the wrapper jar out of git tracking. Fixed in commit `2da12cf` by adding `!gradle/wrapper/gradle-wrapper.jar` as an exception. The script's check makes that mistake unreproducible. |
| `archive contains no secrets` | FAIL — refuses to ship if any entry's basename matches `local.properties` / `*.keystore` / `*.jks` / `android_keystore`. |

Optional `--verify-build` flag runs the literal command from `docs/pipe/Third Party Pipeline.md`'s "verify prior to submission" recipe against `artifacts.tak.gov` with credentials from `local.properties`:

```sh
./gradlew -Ptakrepo.force=true \
          -Ptakrepo.url=https://artifacts.tak.gov/artifactory/maven \
          -Ptakrepo.user=<user> \
          -Ptakrepo.password=<pass> \
          assembleCivRelease
```

Useful before a first-time submission; redundant after the same code has been TPP-built successfully.

### Stage 3 — TPP submission

- Open `https://tak.gov/user_builds` (accept the participation terms if prompted).
- Drag-drop the `<repo>-source-tpp-v<VERSION>.zip` into the upload area.
- Wait. Typical turnaround: 2–3 minutes for the actual build, 8–15 minutes for the full bundle (the dependency-check NVD CVE cache update dominates).
- Email notifies on completion; bundle downloads from the dashboard as `<email>-<YYYYMMDD>-<HHMMSS>.zip`.

Bundle contents (per `docs/pipe/Third Party Pipeline.md` + observed v1.0.0–v1.0.3 outputs):

| File | Purpose |
| --- | --- |
| `build.log` | Full Gradle output. Read this when a TPP build fails. |
| `fortify_analyze.log` + `fortify_analyze_FortifySupport.log` | Fortify SAST analysis logs. |
| `fortify_scan.txt` + `fortify_scan_FortifySupport.txt` | Fortify scan output. |
| `scan_results.fpr` | Fortify proprietary report format. |
| `fortify_scan_results.pdf` | **Human-readable SAST summary — attach to GitHub Release.** |
| `dependency-check-report.html` | **OWASP dependency-check report — attach to GitHub Release.** |
| `ATAK-Plugin-<rootProjectName>-<VERSION>--<ATAK_VERSION>-civ-release.aab` | Play-Store bundle, unused for sideload. |
| `ATAK-Plugin-<rootProjectName>-<VERSION>--<ATAK_VERSION>-civ-release-unsigned.apk` | **The APK. Despite the `-unsigned` suffix in the filename, TPP HAS re-signed this with the Untrusted Plugin Release cert** — verify with `apksigner verify --print-certs`. The misleading suffix is a side-effect of our `archivesBaseName` convention; the file is shippable as-is. |
| `civRelease-app-mapping.txt` | **R8/ProGuard obfuscation map. Attach to GitHub Release** for future stack-trace deobfuscation. |

Expected signer DN on the APK:

```
CN=TAK Product Center ATAK Untrusted Plugin Release,
  OU=Product Center, O=TAK, L=Fort Belvoir, ST=Virginia, C=US
SHA-256 fingerprint: f24a38057275fcecf67be975ab803d12f75dc23581bef69cba9eb03a15bb8c17
```

Identical across every v1.0.x release. If a future release ever changes signer, document the reason loudly — it forces every end user to uninstall before updating.

### Stage 4 — release-artifact staging

Working location: `build/release-vX.Y.Z/` (gitignored under `build/`).

Renaming convention (changes from TPP's `*-unsigned.apk` naming so end users don't worry about the misleading suffix):

| TPP output | Renamed to |
| --- | --- |
| `ATAK-Plugin-atak_tw_coord_plugin-1.0.3--5.4.0-civ-release-unsigned.apk` | `ATAK-Plugin-TWCoord-v1.0.3-ATAK-5.4+.apk` |
| `civRelease-app-mapping.txt` | `mapping-v1.0.3.txt` |
| `fortify_scan_results.pdf` | `security-scan-v1.0.3.pdf` |
| `dependency-check-report.html` | `dependency-check-v1.0.3.html` |
| (from stage 2) `build/atak_tw_coord_plugin-source-tpp-v1.0.3.zip` | `source-archive-v1.0.3.zip` |

Verification before publish:

```sh
apksigner verify --print-certs ATAK-Plugin-TWCoord-v1.0.3-ATAK-5.4+.apk
sha256sum ATAK-Plugin-TWCoord-v1.0.3-ATAK-5.4+.apk
```

The SHA-256 of the APK is included in the release notes table for end-user verification.

### Stage 5 — tag + push

- Bump `ext.PLUGIN_VERSION` in `app/build.gradle`. Commit as `chore(release): bump to 1.0.3` (or fold into the actual functional commit — both styles used historically).
- Fast-forward `master`: `git switch master && git merge --ff-only <feature-branch>`.
- Annotated tag: `git tag -a vX.Y.Z -m "<summary…>"`.
- Push: `git push origin master vX.Y.Z`.

**Known Windows gotcha — reflog `Permission denied`:**

On Windows, `git merge --ff-only` (and `git tag`) occasionally hits `fatal: update_ref failed for ref 'HEAD': cannot update the ref 'HEAD': unable to append to '.git/logs/HEAD': Permission denied`. Likely caused by an AV / file-indexer briefly holding the file. When it triggers:

1. Tag may still get created **pointing to the OLD HEAD** (the FF merge didn't actually advance the ref).
2. If the tag was pushed before discovery, both the local and remote tag point to the wrong commit.

**Recovery recipe** (tested v1.0.1 + v1.0.3):

```sh
git tag -d vX.Y.Z                          # delete bad local tag
git push origin :refs/tags/vX.Y.Z          # delete bad remote tag
sleep 1                                    # let any file lock release
git merge --ff-only <feature-branch>       # retry the FF (succeeds now)
git tag -a vX.Y.Z -m "<same summary…>"     # retag at correct HEAD
git rev-parse vX.Y.Z^{commit}              # sanity-check: should match HEAD
git push origin master vX.Y.Z              # push both
```

**Standing convention for tag-moving post-release:** if a maintainer commits something *after* the release tag that doesn't affect the APK (e.g. a fix to `scripts/build-tpp-source-zip.py`, a docs typo), they may move the existing tag to the new HEAD via the same delete-and-recreate dance. This was done for v1.0.3 (twice — after the `docs/images/` exclusion fix and the versioned-filename fix). Force-push is not used; explicit delete-then-create is preferred because it's transparent in the git push output.

### Stage 6 — GitHub Release

```sh
cd build/release-vX.Y.Z/
gh release create vX.Y.Z \
  --repo swim-fish/atak_tw_coord_plugin \
  --title "vX.Y.Z — <one-line summary>" \
  --notes-file RELEASE_NOTES_vX.Y.Z.md \
  --target master \
  ATAK-Plugin-TWCoord-vX.Y.Z-ATAK-5.4+.apk \
  mapping-vX.Y.Z.txt \
  security-scan-vX.Y.Z.pdf \
  dependency-check-vX.Y.Z.html \
  source-archive-vX.Y.Z.zip
```

The release-notes template lives next to the artefacts (`RELEASE_NOTES_vX.Y.Z.md`). Sections (in order):

1. One-paragraph summary of what changed.
2. **What changed since v<previous>** (bullet list grouped by domain — UI / Build / Docs).
3. **Install** (4-step sideload recipe).
4. **Trust / signature** (signer DN + SHA-256 table; copy the SHA-256 of the APK file from `sha256sum`).
5. **Security review** (Fortify result + dep-check + zero-network reminder).
6. **Source & reproducibility** (links to the source zip + mapping + the canonical git tag).

Post-publish verification:

```sh
gh release view vX.Y.Z --repo swim-fish/atak_tw_coord_plugin --json publishedAt,assets
gh release download vX.Y.Z --pattern "ATAK-Plugin-TWCoord-vX.Y.Z-ATAK-5.4+.apk" -O /tmp/_check.apk
sha256sum /tmp/_check.apk           # MUST equal the SHA-256 in the release notes
rm /tmp/_check.apk
```

## Keystore

See also `keystore/README.md` for the regeneration recipe.

The plugin's release keystore was migrated out of `app/build.gradle` into `local.properties` in commit `2aceae6` (branch `chore/release-keystore`). Three motivations:

1. **Stop using the community demo cert.** Previously `app/build.gradle` hard-coded the `WinTec Arrowmaker` keystore (alias `wintec_mapping`, password `tnttnt`), the same one Meshtastic's ATAK-Plugin and the upstream plugin template both ship with. Any community plugin signed with that cert is indistinguishable from any other; if it ever needs to rotate, everyone using it has to coordinate.
2. **Stop committing the password to git.** The hard-coded `tnttnt` literal was right there in `build.gradle`. Anyone forking the repo inherited the password.
3. **Don't require a keystore for CI / fresh-clone builds.** TPP doesn't need one (it re-signs downstream). A CI that only verifies compilation shouldn't need to provision signing material.

### Implementation

- New keystore at `keystore/release.keystore`, gitignored via the existing `*.keystore` rule in `.gitignore`. Subject DN: `CN=Taichung City Citizen Corps Association, O=Taichung City Citizen Corps Association, L=Taichung, ST=Taiwan, C=TW`. Algorithm: SHA384withRSA, 4096-bit RSA, valid through 2094-10-27.
- Password (28-character random alnum) lives in `local.properties` under `releaseStorePassword` / `releaseKeyPassword` — file is gitignored.
- `app/build.gradle`'s `signingConfigs.release` block now reads those four properties at evaluation time; when *any* are absent the block is skipped and the release build produces an unsigned APK (still acceptable for TPP submission since TPP re-signs).
- The `debug` signingConfig was removed entirely; debug builds now use Android's default `~/.android/debug.keystore` (auto-generated by AGP on first build).

### Backup discipline

The keystore + its password together constitute the maintainer's release identity. Losing either means:

- **Lose keystore:** can't push updates to existing users (Android refuses `INSTALL_FAILED_UPDATE_INCOMPATIBLE`); have to publish under a new package name or force everyone to uninstall.
- **Lose password:** keystore file is useless without it; same effect as losing the keystore.

For Route A (TPP) the practical impact is muted — TPP re-signs anyway — but if anyone ever ships a Route-B (self-signed) build, the local keystore IS the trust root. Treat both as production secrets:

- At least two copies, in different storage media (encrypted USB / encrypted cloud / hardware token / password-manager secure-note attachment).
- Password in a password manager, **not** colocated with the keystore file (so a single backup leak doesn't compromise both).
- Periodically (every ~6 months) restore from backup and verify: `keytool -list -v -keystore <path> -storepass <pw> -alias twcoord-release` against the SHA-256 recorded in `keystore/README.md`.
- The SHA-256 lives in `keystore/README.md` (committed) so anyone can verify a recovered keystore matches the published identity.

## Alternatives considered

- **Self-signed + GitHub Releases only (Route B).** Rejected — the maintainer has TPP access; Government-cert chain is a real (if small) trust signal; TPP's built-in scan reports are operationally valuable.
- **CI automation via GitHub Actions on tag push.** Considered for v1.0.4+ but deferred. Would require provisioning `takrepo.url`/`takrepo.user`/`takrepo.password` secrets on the repo (since TPP submission is manual upload, the most CI could automate is the source-zip generation + the GitHub Release publish after the maintainer has dropped the TPP response into a designated location). Manual flow is 4 minutes end-to-end and we've now run it three times without incident; pre-mature to automate.
- **Skip the source-archive zip exclusion list.** Rejected — pre-v1.0.3 the zip was 4.2 MB (mostly the 11 user-guide screenshots). TPP submission upload is slow and the screenshots leak nothing useful into the pipeline. The exclusion list earns its keep.
- **Treat the SHA-256 in release notes as advisory.** Rejected — end users sideloading a plugin from GitHub Releases should be able to verify before installing. The SHA-256 in the release notes table is the cheapest possible integrity gate, and the round-trip verification step (download → `sha256sum`) is in the publish checklist explicitly.

## Consequences

**Positive:**

- Reproducible release recipe — each release runs the same six stages, scripted where it makes sense.
- Three independent integrity layers visible to end users: TPP's TAK Untrusted Plugin Release cert (signature), the published SHA-256 (file integrity), and the attached source-zip + scan reports (provenance + audit trail).
- Keystore migration removed both the community-demo-cert ambiguity and the in-git password leak.
- Recovery recipes are documented — the Windows reflog `Permission denied` bug, which bit on both v1.0.1 and v1.0.3, will not consume an hour the next time it happens.

**Negative:**

- Stage 3 (TPP submission) is manual upload — no API. CI cannot automate end-to-end without involving a human at the `tak.gov` dashboard. This is a TAK constraint, not ours.
- TPP's misleading `-unsigned.apk` filename suffix forces the renaming step in Stage 4. If TPP ever fixes that, the rename can drop, but doing so silently would surprise anyone scripting against the filename pattern — coordinate any change explicitly.
- The wrapper-jar-gitignored-by-accident bug (caught + fixed during v1.0.0) is a permanent reminder that broad ignore rules can swallow critical files. The script's archive-shape check catches this specific case; future broad gitignore additions should be reviewed against the script's exclusion list.
- Password rotation is a fully manual ceremony — regenerate keystore, update `local.properties`, update `keystore/README.md`'s recorded SHA-256, document the rotation in a new ADR (or update this one), notify end users in the next release notes that the cert changed (forces uninstall-then-reinstall).

## Links

- TPP requirements: [`docs/pipe/Third Party Pipeline.md`](../pipe/Third Party Pipeline.md)
- TPP terms of participation: [`docs/pipe/Third Party Pipeline Participation Terms.md`](../pipe/Third Party Pipeline Participation Terms.md)
- Keystore: [`keystore/README.md`](../../keystore/README.md)
- Source-archive script: [`scripts/build-tpp-source-zip.py`](../../scripts/build-tpp-source-zip.py)
- Related ADRs: [ADR-0012](./0012-tw-icon-asset-pipeline.md) (icon pipeline; uses the same "render scripts derive from source XML" pattern as this release pipeline derives from `app/build.gradle`'s `PLUGIN_VERSION`).
- Past commits referenced:
  - `2aceae6` chore(release): migrate signing config out of build.gradle into local.properties
  - `39fafce` chore(release): add scripts/build-tpp-source-zip.py
  - `2da12cf` fix(release): commit gradle-wrapper.jar + script preflight to keep it shipped
  - `811eea8` chore(release): exclude docs/images/ from TPP source zip
  - `3aaf131` chore(release): include PLUGIN_VERSION in TPP source-zip filename
- Release tags: `v1.0.0`, `v1.0.1`, `v1.0.3` on `swim-fish/atak_tw_coord_plugin`.
