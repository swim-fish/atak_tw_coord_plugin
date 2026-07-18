#!/usr/bin/env python3
"""Remove EXIF/XMP and sensitive PNG text chunks without changing pixels."""

from __future__ import annotations

import argparse
import struct
from pathlib import Path


PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
SENSITIVE_PNG_CHUNKS = {b"eXIf", b"tEXt", b"zTXt", b"iTXt"}


def strip_jpeg(data: bytes) -> bytes:
    if not data.startswith(b"\xff\xd8"):
        raise ValueError("invalid JPEG")
    output = bytearray(data[:2])
    offset = 2
    while offset < len(data):
        if data[offset] != 0xFF:
            output.extend(data[offset:])
            break
        marker_start = offset
        while offset < len(data) and data[offset] == 0xFF:
            offset += 1
        if offset >= len(data):
            break
        marker = data[offset]
        offset += 1
        if marker == 0xDA:
            output.extend(data[marker_start:])
            break
        if marker in (0x01, *range(0xD0, 0xD9)):
            output.extend(data[marker_start:offset])
            continue
        if offset + 2 > len(data):
            raise ValueError("truncated JPEG segment")
        length = struct.unpack_from(">H", data, offset)[0]
        segment_end = offset + length
        if segment_end > len(data):
            raise ValueError("invalid JPEG segment length")
        payload = data[offset + 2:segment_end]
        is_metadata = marker == 0xE1 and (
            payload.startswith(b"Exif\x00\x00") or
            payload.startswith(b"http://ns.adobe.com/xap/1.0/\x00"))
        if not is_metadata:
            output.extend(data[marker_start:segment_end])
        offset = segment_end
    return bytes(output)


def strip_png(data: bytes) -> bytes:
    if not data.startswith(PNG_SIGNATURE):
        raise ValueError("invalid PNG")
    output = bytearray(PNG_SIGNATURE)
    offset = len(PNG_SIGNATURE)
    while offset + 12 <= len(data):
        length = struct.unpack_from(">I", data, offset)[0]
        end = offset + 12 + length
        if end > len(data):
            raise ValueError("invalid PNG chunk length")
        kind = data[offset + 4:offset + 8]
        if kind not in SENSITIVE_PNG_CHUNKS:
            output.extend(data[offset:end])
        offset = end
    if offset != len(data):
        raise ValueError("trailing or truncated PNG data")
    return bytes(output)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="*", type=Path,
                        help="Images to scrub (default: docs/images/*.{jpg,jpeg,png})")
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    paths = args.paths or sorted(
        path for path in (root / "docs" / "images").iterdir()
        if path.suffix.lower() in (".jpg", ".jpeg", ".png"))
    changed = 0
    for path in paths:
        data = path.read_bytes()
        cleaned = strip_png(data) if path.suffix.lower() == ".png" else strip_jpeg(data)
        if cleaned != data:
            path.write_bytes(cleaned)
            changed += 1
            print(f"scrubbed {path.relative_to(root) if path.is_relative_to(root) else path}")
    print(f"Changed {changed} of {len(paths)} image(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
