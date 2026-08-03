# Research: Compact Structured Address Layout

## R1. Preserve the existing Address content/action split

**Decision**: Keep the Address body as an 8:2 horizontal split. Structured
fields remain in the left content column; the alternate-mode and candidate
actions remain in the top-aligned right column.

**Rationale**: This matches the accepted preview, the current native Address
contract, and the compact Taipower action pattern. It avoids placing an action
below content that can grow and keeps ATAK-owned controls reachable.

**Alternatives considered**:

- Use the full pane width for fields and move the mode action below them:
  rejected because it regresses the established Address/Taipower pattern and
  consumes vertical space.
- Reduce the action column below 20 percent: rejected because localized action
  text and the 48 dp target already fit the accepted 8:2 geometry.

## R2. Nest existing field groups inside two equal rows

**Decision**: Add one first-row container for county/city plus
district/township and one second-row container for road/locality plus
house-number/floor. Each existing field-group container occupies weight 1 in
its row and keeps its label/input 3:7 internal proportion.

**Rationale**: Existing view IDs, field types, labels, hints, controller
bindings, enabled states, and editor actions remain unchanged. Only parentage
and geometry change, keeping the implementation narrow and testable.

**Alternatives considered**:

- Replace the form with a grid widget: rejected because it adds a new layout
  abstraction and makes equal weights and accessibility order less explicit.
- Put labels above inputs: rejected because the approved preview uses inline
  compact labels and the existing 3:7 groups have adequate width inside each
  half-row at the supported pane sizes.
- Use four raw input fields without labels like Taipower guided entry: rejected
  because locality and address components are not self-identifying fixed
  groups and require persistent visible labels.

## R3. Keep row-major accessibility and editor order

**Decision**: Order view children as county/city, district/township,
road/locality, then house-number/floor. County and district keep their existing
selector behavior; road keeps Next targeting the tail; tail keeps Search/Done.

**Rationale**: Visual, touch, screen-reader, and keyboard order agree. No Java
callback change is necessary.

**Alternatives considered**:

- Column-major order: rejected because it conflicts with the operator's visual
  scan and the accepted row grouping.
- Add custom accessibility traversal code: rejected because ordered XML
  children and existing content descriptions already express the required
  sequence.

## R4. Shrink-wrap the compact form instead of forcing the old viewport cap

**Decision**: The existing bounded outer scroll owner remains, but the two-row
structured form should measure below the 216 dp cap at the 900 dp reference
width when no status/candidate content is visible.

**Rationale**: The old four-row test intentionally reached the cap. Keeping
that result after reducing the form to two rows would hide the benefit and
indicate unnecessary height or padding.

**Alternatives considered**:

- Retain a fixed 216 dp structured viewport: rejected because it wastes the
  vertical space this feature is meant to return to ATAK.
- Remove the bounded scroll owner: rejected because status text, large fonts,
  and future candidate content still require one safe overflow path.

## R5. Synchronize version and current documentation without an ADR

**Decision**: Set the plugin and user-facing documentation version to `1.5.1`,
add a changelog entry, and update the UI and user-guide descriptions. Keep
current screenshots until a sanitized physical-device replacement is captured
as a release gate.

**Rationale**: The user explicitly requested a patch-version increment. The
change refines presentation within ADR-0023's existing native pane architecture
and does not establish or reverse an architectural decision.

**Alternatives considered**:

- Add an ADR: rejected because no contract boundary, data model, compatibility
  strategy, or operational posture changes.
- Treat the generated mockup as release evidence: rejected because it is a
  design preview, not a physical-device screenshot.
