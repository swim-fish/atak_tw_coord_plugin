#!/usr/bin/env python3
"""Render the two Tools-menu silhouette icons (ic_tw_coord + ic_tw_coord_goto)
to standalone PNGs for docs/user-guide{,_zh}.md §4.

Source-of-truth: the actual Android vector drawables in
    app/src/main/res/drawable/ic_tw_coord.xml
    app/src/main/res/drawable/ic_tw_coord_goto.xml
This script *parses* those XMLs and renders each <path>, so when the
drawables change the doc previews stay in sync automatically — no
hand-copied coordinates in Python.

Subset of SVG path commands supported (the only ones used by the two
target drawables):
  M x,y                           moveto absolute
  L x,y                           lineto absolute
  Z                               closepath
  a rx,ry rot laf sf dx,dy        relative arc — matched against the
                                  standard "two half-arcs that form a
                                  closed circle/ellipse" idiom and
                                  rendered via PIL.Image.ellipse.

Each <path> in the XML is classified once per kind by inspecting its
command sequence:
  - polygon  (M + Ls, optional Z, only) — drawn with PIL.polygon (filled)
  - stroke   (M + single L, no Z)       — drawn with PIL.line (stroked)
  - ellipse  (M + 2 'a' arcs + Z)       — drawn with PIL.ellipse (stroke
                                          if strokeColor, otherwise fill)

Outputs:
    docs/images/08a-tools-icon-tw-coord.png
    docs/images/08b-tools-icon-tw-coord-goto.png

To match ATAK's Tools-menu cell visually, each PNG is composited onto a
pure-black panel (the Tools-menu background) with a small margin.
"""

from __future__ import annotations

import math
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image, ImageDraw

REPO = Path(__file__).resolve().parent.parent
RES_DIR = REPO / "app" / "src" / "main" / "res" / "drawable"
OUT_DIR = REPO / "docs" / "images"

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

# Output panel: pure black to match Tools-menu cell, plus a small inset so
# the icon doesn't bleed against neighbour columns when read inline in MD.
PANEL_BG = (0, 0, 0, 255)
MARGIN = 14

# Token pattern: one of the recognised command letters, or a signed float.
# (lowercase 'm' — relative moveto — is required by the pin-inset circle idiom
#  `M cx,cy m -r,0 a … a … Z`; without it the m-coords leak into the prior cmd.)
PATH_TOKEN = re.compile(r"[MLHVZACSQTamclshvzqtsc]|-?\d+(?:\.\d+)?")


# ---------------- XML / path parsing ----------------

def parse_path_commands(d: str) -> list[tuple[str, list[float]]]:
    """Tokenise an SVG path-data string into a sequence of (cmd, args).
    Whitespace and commas are skipped naturally by the regex."""
    tokens = PATH_TOKEN.findall(d)
    out: list[tuple[str, list[float]]] = []
    i = 0
    while i < len(tokens):
        tok = tokens[i]
        i += 1
        if tok in "Zz":
            out.append((tok, []))
            continue
        # Consume numeric args until the next command letter.
        args: list[float] = []
        while i < len(tokens) and not tokens[i][0].isalpha():
            args.append(float(tokens[i]))
            i += 1
        out.append((tok, args))
    return out


def parse_color(s: str | None) -> tuple[int, int, int, int] | None:
    """Parse #RRGGBB / #AARRGGBB / #RGB into RGBA tuple. Returns None for
    transparent / missing / unsupported forms."""
    if not s:
        return None
    s = s.strip()
    if s.lower().startswith("@android:color/transparent"):
        return None
    if not s.startswith("#"):
        return None
    h = s[1:]
    if len(h) == 8:  # AARRGGBB
        a = int(h[0:2], 16); r = int(h[2:4], 16); g = int(h[4:6], 16); b = int(h[6:8], 16)
    elif len(h) == 6:  # RRGGBB
        a = 255; r = int(h[0:2], 16); g = int(h[2:4], 16); b = int(h[4:6], 16)
    elif len(h) == 3:  # RGB
        a = 255; r = int(h[0] * 2, 16); g = int(h[1] * 2, 16); b = int(h[2] * 2, 16)
    else:
        return None
    if a == 0:
        return None
    return (r, g, b, a)


# ---------------- Path → PIL ----------------

def is_polygon(cmds: list[tuple[str, list[float]]]) -> bool:
    """Pattern: M + only Ls (then optional Z). Pure straight-line polygon."""
    if not cmds or cmds[0][0] != "M":
        return False
    for c, _ in cmds[1:]:
        if c not in "LZ":
            return False
    return True


def is_stroke_line(cmds: list[tuple[str, list[float]]]) -> bool:
    """Pattern: M + one L, no Z. Single straight stroke."""
    return (len(cmds) == 2
            and cmds[0][0] == "M"
            and cmds[1][0] == "L")


