#!/usr/bin/env python3
"""Validate documentation image names, links, and sensitive metadata."""

from __future__ import annotations

import re
import struct
import subprocess
import sys
from pathlib import Path


IMAGE_NAME = re.compile(r"^\d{2}[a-z]?-[a-z0-9-]+\.(?:jpg|jpeg|png)$")
HTML_IMAGE = re.compile(r"<img\s+[^>]*src=[\"']([^\"']+)[\"']", re.I)
MARKDOWN_IMAGE = re.compile(r"!\[[^]]*]\(([^)]+)\)")
SENSITIVE_EXIF_TAGS = {
    0x010F, 0x0110, 0x0131, 0x0132, 0x013B, 0x8298, 0x8825,
    0x9003, 0x9004, 0x9286, 0xA430, 0xA431, 0xA432, 0xA433, 0xA434, 0xA435,
}
SENSITIVE_TEXT = re.compile(
    rb"gps|latitude|longitude|author|artist|owner|serial|device|software|datetime",
    re.I)


def jpeg_sensitive_tags(data: bytes) -> set[int]:
    marker = data.find(b"Exif\x00\x00")
    if marker < 0:
        return set()
    tiff = data[marker + 6:]
    if len(tiff) < 8 or tiff[:2] not in (b"II", b"MM"):
        return {0xFFFF}
    endian = "<" if tiff[:2] == b"II" else ">"
    try:
        first = struct.unpack_from(endian + "I", tiff, 4)[0]
    except struct.error:
        return {0xFFFF}
    found: set[int] = set()
    pending = [first]
    visited: set[int] = set()
    while pending:
        offset = pending.pop()
        if offset in visited or offset + 2 > len(tiff):
            continue
        visited.add(offset)
        try:
            count = struct.unpack_from(endian + "H", tiff, offset)[0]
            for index in range(count):
                entry = offset + 2 + index * 12
                tag = struct.unpack_from(endian + "H", tiff, entry)[0]
                value = struct.unpack_from(endian + "I", tiff, entry + 8)[0]
                if tag in SENSITIVE_EXIF_TAGS:
                    found.add(tag)
                if tag in (0x8769, 0x8825):
                    pending.append(value)
        except struct.error:
            found.add(0xFFFF)
    return found


def png_has_sensitive_metadata(data: bytes) -> bool:
    offset = 8
    while offset + 12 <= len(data):
        length = struct.unpack_from(">I", data, offset)[0]
        kind = data[offset + 4:offset + 8]
        payload = data[offset + 8:offset + 8 + length]
        if kind == b"eXIf" or (kind in (b"tEXt", b"iTXt", b"zTXt") and
                                SENSITIVE_TEXT.search(payload)):
            return True
        offset += 12 + length
    return False


def references(docs: Path) -> list[tuple[Path, str]]:
    found: list[tuple[Path, str]] = []
    for markdown in docs.rglob("*.md"):
        text = markdown.read_text(encoding="utf-8", errors="replace")
        for match in (*HTML_IMAGE.findall(text), *MARKDOWN_IMAGE.findall(text)):
            target = match.split("#", 1)[0].strip("<>")
            if "://" not in target and not target.startswith("data:"):
                found.append((markdown, target))
    return found


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    docs = root / "docs"
    image_dir = docs / "images"
    errors: list[str] = []
    images = [path for path in image_dir.iterdir()
              if path.suffix.lower() in (".jpg", ".jpeg", ".png")]
    for image in images:
        if not IMAGE_NAME.fullmatch(image.name):
            errors.append(f"invalid image filename: {image.name}")
        data = image.read_bytes()
        if image.suffix.lower() in (".jpg", ".jpeg"):
            tags = jpeg_sensitive_tags(data)
            if tags:
                errors.append(f"sensitive or malformed EXIF: {image.name} ({len(tags)} tag(s))")
        elif png_has_sensitive_metadata(data):
            errors.append(f"sensitive PNG metadata: {image.name}")

    if images:
        attrs = subprocess.run(
            ["git", "check-attr", "filter", "--",
             *[str(path.relative_to(root)) for path in images]],
            cwd=root, capture_output=True, text=True, check=False)
        if attrs.returncode != 0:
            errors.append("could not verify Git LFS attributes")
        else:
            lfs_paths = {
                line.split(": filter:", 1)[0].strip('"').replace("\\\\", "/").replace("\\", "/")
                for line in attrs.stdout.splitlines()
                if line.rstrip().endswith(": lfs")
            }
            for image in images:
                relative = image.relative_to(root).as_posix()
                if relative not in lfs_paths:
                    errors.append(f"image is not covered by Git LFS: {relative}")

    for markdown, target in references(docs):
        resolved = (markdown.parent / target).resolve()
        if not resolved.is_file():
            errors.append(f"missing image link: {markdown.relative_to(root)} -> {target}")

    print(f"Checked {len(images)} documentation images.")
    for error in errors:
        print(f"[FAIL] {error}")
    if errors:
        return 1
    print("[PASS] names, local links, Git LFS, and sensitive metadata")
    return 0


if __name__ == "__main__":
    sys.exit(main())
