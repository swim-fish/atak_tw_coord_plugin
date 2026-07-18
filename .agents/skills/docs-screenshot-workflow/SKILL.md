---
name: docs-screenshot-workflow
description: Add, replace, renumber, sanitize, or verify ATAK documentation screenshots. Use when screenshots or docs/images change, including English/zh-TW manual synchronization and EXIF privacy checks.
---

# Documentation Screenshot Workflow

Read `docs/images/README.md` and inspect both user guides plus the relevant
`docs/ui/` page.

1. Assign the next unique two-digit number; use letter suffixes only for a
   coupled sequence. Never reuse a number for unrelated content.
2. Review visible content for callsigns, notifications, identifiers, account
   data, or locations.
3. Strip sensitive EXIF/XMP/PNG metadata without changing pixels.
4. Update applicable English, zh-TW, and UI-guide references and alt text.
5. Run `python scripts/check-doc-images.py` and verify Git LFS attributes.

Raw screenshots remain outside Git. Commit only reviewed sanitized assets.
