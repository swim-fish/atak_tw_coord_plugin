"""Measure the 10:50 tw-central-full.zip build for the research refresh.

Extracts the bundle to a temp dir and reports the figures the
docs/research notes cite as (measured): township level counts + the
12-county list, per-county place counts, and the forward-search bbox
candidate counts + 中山路 / 向上路 family counts.

Output is written to scripts/measure_tw_central.out.txt so the relay
cannot truncate it.
"""
from __future__ import annotations

import math
import os
import sqlite3
import tempfile
import zipfile
from pathlib import Path

GEN = Path(r"C:\Users\hhhnr\source\tak\atak_vns_offline_routing\atak-tw-address-generator")
ZIP = GEN / "output" / "tw-central-full.zip"
OUT = Path(__file__).resolve().parent / "measure_tw_central.out.txt"

lines: list[str] = []


def p(s: str = "") -> None:
    lines.append(s)


def meta(conn: sqlite3.Connection) -> dict:
    try:
        return dict(conn.execute("select key,value from metadata").fetchall())
    except Exception as e:  # noqa: BLE001
        return {"_err": str(e)}


def haversine_m(lat1, lon1, lat2, lon2):
    r = 6371000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    a = (math.sin(math.radians(lat2 - lat1) / 2) ** 2
         + math.cos(p1) * math.cos(p2) * math.sin(math.radians(lon2 - lon1) / 2) ** 2)
    return 2 * r * math.asin(math.sqrt(a))


def bbox_count(conn, lat, lon, half):
    return conn.execute(
        "select count(*) from places_rtree "
        "where min_lat<=? and max_lat>=? and min_lon<=? and max_lon>=?",
        (lat + half, lat - half, lon + half, lon - half),
    ).fetchone()[0]


def main() -> int:
    p(f"ZIP: {ZIP}")
    p(f"ZIP exists: {ZIP.exists()}  size: {ZIP.stat().st_size/1e6:.2f} MB"
      if ZIP.exists() else "ZIP MISSING")
    if not ZIP.exists():
        OUT.write_text("\n".join(lines), encoding="utf-8")
        return 1

    with tempfile.TemporaryDirectory() as td:
        tdp = Path(td)
        with zipfile.ZipFile(ZIP) as zf:
            names = zf.namelist()
            p("\n=== ZIP entries ===")
            for n in names:
                info = zf.getinfo(n)
                p(f"  {n}  ({info.file_size/1e6:.2f} MB uncompressed)")
            zf.extractall(tdp)

        def f(name):
            cands = list(tdp.rglob(name))
            return cands[0] if cands else None

        # ---- townships ----
        tj = f("townships.sqlite")
        p("\n=== townships.sqlite ===")
        if tj:
            c = sqlite3.connect(str(tj))
            m = meta(c)
            for k in ("schema_version", "source", "boundary_release", "region",
                      "bbox", "inserted_level4", "inserted_level7", "inserted_level8"):
                if k in m:
                    p(f"  {k} = {m[k]}")
            p("\n  -- level 4 (縣市) names --")
            for (nm,) in c.execute(
                "select name_zh from townships where admin_level=4 order by name_zh"
            ):
                p(f"    {nm}")
            p("\n  -- township count per county (level 7/8) --")
            for cty, n in c.execute(
                "select county_zh, count(*) from townships "
                "where admin_level in (7,8) group by county_zh order by county_zh"
            ):
                p(f"    {cty}: {n}")
            c.close()
        else:
            p("  MISSING")

        # ---- places per county ----
        p("\n=== places-*.sqlite ===")
        place_dbs = {}
        for name in ("places-taichung.sqlite", "places-changhua.sqlite", "places-osm.sqlite"):
            fp = f(name)
            if not fp:
                p(f"  {name}: MISSING")
                continue
            c = sqlite3.connect(str(fp))
            m = meta(c)
            cnt = c.execute("select count(*) from places").fetchone()[0]
            p(f"  {name}: county={m.get('county')} source={m.get('source')} "
              f"schema={m.get('schema_version')} inserted_meta={m.get('inserted')} "
              f"COUNT(*)={cnt}")
            place_dbs[name] = fp
            c.close()

        # ---- forward-search measurements on Taichung ----
        tc = place_dbs.get("places-taichung.sqlite")
        if tc:
            c = sqlite3.connect(str(tc))
            p("\n=== forward-search bbox candidate counts (places-taichung) ===")
            centres = {
                "台中車站 24.1417,120.6736": (24.1417, 120.6736),
                "一中商圈 24.1505,120.6840": (24.1505, 120.6840),
                "大甲區 24.3486,120.6225": (24.3486, 120.6225),
            }
            for label, (lat, lon) in centres.items():
                row = [f"{label}:"]
                for half in (0.0023, 0.0045, 0.0090):
                    row.append(f"±{half}={bbox_count(c, lat, lon, half)}")
                p("  " + "  ".join(row))

            p("\n=== 中山路 / 向上路 family (places-taichung) ===")
            def q1(sql, *a):
                return c.execute(sql, a).fetchone()[0]
            p("  street='中山路'        : %d" % q1("select count(*) from places where street='中山路'"))
            p("  street LIKE '中山路%%'  : %d" % q1("select count(*) from places where street like '中山路%'"))
            p("  street='向上路'        : %d" % q1("select count(*) from places where street='向上路'"))
            p("  street LIKE '向上路%%'  : %d" % q1("select count(*) from places where street like '向上路%'"))
            p("  name LIKE '%%中山%%'     : %d" % q1("select count(*) from places where name like '%中山%'"))
            p("  distinct street        : %d" % q1("select count(distinct street) from places"))
            # 臺 vs 台 in street
            p("  street LIKE '臺%%'       : %d" % q1("select count(*) from places where street like '臺%'"))
            c.close()

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {OUT} ({len(lines)} lines)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
