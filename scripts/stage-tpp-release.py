#!/usr/bin/env python3
"""Stage a public GitHub Release directory from a raw TPP result bundle.

The TAK Third Party Pipeline (tak.gov/user_builds) emails back a bundle named
``<email>-<YYYYMMDD>-<HHMMSS>.zip`` containing 11 files. Only 4 of them are
shippable to end users; the rest are build/scan diagnostics that nobody
sideloading a plugin needs (full Gradle log, raw Fortify logs/txt, the
Fortify-proprietary .fpr, and the Play-Store .aab). See ADR-0013 Stage 4.

This script extracts ONLY the essential files, renames them to the public
naming convention, drops everything else, and prints the ready-to-run
``gh release create`` command. It is the automated form of ADR-0013 Stage 4.

    # Stage from a TPP bundle (version + ATAK target auto-detected from the
    # APK filename inside the bundle):
    python scripts/stage-tpp-release.py build/hhhnrnew82-gmail-com-20260530-174239.zip

    # Override anything auto-detection gets wrong:
    python scripts/stage-tpp-release.py <bundle.zip> --version 1.2.0 \
        --atak-display 5.4+ --public-name TWCoord

What it produces in build/release-v<VERSION>/ (5 assets, matching Stage 6):

    ATAK-Plugin-<PUBLIC_NAME>-v<VERSION>-ATAK-<ATAK_DISPLAY>.apk   (from *-unsigned.apk)
    mapping-v<VERSION>.txt                                          (from civRelease-app-mapping.txt)
    security-scan-v<VERSION>.pdf                                    (from fortify_scan_results.pdf)
    dependency-check-v<VERSION>.html                               (from dependency-check-report.html)
    source-archive-v<VERSION>.zip                                  (from build/<repo>-source-tpp-v<VERSION>.zip, if present)

Exit codes: 0 = staged OK, 1 = a required bundle file was missing, 2 = script error.
"""

from __future__ import annotations

import argparse
import hashlib
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path
from typing import Optional

# ---------- Public naming convention (ADR-0013 Stage 4) ----------

DEFAULT_PUBLIC_NAME = "TWCoord"   # short, user-facing name; rootProject.name is atak_tw_coord_plugin

# The signer cert TPP re-signs every release with. ADR-0013 §Stage 3: if this
# ever changes, end users must uninstall before updating — so we warn loudly.
EXPECTED_SIGNER_SHA256 = "f24a38057275fcecf67be975ab803d12f75dc23581bef69cba9eb03a15bb8c17"

# ---------- Bundle entry classification ----------
# Each essential file is matched by a predicate against the bundle entry's
# basename, then renamed via a template that gets {ver} / {name} / {atak}.

ESSENTIAL = [
    # (label, match predicate, rename template)
    ("apk",
     lambda b: b.endswith("-release-unsigned.apk"),
     "ATAK-Plugin-{name}-v{ver}-ATAK-{atak}.apk"),
    ("mapping",
     lambda b: b == "civRelease-app-mapping.txt",
     "mapping-v{ver}.txt"),
    ("fortify-pdf",
     lambda b: b == "fortify_scan_results.pdf",
     "security-scan-v{ver}.pdf"),
    ("dep-check",
     lambda b: b == "dependency-check-report.html",
     "dependency-check-v{ver}.html"),
]

# Everything else in a TPP bundle is diagnostic-only and deliberately dropped.
# Listed for the human-readable summary, not used for matching.
KNOWN_NONESSENTIAL = (
    "build.log",
    "fortify_analyze.log",
    "fortify_analyze_FortifySupport.log",
    "fortify_scan.txt",
    "fortify_scan_FortifySupport.txt",
    "scan_results.fpr",
    "*.aab",
)


def repo_root() -> Path:
    p = Path(__file__).resolve().parent
    while p != p.parent:
        if (p / ".git").exists():
            return p
        p = p.parent
    sys.exit("ERROR: could not find repo root (no .git found)")


def read_plugin_version(root: Path) -> Optional[str]:
    bg = root / "app" / "build.gradle"
    if not bg.is_file():
        return None
    m = re.search(r"""PLUGIN_VERSION\s*=\s*["']([^"']+)["']""",
                  bg.read_text(encoding="utf-8"))
    return m.group(1) if m else None


def repo_name(root: Path) -> str:
    sg = root / "settings.gradle"
    if sg.is_file():
        m = re.search(r"""rootProject\.name\s*=\s*["']([^"']+)["']""",
                      sg.read_text(encoding="utf-8"))
        if m:
            return m.group(1)
    return root.name


