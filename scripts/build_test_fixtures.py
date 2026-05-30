"""Build trimmed SQLite fixtures for feature 006 JVM tests (T005).

Copies a small, self-consistent subset out of the real generator output
(tw-central-full.zip's townships.sqlite + places-taichung.sqlite) so the
boundary-facade and street-query JVM tests run against authentic bytes
without committing the full 10 MB / 324 MB databases.

Outputs (committed):
  app/src/test/resources/fixtures/townships-fixture.sqlite
  app/src/test/resources/fixtures/places-taichung-fixture.sqlite

townships-fixture keeps:
  - level-4 rows for 台中市 / 彰化縣 / 雲林縣 / 南投縣
  - all level 7/8 districts for those four counties
  (the R*Tree is rebuilt to match the trimmed id set)

places-taichung-fixture keeps rows in 大甲區 / 西區 whose street is in the
中山路 / 向上路 / 臺灣大道 families (plus a sample of others), enough to
exercise the substring-incl-段 + 臺↔台 fold + distance-rank assertions.

Re-run after any generator rebuild; see the fixtures README.
"""
from __future__ import annotations

import sqlite3
from pathlib import Path
import zipfile
import tempfile

GEN = Path(r"C:\Users\hhhnr\source\tak\atak_vns_offline_routing\atak-tw-address-generator")
ZIP = GEN / "output" / "tw-central-full.zip"
OUT = Path(__file__).resolve().parent.parent / "app" / "src" / "test" / "resources" / "fixtures"

KEEP_COUNTIES = ("台中市", "彰化縣", "雲林縣", "南投縣")
PLACES_DISTRICTS = ("大甲區", "西區")


def build_townships(src: Path, dst: Path) -> None:
    if dst.exists():
        dst.unlink()
    s = sqlite3.connect(str(src))
    d = sqlite3.connect(str(dst))
    d.executescript(
        """
        PRAGMA journal_mode=DELETE;
        CREATE TABLE townships (
            id INTEGER PRIMARY KEY, moi_code TEXT NOT NULL, admin_level INTEGER NOT NULL,
            name_zh TEXT NOT NULL, name_en TEXT, county_zh TEXT, geometry_wkb BLOB NOT NULL);
        CREATE INDEX idx_townships_level  ON townships(admin_level);
        CREATE INDEX idx_townships_name   ON townships(name_zh);
        CREATE INDEX idx_townships_county ON townships(county_zh);
        CREATE VIRTUAL TABLE townships_rtree USING rtree(id, min_lat, max_lat, min_lon, max_lon);
        CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL);
        """
    )
    placeholders = ",".join("?" for _ in KEEP_COUNTIES)
    rows = s.execute(
        f"SELECT id,moi_code,admin_level,name_zh,name_en,county_zh,geometry_wkb "
        f"FROM townships WHERE (admin_level=4 AND name_zh IN ({placeholders})) "
        f"   OR (admin_level IN (7,8) AND county_zh IN ({placeholders}))",
        KEEP_COUNTIES + KEEP_COUNTIES,
    ).fetchall()
    for r in rows:
        d.execute(
            "INSERT INTO townships (id,moi_code,admin_level,name_zh,name_en,county_zh,geometry_wkb)"
            " VALUES (?,?,?,?,?,?,?)",
            r,
        )
    # Rebuild R*Tree from the trimmed geometry bounds.
    import struct

    def bounds(wkb: bytes):
        # minimal LE WKB walk to get bbox (mirrors WkbMultiPolygonParser)
        import io

        mv = memoryview(wkb)
        off = 0
        order = mv[off]; off += 1
        assert order == 1
        (gtype,) = struct.unpack_from("<I", mv, off); off += 4
        minx = miny = float("inf"); maxx = maxy = float("-inf")

        def ring(o):
            nonlocal minx, miny, maxx, maxy
            (npts,) = struct.unpack_from("<I", mv, o); o += 4
            for _ in range(npts):
                x, y = struct.unpack_from("<dd", mv, o); o += 16
                if x < minx: minx = x
                if x > maxx: maxx = x
                if y < miny: miny = y
                if y > maxy: maxy = y
            return o

        if gtype == 6:
            (npoly,) = struct.unpack_from("<I", mv, off); off += 4
            for _ in range(npoly):
                off += 1
                (_t,) = struct.unpack_from("<I", mv, off); off += 4
                (nrings,) = struct.unpack_from("<I", mv, off); off += 4
                for _ in range(nrings):
                    off = ring(off)
        elif gtype == 3:
            (nrings,) = struct.unpack_from("<I", mv, off); off += 4
            for _ in range(nrings):
                off = ring(off)
        return miny, maxy, minx, maxx

    for r in rows:
        rid, wkb = r[0], r[6]
        mnlat, mxlat, mnlon, mxlon = bounds(wkb)
        d.execute(
            "INSERT INTO townships_rtree(id,min_lat,max_lat,min_lon,max_lon) VALUES (?,?,?,?,?)",
            (rid, mnlat, mxlat, mnlon, mxlon),
        )
    meta = dict(s.execute("SELECT key,value FROM metadata").fetchall())
    meta["region"] = "tw-central-fixture"
    for k, v in meta.items():
        d.execute("INSERT INTO metadata(key,value) VALUES (?,?)", (k, v))
    d.commit()
    n4 = d.execute("SELECT COUNT(*) FROM townships WHERE admin_level=4").fetchone()[0]
    n78 = d.execute("SELECT COUNT(*) FROM townships WHERE admin_level IN (7,8)").fetchone()[0]
    s.close(); d.close()
    print(f"townships-fixture: {n4} level-4, {n78} level-7/8, {dst.stat().st_size/1e6:.2f} MB")


