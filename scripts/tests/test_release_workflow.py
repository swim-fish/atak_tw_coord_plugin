from __future__ import annotations

import importlib.util
import json
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[2]


def load_script(name: str):
    path = ROOT / "scripts" / name
    module_name = "test_" + name.replace("-", "_").replace(".", "_")
    spec = importlib.util.spec_from_file_location(module_name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


class ReleaseReadinessTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.readiness = load_script("check-release-readiness.py")

    def test_open_release_gates_only_returns_unchecked_labeled_tasks(self):
        with tempfile.TemporaryDirectory() as temp:
            feature = Path(temp)
            (feature / "tasks.md").write_text(
                "- [ ] T001 [RELEASE-GATE] device evidence\n"
                "- [X] T002 [RELEASE-GATE] signer evidence\n"
                "- [ ] T003 ordinary task\n", encoding="utf-8")
            self.assertEqual(["tasks.md:1"], self.readiness.open_release_gates(feature))

    def test_project_skill_eval_files_are_valid(self):
        for skill in ("release-readiness", "tpp-release-pipeline",
                      "atak-device-deploy", "docs-screenshot-workflow",
                      "native-coordinate-entry-pane"):
            path = ROOT / ".agents" / "skills" / skill / "evals" / "evals.json"
            data = json.loads(path.read_text(encoding="utf-8"))
            self.assertEqual(skill, data["skill"])
            self.assertGreaterEqual(len(data["evals"]), 2)


class ArchiveSafetyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tpp = load_script("build-tpp-source-zip.py")

    def test_sensitive_content_finds_windows_home_inside_text(self):
        with tempfile.TemporaryDirectory() as temp:
            archive = Path(temp) / "source.zip"
            with zipfile.ZipFile(archive, "w") as output:
                local_path = "C:" + "\\Users\\operator\\work"
                output.writestr("root/README.md", f"path {local_path}")
            result = self.tpp.verify_sensitive_content(archive)
            self.assertEqual(self.tpp.Check.FAIL, result.status)

    def test_sensitive_content_accepts_portable_placeholder(self):
        with tempfile.TemporaryDirectory() as temp:
            archive = Path(temp) / "source.zip"
            with zipfile.ZipFile(archive, "w") as output:
                output.writestr("root/README.md", "path <TAK_WORKSPACE>/work")
            result = self.tpp.verify_sensitive_content(archive)
            self.assertEqual(self.tpp.Check.PASS, result.status)

    def test_source_provenance_round_trip(self):
        stage = load_script("stage-tpp-release.py")
        with tempfile.TemporaryDirectory() as temp:
            archive = Path(temp) / "source.zip"
            with zipfile.ZipFile(archive, "w") as output:
                output.writestr("root/settings.gradle", "")
            self.tpp.add_source_provenance(archive, "root", "1.4.2", "a" * 40)
            self.assertEqual(
                {"schema_version": 1, "plugin_version": "1.4.2",
                 "source_commit": "a" * 40},
                stage.read_source_provenance(archive))


class DocumentationImageTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.check = load_script("check-doc-images.py")
        cls.scrub = load_script("scrub-doc-images.py")

    @staticmethod
    def jpeg_with_exif() -> bytes:
        tiff = (b"II" + struct.pack("<H", 42) + struct.pack("<I", 8) +
                struct.pack("<H", 1) +
                struct.pack("<H", 0x0131) + struct.pack("<H", 2) +
                struct.pack("<I", 4) + b"tool" + struct.pack("<I", 0))
        payload = b"Exif\x00\x00" + tiff
        app1 = b"\xff\xe1" + struct.pack(">H", len(payload) + 2) + payload
        return b"\xff\xd8" + app1 + b"\xff\xda\x00\x02\xff\xd9"

    def test_scrub_jpeg_removes_exif_without_changing_scan_data(self):
        original = self.jpeg_with_exif()
        cleaned = self.scrub.strip_jpeg(original)
        self.assertIn(b"\xff\xda\x00\x02\xff\xd9", cleaned)
        self.assertNotIn(b"Exif\x00\x00", cleaned)
        self.assertFalse(self.check.jpeg_sensitive_tags(cleaned))

    def test_numbered_image_filename_contract(self):
        self.assertIsNotNone(self.check.IMAGE_NAME.fullmatch("08a-tools-icon.png"))
        self.assertIsNone(self.check.IMAGE_NAME.fullmatch("final.jpg"))


class StageReleaseTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.stage = load_script("stage-tpp-release.py")
        cls.tpp = load_script("build-tpp-source-zip.py")

    def test_unverifiable_signer_leaves_no_partial_staging_directory(self):
        head = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT,
                              capture_output=True, text=True, check=True).stdout.strip()
        with tempfile.TemporaryDirectory() as temp:
            temp_path = Path(temp)
            source = temp_path / "source.zip"
            with zipfile.ZipFile(source, "w") as output:
                output.writestr("root/settings.gradle", "")
            self.tpp.add_source_provenance(source, "root", "1.4.2", head)

            bundle = temp_path / "response.zip"
            entries = {
                "ATAK-Plugin-x-1.4.2--5.5.0-civ-release-unsigned.apk": b"not an apk",
                "civRelease-app-mapping.txt": b"mapping",
                "fortify_scan_results.pdf": b"pdf",
                "dependency-check-report.html": b"html",
            }
            with zipfile.ZipFile(bundle, "w") as output:
                for name, data in entries.items():
                    output.writestr(name, data)
            destination = temp_path / "stage"
            argv = ["stage-tpp-release.py", str(bundle), "--source-zip",
                    str(source), "--out", str(destination)]
            with mock.patch.object(sys, "argv", argv), \
                    mock.patch.object(self.stage, "verify_signer", return_value=None):
                self.assertEqual(1, self.stage.main())
            self.assertFalse(destination.exists())


if __name__ == "__main__":
    unittest.main()
