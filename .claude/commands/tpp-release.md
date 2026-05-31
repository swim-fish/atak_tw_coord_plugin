---
description: Drive the TAK Third Party Pipeline (TPP) release flow — build & verify the source zip, then stage the result bundle into a GitHub Release
argument-hint: [path/to/<email>-<date>-<time>.zip]
allowed-tools: Bash(python:*), Bash(git:*), Bash(gh:*), Bash(apksigner:*), Bash(ls:*), Read, Edit
---

# TPP Release

Drive a TAK Third Party Pipeline release end-to-end for this plugin. The
canonical reference is `docs/adr/0013-release-pipeline.md` — read it if any
step is ambiguous. This command automates everything around the one stage that
cannot be automated.

## ⚠️ The one hard constraint — read first

**Stage 3 (TPP build) is a manual web upload to `https://tak.gov/user_builds`.
There is no API. You (the agent) CANNOT perform it.** The flow therefore splits
in two, selected by whether a bundle path was passed in `$ARGUMENTS`:

- **No argument → Pre-upload (Stages 1–2).** Verify + build the source zip, then
  STOP and hand the upload to the user.
- **A `*.zip` bundle path → Post-upload (Stages 4–6).** Stage the TPP result
  bundle into a release and publish.

Detect the mode from `$ARGUMENTS` and run only that half. Never claim the
release is "done" until the GitHub Release exists and its APK SHA-256 has been
round-trip verified.

---

## Mode A — Pre-upload (no argument)

Goal: produce a verified, minimal source zip ready to drag into tak.gov.

1. **Confirm the version.** Read `PLUGIN_VERSION` from `app/build.gradle`. If
   this release ships a code change, that version must already be bumped — the
   zip is named after it. If the intended work does NOT change the APK (tooling,
   docs), per ADR-0013 you do not bump; you move the existing tag later.

2. **Exclusion-safety guard.** Run:
   ```
   python scripts/check-tpp-exclusions.py
   ```
   Must exit 0. A FAIL means an exclusion rule would break the TPP build (a
   build-critical input got excluded, or an active gradle file now wires an
   excluded path in). Fix before continuing — do not hand a broken zip to TPP.

3. **Build the source zip + preflight.** Run:
   ```
   python scripts/build-tpp-source-zip.py
   ```
   Read the summary. Requirements:
   - `0 FAIL`. Any FAIL blocks upload — resolve it.
   - `git working tree clean` should PASS. The zip is `git archive HEAD`, so
     **uncommitted edits silently won't ship** — commit first, then re-run.
   - The `gradle version vs TPP FAQ` WARN is expected/benign (TPP runs modern
     Gradle; the FAQ pinning 6.9.1 is stale).
   The script writes `build/<repo>-source-tpp-v<VERSION>.zip` (~340 KB).

4. **Hand off the upload.** Tell the user exactly:
   - Open `https://tak.gov/user_builds` and drag in the zip from step 3 (give
     the absolute path).
   - Wait for the email; download the result bundle
     `<email>-<YYYYMMDD>-<HHMMSS>.zip` into `build/`.
   - Resume with: `/tpp-release build/<that-bundle>.zip`
   Then STOP. Do not fabricate TPP output.

---

## Mode B — Post-upload (bundle path in `$ARGUMENTS`)

Goal: turn the TPP result bundle into a published GitHub Release. Treat the
bundle path in `$ARGUMENTS` as the input.

1. **Stage the bundle.** Run:
   ```
   python scripts/stage-tpp-release.py $ARGUMENTS
   ```
   It extracts only the four shippable files (APK, mapping, Fortify PDF,
   dependency-check HTML), renames them to the public convention, copies in the
   Stage-2 source zip, drops the seven diagnostics, prints the APK **SHA-256**
   and a signer-cert check, and emits a `gh release create` command. Output
   lands in `build/release-v<VERSION>/`.
   - If it reports a **missing source zip**, run Mode A step 3 first (or pass
     `--source-zip`).
   - **Signer cert:** the script flags it ✓ when the APK carries the
     `TAK Untrusted Plugin Release` cert. If apksigner isn't on PATH it prints a
     manual `apksigner verify --print-certs` command — run it. An **unexpected
     signer is a stop-the-line event**: a changed cert forces every end user to
     uninstall before updating; confirm intent loudly before proceeding.

2. **Release notes.** Ensure `build/release-v<VERSION>/RELEASE_NOTES_v<VERSION>.md`
   exists (sections per ADR-0013 Stage 6: summary; what changed; install;
   trust/signature; security review; source & reproducibility). Put the APK
   **SHA-256 from step 1** into the trust/signature table so end users can
   verify their download.

3. **Tag + push (Stage 5).** Confirm with the user before any outward action.
   - If the APK changed: bump was already done; tag the release:
     `git tag -a v<VERSION> -m "<summary>"` then `git push origin master v<VERSION>`.
   - If the APK did NOT change (tooling/docs only): per ADR-0013, move the
     existing tag rather than cutting a new version.
   - **Windows reflog gotcha:** `git merge --ff-only` / `git tag` may hit
     `Permission denied … unable to append to '.git/logs/HEAD'` (AV/indexer
     lock). Recovery: delete the bad local+remote tag, retry the FF, retag at
     the correct HEAD, `git rev-parse v<VERSION>^{commit}` to confirm it matches
     HEAD, then push. Full recipe in ADR-0013 Stage 5.

4. **GitHub Release (Stage 6).** Run the `gh release create` command the staging
   script printed (5 assets: APK, mapping, security-scan PDF, dependency-check
   HTML, source-archive zip), targeting `master`. Confirm before publishing —
   this is outward-facing.

5. **Post-publish verification — required before claiming done.**
   ```
   gh release view v<VERSION> --json publishedAt,assets
   gh release download v<VERSION> --pattern "ATAK-Plugin-*.apk" -O /tmp/_check.apk
   sha256sum /tmp/_check.apk   # MUST equal the SHA-256 in the release notes
   ```
   Only report success once the downloaded APK's SHA-256 matches. Then clean up
   the temp file.

---

## Gotchas (apply in both modes)

- **`-unsigned.apk` is actually signed.** TPP re-signs but keeps the misleading
  filename suffix; the staging script renames it. Don't be alarmed by the name.
- **Fortify findings in test code are gone by design.** `app/src/test` is
  excluded from the zip, so the Fortify scan covers only shipped code — no
  test-only password/SQL false-positives in the PDF. If you ever DO see findings
  in shipped code, triage them, don't wave them through.
- **The dependency-check `[ERROR] CVE-…` lines in `build.log`** are ODC's NVD
  database write glitches (URL too long for its column), not vulnerabilities in
  this plugin.
- **Never commit secrets into the zip.** The preflight refuses `local.properties`
  / `*.keystore` / `*.jks`; if that check ever FAILs, stop and investigate.
- **Source of truth is the scripts + ADR-0013**, not this command. If the flow
  and this file disagree, the ADR wins — and update this command to match.
