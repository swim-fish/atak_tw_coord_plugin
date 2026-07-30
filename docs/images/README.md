# Documentation Image Workflow

Documentation screenshots use stable, unique numeric filenames such as
`09-native-entry-all-tabs.jpg`; letter suffixes are allowed for a tightly
coupled sequence such as `08a-...` and `08b-...`. Never reuse a number for a
different image.

Before committing an image:

1. Crop only what is needed and check that no callsign, notification content,
   device identifier, location, account, or workstation path is visible.
2. Remove EXIF/XMP fields that can reveal GPS, device make/model, timestamps,
   software, author, comments, or serial identifiers.
3. Run `python scripts/check-doc-images.py` to verify naming, references, and
   metadata policy.
4. Update every applicable English, zh-TW, and UI guide reference. Do not assume
   replacing the file alone updates translated documentation.
5. Confirm `git check-attr filter -- docs/images/<file>` reports the expected
   Git LFS policy for binary assets.

Debug captures and raw originals remain outside Git. Only the reviewed,
sanitized image is committed.

## Current feature 013 replacements

- `08-tools-menu.jpg` is a pixel crop of the retained `TW Coordinates` tile
  from the earlier full Tools capture. The crop removes the retired
  `TW Coord GoTo`, `TW Offline Addr`, and `TW Addr Search` tiles without
  redrawing the remaining UI. `08a-tools-icon-tw-coord.png` remains the current
  standalone icon; `08b` through `08d` are historical exports only.
- `23a-native-address-full.png` and
  `23b-native-address-structured.png` are a coupled ATAK-CIV 5.7.0.9 capture
  of the native Taiwan Address tab, refreshed for plugin 1.4.4 to show the
  top-aligned right action column. They replace the obsolete three-tab
  `22-atak-enter-coordinate.jpg`; address values are redacted.
- `24-offline-address-data.png` shows the current `TW Coordinates` landing
  page, including the top settings action. It replaces the older populated
  manager capture `17-tw-offline-addr-usage.jpg`.
- `25a-native-address-county-selector.png` and
  `25b-native-address-district-selector.png` are a coupled active-data chooser
  sequence. They show the strictly contained map-centre locality promoted
  ahead of the remaining imported counties or postal-ordered districts.

The committed crops exclude the surrounding map, coordinate readouts, desktop
window frame, and device status information. Raw captures are retained only
outside Git for local recovery.

## Current feature 014 additions

- `26a-native-taipower-single.png` and
  `26b-native-taipower-split.png` are a coupled ATAK-CIV 5.7.0.9 capture of
  the Taipower layout switch. The first shows **Single field** content with
  the far-right **Guided fields** action; the second shows the 1/4/2/2-or-4
  guided groups with the far-right **Single field** action.
- Both committed images are cropped to the ATAK Go To dialog. The surrounding
  map, address/readout overlays, and exact Taipower coordinate values are
  excluded or redacted.
