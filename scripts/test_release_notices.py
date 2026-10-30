"""Verify evidence preservation without treating extracted notices as clearance."""

import csv
import hashlib
import importlib.util
import pathlib
import tempfile
import unittest
import zipfile


SPEC = importlib.util.spec_from_file_location("notices", pathlib.Path(__file__).with_name("collect-third-party-notices.py"))
NOTICES = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(NOTICES)


class PackagedNoticesTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = pathlib.Path(temporary.name)
        self.package = self.root / "package"
        self.package.mkdir()
        self.archive = self.package / "library.jar"
        self.inventory = self.root / "inventory.csv"
        self.output = self.root / "notices"

    def prepare(self, entries, coordinate="example:library:1"):
        with zipfile.ZipFile(self.archive, "w") as jar:
            for name, content in entries:
                jar.writestr(name, content)
        with self.inventory.open("w", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=["packagePath", "coordinate", "packageSha256"])
            writer.writeheader()
            writer.writerow({"packagePath": "library.jar", "coordinate": coordinate, "packageSha256": NOTICES.sha256(self.archive)})

    def collect(self):
        return NOTICES.collect(self.package, self.inventory, self.output)

    def test_original_bytes_and_hashes_are_preserved_without_approval(self):
        content = b"Original copyright\r\nLicense terms\r\n"
        self.prepare([("META-INF/LICENSE.txt", content), ("META-INF/NOTICE", b"Notice")])
        report = self.collect()
        self.assertFalse(report["redistributionApproved"])
        component = report["components"][0]
        self.assertEqual("unreviewed", component["reviewStatus"])
        self.assertEqual("retained", component["noticeStatus"])
        notice = component["notices"][0]
        self.assertEqual(content, (self.output / notice["path"]).read_bytes())
        self.assertEqual(hashlib.sha256(content).hexdigest(), notice["sha256"])
        self.assertNotIn(str(self.root), (self.output / "manifest.json").read_text())

    def test_missing_notices_are_explicit_and_classes_are_not_license_evidence(self):
        self.prepare([("internal/license/AuthMethod.class", b"class"), ("LICENSE.class", b"class")])
        component = self.collect()["components"][0]
        self.assertEqual("no-detected-notice", component["noticeStatus"])
        self.assertEqual([], component["notices"])

    def test_changed_archive_is_rejected(self):
        self.prepare([("LICENSE", b"license")])
        self.archive.write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "does not match"):
            self.collect()

    def test_escaping_notice_paths_are_rejected(self):
        for name in ("../LICENSE", "/LICENSE", "C:/LICENSE", "folder\\LICENSE"):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                self.output = pathlib.Path(directory) / "out"
                self.prepare([(name, b"license")])
                with self.assertRaisesRegex(ValueError, "Unsafe notice"):
                    self.collect()

    def test_escaping_artifact_paths_are_rejected(self):
        for name in ("../library.jar", "/library.jar", "C:/library.jar", "folder\\library.jar"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                NOTICES.confined_file(self.package, name)

    def test_oversized_notice_is_rejected(self):
        self.prepare([("LICENSE", b"license")])
        original = NOTICES.MAX_NOTICE_BYTES
        self.addCleanup(setattr, NOTICES, "MAX_NOTICE_BYTES", original)
        NOTICES.MAX_NOTICE_BYTES = 2
        with self.assertRaisesRegex(ValueError, "oversized"):
            self.collect()

    def test_linked_notice_is_rejected(self):
        entry = zipfile.ZipInfo("LICENSE")
        entry.create_system = 3
        entry.external_attr = 0o120777 << 16
        self.prepare([(entry, b"../outside")])
        with self.assertRaisesRegex(ValueError, "Linked"):
            self.collect()

    def test_existing_output_is_not_silently_reused(self):
        self.prepare([("LICENSE", b"license")])
        self.collect()
        with self.assertRaisesRegex(ValueError, "new or empty"):
            self.collect()

    def test_project_artifacts_are_verified_but_not_mislabelled_third_party(self):
        self.prepare([("LICENSE", b"MIT")], "project ':application'")
        self.assertEqual([], self.collect()["components"])

    def test_duplicate_inventory_rows_are_rejected(self):
        self.prepare([("LICENSE", b"license")])
        lines = self.inventory.read_text().splitlines()
        self.inventory.write_text("\n".join(lines + lines[1:]) + "\n")
        with self.assertRaisesRegex(ValueError, "Duplicate packaged"):
            self.collect()
        self.assertFalse((self.output / "manifest.json").exists())

    def test_total_extraction_limit_is_enforced(self):
        self.prepare([("LICENSE", b"license"), ("NOTICE", b"notice")])
        original = NOTICES.MAX_TOTAL_BYTES
        self.addCleanup(setattr, NOTICES, "MAX_TOTAL_BYTES", original)
        NOTICES.MAX_TOTAL_BYTES = 8
        with self.assertRaisesRegex(ValueError, "limit exceeded"):
            self.collect()

    def test_empty_notices_are_not_treated_as_complete_evidence(self):
        self.prepare([("LICENSE", b"")])
        with self.assertRaisesRegex(ValueError, "Empty"):
            self.collect()

    def test_empty_inventory_is_rejected(self):
        self.inventory.write_text("packagePath,coordinate,packageSha256\n")
        with self.assertRaisesRegex(ValueError, "inventory is empty"):
            self.collect()

    def test_platform_workflow_collects_and_uploads_original_notices(self):
        workflow = (pathlib.Path(__file__).parents[1] / ".github/workflows/license-inventory.yml").read_text()
        self.assertIn("python3 scripts/collect-third-party-notices.py", workflow)
        self.assertIn("--inventory modules/desktop/build/reports/dependency-license/packaged-artifacts.csv", workflow)
        self.assertIn("build/reports/dependency-license/third-party-notices/", workflow)
        self.assertIn('"scripts/test_release_notices.py"', workflow)


if __name__ == "__main__":
    unittest.main()
