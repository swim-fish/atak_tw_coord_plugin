#!/usr/bin/env python3
"""Build the source archive for the TAK Third Party Pipeline (TPP) plus run
the preflight checks the TPP submission documentation calls out.

Reference: docs/pipe/Third Party Pipeline.md (Source Archive Requirements).

Three modes:

    # Static checks + build the zip (default — what you run before uploading)
    python scripts/build-tpp-source-zip.py

    # Static checks only, no zip
    python scripts/build-tpp-source-zip.py --check-only

    # Static checks + zip + run the TPP-equivalent rebuild end-to-end.
    # Requires takrepo.url / takrepo.user / takrepo.password in
    # local.properties (your artifacts.tak.gov credentials). This is the
    # exact command the TPP doc says "should build successfully" — i.e. if
    # it fails here, TPP will also fail.
    python scripts/build-tpp-source-zip.py --verify-build

Exit codes: 0 = ready to upload, 1 = at least one FAIL, 2 = script error.
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path
from typing import Optional

# ---------- Archive shape ----------

EXCLUDE_PREFIXES: tuple[str, ...] = (
    # Developer-tooling overhead that TPP's `./gradlew assembleCivRelease`
    # does not need. Stripping these shrinks the upload and avoids leaking
    # internal agent / Claude Code / Codex / Speckit state to a public-facing
    # pipeline.
    ".agents/",
    ".claude/",
    ".codex/",
    ".specify/",
    # All documentation. TPP only ever runs `assembleCivRelease`, which reads
    # nothing under docs/. The zip we attach to the GitHub Release as
    # `source-archive-vX.Y.Z.zip` is purely the *TPP build input* — it is NOT
    # the authoritative source snapshot, because GitHub already auto-attaches a
    # full "Source code (zip)" at the tag and the git tag itself carries
    # everything (ADRs, user-guide, research). So docs add provenance value
    # nowhere the zip is consumed; dropping the whole tree (this supersedes the
    # earlier docs/images/-only rule) keeps the upload at the code+config
    # baseline.
    "docs/",
    # Test sources + their fixtures. `assembleCivRelease` compiles only the
    # main sourceSet — it never builds src/test or src/androidTest (spotless is
    # the lone build reference to them and it only runs under the `check` task,
    # not `assemble`). Excluding them: (1) drops ~5.8 MB, almost all of it the
    # binary SQLite fixtures under src/test/resources/fixtures/, and (2) scopes
    # TPP's Fortify scan to the *shipped* code — the test-only findings
    # (hardcoded test passwords, test SQL strings) no longer clutter the
    # published security-scan PDF.
    "app/src/test/",
    "app/src/androidTest/",
    # More inputs `assembleCivRelease` never reads (verified: no exec /
    # commandLine task in any *.gradle references any of them):
    #   specs/      — speckit planning docs (~1.1 MB of spec/plan/tasks/research)
    #   scripts/    — dev + build helper scripts; icons ship as pre-rendered
    #                 PNGs so the build uses those, not the render scripts
    #   test-data/  — standalone taiwan_cities_coords.csv, unreferenced by any
    #                 main or test source
    # All remain in the git tag + GitHub's auto "Source code (zip)"; none is
    # part of the TPP build input, so none belongs in the curated upload.
    "specs/",
    "scripts/",
    "test-data/",
)
EXCLUDE_EXACT: frozenset[str] = frozenset({
    "AGENTS.md",
    "CLAUDE.md",
})

# ---------- Exclusion-safety guard data ----------
# Two failure modes turn a harmless exclusion into a broken TPP build:
#   (A) the rules drop something `assembleCivRelease` actually needs, or
#   (B) a new build wiring (exec / apply from / srcDir) starts depending on a
#       path we exclude.
# check_exclusions_keep_required_inputs (A) and check_no_build_wiring_into_excluded
# (B) below catch each, and run on every zip build + in --check-only.

# Paths `assembleCivRelease` needs; the exclusion rules must never drop them.
# ("file", p) = that exact tracked file must survive. ("glob", p) = at least
# one tracked file under prefix p must survive.
REQUIRED_BUILD_INPUTS: tuple[tuple[str, str], ...] = (
    ("file", "settings.gradle"),
    ("file", "build.gradle"),
    ("file", "gradle.properties"),
    ("file", "gradlew"),
    ("file", "gradlew.bat"),
    ("file", "gradle/wrapper/gradle-wrapper.jar"),
    ("file", "gradle/wrapper/gradle-wrapper.properties"),
    ("file", "app/build.gradle"),
    ("file", "app/src/main/AndroidManifest.xml"),
    ("file", "app/proguard-gradle.txt"),
    ("glob", "app/src/main/java/"),
    ("glob", "app/src/main/res/"),
)

# Known-safe references to an excluded path from an active gradle file. These
# are in the build script but NOT in the assembleCivRelease task graph, so the
# exclusion can't break the release build. Each entry is (gradle file relpath,
# matched token). Anything not listed here trips check_no_build_wiring_into_excluded.
GRADLE_REF_ALLOWLIST: frozenset[tuple[str, str]] = frozenset({
    # spotless formats test sources, but spotlessCheck is wired into `check`,
    # never `assemble` (see app/build.gradle: tasks.named('check')).
    ("app/build.gradle", "src/test/"),
    ("app/build.gradle", "src/androidTest/"),
})

# Anything matching these would indicate a leaked secret. Refuse to ship.
SECRET_BASENAMES: tuple[str, ...] = (
    "local.properties",        # release keystore password lives here
    "release.keystore",        # the keystore itself
    "android_keystore",        # legacy keystore filename
)
SECRET_SUFFIXES: tuple[str, ...] = (
    ".jks",                    # generic Java keystore
    ".p12",                    # PKCS#12
)


# ---------- TPP requirement constants (from docs/pipe/...) ----------

REQUIRED_MANIFEST_ACTIVITY = "com.atakmap.app.component"
REQUIRED_MANIFEST_ACTION = "com.atakmap.app.component"
DEFAULT_REPACKAGE_MARKER = "PluginTemplate"   # MUST be replaced with plugin-specific text
TAKDEV_PLUGIN_ID = "atak-takdev-plugin"       # what apply plugin: line should reference
TPP_RECOMMENDED_GRADLE = "6.9.1"              # per TPP doc FAQ (may be stale)
TPP_ALLOWED_NDK: tuple[str, ...] = (
    "12.1.2977051",
    "21.0.6113669",
    "21.4.7075529",
    "23.0.7599858",
    "25.1.8937393",
)


# ---------- Helpers ----------

class Check:
    """Holds the result of one preflight check."""

    PASS = "PASS"
    WARN = "WARN"
    FAIL = "FAIL"

    def __init__(self, name: str, status: str, detail: str):
        self.name = name
        self.status = status
        self.detail = detail

    def render(self) -> str:
        glyph = {"PASS": "✓", "WARN": "!", "FAIL": "✗"}[self.status]
        return f"  [{glyph} {self.status:<4}] {self.name:<42} {self.detail}"


def repo_root() -> Path:
    p = Path(__file__).resolve().parent
    while p != p.parent:
        if (p / ".git").exists():
            return p
        p = p.parent
    sys.exit("ERROR: could not find repo root (no .git found)")


def run(cmd: list[str], cwd: Path, check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, check=check)


def read_plugin_version(root: Path) -> Optional[str]:
    """Pull PLUGIN_VERSION out of app/build.gradle so the output zip can be
    named with the version (e.g. atak_tw_coord_plugin-source-tpp-v1.0.3.zip)
    for easy identification when shuffling between local builds and TPP
    submissions. Returns None if app/build.gradle is missing or doesn't
    declare PLUGIN_VERSION."""
    bg = root / "app" / "build.gradle"
    if not bg.is_file():
        return None
    m = re.search(r"""PLUGIN_VERSION\s*=\s*["']([^"']+)["']""",
                  bg.read_text(encoding="utf-8"))
    return m.group(1) if m else None


def read_local_property(root: Path, key: str) -> Optional[str]:
    path = root / "local.properties"
    if not path.is_file():
        return None
    # Properties file syntax: key=value. Escape \: → :, \\ → \.
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            continue
        k, _, v = line.partition("=")
        if k.strip() == key:
            return v.strip().replace("\\:", ":").replace("\\\\", "\\")
    return None


# ---------- Static checks ----------

def check_git_clean(root: Path) -> Check:
    """Warn (not fail) if working tree has uncommitted changes — `git archive
    HEAD` will silently snapshot only what's committed."""
    out = run(["git", "status", "--porcelain"], root).stdout.strip()
    if not out:
        return Check("git working tree clean", Check.PASS, "")
    n = len(out.splitlines())
    return Check("git working tree clean", Check.WARN,
                 f"{n} uncommitted change(s) — git archive will snapshot HEAD only")


