"""Prove the Tier-1 polygon-in claim WITHOUT shapely — using a hand-rolled
minimal WKB MultiPolygon parser + ray-casting point-in-polygon.

This doubles as a feasibility proof for county-scoped-forward-search.md §2's
recommendation: "prototype a minimal hand-rolled WKB-MultiPolygon reader + PIP
(~0 KB, no JTS)". If this ~80-line pure-Python parser correctly resolves known
points to the right 縣市 + 鄉鎮市區 against the real generator bytes, the same
algorithm is portable to Android with no geometry dependency.

WKB layout consumed (little-endian, OGC/ISO basic 2D, as emitted by
extract_townships.py via shapely .wkb):

  MultiPolygon = byteorder(1) type(4)=6 nPolys(4) [ Polygon ]*
  Polygon      = byteorder(1) type(4)=3 nRings(4) [ Ring ]*
  Ring         = nPoints(4) [ x(double8) y(double8) ]*

Output -> scripts/verify_polygon_in.out.txt
"""
from __future__ import annotations

import os
import sqlite3
import struct
import tempfile
import zipfile
from pathlib import Path

GENERATOR_ENV = "ATAK_TW_ADDRESS_GENERATOR"
if not os.environ.get(GENERATOR_ENV):
    raise SystemExit(f"Set {GENERATOR_ENV} to the sibling generator checkout")
GEN = Path(os.environ[GENERATOR_ENV]).expanduser()
ZIP = GEN / "output" / "tw-central-full.zip"
OUT = Path(__file__).resolve().parent / "verify_polygon_in.out.txt"

lines: list[str] = []


def p(s: str = "") -> None:
    lines.append(s)


# --- minimal WKB MultiPolygon reader (the Android-portable algorithm) -------

def _read_ring(buf: memoryview, off: int):
    (npts,) = struct.unpack_from("<I", buf, off)
    off += 4
    coords = struct.unpack_from("<%dd" % (npts * 2), buf, off)
    off += npts * 16
    ring = [(coords[i], coords[i + 1]) for i in range(0, len(coords), 2)]
    return ring, off


def parse_wkb_multipolygon(wkb: bytes):
    """Return list[polygon]; polygon = (exterior_ring, [hole_ring,...])."""
    buf = memoryview(wkb)
    off = 0
    (order,) = struct.unpack_from("<B", buf, off); off += 1
    assert order == 1, "only little-endian handled in this probe"
    (gtype,) = struct.unpack_from("<I", buf, off); off += 4
    polys = []
    if gtype == 6:  # MultiPolygon
        (npoly,) = struct.unpack_from("<I", buf, off); off += 4
        for _ in range(npoly):
            (_o,) = struct.unpack_from("<B", buf, off); off += 1
            (_t,) = struct.unpack_from("<I", buf, off); off += 4
            (nrings,) = struct.unpack_from("<I", buf, off); off += 4
            rings = []
            for _r in range(nrings):
                ring, off = _read_ring(buf, off)
                rings.append(ring)
            polys.append((rings[0], rings[1:]))
    elif gtype == 3:  # Polygon
        (nrings,) = struct.unpack_from("<I", buf, off); off += 4
        rings = []
        for _r in range(nrings):
            ring, off = _read_ring(buf, off)
            rings.append(ring)
        polys.append((rings[0], rings[1:]))
    else:
        raise ValueError("unexpected geom type %d" % gtype)
    return polys


def _point_in_ring(x: float, y: float, ring) -> bool:
    """Ray casting. ring is list of (lon,lat)=(x,y)."""
    inside = False
    n = len(ring)
    j = n - 1
    for i in range(n):
        xi, yi = ring[i]
        xj, yj = ring[j]
        if ((yi > y) != (yj > y)) and (x < (xj - xi) * (y - yi) / (yj - yi) + xi):
            inside = not inside
        j = i
    return inside


def point_in_multipolygon(lon: float, lat: float, polys) -> bool:
    for ext, holes in polys:
        if _point_in_ring(lon, lat, ext):
            if any(_point_in_ring(lon, lat, h) for h in holes):
                continue  # in a hole -> not in this polygon
            return True
    return False