def is_stroke_polyline(cmds: list[tuple[str, list[float]]]) -> bool:
    """Pattern: one or more (M + Ls) subpaths, no Z. Used by stroked-only
    paths that draw multiple disconnected line runs in a single <path>
    element — e.g. the small lowercase 'tw' label in ic_tw_coord_goto.xml
    declares three subpaths (t stem, t crossbar, w polyline) inside one
    pathData string."""
    if not cmds or cmds[0][0] != "M":
        return False
    has_extra_m = False
    for c, _ in cmds[1:]:
        if c == "M":
            has_extra_m = True
        elif c not in "L":
            # Z, arcs or anything else — not a pure stroked polyline.
            return False
    # Only call this "polyline" if there's at least one extra M (otherwise
    # is_stroke_line / is_polygon would have matched).
    return has_extra_m


def is_two_arc_ellipse(cmds: list[tuple[str, list[float]]]) -> tuple[float, float, float, float] | None:
    """Pattern: M cx-rx,cy a rx,ry … +2rx,0 a rx,ry … -2rx,0 Z.
    Returns (cx-rx, cy-ry, cx+rx, cy+ry) bounding box for PIL.ellipse,
    or None if pattern doesn't match."""
    if len(cmds) < 3 or cmds[0][0] != "M":
        return None
    mx, my = cmds[0][1][0], cmds[0][1][1]   # start point
    rest = cmds[1:]
    # Fold a leading relative moveto (the `M cx,cy m -r,0 a … a … Z` circle idiom
    # used by pin-inset holes) into the start point.
    if rest and rest[0][0] == "m":
        mx += rest[0][1][0]
        my += rest[0][1][1]
        rest = rest[1:]
    if len(rest) < 2 or rest[0][0] != "a" or rest[1][0] != "a":
        return None
    a1 = rest[0][1]                          # rx, ry, rot, laf, sf, dx, dy
    a2 = rest[1][1]
    if len(a1) < 7 or len(a2) < 7:
        return None
    rx, ry = a1[0], a1[1]
    # Sanity-check the closed-loop idiom.
    if abs(a1[5] - 2 * rx) > 0.01 or abs(a1[6]) > 0.01:
        return None
    if abs(a2[5] + 2 * rx) > 0.01 or abs(a2[6]) > 0.01:
        return None
    cx = mx + rx
    cy = my
    return (cx - rx, cy - ry, cx + rx, cy + ry)


def render_path(
    draw: ImageDraw.ImageDraw,
    cmds: list[tuple[str, list[float]]],
    fill: tuple[int, int, int, int] | None,
    stroke: tuple[int, int, int, int] | None,
    stroke_w: float,
) -> None:
    """Dispatch one parsed path to the appropriate PIL primitive."""
    if is_two_arc_ellipse(cmds) is not None:
        bbox = is_two_arc_ellipse(cmds)
        if stroke is not None:
            draw.ellipse(bbox, outline=stroke, width=max(1, int(round(stroke_w))),
                         fill=fill)
        elif fill is not None:
            draw.ellipse(bbox, fill=fill)
        return

    if is_stroke_line(cmds):
        x1, y1 = cmds[0][1]
        x2, y2 = cmds[1][1]
        if stroke is not None:
            draw.line([(x1, y1), (x2, y2)], fill=stroke,
                      width=max(1, int(round(stroke_w))))
        return

    if is_stroke_polyline(cmds):
        # Split into subpaths at every M command, render each subpath as a
        # connected stroked line through its points.
        if stroke is None:
            return
        subpaths: list[list[tuple[float, float]]] = []
        current: list[tuple[float, float]] = []
        for c, args in cmds:
            if c == "M":
                if current:
                    subpaths.append(current)
                current = [(args[0], args[1])]
            elif c == "L":
                current.append((args[0], args[1]))
        if current:
            subpaths.append(current)
        for sub in subpaths:
            if len(sub) < 2:
                continue
            draw.line(sub, fill=stroke,
                      width=max(1, int(round(stroke_w))),
                      joint="curve")
        return

    if is_polygon(cmds):
        # Collect all points from M and L commands; ignore the closing Z (PIL
        # closes implicitly for polygon()).
        pts: list[tuple[float, float]] = []
        for c, args in cmds:
            if c == "M":
                pts.append((args[0], args[1]))
            elif c == "L":
                pts.append((args[0], args[1]))
        if fill is not None:
            draw.polygon(pts, fill=fill)
        elif stroke is not None:
            draw.polygon(pts, outline=stroke)
        return

    # Mixed line/arc outline (e.g. the map-pin teardrop:
    #   M tip L shoulder a … L tip Z). Flatten arcs to line segments and draw
    # the whole subpath as a stroked (or filled) polyline.
    if any(c == "a" for c, _ in cmds) and all(c in ("M", "L", "a", "Z") for c, _ in cmds):
        pts = _flatten_to_points(cmds)
        closed = any(c == "Z" for c, _ in cmds)
        if fill is not None:
            draw.polygon(pts, fill=fill)
        elif stroke is not None:
            line_pts = pts + [pts[0]] if closed and len(pts) > 2 else pts
            draw.line(line_pts, fill=stroke, width=max(1, int(round(stroke_w))),
                      joint="curve")
        return

    # Unknown shape — fall through with a warning so a future drawable that
    # introduces new path commands is noticed instead of silently dropped.
    print(f"  ! skipping unrecognised path: {cmds[:3]}…", file=sys.stderr)


