# ADR-0024: Use ATAK-CIV 5.7.0.9 as the Compile SDK

**Status**: Accepted
**Date**: 2026-07-17
**Origin**: `/speckit-implement` on feature `011-native-coordinate-entry`
**Related decision**: ADR-0022 retains the ATAK-CIV 5.5.0 minimum runtime

## Context

Feature 011 adds a direct dependency on ATAK's public `CoordinateEntryPane`
and `CoordinateEntryCapability` API. The workspace does not contain an exact
ATAK-CIV 5.5.0 SDK or APK, and the official public 5.5 source tags begin at
5.5.1.1. The 5.5.1.1 source exposes the required public interface and
registration methods, but it is not an exact 5.5.0 binary.

The workspace does contain `ATAK-CIV-5.7.0.9-SDK`, and the reference Galaxy
Tab S10+ (`SM-X826B`, serial `<DEVICE_SERIAL>`) runs ATAK-CIV 5.7.0.9
(`versionCode=1782294331`, build `7a0f6f29`). The SDK `main.jar` has SHA-256
`8AE6CA6028F72A99537FC2CE9436A4E4964356CB90C7934C35ABE7A7CB065B70`, and
`javap -public` confirms the complete pane interface plus public
`getInstance`, `registerPane`, and `unregisterPane` methods.

The product requirement continues to require compatibility with the ATAK 5.5
runtime family. Raising the manifest compatibility token to 5.7.0.9 would
exclude supported 5.5 and 5.6 installations.

## Decision

Use ATAK-CIV 5.7.0.9 as the compile and current-device validation SDK while
retaining `ext.ATAK_VERSION = "5.5.0"` and the manifest contract
`com.atakmap.app@5.5.0.CIV` from ADR-0022.

The 5.5.1.1 public source is accepted as the implementation-time source anchor
for the 5.5 family. Exact ATAK 5.5 on-device install, registration, lifecycle,
and user-journey validation remains a release gate and must not be inferred
from a successful 5.7.0.9 build or device run.

## Alternatives considered

### Raise the minimum runtime to ATAK 5.7.0.9

Rejected because the product must remain loadable on supported ATAK 5.5 and
5.6 installations.

### Continue compiling with ATAK 5.7.0.3

Rejected because the user selected the locally available 5.7.0.9 SDK and the
reference device runs the exact matching 5.7.0.9 runtime.

### Infer all 5.5 runtime behaviour from the current SDK

Rejected. The current SDK proves compilation only. The 5.5.1.1 source anchors
the public surface, and physical 5.5 acceptance remains separately required.

## Consequences

- Gradle resolves ATAK APIs and the takdev plugin from the 5.7.0.9 SDK.
- Generated plugin metadata and APK names continue to declare 5.5.0 minimum
  runtime compatibility.
- ATAK 5.5 device acceptance remains explicitly pending and blocks the final
  release compatibility claim, but not source implementation.
- No reflection or private API compatibility bridge is introduced.

## Links

- `specs/011-native-coordinate-entry/spec.md` — FR-024, FS-005
- `specs/011-native-coordinate-entry/plan.md` — Technical Context and
  Compatibility Matrix
- `specs/011-native-coordinate-entry/research.md` — R1 and T001 evidence
- `docs/adr/0022-set-minimum-atak-runtime-to-5-5.md`
- `.specify/memory/constitution.md` — Principle VII