def check_manifest_activity(root: Path) -> Check:
    """TPP requires a discoverable plugin activity in AndroidManifest."""
    manifest = root / "app" / "src" / "main" / "AndroidManifest.xml"
    if not manifest.is_file():
        return Check("AndroidManifest plugin activity", Check.FAIL,
                     "app/src/main/AndroidManifest.xml not found")
    text = manifest.read_text(encoding="utf-8")
    has_activity = re.search(
        rf'<activity\b[^>]*android:name=["\']{re.escape(REQUIRED_MANIFEST_ACTIVITY)}["\']',
        text)
    has_action = re.search(
        rf'<action\b[^>]*android:name=["\']{re.escape(REQUIRED_MANIFEST_ACTION)}["\']',
        text)
    if has_activity and has_action:
        return Check("AndroidManifest plugin activity", Check.PASS,
                     f"<activity {REQUIRED_MANIFEST_ACTIVITY}> + matching intent-filter present")
    missing = []
    if not has_activity:
        missing.append("activity")
    if not has_action:
        missing.append("intent-filter action")
    return Check("AndroidManifest plugin activity", Check.FAIL,
                 f"missing: {', '.join(missing)}")


def check_proguard_repackage(root: Path) -> Check:
    """TPP requires the proguard -repackageclasses to be customised so crash
    logs identify this plugin (not 'PluginTemplate')."""
    pg = root / "app" / "proguard-gradle.txt"
    if not pg.is_file():
        return Check("proguard -repackageclasses", Check.WARN,
                     "app/proguard-gradle.txt not found")
    text = pg.read_text(encoding="utf-8")
    # Find the -repackageclasses line (may have multiple words after).
    m = re.search(r"-repackageclasses\s+(\S+)", text)
    if not m:
        return Check("proguard -repackageclasses", Check.WARN,
                     "no -repackageclasses directive found (acceptable but TPP recommends one)")
    arg = m.group(1)
    if DEFAULT_REPACKAGE_MARKER.lower() in arg.lower():
        return Check("proguard -repackageclasses", Check.FAIL,
                     f"still uses default '{DEFAULT_REPACKAGE_MARKER}' marker: {arg}")
    return Check("proguard -repackageclasses", Check.PASS,
                 f"customised → {arg}")


