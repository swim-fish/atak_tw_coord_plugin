#!/usr/bin/env python3
"""Stand-alone guard: verify the TPP source-zip exclusion rules cannot break
the TPP build. Cheap enough to run in CI on every commit — no zip is built.

It runs the two exclusion-safety checks that `build-tpp-source-zip.py` also
runs in its preflight:

  1. required build inputs survive exclusion   — catches over-exclusion (a
     rule that drops something assembleCivRelease needs).
  2. excluded paths are not build inputs       — catches the reverse: an
     active gradle file (settings/build/app build.gradle + apply-from targets)
     wiring an excluded path into the build via exec / srcDir / file(...).

Rules and logic are imported from build-tpp-source-zip.py — never duplicated —
so the guard and the generator can't drift. (The generator filename has a
hyphen, so it's loaded by path rather than `import`.)

    python scripts/check-tpp-exclusions.py

Exit codes: 0 = exclusions safe, 1 = a check FAILed, 2 = script error.
"""

from __future__ import annotations

import importlib.util
import sys
from pathlib import Path


def _load_generator():
    path = Path(__file__).resolve().parent / "build-tpp-source-zip.py"
    if not path.is_file():
        sys.exit(f"ERROR: cannot find {path}")
    spec = importlib.util.spec_from_file_location("tpp_gen", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def main() -> int:
    tpp = _load_generator()
    root = tpp.repo_root()

    print("=== TPP exclusion-safety check ===")
    print(f"Repo:              {root}")
    print(f"Excluded prefixes: {', '.join(tpp.EXCLUDE_PREFIXES)}")
    print(f"Excluded files:    {', '.join(sorted(tpp.EXCLUDE_EXACT))}")
    print()

    checks = [
        tpp.check_exclusions_keep_required_inputs(root),
        tpp.check_no_build_wiring_into_excluded(root),
    ]
    for c in checks:
        print(c.render())

    fails = [c for c in checks if c.status == tpp.Check.FAIL]
    print()
    if fails:
        print(f"  {len(fails)} FAIL — an exclusion could break the TPP build. "
              "Fix before generating/uploading the source zip.")
        return 1
    print("  All exclusion-safety checks passed — exclusions are safe.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\nAborted.")
        sys.exit(130)
