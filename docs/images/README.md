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