def check_takdev_plugin(root: Path) -> Check:
    """TPP requires atak-gradle-takdev to fetch the ATAK SDK."""
    bg = root / "app" / "build.gradle"
    if not bg.is_file():
        return Check("atak-gradle-takdev applied", Check.FAIL,
                     "app/build.gradle not found")
    text = bg.read_text(encoding="utf-8")
    if TAKDEV_PLUGIN_ID in text:
        return Check("atak-gradle-takdev applied", Check.PASS,
                     f"apply plugin: '{TAKDEV_PLUGIN_ID}' present in app/build.gradle")
    return Check("atak-gradle-takdev applied", Check.FAIL,
                 f"'{TAKDEV_PLUGIN_ID}' not referenced in app/build.gradle")


def check_civ_release_target(root: Path) -> Check:
    """assembleCivRelease must be defined (i.e. civ flavor + release buildType
    must both exist)."""
    bg = root / "app" / "build.gradle"
    text = bg.read_text(encoding="utf-8") if bg.is_file() else ""
    has_civ = re.search(r"name\s*:\s*['\"]civ['\"]|productFlavors\s*\{[^}]*\bciv\b",
                        text, flags=re.DOTALL)
    has_release = re.search(r"\brelease\s*\{[^}]*minifyEnabled", text, flags=re.DOTALL)
    if has_civ and has_release:
        return Check("assembleCivRelease target", Check.PASS,
                     "'civ' flavor + 'release' buildType defined in app/build.gradle")
    missing = []
    if not has_civ:
        missing.append("'civ' flavor")
    if not has_release:
        missing.append("'release' buildType")
    return Check("assembleCivRelease target", Check.FAIL,
                 f"missing: {', '.join(missing)}")


