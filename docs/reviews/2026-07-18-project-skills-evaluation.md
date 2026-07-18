# Project Skills Evaluation — 2026-07-18

## Scope

This review covers the five project-specific skills introduced by the release
workflow hardening change:

- `release-readiness`
- `tpp-release-pipeline`
- `atak-device-deploy`
- `docs-screenshot-workflow`
- `native-coordinate-entry-pane`

Each skill has two committed prompt/expectation cases in its `evals/evals.json`.
The deterministic assertions in `scripts/tests/test_release_workflow.py` verify
that every eval file parses, names the owning skill, and retains at least two
cases. Repository scripts provide the objective behavior gates used by the
release and screenshot skills.

## Results

| Skill | Trigger clarity | Safety boundary | Objective check | Result |
| --- | --- | --- | --- | --- |
| release-readiness | TPP/tag/publication requests | Does not infer device evidence or mutate external state | `check-release-readiness.py` | PASS |
| tpp-release-pipeline | Source ZIP, TPP bundle, release assets | Requires explicit upload/push/publish authority; immutable tags | TPP source/stage scripts + provenance tests | PASS |
| atak-device-deploy | Build/install/reload/device evidence | Does not commit serials or infer ATAK 5.5 from 5.7 | Procedure/eval inspection | PASS |
| docs-screenshot-workflow | Screenshot add/replace/renumber | Visible-data review plus EXIF/XMP/LFS gate | `check-doc-images.py` + scrub tests | PASS |
| native-coordinate-entry-pane | Native Go To/Convert Coordinates work | Public SDK, lifecycle, DD sizing, all-tab/active-only invariants | Procedure/eval inspection | PASS |

## Limitations

This environment did not run independent model-vs-baseline agent trials. The
evaluation therefore covers trigger wording, required safety decisions,
machine-readable eval cases, script behavior, and unit tests. Future material
skill changes should reuse the committed prompts for comparative agent trials
when that execution mode is available.
