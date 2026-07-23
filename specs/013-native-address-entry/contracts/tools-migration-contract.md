# Contract: Tools and Legacy Workflow Migration

## Public information architecture

After plugin reload, ATAK Tools contains exactly one plugin item:

```text
TW Coordinates
```

The toolbar item array contains only the existing `TwCoordTool`. The public
`TW Coord GoTo`, `TW Addr Search`, and `TW Offline Addr` items no longer exist.
Selecting `TW Coordinates` opens the retained internal offline-data manager
directly; it does not open Settings first.

## Retained navigation

The offline-data manager is the default Tools landing page and retains:

- Import, Replace, Remove, status, provenance, progress, and error states;
- a top `TW Coordinates settings` action that closes the DropDown before
  opening the plugin Settings Activity.

`TW Coordinates` settings retain:

- coordinate display settings;
- map address-row settings;
- address candidate ordering/confidence settings as applicable;
- offline dataset status and imported county summaries;
- an always-selectable dataset management entry.

The Settings dataset entry closes the foreground Settings Activity before it
posts the manager action through the map View. The resulting DropDown must be
visible immediately rather than hidden beneath Settings. This route does not
require any map address-row display toggle to be enabled.

```text
ATAK Tools
  → TW Coordinates
  → Offline address data
      → TW Coordinates settings
          → Dataset status
              → close Settings
              → Offline address data
```

## Receiver and action contract

### Retain

- Main `TW Coordinates` settings action and receiver.
- Internal `SHOW_OFFLINE_ADDRESS` action and `OfflineAddressReceiver`.
- Dataset-change action/listeners used by registry, widget, settings, and
  native lookup.
- File picker/import activities declared by the existing manifest.

### Retire

- Custom Go To tool, show action, receiver, page, and navigation-completed
  action.
- Forward-search tool, show action, receiver, and page.
- Offline-address public tool only; its internal action/receiver remains.

Stale custom Go To and forward-search action strings are not registered. When
sent, they open no legacy UI and do not crash or mutate the map. No redirect is
promised because no ATAK 5.5 public contract selects Taiwan Address directly.

## Data and preference migration

- Existing valid imported county datasets, boundary data, manifests,
  provenance, and paths remain byte-compatible and usable without re-import.
- Existing search ordering, confidence, readout toggles, and native last-tab
  preference remain in effect where relevant.
- Remove the custom Go To settings shortcut.
- Legacy custom Go To drafts, Recent entries, marker mode, and icon values
  remain inert. They are not deleted and are not read by the native workflow.
- There is no inactive dataset state introduced by this feature.

## Safe code/resource removal order

1. Move shared coordinate parser/value classes and their tests to a neutral
   coordinate package without behavior changes.
2. Establish the shared address service, candidate/provenance model, parser,
   ranking, database, and lifecycle tests.
3. Integrate native Address and prove parity for forward/reverse lookup.
4. Remove custom Go To receiver/page/tool/intents and UI-only state/resources.
5. Remove forward-search receiver/page/tool/intents and UI-only state/resources.
6. Remove the offline-address tool/icon only; retain internal page resources.
7. Audit strings, layouts, drawables, render scripts, tests, and documentation
   for orphaned references before deletion.

## Lifecycle

### Startup order

1. Initialize plugin contexts/preferences and non-address foundation.
2. Initialize filesystem, importer, import executor, migration, registry, and
   boundary data.
3. Initialize shared lookup service and widget adapter.
4. Initialize coordinator and retained internal offline manager.
5. Construct/start native registrar with lookup and manager navigation.
6. Register remaining settings/dataset actions and listeners.

Address infrastructure failure supplies a no-data service so the coordinate
tabs and `TW Coordinates` remain usable.

### Teardown order

1. Mark component closing and clear externally reachable providers.
2. Stop registrar and dispose native pane/lookup handles.
3. Unregister/dispose internal manager and UI listeners.
4. Close coordinator and reject late import activation.
5. Close shared lookup service and widget adapter.
6. Close boundary and leased dataset registry/facades.
7. Shut down import executor and clear component references.

Every step is idempotent and failure-contained; a failed step does not skip
later ownership release.

## Device reload requirement

ATAK caches Tools items at plugin load. Installation alone is not acceptance
evidence. After installing a test APK, disable and re-enable the plugin or
fully restart ATAK before verifying the one-item Tools state.

## Documentation migration

- README and both user guides describe the single Tools entry and native
  four-tab workflow.
- The forward-search guide is replaced by a native Address guide or clearly
  retired with a replacement link.
- Offline address guides enter through `TW Coordinates`.
- Native pane and settings UI documents are updated.
- Active Tools/native screenshots are replaced and renumbered through the
  repository screenshot workflow; obsolete icon crops are removed or marked
  historical only after reference checks.
- Historical specs and ADRs remain intact. ADR-0026 records the superseding
  decision and links the affected historical ADRs.
