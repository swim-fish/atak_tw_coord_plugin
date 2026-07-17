---
title: "ADR-0022: Set the Minimum ATAK Runtime to 5.5"
status: "Accepted"
date: "2026-07-17"
authors: "Project maintainers"
tags: ["architecture", "compatibility", "atak-sdk"]
supersedes: ""
superseded_by: ""
---

# ADR-0022: Set the Minimum ATAK Runtime to 5.5

## Status

Accepted

## Context

The plugin compiles against the ATAK-CIV 5.7.0.3 SDK while previously declaring runtime compatibility with `com.atakmap.app@5.4.0.CIV`. The planned integration of Taiwan coordinate input into ATAK's native coordinate-entry dialog uses the public `CoordinateEntryPane` and `CoordinateEntryCapability` extension seam. The local `atak-civ` 5.5 source line exposes this seam, but this workspace has no ATAK 5.4 SDK with which to validate the same contract.

Keeping the 5.4 declaration would advertise a compatibility boundary that the native coordinate-entry integration cannot substantiate.

## Decision

Set `ext.ATAK_VERSION` to `5.5.0`, making `com.atakmap.app@5.5.0.CIV` the minimum declared runtime compatibility. Keep ATAK-CIV 5.7.0.3 as the compile SDK.

New code may use the public ATAK 5.5 coordinate-entry seam directly and does not require a reflection-based ATAK 5.4 fallback.

## Consequences

### Positive

- **POS-001**: The declared runtime boundary matches the SDK surface required by the native coordinate-entry integration.
- **POS-002**: The implementation avoids a reflection bridge and a separate ATAK 5.4 fallback path.
- **POS-003**: Build metadata, documentation, and generated APK names share one minimum-version value.

### Negative

- **NEG-001**: ATAK 5.4 installations are no longer supported by future plugin releases.
- **NEG-002**: The project still needs an on-device ATAK 5.5 smoke test before claiming that new SDK integrations are verified at the minimum runtime.
- **NEG-003**: Historical release documents continue to mention 5.4 and must be interpreted in their original release context.

## Alternatives Considered

### Keep ATAK 5.4 with Conditional Loading

- **ALT-001**: **Description**: Isolate `CoordinateEntryPane` references behind a runtime reflection bridge and retain the custom GoTo page as the ATAK 5.4 fallback.
- **ALT-002**: **Rejection Reason**: This adds lifecycle complexity and preserves two user flows solely for an unverified runtime.

### Raise the Minimum Runtime to ATAK 5.7

- **ALT-003**: **Description**: Match the minimum runtime exactly to the 5.7.0.3 compile SDK.
- **ALT-004**: **Rejection Reason**: This unnecessarily excludes ATAK 5.5 and 5.6 installations from the supported range.

## Implementation Notes

- **IMP-001**: Set `ext.ATAK_VERSION = "5.5.0"` in `app/build.gradle`.
- **IMP-002**: Keep historical ADRs, shipped feature plans, and old release artifact examples unchanged.
- **IMP-003**: Verify a generated APK declares `com.atakmap.app@5.5.0.CIV` and includes `5.5.0` in its generated filename.
- **IMP-004**: Run an on-device smoke test on the oldest supported ATAK 5.5 build before releasing native coordinate-entry integration.

## References

- **REF-001**: `app/build.gradle`
- **REF-002**: `specs/010-goto-ui-redesign/plan.md`
- **REF-003**: ATAK source classes `CoordinateEntryPane` and `CoordinateEntryCapability`
- **REF-004**: `README.md` and `CHANGELOG.md`
