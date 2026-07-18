"""Verify the docs/research claims against the CURRENT tw-central-full.zip.

Goes beyond scripts/measure_tw_central.py: in addition to the headline
counts, it *executes* the load-bearing claims of
county-scoped-forward-search.md so they are proven, not asserted:

  V1  zip freshness — mtime + SHA-256 of the zip actually being measured.
  V2  township level counts + per-county district counts (re-measure).
  V3  county_zh COVERAGE — every level 7/8 row has a non-null parent county
      (the whole "one polygon-in hit yields county+township" claim).
  V4  polygon-in ACTUALLY WORKS — pick a known Taichung-station point, run
      the generator's Tier-1 query shape (R*Tree bbox -> WKB covers), and
      confirm it returns 台中市 + the right 區. Also a Changhua point.
  V5  WKB shape — confirm geometry_wkb parses as (Multi)Polygon in WGS84
      lon/lat, so the "Android needs a WKB MultiPolygon parser" claim is
      grounded in the real bytes (report byte0 endianness + geom type code).
  V6  stage-③ district scoping — does places have a usable per-district key
      (district_code / township) and how much does it actually narrow a
      street family vs county-wide? (中山路, 向上路).
  V7  forward-search bbox candidate counts (re-measure, all three centres).
  V8  臺/台 fold + 段 suffix counts (re-measure).

Output -> scripts/verify_research_claims.out.txt
"""
from __future__ import annotations

import hashlib
import math
import os
import sqlite3
import tempfile
import zipfile
from pathlib import Path

GENERATOR_ENV = "ATAK_TW_ADDRESS_GENERATOR"
if not os.environ.get(GENERATOR_ENV):
    raise SystemExit(f"Set {GENERATOR_ENV} to the sibling generator checkout")
GEN = Path(os.environ[GENERATOR_ENV]).expanduser()
ZIP = GEN / "output" / "tw-central-full.zip"
OUT = Path(__file__).resolve().parent / "verify_research_claims.out.txt"

lines: list[str] = []


def p(s: str = "") -> None:
    lines.append(s)


def ok(cond: bool) -> str:
    return "PASS" if cond else "**FAIL**"


def haversine_m(lat1, lon1, lat2, lon2):
    r = 6371000.0
    a = (math.sin(math.radians(lat2 - lat1) / 2) ** 2
         + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2))
         * math.sin(math.radians(lon2 - lon1) / 2) ** 2)
    return 2 * r * math.asin(math.sqrt(a))


