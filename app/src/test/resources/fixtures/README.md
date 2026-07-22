# Test fixtures (feature 006)

Trimmed SQLite subsets of the generator's `tw-central-full.zip` output, used by the
JVM unit tests so they run against **authentic generator bytes** without committing
the full 10 MB / 324 MB databases.

| File | Source | Contents |
|---|---|---|
| `townships-fixture.sqlite` | `townships.sqlite` (MOI release 1140318) | level-4 縣市 for 台中市 / 彰化縣 / 雲林縣 / 南投縣 + all their level-7/8 districts (88), R*Tree rebuilt to match |
| `places-taichung-fixture.sqlite` | `places-taichung.sqlite` (TGOS 115-01) | 4,112 rows in 大甲區 / 西區 — the 中山路 / 向上路 / 臺灣大道 families + a sample of others; `places_rtree` rebuilt |
| `native_address_entry_corpus.csv` | Feature 013 curated grammar corpus, cross-checked against TGOS/MOI fixture names | 100 full-width, alias, numeral, subnumber, floor, room, overlapping-locality, and unclassified-tail cases; no coordinate accuracy claim |

## Ground truth the tests rely on

- townships: `counties()` = {南投縣, 台中市, 彰化縣, 雲林縣}; 台中市 has 29 districts incl. 西區, 大甲區.
- 8 reference points resolve as in `scripts/verify_polygon_in.py` (台中車站→台中市西區, 大甲→台中市大甲區, 彰化市→彰化縣彰化市, 鹿港→彰化縣鹿港鎮, 斗六→雲林縣斗六市, 南投市→南投縣南投市). Points whose county is outside these four (e.g. an offshore point, or 一中→北區 which is not kept here) resolve to None against THIS fixture.
- places: `street='向上路'` → 0 rows; `street LIKE '向上路%'` → 817 (proves substring-not-`=`, FR-009). `street LIKE '臺灣大道%'` / `LIKE '臺%'` → 415 (proves 臺↔台 fold need, FR-010).

## Regenerate

```bash
python scripts/build_test_fixtures.py
```

Re-run after any generator rebuild; if the reference districts or street families
change, update the test expectations in `TownshipBoundaryFacadeTest` /
`AddressDatabaseFacadeStreetQueryTest` accordingly.

The native Address corpus is deterministic input/normalization evidence, not
an address-location dataset. `CURATED_FEATURE_013` rows use locality and road
names represented by the existing fixtures or explicit Taiwan address grammar
regressions. Update its expected projections only with a matching specification
change.