def detect_from_apk_name(bundle_basenames: list[str]) -> tuple[Optional[str], Optional[str]]:
    """TPP names the APK
    ATAK-Plugin-<rootProjectName>-<VERSION>--<ATAK_VERSION>-civ-release-unsigned.apk
    Pull (version, atak_version) out of it so the caller needn't pass them."""
    for b in bundle_basenames:
        m = re.search(
            r"^ATAK-Plugin-.+?-(\d+\.\d+\.\d+)--(\d+\.\d+(?:\.\d+)?)-civ-release-unsigned\.apk$",
            b)
        if m:
            return m.group(1), m.group(2)
    return None, None


def atak_display_from_version(atak_version: str) -> str:
    """5.4.0 -> 5.4+ (the user-facing 'works on ATAK X.Y and up' convention)."""
    parts = atak_version.split(".")
    return ".".join(parts[:2]) + "+"


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def verify_signer(apk: Path) -> Optional[str]:
    """Best-effort signer-cert SHA-256 (lowercase hex, no colons). Tries
    apksigner first (Android build-tools — also validates the signature), then
    falls back to keytool, which ships with the JDK and is therefore present on
    any box that can build this plugin. Returns None only if neither tool is
    available or the APK carries no signer cert. Never fatal."""
    # apksigner (Android build-tools): "SHA-256 digest: <64 hex>"
    exe = shutil.which("apksigner") or shutil.which("apksigner.bat")
    if exe:
        try:
            res = subprocess.run([exe, "verify", "--print-certs", str(apk)],
                                 capture_output=True, text=True, check=False)
            if res.returncode == 0:
                m = re.search(r"SHA-256 digest:\s*([0-9a-fA-F]{64})", res.stdout)
                if m:
                    return m.group(1).lower()
        except OSError:
            pass
    # keytool (JDK) fallback: "SHA256: F2:4A:38:..." (colon-separated, uppercase)
    kt = shutil.which("keytool") or shutil.which("keytool.exe")
    if kt:
        try:
            res = subprocess.run([kt, "-printcert", "-jarfile", str(apk)],
                                 capture_output=True, text=True, check=False)
            if res.returncode == 0:
                m = re.search(r"SHA256:\s*([0-9A-Fa-f:]+)", res.stdout)
                if m:
                    h = m.group(1).replace(":", "").lower()
                    if len(h) == 64:
                        return h
        except OSError:
            pass
    return None