def check_gradle_version(root: Path) -> Check:
    """TPP FAQ pins Gradle 6.9.1. We use modern Gradle. Warn — TPP env may
    have updated since the doc was written; only the actual TPP build can
    confirm."""
    wp = root / "gradle" / "wrapper" / "gradle-wrapper.properties"
    if not wp.is_file():
        return Check("gradle version vs TPP FAQ", Check.WARN,
                     "gradle-wrapper.properties not found")
    text = wp.read_text(encoding="utf-8")
    m = re.search(r"gradle-(\d+\.\d+(?:\.\d+)?)-", text)
    ver = m.group(1) if m else "unknown"
    if ver == TPP_RECOMMENDED_GRADLE:
        return Check("gradle version vs TPP FAQ", Check.PASS, f"Gradle {ver} matches TPP FAQ")
    return Check("gradle version vs TPP FAQ", Check.WARN,
                 f"Gradle {ver} (TPP FAQ says {TPP_RECOMMENDED_GRADLE}; may be stale, "
                 "verify via --verify-build with takrepo creds)")


def check_ndk_version(root: Path) -> Check:
    """TPP env has a fixed NDK list. Pure-Java plugins don't need NDK but if
    ndkVersion is pinned it should be one of TPP_ALLOWED_NDK."""
    bg = root / "app" / "build.gradle"
    text = bg.read_text(encoding="utf-8") if bg.is_file() else ""
    m = re.search(r"\bndkVersion\s+['\"]([^'\"]+)['\"]", text)
    if not m:
        return Check("NDK version (if pinned)", Check.PASS,
                     "ndkVersion not pinned (fine for pure-Java plugins)")
    v = m.group(1)
    if v in TPP_ALLOWED_NDK:
        return Check("NDK version (if pinned)", Check.PASS, f"{v} is in TPP allowlist")
    return Check("NDK version (if pinned)", Check.FAIL,
                 f"{v} not in TPP allowlist {TPP_ALLOWED_NDK}")


def _is_excluded(path: str) -> bool:
    """Mirror the strip_dev_tooling() filter: would this repo-relative path be
    dropped from the zip?"""
    return path in EXCLUDE_EXACT or any(path.startswith(p) for p in EXCLUDE_PREFIXES)


def _active_gradle_files(root: Path) -> list[str]:
    """The gradle files actually evaluated by the build: the always-active trio
    plus anything they `apply from`. typst.gradle is deliberately NOT here —
    nothing applies it, so its docs/ reference can't affect assembleCivRelease.
    If someone later applies it, it gets pulled in and its references are then
    scrutinised — which is exactly what we want."""
    seen: list[str] = [rel for rel in ("settings.gradle", "build.gradle", "app/build.gradle")
                       if (root / rel).is_file()]
    i = 0
    while i < len(seen):
        text = (root / seen[i]).read_text(encoding="utf-8", errors="replace")
        for m in re.finditer(r"""apply\s+from\s*:\s*["']([^"']+)["']""", text):
            target = (m.group(1).replace("${rootDir}/", "").replace("$rootDir/", "")
                      .lstrip("./"))
            if target.endswith(".gradle") and target not in seen and (root / target).is_file():
                seen.append(target)
        i += 1
    return seen


def _exclusion_ref_tokens(prefix: str) -> set[str]:
    """Path tokens a gradle file might use to reference `prefix`. app/-rooted
    prefixes are also written module-relative (app/build.gradle says
    'src/test/java', not 'app/src/test/java')."""
    tokens = {prefix}
    if prefix.startswith("app/"):
        tokens.add(prefix[len("app/"):])
    return tokens


def check_exclusions_keep_required_inputs(root: Path) -> Check:
    """(A) Fail if the exclusion rules would drop a file assembleCivRelease needs."""
    tracked = run(["git", "ls-files"], root).stdout.splitlines()
    tracked_set = set(tracked)
    surviving = [p for p in tracked if not _is_excluded(p)]
    surviving_set = set(surviving)
    missing: list[str] = []
    for kind, val in REQUIRED_BUILD_INPUTS:
        if kind == "file":
            if val in tracked_set and val not in surviving_set:
                missing.append(val)
        else:  # glob: at least one surviving entry under the prefix
            if any(t.startswith(val) for t in tracked) and \
                    not any(t.startswith(val) for t in surviving):
                missing.append(val + "*")
    if missing:
        return Check("required build inputs survive exclusion", Check.FAIL,
                     f"exclusion rules would drop build-critical: {', '.join(missing)}")
    return Check("required build inputs survive exclusion", Check.PASS,
                 f"all {len(REQUIRED_BUILD_INPUTS)} build-critical inputs retained")