def main() -> int:
    # ---- V1 zip freshness ----
    p("############ V1 — zip freshness ############")
    if not ZIP.exists():
        p("**FAIL** zip missing: " + str(ZIP))
        OUT.write_text("\n".join(lines), encoding="utf-8")
        return 1
    st = ZIP.stat()
    h = hashlib.sha256()
    with ZIP.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    p(f"  path   : {ZIP}")
    p(f"  size   : {st.st_size/1e6:.2f} MB")
    p(f"  mtime  : {st.st_mtime}  (epoch)")
    p(f"  sha256 : {h.hexdigest()}")

    try:
        import shapely.wkb  # noqa: F401
        from shapely.geometry import Point
        have_shapely = True
    except Exception as e:  # noqa: BLE001
        have_shapely = False
        p(f"  WARN: shapely unavailable ({e}); V4/V5 polygon tests will be skipped")

    with tempfile.TemporaryDirectory() as td:
        tdp = Path(td)
        with zipfile.ZipFile(ZIP) as zf:
            entries = zf.namelist()
            zf.extractall(tdp)
        p("\n  zip entries:")
        for n in entries:
            p(f"    {n}")

        def f(name):
            c = list(tdp.rglob(name))
            return c[0] if c else None

        tj = f("townships.sqlite")
        tc = f("places-taichung.sqlite")
        ch = f("places-changhua.sqlite")
        osm = f("places-osm.sqlite")

        # ---- V2 township counts ----
        p("\n############ V2 — township level + per-county counts ############")
        cj = sqlite3.connect(str(tj))
        meta = dict(cj.execute("select key,value from metadata").fetchall())
        p(f"  source={meta.get('source')} release={meta.get('boundary_release')} "
          f"bbox={meta.get('bbox')}")
        lv = {lvl: cj.execute(
            "select count(*) from townships where admin_level=?", (lvl,)
        ).fetchone()[0] for lvl in (4, 7, 8)}
        p(f"  level4={lv[4]} (meta {meta.get('inserted_level4')}) "
          f"level7={lv[7]} (meta {meta.get('inserted_level7')}) "
          f"level8={lv[8]} (meta {meta.get('inserted_level8')})")
        p(f"  doc claim 12/31/105 -> {ok(lv[4]==12 and lv[7]==31 and lv[8]==105)}")
        counties = [r[0] for r in cj.execute(
            "select name_zh from townships where admin_level=4 order by name_zh")]
        p(f"  counties ({len(counties)}): {' '.join(counties)}")
        p("  districts/county (level 7/8):")
        for cty, n in cj.execute(
            "select county_zh,count(*) from townships where admin_level in (7,8) "
            "group by county_zh order by count(*) desc"):
            p(f"    {cty}: {n}")

        # ---- V3 county_zh coverage ----
        p("\n############ V3 — county_zh coverage on level 7/8 ############")
        total78 = cj.execute(
            "select count(*) from townships where admin_level in (7,8)").fetchone()[0]
        nullcty = cj.execute(
            "select count(*) from townships where admin_level in (7,8) "
            "and (county_zh is null or county_zh='')").fetchone()[0]
        p(f"  level7/8 rows={total78}  null/empty county_zh={nullcty}")
        p(f"  claim 'county_zh inline on EVERY 鄉鎮市區' -> {ok(nullcty==0)}")

        # ---- V4 polygon-in actually works ----
        p("\n############ V4 — Tier-1 polygon-in actually resolves ############")
        if have_shapely:
            def tier1(lat, lon):
                for lvl in (8, 7):
                    rows = cj.execute(
                        "select t.name_zh,t.county_zh,t.geometry_wkb "
                        "from townships t join townships_rtree r on r.id=t.id "
                        "where t.admin_level=? and r.min_lat<=? and ?<=r.max_lat "
                        "and r.min_lon<=? and ?<=r.max_lon",
                        (lvl, lat, lat, lon, lon)).fetchall()
                    for name, cty, wkb in rows:
                        if shapely.wkb.loads(wkb).covers(Point(lon, lat)):
                            return cty, name, len(rows)
                return None, None, 0
            probes = [
                ("台中車站 24.1417,120.6736", 24.1417, 120.6736, "台中市"),
                ("彰化市 24.08,120.54", 24.08, 120.54, "彰化縣"),
                ("一中商圈 24.1505,120.6840", 24.1505, 120.6840, "台中市"),
            ]
            for label, lat, lon, exp_cty in probes:
                cty, dist, ncand = tier1(lat, lon)
                p(f"  {label} -> {cty} {dist}  (rtree candidates={ncand})  "
                  f"county {ok(cty==exp_cty)}")
        else:
            p("  SKIPPED (no shapely)")

        # ---- V5 WKB shape ----
        p("\n############ V5 — geometry_wkb byte shape ############")
        row = cj.execute(
            "select name_zh,geometry_wkb from townships where admin_level=8 limit 1"
        ).fetchone()
        wkb = row[1]
        byte0 = wkb[0]
        # WKB: byte0 = endianness (0=big,1=little); next 4 bytes = geom type
        endian = "little" if byte0 == 1 else "big"
        gtype = int.from_bytes(wkb[1:5], endian)
        # 3=Polygon, 6=MultiPolygon (ISO/OGC); +0x80000000 etc for Z/M
        gtype_name = {1: "Point", 2: "LineString", 3: "Polygon",
                      6: "MultiPolygon"}.get(gtype, f"code {gtype}")
        p(f"  sample={row[0]}  bytes={len(wkb)}  endian={endian}  geomtype={gtype_name}")
        if have_shapely:
            g = shapely.wkb.loads(wkb)
            minx, miny, maxx, maxy = g.bounds
            p(f"  shapely parse: {g.geom_type}  bounds lon[{minx:.3f},{maxx:.3f}] "
              f"lat[{miny:.3f},{maxy:.3f}]  (WGS84-range {ok(119<minx<123 and 21<miny<26)})")
            p(f"  claim 'WGS84 lon/lat MultiPolygon' parser needed -> "
              f"{ok(g.geom_type in ('Polygon','MultiPolygon'))}")
        cj.close()

        # ---- V6 stage-③ district scoping ----
        p("\n############ V6 — stage-③ per-district scoping in places ############")
        ctc = sqlite3.connect(str(tc))
        cols = [r[1] for r in ctc.execute("PRAGMA table_info(places)")]
        p(f"  places columns: {', '.join(cols)}")
        has_dc = "district_code" in cols
        has_tw = "township" in cols
        p(f"  district_code present={has_dc}  township present={has_tw}")

        def q(sql, *a):
            return ctc.execute(sql, a).fetchone()[0]

        # how many distinct districts hold 中山路 family in Taichung?
        if has_tw:
            n_tw_zhongshan = q(
                "select count(distinct township) from places where street like '中山路%'")
            p(f"  distinct townships holding 中山路% : {n_tw_zhongshan}")
            # pick the district with the most 中山路 rows, show the narrowing
            top = ctc.execute(
                "select township,count(*) c from places where street like '中山路%' "
                "group by township order by c desc limit 5").fetchall()
            p("  top 中山路 districts: " + "  ".join(f"{t}={c}" for t, c in top))
            cty_zhongshan = q("select count(*) from places where street like '中山路%'")
            if top:
                top_d, top_c = top[0]
                p(f"  county-wide 中山路%={cty_zhongshan} -> scoped to {top_d}={top_c}  "
                  f"({100*top_c/cty_zhongshan:.0f}% of county) — scoping narrows "
                  f"{ok(top_c < cty_zhongshan)}")
            # 向上路 across districts
            top_sx = ctc.execute(
                "select township,count(*) c from places where street like '向上路%' "
                "group by township order by c desc").fetchall()
            p("  向上路% across districts: " + "  ".join(f"{t}={c}" for t, c in top_sx))
        ctc.close()

        # ---- V7 forward-search bbox candidate counts ----
        p("\n############ V7 — forward-search bbox candidate counts (taichung) ############")
        ctc = sqlite3.connect(str(tc))

        def bbox(lat, lon, half):
            return ctc.execute(
                "select count(*) from places_rtree where min_lat<=? and max_lat>=? "
                "and min_lon<=? and max_lon>=?",
                (lat + half, lat - half, lon + half, lon - half)).fetchone()[0]
        for label, lat, lon in [
            ("台中車站 24.1417,120.6736", 24.1417, 120.6736),
            ("一中商圈 24.1505,120.6840", 24.1505, 120.6840),
            ("大甲區 24.3486,120.6225", 24.3486, 120.6225)]:
            p(f"  {label}: 0.5km={bbox(lat,lon,0.0023)}  "
              f"1km={bbox(lat,lon,0.0045)}  2km={bbox(lat,lon,0.0090)}")

        # ---- V8 臺/台 + 段 ----
        p("\n############ V8 — 臺/台 fold + 段 suffix (taichung) ############")

        def q2(sql, *a):
            return ctc.execute(sql, a).fetchone()[0]
        z_eq = q2("select count(*) from places where street='中山路'")
        z_lk = q2("select count(*) from places where street like '中山路%'")
        s_eq = q2("select count(*) from places where street='向上路'")
        s_lk = q2("select count(*) from places where street like '向上路%'")
        tai = q2("select count(*) from places where street like '臺%'")
        p(f"  中山路 =:{z_eq}  LIKE%:{z_lk}  (= drops {ok(z_eq<z_lk)})")
        p(f"  向上路 =:{s_eq}  LIKE%:{s_lk}  (= is 100% miss {ok(s_eq==0 and s_lk>0)})")
        p(f"  street LIKE '臺%':{tai}  (臺↔台 fold needed {ok(tai>0)})")
        ctc.close()

        # ---- places-osm sanity ----
        p("\n############ extra — places-osm + per-county COUNT(*) ############")
        for name, fp in [("places-taichung", tc), ("places-changhua", ch),
                         ("places-osm", osm)]:
            c = sqlite3.connect(str(fp))
            m = dict(c.execute("select key,value from metadata").fetchall())
            cnt = c.execute("select count(*) from places").fetchone()[0]
            p(f"  {name}: county={m.get('county')} schema={m.get('schema_version')} "
              f"meta.inserted={m.get('inserted')} COUNT(*)={cnt}")
            c.close()

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {OUT} ({len(lines)} lines)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
