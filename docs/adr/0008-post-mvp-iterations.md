# ADR-0008: Post-MVP on-device iterations (Taipower precision, outer islands, Tools cycle, settings UX, identifier rename)

**Status**: Accepted
**Date**: 2026-05-16
**Origin**: Series of on-device iterations with the user after the v1 MVP was installed on a Galaxy Tab S10+ running ATAK-CIV 5.7.0.3. Captured collectively to keep ADR count manageable; each subsection is a discrete decision in its own right.

## Context

The spec / plan / tasks captured at `/speckit-implement` time assumed a clean MVP path. On-device verification surfaced six concrete user requests that each cut across spec, code, and docs. We made the changes live, with the user signing off on each, then ran `/speckit-analyze` which identified that the spec had drifted from the shipped behaviour. This ADR records all six decisions in one place so a reader does not have to reconstruct them from commit history.

## Decisions

### D1 — Flip Taipower default precision from 10 m (9-char) to 1 m (11-char)

**Spec change**: FR-011 (also Clarifications session 2026-05-16 post-MVP).

The original FR-011 wording — relaxed during `/speckit-analyze` finding F1 — set the Taipower default to 10 m precision (9-char codes), with 11-char (1 m) reserved for a future toggle. End users on device asked for "more digits"; we flipped the default. Rationale: the trailing two digits are computable from the same TWD67 input at zero extra cost; field surveyors expect Taipower codes in the 11-char form; pwa_map's `H7509 DB4016` golden vector now sees direct test coverage.

Tradeoff accepted: the trailing two digits are speculative beyond typical GPS accuracy (3-5 m). Display does not signal which digits are confidence-bounded; users assumed to be aware.

### D2 — Outer-island support via TM2 zone 119

**Spec change**: FR-021 (new); Clarifications session 2026-05-16 post-MVP.

Plugin originally scoped to Taiwan main island. The 22-county authoritative CSV ships values for all of Penghu / Kinmen / Lienchiang (Matsu) on TM2 zone 119 (EPSG:3825). Added a second `proj4j` `CoordinateTransform` keyed on `+lon_0=119` and a `pickZoneForLongitude(lonDeg)` helper that auto-routes by longitude (<120° → z119). The `Twd97Tm2` / `Twd67Tm2` value classes now carry the actual zone; `DatumShiftTwd67` preserves it through the 4-parameter shift.

Display: the formatter appends a ` z119` suffix only when the zone is not 121 — zone 121 stays implicit (the main-island default) to keep the readout clean for the majority case. Without this signpost, Penghu's easting/northing values look identical in magnitude to main-island values, which is a real user-confusion risk.

Taipower grid stays main-island only (Y/Z letters not implemented). Outer-island fixes in Taipower mode return `OUT_OF_RANGE` with the WGS84 fallback line, matching FR-009.

Accuracy degradation: the 4-parameter shift constants are calibrated for the main island. On the outer islands they disagree with the official 7-parameter Bursa-Wolf shift by ~10-20 m. We chose not to ship the 7-parameter shift for v1 because the simpler 4-param path is what `pwa_map` uses; the degradation is disclosed in the settings advisory (D5 below).

### D3 — Tools-menu icon now cycles units

**Spec change**: FR-022 (new); Clarifications session 2026-05-16 post-MVP.

The Tools-menu icon was previously a no-op (registered with action `com.atakmap.android.twcoord.SHOW_PLUGIN` but no `BroadcastReceiver`). Added a 4-state cycle on tap:

```
Off → Taipower (11-char) → TWD97 → TWD67 → Off → …
```

Each transition writes the new unit to `PreferenceStore` (so the settings page reflects the cycle position) and toasts the localised state name. Toggle covers all three readouts (MAP / ME / TGT) together; individual rows are not independently toggleable from the icon.

Why a cycle, not just on/off: a tool icon with only one visible side-effect (hide/show) is wasteful when the same physical action could also pick a unit. The user explicitly asked for the four-state cycle on device; the resulting interaction model is "tap to see, tap again to switch, tap again to switch, tap again to hide".

### D4 — Polished settings page (per-row live preview)

**Spec change**: FR-023 (new, half).

Each `PanListPreference`'s summary used to be a static descriptive string (e.g. "Choose Taipower / TWD97 / TWD67"). Replaced with a live preview that re-renders on every `SharedPreferences` change:

- Coordinate unit row: `"<entry label> — <Taipei 101 sample formatted in that unit>"` (e.g. `Taipower grid — TPC: B7039 BD3223`).
- Language row: `"<entry label> — <label_map / label_me / label_target translated to that locale>"` (e.g. `中文（正體） — 地圖 / 我 / 目標`).

Implementation: `TwCoordPreferenceFragment` implements `OnSharedPreferenceChangeListener` and rebuilds both summaries against a `LocaleOverride.contextFor(...)` wrapped context. The wrapped context also drives the category header titles and the accuracy-notice text — without it, switching language only updated the on-map readouts and left the settings page frozen in the locale it was opened with (Preference framework binds `@string/...` at inflate time).

### D5 — Settings-page accuracy advisory

**Spec change**: FR-023 (new, other half).