def main() -> int:
    p("############ Hand-rolled WKB MultiPolygon + PIP — Tier-1 proof ############")
    p(f"  zip: {ZIP}  exists={ZIP.exists()}")
    if not ZIP.exists():
        OUT.write_text("\n".join(lines), encoding="utf-8")
        return 1

    with tempfile.TemporaryDirectory() as td:
        tdp = Path(td)
        with zipfile.ZipFile(ZIP) as zf:
            zf.extract("townships.sqlite", tdp)
        tj = tdp / "townships.sqlite"
        conn = sqlite3.connect(str(tj))

        def tier1(lat: float, lon: float):
            """Generator Tier-1 query shape: R*Tree bbox -> WKB covers.
            level 8 then 7; county_zh comes back inline."""
            cand_total = 0
            for lvl in (8, 7):
                rows = conn.execute(
                    "select t.name_zh,t.county_zh,t.geometry_wkb "
                    "from townships t join townships_rtree r on r.id=t.id "
                    "where t.admin_level=? and r.min_lat<=? and ?<=r.max_lat "
                    "and r.min_lon<=? and ?<=r.max_lon",
                    (lvl, lat, lat, lon, lon)).fetchall()
                cand_total += len(rows)
                for name, cty, wkb in rows:
                    polys = parse_wkb_multipolygon(wkb)
                    if point_in_multipolygon(lon, lat, polys):
                        return cty, name, lvl, cand_total
            return None, None, None, cand_total

        # Known points with expected (county, district).
        probes = [
            ("台中車站",      24.1417, 120.6736, "台中市", None),
            ("一中商圈",      24.1505, 120.6840, "台中市", None),
            ("彰化市中心",    24.0809, 120.5386, "彰化縣", "彰化市"),
            ("鹿港鎮",        24.0576, 120.4347, "彰化縣", "鹿港鎮"),
            ("台中大甲區",    24.3486, 120.6225, "台中市", "大甲區"),
            ("雲林斗六",      23.7092, 120.5430, "雲林縣", "斗六市"),
            ("南投市",        23.9099, 120.6856, "南投縣", "南投市"),
            ("海上(無縣市)",  24.0000, 119.5000, None,    None),
        ]
        npass = 0
        ntest = 0
        for label, lat, lon, exp_cty, exp_dist in probes:
            cty, dist, lvl, ncand = tier1(lat, lon)
            cty_ok = (cty == exp_cty)
            dist_ok = (exp_dist is None) or (dist == exp_dist)
            verdict = "PASS" if (cty_ok and dist_ok) else "**FAIL**"
            ntest += 1
            if cty_ok and dist_ok:
                npass += 1
            p(f"  {label:10s} ({lat},{lon}) -> county={cty} district={dist} "
              f"lvl={lvl} rtree_cand={ncand}  exp=({exp_cty},{exp_dist})  {verdict}")
        p(f"\n  RESULT: {npass}/{ntest} probes correct  "
          f"-> hand-rolled WKB MultiPolygon + PIP is "
          f"{'VIABLE for Android (no JTS needed)' if npass==ntest else 'NOT yet correct'}")

        # Stress: how big is the largest multipolygon (worst-case parse on device)?
        big = conn.execute(
            "select name_zh,length(geometry_wkb) from townships "
            "order by length(geometry_wkb) desc limit 3").fetchall()
        p("\n  largest geometry_wkb blobs (device parse worst-case):")
        for name, sz in big:
            polys = parse_wkb_multipolygon(
                conn.execute("select geometry_wkb from townships where name_zh=? "
                             "order by length(geometry_wkb) desc limit 1",
                             (name,)).fetchone()[0])
            nverts = sum(len(e) + sum(len(h) for h in hs) for e, hs in polys)
            p(f"    {name}: {sz/1024:.1f} KB, {len(polys)} polygons, {nverts} vertices")
        conn.close()

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {OUT} ({len(lines)} lines)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
