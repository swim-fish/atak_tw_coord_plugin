# Coordinate Test-Data Attribution

`osgeo-taiwan-control-points.csv` is an adapted, test-only fixture derived from the OSGeo Wiki page
**Taiwan datums/Test points**.

- Source: <https://wiki.osgeo.org/wiki/Taiwan_datums/Test_points>
- Upstream attribution: OSGeo Wiki contributors
- Upstream Wiki licence: Creative Commons Attribution-ShareAlike 2.5
  (<https://creativecommons.org/licenses/by-sa/2.5/>)
- Fixture licence: Creative Commons Attribution-ShareAlike 2.5

Changes made for this repository:

- selected a geographically stratified set of 33 main-island rows;
- retained all 42 published Penghu common-point rows;
- retained 5 Kinmen and 8 Matsu rows for projection and zone-selection regression coverage;
- reduced the upstream table to the fields required by automated tests;
- normalised decimal formatting and added explicit region, TM2 zone, and observed-versus-derived
  metadata;
- marked Kinmen and Matsu TWD67 values as software-derived because the source states that those
  areas did not historically adopt TWD67.

The repository-level MIT licence does not replace or relicense this fixture. Code that loads or tests
the fixture remains under the repository's MIT licence; the adapted data file remains under the
licence above.