def build_places(src: Path, dst: Path) -> None:
    if dst.exists():
        dst.unlink()
    s = sqlite3.connect(str(src))
    d = sqlite3.connect(str(dst))
    cols = [r[1] for r in s.execute("PRAGMA table_info(places)")]
    coldef = ", ".join(f"{c} {('INTEGER PRIMARY KEY' if c=='id' else 'TEXT' if c in ('source','name','display_name','display_name_halfwidth','district_code','county','township','village','neighbor','street','area','lane','alley','number','place_type') else 'REAL' if c in ('lat','lon') else 'INTEGER')}" for c in cols)
    d.executescript(
        f"""
        PRAGMA journal_mode=DELETE;
        CREATE TABLE places ({coldef});
        CREATE VIRTUAL TABLE places_rtree USING rtree(id, min_lat, max_lat, min_lon, max_lon);
        CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL);
        """
    )
    collist = ",".join(cols)
    qmarks = ",".join("?" for _ in cols)
    # Keep 中山路/向上路/臺灣大道 families in the two districts + a small sample of others.
    rows = s.execute(
        f"SELECT {collist} FROM places "
        "WHERE township IN ('大甲區','西區') AND ("
        "  street LIKE '中山路%' OR street LIKE '向上路%' OR street LIKE '臺灣大道%' "
        "  OR street LIKE '台灣大道%') "
        "LIMIT 4000"
    ).fetchall()
    # Plus 200 arbitrary 大甲區 rows so wrong-street / ranking tests have neighbours.
    rows += s.execute(
        f"SELECT {collist} FROM places WHERE township='大甲區' LIMIT 200"
    ).fetchall()
    idx_id = cols.index("id")
    idx_lat = cols.index("lat")
    idx_lon = cols.index("lon")
    seen = set()
    for r in rows:
        if r[idx_id] in seen:
            continue
        seen.add(r[idx_id])
        d.execute(f"INSERT INTO places ({collist}) VALUES ({qmarks})", r)
        d.execute(
            "INSERT INTO places_rtree(id,min_lat,max_lat,min_lon,max_lon) VALUES (?,?,?,?,?)",
            (r[idx_id], r[idx_lat], r[idx_lat], r[idx_lon], r[idx_lon]),
        )
    for k, v in s.execute("SELECT key,value FROM metadata").fetchall():
        d.execute("INSERT INTO metadata(key,value) VALUES (?,?)", (k, v))
    d.commit()
    n = d.execute("SELECT COUNT(*) FROM places").fetchone()[0]
    nz = d.execute("SELECT COUNT(*) FROM places WHERE street LIKE '臺%'").fetchone()[0]
    sx = d.execute("SELECT COUNT(*) FROM places WHERE street LIKE '向上路%'").fetchone()[0]
    s.close(); d.close()
    print(f"places-fixture: {n} rows ({sx} 向上路%, {nz} 臺%), {dst.stat().st_size/1e6:.2f} MB")


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as td:
        tdp = Path(td)
        with zipfile.ZipFile(ZIP) as zf:
            zf.extract("townships.sqlite", tdp)
            zf.extract("places-taichung.sqlite", tdp)
        build_townships(tdp / "townships.sqlite", OUT / "townships-fixture.sqlite")
        build_places(tdp / "places-taichung.sqlite", OUT / "places-taichung-fixture.sqlite")
    print("OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