def check_no_build_wiring_into_excluded(root: Path) -> Check:
    """(B) Fail if an active gradle file wires an excluded path into the build
    (and it isn't a known-safe, non-assemble reference on the allowlist)."""
    actives = _active_gradle_files(root)
    offenders: list[str] = []
    for rel in actives:
        lines = (root / rel).read_text(encoding="utf-8", errors="replace").splitlines()
        for prefix in EXCLUDE_PREFIXES:
            for token in _exclusion_ref_tokens(prefix):
                if (rel, token) in GRADLE_REF_ALLOWLIST:
                    continue
                for lineno, line in enumerate(lines, 1):
                    if token in line:
                        offenders.append(f"{rel}:{lineno} → '{token}' (excluded by '{prefix}')")
    if offenders:
        return Check("excluded paths are not build inputs", Check.FAIL,
                     "; ".join(offenders[:3]) + ("…" if len(offenders) > 3 else "") +
                     " — remove the dependency, or allowlist it if it's outside "
                     "the assembleCivRelease task graph")
    return Check("excluded paths are not build inputs", Check.PASS,
                 f"no active gradle file ({', '.join(actives)}) wires an excluded path in")


STATIC_CHECKS = [
    check_git_clean,
    check_manifest_activity,
    check_proguard_repackage,
    check_takdev_plugin,
    check_civ_release_target,
    check_gradle_version,
    check_ndk_version,
    check_exclusions_keep_required_inputs,
    check_no_build_wiring_into_excluded,
]


# ---------- Verify-build (the TPP-equivalent rebuild) ----------

def verify_build(root: Path) -> Check:
    """Run the exact command the TPP doc says 'should build successfully' as
    a precondition for TPP success:

        ./gradlew -Ptakrepo.force=true
                  -Ptakrepo.url=https://artifacts.tak.gov/artifactory/maven
                  -Ptakrepo.user=<user>
                  -Ptakrepo.password=<pass>
                  assembleCivRelease

    Reads credentials from local.properties (takrepo.url / takrepo.user /
    takrepo.password). Fails fast with a usable error if any are missing.
    """
    url = read_local_property(root, "takrepo.url")
    user = read_local_property(root, "takrepo.user")
    pw = read_local_property(root, "takrepo.password")
    missing = [k for k, v in (("takrepo.url", url), ("takrepo.user", user),
                              ("takrepo.password", pw)) if not v]
    if missing:
        return Check("TPP-equivalent rebuild", Check.FAIL,
                     f"local.properties missing: {', '.join(missing)} — "
                     "request artifacts.tak.gov credentials first")
    gradlew = "gradlew.bat" if os.name == "nt" else "./gradlew"
    cmd = [
        gradlew, "clean", ":app:assembleCivRelease",
        "-Ptakrepo.force=true",
        f"-Ptakrepo.url={url}",
        f"-Ptakrepo.user={user}",
        f"-Ptakrepo.password={pw}",
    ]
    print()
    print("Running TPP-equivalent rebuild — this is the literal command the")
    print("TPP doc says must succeed for submission to succeed. Streaming output…")
    print("  $", " ".join(c if not c.startswith("-Ptakrepo.password=") else
                          "-Ptakrepo.password=<redacted>" for c in cmd))
    print()
    res = subprocess.run(cmd, cwd=root)
    if res.returncode == 0:
        return Check("TPP-equivalent rebuild", Check.PASS,
                     "assembleCivRelease succeeded with takrepo credentials")
    return Check("TPP-equivalent rebuild", Check.FAIL,
                 f"gradle exited {res.returncode} — fix above before submitting")


# ---------- Archive build + safety ----------

