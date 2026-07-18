<!-- SPECKIT START -->
Resolve the active feature directory from `.specify/feature.json`; then read
that directory's `plan.md` and companion documents (`research.md`,
`data-model.md`, `contracts/*.md`, `quickstart.md`). Do not infer the active
feature from the newest numeric directory. Project-wide non-negotiable rules
live in `.specify/memory/constitution.md` and override feature guidance.
<!-- SPECKIT END -->

## Sensitive Information Hygiene

- Never commit real usernames, home-directory paths, email-derived filenames,
  device-owner identifiers, credentials, or other personal workstation data.
- Use portable placeholders such as `<USER_HOME>` and `<TAK_WORKSPACE>`, or
  documented environment variables such as `TAK_WORKSPACE`,
  `ATAK_CIV_SOURCE`, and `ATAK_SDK_5_7_0_9`.
- Before committing documentation, scripts, logs, generated evidence, or Spec
  Kit artifacts, scan the reviewed diff for `C:\Users\`, `/Users/`,
  `/home/`, `file:///`, and known local usernames.
- Treat screenshots and other binary assets as evidence, not opaque files.
  Inspect EXIF/XMP for GPS, device/build, software, author, comment, and time
  metadata before committing; preserve only metadata required to render the
  asset correctly.
- Keep raw TPP response bundles under ignored local artifact storage. Their
  email-derived filenames and full local paths MUST NOT appear in committed
  logs, release notes, specs, or documentation.
- Local history-backup branches that retain unsanitized commits must never be
  pushed and must be deleted after the rewritten branch is verified.

## Compatibility Sources of Truth

- Read Android compile/minimum and ATAK minimum-runtime values from the active
  Gradle configuration. Read the pinned ATAK compile SDK from the current
  accepted compatibility ADR and feature plan; do not copy a historical SDK
  version from an older ADR or screenshot caption.
- The current axes are Android compile/minimum 36/26 and ATAK
  compile/minimum-runtime 5.7.0.9/5.5.0. A change to any axis requires the
  constitution compatibility evidence, matching docs, and an ADR when it
  changes the accepted strategy.
- New ATAK seams require `javap -public` against the pinned compile SDK plus a
  stable minimum-runtime source/API anchor. A current-SDK build is never proof
  of minimum-runtime device compatibility.

## Project Skill Routing

- ATAK Tools-menu pages or missing toolbar icons: `add-tools-menu-page`.
- ATAK-hosted dialogs or plugin resource/window-context bugs:
  `plugin-dialog-resources`.
- `CoordinateEntryPane`, `CoordinateEntryCapability`, native Go To, or Convert
  Coordinate changes: `native-coordinate-entry-pane`.
- APK build/install/reload or device verification: `atak-device-deploy`.
- Documentation screenshots, numbering, LFS, metadata, or guide image updates:
  `docs-screenshot-workflow`.
- TPP source preparation, result-bundle staging, or GitHub release publication:
  `tpp-release-pipeline`.
- Before a TPP upload, tag, or public release: `release-readiness`.

## Git and Release Safety

- Preserve unrelated work and stage only the reviewed change scope. Blanket
  `git add .` is prohibited in a dirty worktree, including from Spec Kit hooks.
- A successful local or TPP build establishes build readiness only. It does
  not satisfy unresolved `[RELEASE-GATE]` device, compatibility, performance,
  documentation, signer, or provenance tasks.
- Freeze and commit `PLUGIN_VERSION` before generating the TPP source archive.
  Build the release variant with `:app:clean :app:assembleCivRelease`; do not
  use the root `clean` task to manage durable release artifacts.
- Keep release staging outside Gradle-owned `build/`. Published release tags
  are immutable and must not be deleted, moved, or recreated for later docs or
  tooling-only commits.
- Tag creation, pushes, TPP uploads, and GitHub release publication remain
  explicit user-authorized external actions.
