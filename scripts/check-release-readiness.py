#!/usr/bin/env python3
"""Report whether the committed candidate is ready for TPP or publication."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Result:
    name: str
    status: str
    detail: str


def run(root: Path, *args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, cwd=root, capture_output=True, text=True,
                          check=False)


def root_from_script() -> Path:
    root = Path(__file__).resolve().parent.parent
    if not (root / ".git").exists():
        raise RuntimeError("repository root not found")
    return root


def read_version(root: Path) -> str:
    text = (root / "app" / "build.gradle").read_text(encoding="utf-8")
    match = re.search(r'PLUGIN_VERSION\s*=\s*["\']([^"\']+)', text)
    if not match:
        raise RuntimeError("PLUGIN_VERSION not found in app/build.gradle")
    return match.group(1)


def active_feature(root: Path) -> Path:
    data = json.loads((root / ".specify" / "feature.json").read_text(encoding="utf-8"))
    feature = root / data["feature_directory"]
    if not feature.is_dir():
        raise RuntimeError(f"active feature directory not found: {feature}")
    return feature


def open_release_gates(scope: Path) -> list[str]:
    gates: list[str] = []
    for path in sorted(scope.rglob("*.md")):
        for line_number, line in enumerate(path.read_text(
                encoding="utf-8", errors="replace").splitlines(), 1):
            if re.match(r"^- \[ \].*\[RELEASE-GATE\]", line):
                gates.append(f"{path.relative_to(scope).as_posix()}:{line_number}")
    return gates


def version_results(root: Path, version: str) -> list[Result]:
    expected = {
        "CHANGELOG.md": rf"^## \[{re.escape(version)}\]",
        "docs/user-guide.md": rf"\*\*Version:\*\* v{re.escape(version)}\b",
        "docs/user-guide_zh.md": rf"\*\*對應版本：\*\* v{re.escape(version)}\b",
    }
    results: list[Result] = []
    for relative, pattern in expected.items():
        text = (root / relative).read_text(encoding="utf-8")
        status = "PASS" if re.search(pattern, text, re.MULTILINE) else "FAIL"
        results.append(Result(f"version in {relative}", status, version))
    return results


def inspect(root: Path, phase: str) -> tuple[list[Result], str]:
    version = read_version(root)
    results = version_results(root, version)

    status = run(root, "git", "status", "--porcelain")
    dirty_count = len(status.stdout.splitlines())
    results.append(Result("git working tree clean",
                          "PASS" if dirty_count == 0 else "FAIL",
                          "clean" if dirty_count == 0 else f"{dirty_count} change(s)"))

    sha = run(root, "git", "rev-parse", "HEAD")
    results.append(Result("source commit", "PASS" if sha.returncode == 0 else "FAIL",
                          sha.stdout.strip() if sha.returncode == 0 else "unresolved"))

    branch = run(root, "git", "branch", "--show-current").stdout.strip()
    branch_status = "PASS" if phase == "tpp" or branch == "master" else "FAIL"
    results.append(Result("release branch", branch_status, branch or "detached"))

    active_feature(root)  # Validate the project routing pointer before release.
    gates = open_release_gates(root / "specs")
    gate_status = "PASS" if not gates else ("WARN" if phase == "tpp" else "FAIL")
    detail = "none open" if not gates else f"{len(gates)} open: {', '.join(gates)}"
    results.append(Result("release gates", gate_status, detail))

    outcome = "TPP_READY" if phase == "tpp" else "PUBLIC_RELEASE_READY"
    if any(result.status == "FAIL" for result in results):
        outcome = "BLOCKED"
    return results, outcome


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--phase", choices=("tpp", "public"), required=True)
    args = parser.parse_args()
    root = root_from_script()
    try:
        results, outcome = inspect(root, args.phase)
    except (OSError, KeyError, ValueError, RuntimeError) as error:
        print(f"ERROR: {error}")
        return 2

    print(f"=== Release readiness: {args.phase} ===")
    for result in results:
        print(f"[{result.status:<4}] {result.name}: {result.detail}")
    print(f"OUTCOME: {outcome}")
    return 0 if outcome != "BLOCKED" else 1


if __name__ == "__main__":
    sys.exit(main())