Added a non-clickable `Preference` row carrying an advisory block:

- TWD97 error < 1 m across all coverage areas.
- TWD67 main island: ±3-5 m.
- TWD67 outer islands (Penghu / Kinmen / Matsu): ±10-20 m.
- Taipower grid: main-island only.

Wording is intentionally end-user-facing: no references to ADR numbers, library names (`pwa_map`), or implementation jargon (`4-parameter Bursa-Wolf shift`). The Traditional Chinese translation passes the `zhtw-mcp` lint at 0 errors / 0 warnings; "次米" was rewritten to the explicit "< 1 公尺" because the literal "米" is Mainland-flavoured and "next-metre" jargon does not help a field operator.

Custom layouts (`res/layout/pref_item.xml`, `pref_warning_item.xml`, `pref_category.xml`) hard-code white / light-grey / amber text colours because ATAK's SettingsActivity on Android 14+ rendered the system-default preference text in near-invisible dim grey. Custom layout is the cleanest fix without forking the SettingsActivity theme.

### D6 — Plugin identifier rename: `twpower` → `twcoord`

**Spec change**: none (identifier choice is implementation detail); spec.md Assumptions section will be updated to record `com.atakmap.android.twcoord.plugin` as the applicationId at next pass.

The original namespace `com.atakmap.android.twpower.plugin` read as "Taiwan Power Company" (台電 — only one of three supported units). Renamed everything: Android `applicationId` / `namespace`, Java package tree, class names (`TwPower*` → `TwCoord*`), `Intent` action (`com.atakmap.android.twcoord.SHOW_PLUGIN`), `SharedPreferences` key (`tw_coord_settings`), `drawable` (`ic_tw_coord`), Gradle `rootProject.name` (`atak_tw_coord_plugin`), ProGuard repackage class, APK filename. Live docs (UI, ADR prose, spec/contracts) flipped in lockstep. Historical commit messages and the git repo root directory (`atak_tw_power_plugin/`) remain unchanged.

Pre-launch plugin so SharedPreferences migration was unnecessary.

## Alternatives considered

- **Keep the spec as deferred items / future flags.** Rejected. Spec is supposed to describe what the plugin does, not what we thought it would do. Letting spec lag the code by a sprint defeats `/speckit-analyze`'s coherence check.
- **Sub-divide into one ADR per decision.** Would have produced ADR-0008 through ADR-0013, with significant cross-references. The decisions all originated in the same on-device session and share their motivation (user feedback on a working MVP), so a bundle ADR is genuinely the unit of thought here.
- **Switch to 7-parameter Bursa-Wolf for outer-island TWD67 accuracy parity with NCKU / pyproj.** Rejected for v1: the simpler 4-param path is what `pwa_map` uses (our anchor reference), and the ±10-20 m outer-island drift is acceptable for typical GPS work. Documented as an upgrade path in the settings advisory and in `TaiwanCities` Javadoc.
- **Make the Tools icon open the settings page instead of cycling.** Rejected: the user-facing affordance is the cycle. Settings are reachable via the standard `Tool Preferences → Specific → TW Coordinates` path; duplicating that under the Tools icon would be redundant.

## Consequences

**Positive:**

- Spec and shipped behaviour are aligned again (analyse F1-F4 closed).
- The cycle interaction gives the plugin a discoverable single-tap affordance that the no-op icon previously lacked.
- Outer-island users (Penghu / Kinmen / Matsu fieldwork) are now first-class instead of "out of coverage".
- Settings page is usable on Android 14+ ATAK (the dim-grey preference text was a real visibility regression on the original install).
- The `twcoord` identifier no longer implies "台電 only".

**Negative:**

- Six concurrent changes inflate the commit-history footprint between the two `/speckit-analyze` runs. Bisecting "did the cycle break ME row rendering" needs to walk past several intermediate commits.
- The 11-char Taipower default produces digits that exceed typical GPS accuracy. Users who care will need a future precision-toggle preference; we have not yet exposed it.
- Outer-island TWD67 is documented as ±10-20 m vs the official 7-param shift. Users who measure sub-metre on Penghu will see the gap and may file a follow-up.
- Custom `pref_item.xml` layouts hard-code colours; if ATAK ships a new SettingsActivity theme in a future version we may regress against it. Mitigation: revisit during the next ATAK SDK upgrade.
- The bundle nature of this ADR means each individual decision is less prominent in `docs/adr/` than it would be as its own ADR. Cross-references from FR-021 / FR-022 / FR-023 link back to specific sections here.

## Links

- Commits between `109e9dd` (twpower → twcoord doc sweep) and the commit carrying this ADR cover D1-D6.
- Spec: FR-011 (revised), FR-021 / FR-022 / FR-023 (new).
- Data-model: §1 row for `TAIPOWER` (revised default).
- Prior ADRs: ADR-0001 (coord math source — D1 references the precision policy here), ADR-0002 (no-TDAL — explains why we don't ship `coordinate_systems.xml`), ADR-0007 (native-widget styling — D4/D5 reuse the same reverse-engineering technique to fix the dim preferences).
- Constitution Principles III (UX consistency — D4/D5) and V (this ADR satisfies the post-implement / post-analyze documentation requirement).