def main() -> int:
    root = repo_root()
    ap = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("bundle", type=Path,
                    help="The TPP result bundle zip (<email>-<date>-<time>.zip).")
    ap.add_argument("--version", default=None,
                    help="Plugin version X.Y.Z (default: auto-detect from the "
                         "bundle APK name, then app/build.gradle PLUGIN_VERSION).")
    ap.add_argument("--atak-display", default=None,
                    help="User-facing ATAK target string, e.g. '5.4+' (default: "
                         "derived from the bundle APK's ATAK version).")
    ap.add_argument("--public-name", default=DEFAULT_PUBLIC_NAME,
                    help=f"Public short name in the APK filename (default: {DEFAULT_PUBLIC_NAME}).")
    ap.add_argument("--source-zip", type=Path, default=None,
                    help="Path to the Stage-2 source archive to include as "
                         "source-archive-v<VERSION>.zip (default: "
                         "build/<repo>-source-tpp-v<VERSION>.zip if it exists).")
    ap.add_argument("--out", type=Path, default=None,
                    help="Staging directory (default: build/release-v<VERSION>/).")
    args = ap.parse_args()

    if not args.bundle.is_file():
        sys.exit(f"ERROR: bundle not found: {args.bundle}")

    with zipfile.ZipFile(args.bundle, "r") as z:
        entries = [n for n in z.namelist() if not n.endswith("/")]
    basenames = [Path(n).name for n in entries]

    # --- resolve version + ATAK display ---
    det_ver, det_atak = detect_from_apk_name(basenames)
    version = args.version or det_ver or read_plugin_version(root)
    if not version:
        sys.exit("ERROR: could not determine version — pass --version X.Y.Z")
    if args.atak_display:
        atak_display = args.atak_display
    elif det_atak:
        atak_display = atak_display_from_version(det_atak)
    else:
        sys.exit("ERROR: could not determine ATAK target — pass --atak-display 5.4+")

    name = repo_name(root)
    out_dir = args.out or (root / "build" / f"release-v{version}")

    print("=== Stage TPP release bundle (ADR-0013 Stage 4) ===")
    print(f"Bundle:       {args.bundle}")
    print(f"Repo:         {name}")
    print(f"Version:      {version}{'  (auto)' if not args.version else '  (--version)'}")
    print(f"ATAK display: {atak_display}{'  (auto)' if not args.atak_display else '  (--atak-display)'}")
    print(f"Public name:  {args.public_name}")
    print(f"Staging dir:  {out_dir}")
    print()

    fmt = {"ver": version, "name": args.public_name, "atak": atak_display}

    # --- match each essential file to a bundle entry ---
    plan: list[tuple[str, str, str]] = []   # (label, src_entry, dst_basename)
    missing: list[str] = []
    for label, pred, template in ESSENTIAL:
        hit = next((n for n in entries if pred(Path(n).name)), None)
        if hit is None:
            missing.append(label)
            continue
        plan.append((label, hit, template.format(**fmt)))

    if missing:
        print(f"ERROR: bundle is missing required file(s): {', '.join(missing)}")
        print("Bundle contained:")
        for b in sorted(basenames):
            print(f"  - {b}")
        return 1

    # --- extract + rename the essentials, drop the rest ---
    out_dir.mkdir(parents=True, exist_ok=True)
    print("--- Essential files (extracted + renamed) ---")
    with zipfile.ZipFile(args.bundle, "r") as z:
        for label, src, dst in plan:
            data = z.read(src)
            (out_dir / dst).write_bytes(data)
            print(f"  [keep] {Path(src).name:<48} -> {dst}  ({len(data):,} B)")

    kept_srcs = {src for _, src, _ in plan}
    dropped = [Path(n).name for n in entries if n not in kept_srcs]
    print()
    print("--- Non-essential files (dropped — diagnostics only) ---")
    for b in sorted(dropped):
        print(f"  [drop] {b}")

    # --- include Stage-2 source archive if available ---
    src_zip = args.source_zip or (root / "build" / f"{name}-source-tpp-v{version}.zip")
    src_dst = out_dir / f"source-archive-v{version}.zip"
    print()
    print("--- Source archive (Stage 2) ---")
    if src_zip.is_file():
        shutil.copy2(src_zip, src_dst)
        print(f"  [keep] {src_zip.name:<48} -> {src_dst.name}")
    else:
        print(f"  [MISS] {src_zip} not found — run scripts/build-tpp-source-zip.py,")
        print(f"         or pass --source-zip. (Release needs {src_dst.name}.)")

    # --- integrity: SHA-256 + signer cert check ---
    apk_dst = out_dir / next(dst for label, _, dst in plan if label == "apk")
    apk_sha = sha256_file(apk_dst)
    print()
    print("--- APK integrity ---")
    print(f"  file:    {apk_dst.name}")
    print(f"  SHA-256: {apk_sha}")
    signer = verify_signer(apk_dst)
    if signer is None:
        print("  signer:  (neither apksigner nor keytool found, or APK unsigned —")
        print(f"           verify manually: keytool -printcert -jarfile {apk_dst.name})")
    elif signer == EXPECTED_SIGNER_SHA256:
        print(f"  signer:  ✓ TAK Untrusted Plugin Release cert ({signer[:16]}…)")
    else:
        print(f"  signer:  ✗ UNEXPECTED cert {signer}")
        print(f"           expected {EXPECTED_SIGNER_SHA256}")
        print("           A changed signer forces every end user to uninstall before")
        print("           updating — confirm this is intended (ADR-0013 Stage 3).")

    # --- ready-to-run gh release create ---
    assets = [dst for _, _, dst in plan] + [src_dst.name]
    print()
    print("--- Next: GitHub Release (ADR-0013 Stage 6) ---")
    print(f"  Staged {len(assets)} assets in {out_dir}")
    print("  Add RELEASE_NOTES_v{0}.md, then:".format(version))
    print()
    print(f"  cd {out_dir}")
    print(f"  gh release create v{version} \\")
    print(f"    --repo swim-fish/{name} \\")
    print(f"    --title \"v{version} — <one-line summary>\" \\")
    print(f"    --notes-file RELEASE_NOTES_v{version}.md \\")
    print("    --target master \\")
    for a in assets:
        print(f"    {a} \\")
    print(f"    # ^ put SHA-256 {apk_sha[:16]}… in the release-notes table")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\nAborted.")
        sys.exit(130)