def repo_name(root: Path) -> str:
    """Use rootProject.name from settings.gradle as the zip root folder.

    TPP names every output APK after the zip's root folder. Our build.gradle
    derives archivesBaseName from rootProject.name (see
    "ATAK-Plugin-" + rootProject.name + "-..." in app/build.gradle's
    sourceSets.main), so matching the zip root to rootProject.name keeps
    TPP's APK names aligned with our local convention.

    Falls back to the on-disk directory name if settings.gradle can't be
    parsed."""
    sg = root / "settings.gradle"
    if sg.is_file():
        m = re.search(r"""rootProject\.name\s*=\s*["']([^"']+)["']""",
                      sg.read_text(encoding="utf-8"))
        if m:
            return m.group(1)
    return root.name


def git_archive_args(ref: str, prefix: str) -> list[str]:
    """Build a git-archive command that omits excluded paths up front.

    Filtering before archive creation is more than an optimisation: historical
    documentation images are regular Git blobs covered by newer Git LFS
    attributes. Asking Git to archive those excluded blobs can invoke the LFS
    smudge filter and fail before strip_dev_tooling() gets a chance to remove
    them. Pathspec exclusions keep developer-only paths out of both the filter
    pipeline and the temporary archive while preserving LFS expansion for any
    future build input that genuinely needs it.
    """
    args = ["git", "archive", ref, "--format=zip", f"--prefix={prefix}/", "--", "."]
    args.extend(f":(exclude,top){path}**" for path in EXCLUDE_PREFIXES)
    args.extend(f":(exclude,top){path}" for path in sorted(EXCLUDE_EXACT))
    return args


def git_archive_bytes(root: Path, ref: str, prefix: str) -> bytes:
    res = run(git_archive_args(ref, prefix), root, check=False)
    if res.returncode != 0:
        sys.stderr.write(res.stderr)
        sys.exit(f"git archive failed for ref '{ref}'")
    return res.stdout.encode("latin-1") if isinstance(res.stdout, str) else res.stdout


def git_archive_to_file(root: Path, ref: str, prefix: str, out: Path) -> None:
    """git archive can produce binary, use bytes-mode subprocess to avoid
    accidental decoding."""
    with out.open("wb") as fh:
        res = subprocess.run(
            git_archive_args(ref, prefix),
            cwd=root, stdout=fh, stderr=subprocess.PIPE, check=False)
    if res.returncode != 0:
        sys.stderr.write(res.stderr.decode("utf-8", errors="replace"))
        sys.exit(f"git archive failed for ref '{ref}'")


def strip_dev_tooling(src: Path, dst: Path, prefix: str) -> tuple[int, int]:
    kept = dropped = 0
    with zipfile.ZipFile(src, "r") as zin, \
            zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zout:
        for info in zin.infolist():
            inside = info.filename
            if inside.startswith(prefix + "/"):
                inside = inside[len(prefix) + 1:]
            if inside in EXCLUDE_EXACT or any(
                    inside.startswith(p) for p in EXCLUDE_PREFIXES):
                dropped += 1
                continue
            with zin.open(info) as f:
                zout.writestr(info, f.read())
            kept += 1
    return kept, dropped


def verify_safety(zip_path: Path) -> Check:
    with zipfile.ZipFile(zip_path, "r") as z:
        bad = []
        for n in z.namelist():
            base = os.path.basename(n)
            if base in SECRET_BASENAMES or any(base.endswith(s) for s in SECRET_SUFFIXES):
                bad.append(n)
    if bad:
        return Check("archive contains no secrets", Check.FAIL,
                     f"matches: {bad[:3]}{'…' if len(bad) > 3 else ''}")
    return Check("archive contains no secrets", Check.PASS,
                 "no local.properties / *.keystore / *.jks entries")


def verify_single_root(zip_path: Path, expected_root: str) -> Check:
    with zipfile.ZipFile(zip_path, "r") as z:
        names = z.namelist()
    if not names:
        return Check("archive single root folder", Check.FAIL, "archive is empty")
    bad = [n for n in names if not n.startswith(expected_root + "/")]
    if bad:
        return Check("archive single root folder", Check.FAIL,
                     f"entries outside {expected_root}/: {bad[:3]}")
    return Check("archive single root folder", Check.PASS,
                 f"all {len(names)} entries under {expected_root}/")


