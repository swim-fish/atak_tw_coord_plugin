# Contract: Compact Selector Presentation

## Scope

This contract applies only to:

- `native_entry_system_group`;
- `native_entry_twd97_zone_group`;
- `native_entry_twd67_zone_group`;
- their system and 121/119 `RadioButton` children.

Address mode, candidate, county, and district controls retain their current
target and visual contracts.

## Geometry

| Property | Required value |
|----------|----------------|
| Group layout/touch height | 48 dp |
| Option layout/touch height | At least 48 dp |
| Visible track height | 36 dp |
| Visible checked fill height | 36 dp |
| Transparent band | 6 dp top and 6 dp bottom |
| Group vertical padding | 0 dp |
| Child height behavior | Fill the 48 dp group |

The relationship is fixed:

```text
48 dp target - (2 × 6 dp inset) = 36 dp visible track
```

Named dimensions are the source of truth for tests, XML, and documentation.

The actual view rectangle is the touch/focus rectangle. No `TouchDelegate`,
clickable wrapper, overlay, negative offset, or overlapping target is allowed.

## Drawing

1. Both `native_entry_segment_track.xml` and every state in
   `native_entry_segment_option.xml` use the 6 dp vertical inset.
2. Selected fill does not extend beyond the 36 dp track.
3. Track and fill remain vertically centered.
4. Existing corners, selected/unselected affordance, and ATAK panel contrast
   remain recognizable.
5. Disabled-and-checked appears before generic disabled in state resolution and
   remains visibly different from disabled-and-unchecked.

## Interaction

1. Tapping any point inside an option's 48 dp bounds, including either
   transparent band, selects that option.
2. A tap selects only the intended option and produces one existing
   human-change notification.
3. Programmatic render/check produces no human-change notification.
4. Option rectangles never overlap and remain in logical left-to-right order.
5. Read-only selectors reject input while preserving checked state.

## Accessibility and localization

- Retain native `RadioGroup`/`RadioButton` roles and checkable state.
- Each system option exposes its localized visible name.
- Each zone exposes TWD system context and 121/119, either through group
  semantics or explicit localized descriptions.
- Checked and disabled states are both exposed in read-only mode.
- Hidden inactive panes are `GONE` and absent from traversal.
- No duplicate/invisible accessibility focus node is added.
- Traversal remains system selector → active fields → active zone/advisory/
  status → ATAK-owned controls.
- English, Traditional Chinese (Taiwan), and Japanese labels remain one-line,
  centered, unclipped, and unellipsized at font scales 1.0 and 2.0 on the
  smallest supported pane width.
- Touch targets remain at least 48 by 48 dp at that width.

## Scroll and host layout

The actual selector row height remains 48 dp. Therefore:

- the existing `BoundedPaneScrollView` remains the sole plugin scroll owner;
- short-pane shrink-wrap behavior is unchanged;
- active fields and ATAK elevation/Auto Fill/Clear/Copy/confirmation remain
  reachable;
- the visual compaction is not claimed to save 12 dp of layout height.

## Automated contract tests

1. All three groups measure 48 dp and have zero vertical padding.
2. Every child measures/layouts to at least 48 by 48 dp.
3. Named dimensions satisfy the 48/36/6 relationship.
4. Track and checked fill draw to exactly 36 dp within 48 dp bounds.
5. Child rectangles at a conservative and smallest recorded host width are
   pairwise non-overlapping and ordered.
6. Touch DOWN/UP in both transparent bands selects the intended option once.
7. Programmatic checks remain silent.
8. Disabled/checked is distinct from disabled/unchecked and remains exposed to
   accessibility.
9. English, Traditional Chinese (Taiwan), and Japanese labels at 1.0 and 2.0
   font scale are one-line, centered, unellipsized, and unclipped.
10. One-scroll-owner and Address-control reachability regressions remain green.

## Device release gates

On exact ATAK 5.5.x and ATAK 5.7.0.9:

- record density, actual pane content width, orientation, locale, and numeric
  font scale;
- measure the sanitized screenshot's visible track as 36 dp with at most one
  physical-pixel antialias tolerance;
- confirm accessibility/button bounds are at least 48 dp;
- tap the top and bottom transparent bands for every system and zone option,
  20 repetitions each, with zero wrong or duplicate selections;
- verify portrait/landscape and font scales 1.0/2.0 for EN/zh-TW/JA;
- verify TalkBack/Switch Access names, order, checked, and disabled state;
- verify all plugin fields and ATAK-owned controls remain reachable;
- verify the read-only selected system/zone remains visually clear.