def _flatten_to_points(cmds: list[tuple[str, list[float]]]) -> list[tuple[float, float]]:
    """Walk an M/L/a/Z subpath into a flat point list, tessellating each
    relative elliptical arc ('a') into line segments. Handles the circular
    (rx≈ry) case used by the icon set; non-circular arcs use the mean radius."""
    pts: list[tuple[float, float]] = []
    cx, cy = 0.0, 0.0
    for c, args in cmds:
        if c == "M" or c == "L":
            cx, cy = args[0], args[1]
            pts.append((cx, cy))
        elif c == "a":
            rx, ry, _rot, laf, sf, dx, dy = args[:7]
            arc = _arc_to_points(cx, cy, (rx + ry) / 2.0, int(laf), int(sf), dx, dy)
            pts.extend(arc)
            cx, cy = cx + dx, cy + dy
        elif c == "Z":
            pass
    return pts


def _arc_to_points(x0, y0, r, laf, sf, dx, dy, segments=28):
    """Sample a relative circular arc from (x0,y0) by (dx,dy), radius r."""
    x1, y1 = x0 + dx, y0 + dy
    mx, my = (x0 + x1) / 2.0, (y0 + y1) / 2.0
    half_dx, half_dy = (x1 - x0) / 2.0, (y1 - y0) / 2.0
    d = math.hypot(half_dx, half_dy)
    if d == 0:
        return [(x1, y1)]
    r = max(r, d)  # clamp so the chord fits
    h = math.sqrt(max(0.0, r * r - d * d))
    ux, uy = -half_dy / d, half_dx / d  # unit perpendicular to the chord
    if laf != sf:
        ccx, ccy = mx + h * ux, my + h * uy
    else:
        ccx, ccy = mx - h * ux, my - h * uy
    a0 = math.atan2(y0 - ccy, x0 - ccx)
    a1 = math.atan2(y1 - ccy, x1 - ccx)
    if sf == 0 and a1 > a0:
        a1 -= 2 * math.pi
    elif sf == 1 and a1 < a0:
        a1 += 2 * math.pi
    out = []
    for i in range(1, segments + 1):
        a = a0 + (a1 - a0) * (i / segments)
        out.append((ccx + r * math.cos(a), ccy + r * math.sin(a)))
    return out


# ---------------- Vector XML → PNG ----------------

def render_vector_to_png(xml_path: Path, out_path: Path) -> None:
    """Render one Android vector drawable XML to a PNG (on a black panel)."""
    tree = ET.parse(xml_path)
    root = tree.getroot()
    # Viewport dimensions drive the icon-layer size.
    vw = float(root.attrib[ANDROID_NS + "viewportWidth"])
    vh = float(root.attrib[ANDROID_NS + "viewportHeight"])

    icon = Image.new("RGBA", (int(vw), int(vh)), (0, 0, 0, 0))
    d = ImageDraw.Draw(icon)

    # Element tags are unprefixed in Android vector XMLs (only the attributes
    # carry the `android:` namespace), so findall uses the bare tag name.
    for path in root.findall("path"):
        path_data = path.attrib.get(ANDROID_NS + "pathData", "")
        fill = parse_color(path.attrib.get(ANDROID_NS + "fillColor"))
        stroke = parse_color(path.attrib.get(ANDROID_NS + "strokeColor"))
        stroke_w = float(path.attrib.get(ANDROID_NS + "strokeWidth", "0"))
        cmds = parse_path_commands(path_data)
        render_path(d, cmds, fill, stroke, stroke_w)

    # Composite onto a black panel matching the Tools-menu cell background.
    w, h = int(vw) + MARGIN * 2, int(vh) + MARGIN * 2
    panel = Image.new("RGBA", (w, h), PANEL_BG)
    panel.alpha_composite(icon, (MARGIN, MARGIN))
    panel.save(out_path, "PNG", optimize=True)


def main() -> int:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    targets = [
        ("ic_tw_coord.xml",      "08a-tools-icon-tw-coord.png"),
        ("ic_tw_coord_goto.xml", "08b-tools-icon-tw-coord-goto.png"),
        ("ic_offline_address.xml", "08c-tools-icon-offline-address.png"),
        ("ic_forward_search.xml",  "08d-tools-icon-tw-addr-search.png"),
    ]
    for src_name, out_name in targets:
        src = RES_DIR / src_name
        if not src.is_file():
            print(f"ERROR: source vector not found: {src}", file=sys.stderr)
            return 1
        out = OUT_DIR / out_name
        render_vector_to_png(src, out)
        print(f"wrote {out.relative_to(REPO)} "
              f"({out.stat().st_size:,} bytes) — derived from {src.relative_to(REPO)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
