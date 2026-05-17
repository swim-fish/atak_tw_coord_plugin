#!/usr/bin/env python3
"""Render the coloured plugin icon (`ic_tw_coord_plugin.png`) at five
densities by reusing the TW polygon geometry from the silhouette vector
source (`ic_tw_coord.xml`).

Why bother:
  - The silhouette XML is the one and only place the TW glyph shape is
    defined. Letting both icons trace back to that source keeps them
    visually identical — same letter forms, same stencil cut on T, same
    corner bracket positions — just in different colours / on a different
    background.
  - This replaces the old `tmp/icon-candidates/install.py` (removed) that
    hard-coded the polygons in Python and used a TrueType font for the TW
    text, producing a subtly different glyph from the silhouette version.

Pipeline:
  - Parse `app/src/main/res/drawable/ic_tw_coord.xml` for every <path>
    pathData (only M/L/Z commands appear there — polygons only).
  - Drop the path's `fillColor` (always #FFFFFF in the silhouette source)
    and recolour each polygon family by index:
      - paths 0..3 → corner brackets    → SAND_DIM
      - paths 4..6 → T (3 rectangles)   → SAND
      - paths 7..10 → W (4 strokes)     → SAND
  - Composite over an OD hexagonal plate (filled OD + dark OD outline).
  - Downsample to 5 Android densities (mdpi 48 / hdpi 72 / xhdpi 96 /
    xxhdpi 144 / xxxhdpi 192).

Outputs:
    app/src/main/res/drawable-mdpi/ic_tw_coord_plugin.png   (48 px)
    app/src/main/res/drawable-hdpi/ic_tw_coord_plugin.png   (72 px)
    app/src/main/res/drawable-xhdpi/ic_tw_coord_plugin.png  (96 px)
    app/src/main/res/drawable-xxhdpi/ic_tw_coord_plugin.png (144 px)
    app/src/main/res/drawable-xxxhdpi/ic_tw_coord_plugin.png (192 px)
"""

from __future__ import annotations

import math
import re
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image, ImageDraw

REPO = Path(__file__).resolve().parent.parent
RES_DIR = REPO / "app" / "src" / "main" / "res"
SILHOUETTE_XML = RES_DIR / "drawable" / "ic_tw_coord.xml"

SIZE = 192            # native design canvas (matches silhouette viewport)
HEX_RADIUS = 86       # OD plate apothem-ish (vertex-to-centre)
HEX_OUTLINE_WIDTH = 5

# MIL-STD tactical palette — same as the marker-mode icons elsewhere in
# the plugin so the brand reads consistently.
OD       = (91, 107, 58, 255)
OD_DARK  = (66, 78, 42, 255)
SAND     = (197, 176, 122, 255)
SAND_DIM = (157, 140, 95, 255)

DENSITIES = [
    ("drawable-mdpi",    48),
    ("drawable-hdpi",    72),
    ("drawable-xhdpi",   96),
    ("drawable-xxhdpi", 144),
    ("drawable-xxxhdpi",192),
]

PATH_TOKEN = re.compile(r"[MLHVZACSQTaclshvzqtsc]|-?\d+(?:\.\d+)?")


def parse_polygon(d: str) -> list[tuple[float, float]]:
    """Pull every (x, y) coordinate out of an M/L/Z-only path string.
    The silhouette source uses only polygons, so this is sufficient — if
    a future revision adds curves, the script will silently lose detail
    and the resulting colour PNG will look wrong; bake a richer parser
    when that day comes."""
    tokens = PATH_TOKEN.findall(d)
    pts: list[tuple[float, float]] = []
    i = 0
    while i < len(tokens):
        tok = tokens[i]; i += 1
        if tok in "ML":
            x = float(tokens[i]); i += 1
            y = float(tokens[i]); i += 1
            pts.append((x, y))
        elif tok in "Zz":
            continue
        else:
            # Skip stray numbers for any malformed path; defensive only.
            if not tok[0].isalpha():
                pass
    return pts


def load_silhouette_polygons() -> list[list[tuple[float, float]]]:
    tree = ET.parse(SILHOUETTE_XML)
    root = tree.getroot()
    ns = "{http://schemas.android.com/apk/res/android}"
    polys: list[list[tuple[float, float]]] = []
    for path in root.findall("path"):
        d = path.attrib.get(ns + "pathData", "")
        polys.append(parse_polygon(d))
    return polys


def draw_hex_plate(d: ImageDraw.ImageDraw, center: tuple[int, int]) -> None:
    """Flat-top hexagon at `center` with apothem HEX_RADIUS."""
    cx, cy = center
    pts = []
    for i in range(6):
        ang = math.radians(60 * i)
        pts.append((cx + HEX_RADIUS * math.cos(ang),
                    cy + HEX_RADIUS * math.sin(ang)))
    d.polygon(pts, fill=OD, outline=OD_DARK, width=HEX_OUTLINE_WIDTH)


def render_master() -> Image.Image:
    """Build the canonical 192×192 coloured icon. Density variants are
    downsampled from this."""
    polys = load_silhouette_polygons()
    if len(polys) != 11:
        raise SystemExit(
            f"Unexpected path count in {SILHOUETTE_XML.relative_to(REPO)}: "
            f"got {len(polys)}, expected 11 (4 brackets + 3 T + 4 W). "
            "Update the family-by-index colour mapping below to match.")

    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    draw_hex_plate(d, (SIZE // 2, SIZE // 2))

    # Family colour mapping — see module docstring for path-index layout.
    # Bracket index range, T index range, W index range.
    BRACKETS = range(0, 4)
    T_GLYPH  = range(4, 7)
    W_GLYPH  = range(7, 11)
    for idx, poly in enumerate(polys):
        if idx in BRACKETS:
            colour = SAND_DIM
        elif idx in T_GLYPH or idx in W_GLYPH:
            colour = SAND
        else:
            colour = SAND  # defensive — any extra path renders as glyph
        d.polygon(poly, fill=colour)

    return img


def main() -> int:
    master = render_master()
    for folder, px in DENSITIES:
        out_dir = RES_DIR / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        out_path = out_dir / "ic_tw_coord_plugin.png"
        if px == SIZE:
            master.save(out_path, "PNG", optimize=True)
        else:
            master.resize((px, px), Image.LANCZOS).save(out_path, "PNG", optimize=True)
        print(f"wrote {out_path.relative_to(REPO)} ({px}×{px})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