def verify_gradle_wrapper(zip_path: Path, expected_root: str) -> Check:
    """TPP failure 2026-05-17 (06:13 build) was caused by gradle-wrapper.jar
    being .gitignored via the global *.jar rule. Without it, TPP's bootstrap
    fails immediately with:
        Error: Could not find or load main class
        org.gradle.wrapper.GradleWrapperMain
    so we always assert it's present in the zip."""
    needed = f"{expected_root}/gradle/wrapper/gradle-wrapper.jar"
    with zipfile.ZipFile(zip_path, "r") as z:
        if needed in z.namelist():
            return Check("gradle-wrapper.jar present", Check.PASS,
                         f"{needed} found")
    return Check("gradle-wrapper.jar present", Check.FAIL,
                 f"missing {needed} — add `!gradle/wrapper/gradle-wrapper.jar` "
                 "to .gitignore + git add it")


# ---------- Main ----------

def main() -> int:
    root = repo_root()
    ap = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", type=Path, default=None,
                    help="Output zip path "
                         "(default: build/<repo>-source-tpp-v<PLUGIN_VERSION>.zip; "
                         "falls back to build/<repo>-source-tpp.zip when "
                         "PLUGIN_VERSION cannot be parsed from app/build.gradle).")
    ap.add_argument("--ref", default="HEAD",
                    help="Git ref to archive (default: HEAD).")
    ap.add_argument("--check-only", action="store_true",
                    help="Run static checks only; don't build the zip.")
    ap.add_argument("--verify-build", action="store_true",
                    help="Also run the TPP-equivalent rebuild — requires "
                         "takrepo.url/user/password in local.properties.")
    args = ap.parse_args()

    name = repo_name(root)
    version = read_plugin_version(root)
    default_basename = (f"{name}-source-tpp-v{version}.zip" if version
                        else f"{name}-source-tpp.zip")
    out_path = args.out or (root / "build" / default_basename)

    print("=== TPP submission preflight ===")
    print(f"Repo:        {root}")
    print(f"Repo name:   {name}  (zip root + TPP APK name prefix)")
    print(f"Ref:         {args.ref}")
    sha = run(["git", "rev-parse", "--short", args.ref], root, check=False)
    if sha.returncode != 0:
        sys.exit(f"git rev-parse failed for '{args.ref}'")
    print(f"SHA:         {sha.stdout.strip()}")
    print(f"Output:      {out_path if not args.check_only else '(skipped — --check-only)'}")
    print()

    # Static checks always run.
    print("--- Static checks (TPP requirements per docs/pipe/...) ---")
    results: list[Check] = []
    for fn in STATIC_CHECKS:
        c = fn(root)
        print(c.render())
        results.append(c)

    # Build zip (unless check-only) and run archive-shape checks against it.
    if not args.check_only:
        print()
        print("--- Building source archive ---")
        out_path.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory() as tmp:
            raw = Path(tmp) / "raw.zip"
            git_archive_to_file(root, args.ref, name, raw)
            kept, dropped = strip_dev_tooling(raw, out_path, name)
        print(f"  wrote {out_path} (kept {kept} files, "
              f"dropped {dropped} dev-tooling entries)")
        size = out_path.stat().st_size
        print(f"  size: {size:,} bytes ({size / 1024:.1f} KB)")
        print()
        print("--- Archive shape checks ---")
        for c in (verify_single_root(out_path, name),
                  verify_gradle_wrapper(out_path, name),
                  verify_safety(out_path)):
            print(c.render())
            results.append(c)

    # Optional verify-build (slow, opt-in).
    if args.verify_build:
        print()
        print("--- TPP-equivalent rebuild (--verify-build) ---")
        c = verify_build(root)
        print(c.render())
        results.append(c)

    # Summary + exit code.
    print()
    print("--- Summary ---")
    fails = [c for c in results if c.status == Check.FAIL]
    warns = [c for c in results if c.status == Check.WARN]
    passes = [c for c in results if c.status == Check.PASS]
    print(f"  {len(passes)} PASS, {len(warns)} WARN, {len(fails)} FAIL")
    if fails:
        print("  → NOT ready to submit. Fix FAIL items above.")
        return 1
    if warns:
        print("  → Ready to upload, but review WARN items above first.")
    else:
        print("  → Ready to upload.")
    if not args.check_only:
        print(f"  → Upload: {out_path}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\nAborted.")
        sys.exit(130)
