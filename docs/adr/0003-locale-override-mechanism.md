# ADR-0003: Use `createConfigurationContext` for in-app language override

**Status**: Accepted
**Date**: 2026-05-16
**Origin**: `/speckit-plan` on feature `001-tw-coord-display` (filed retroactively during `/speckit-analyze` on 2026-05-16 in response to analyze finding F3)

## Context

Clarification round of `/speckit-clarify` (Q1, Q3) produced two
binding requirements:

- **FR-017** — the plugin follows the Android system locale by
  default but exposes an in-app override ("Use system" / English /
  中文（正體） / 日本語).
- **FR-018** — when the user changes the override, *all* plugin UI
  surfaces (settings page and on-map readout overlay) MUST repaint in
  the new language within one rendered frame, with no ATAK restart.

The blunt Android approach — `Activity.recreate()` after a locale
change — is unavailable to us in two ways:

1. We do not own the host `Activity`; ATAK does. Forcing a recreate
   from a plugin is hostile to the host application.
2. Even if we could, `recreate()` would visibly flash the on-map
   overlay and is overkill for swapping strings in a few widgets.

## Decision

Wrap the plugin's `Context` with
`context.createConfigurationContext(configWithLocale)` whenever the
user picks a non-`SYSTEM` override. The wrapped context's
`Resources.getString(...)` returns strings for the requested locale
*for that context only*, leaving the host `Activity`'s locale
untouched.

Concrete shape (see `contracts/preference-store.md` and
`research.md` R7):

- `LocaleOverride.contextFor(Context base, LanguageOverride choice,
  Locale systemLocale)` builds a `Configuration` with the resolved
  locale and returns `base.createConfigurationContext(cfg)`.
- The plugin keeps a single `localisedContext` field on
  `TwCoordMapComponent`, refreshed whenever the
  `PreferenceStore.Listener` fires for `pref_ui_language`.
- The widget does *not* cache `Strings`. On every `render(...)` call
  the `MapComponent` resolves `Formatter.Strings` from
  `localisedContext.getResources()`, so a language flip becomes
  visible on the very next frame.
- The locale-listener fires *before* the widget-listener
  (registration order is guaranteed by `PreferenceStore.Listener`'s
  contract), so the widget never reads a stale `localisedContext`.

## Alternatives considered

- **`Activity.recreate()` after every override change.** Visibly
  flashes, requires owning the activity, and rebuilds state we do
  not want to rebuild. Rejected.
- **Programmatic string tables (hand-rolled `Map<Locale,
  Map<Key,String>>`).** Bypasses Android resource resolution, loses
  translator tooling (no `strings.xml` workflow), reinvents what
  Android already provides. Rejected.
- **Update `Resources.getSystem().getConfiguration().locale`
  directly.** Deprecated since API 17; globally mutates locale for
  the entire process; pollutes ATAK. Rejected.

## Consequences

**Positive:**

- FR-018 satisfied trivially — string flip happens between two
  consecutive `render(...)` calls.
- The host ATAK locale is never mutated; we are a polite plugin.
- Falls back cleanly: when the override is `SYSTEM`, `contextFor`
  returns the unwrapped `Context` and Android's standard resource
  resolution applies.
- Compatible with our `zh-* → zh-TW`, `ja-* → ja`, else → `en`
  fallback chain (per FR-017 / clarification Q2) implemented in
  `LocaleOverride.mapSystemLocaleToBundle(...)`.

**Negative:**

- Every `render(...)` re-resolves `Strings` from `Resources`, a tiny
  cost (~1 µs per string per call). Mitigated by the small string
  set (8 keys) and the fact that `render(...)` only fires on actual
  map / self-marker / preference changes.
- If a future ATAK update changes how `createConfigurationContext` is
  honoured for plugin contexts, our locale override could silently
  regress; `WidgetRenderTest` covers all three locales as a
  regression sentinel.

## Links

- Spec: FR-017, FR-018
- Plan: `research.md` R7
- Contracts: `contracts/preference-store.md`,
  `contracts/widget-overlay.md`
- Clarification questions: Q1 and Q3, 2026-05-16 session
