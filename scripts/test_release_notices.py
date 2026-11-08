"""Verify evidence preservation without treating extracted notices as clearance."""

import csv
import hashlib
import importlib.util
import pathlib
import shutil
import subprocess
import sys
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
        self.runtime_legal = self.package / "Visual Agent.app/Contents/app/resources/legal/java-runtime"
        (self.runtime_legal / "legal/java.base").mkdir(parents=True)
        (self.runtime_legal / "legal/java.base/LICENSE").write_bytes(b"Zulu runtime license")
        (self.runtime_legal / "vendor/readme.txt").parent.mkdir(parents=True)
        (self.runtime_legal / "vendor/readme.txt").write_bytes(b"Zulu source offer information")
        self.archive = self.package / "library.jar"
        self.inventory = self.root / "inventory.csv"
        self.license_report = self.root / "licenses-all.json"
        self.license_report.write_text(
            '{"dependencies":[{"moduleName":"example:library","moduleVersion":"1",'
            '"moduleUrls":["https://example.org/library"],"moduleLicenses":['
            '{"moduleLicense":"Example License","moduleLicenseUrl":"https://example.org/license"}]}]}',
        )
        self.project_license = self.root / "LICENSE"
        self.project_license.write_bytes(b"Project license")
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
        return NOTICES.collect(self.package, self.inventory, self.output, self.license_report, self.project_license)

    def test_original_bytes_and_hashes_are_preserved_without_approval(self):
        content = b"Original copyright\r\nLicense terms\r\n"
        self.prepare([("META-INF/LICENSE.txt", content), ("META-INF/NOTICE", b"Notice")])
        report = self.collect()
        self.assertFalse(report["redistributionApproved"])
        component = report["components"][0]
        self.assertEqual("unreviewed", component["reviewStatus"])
        self.assertEqual("gradle-license-report", component["licenseMetadata"]["source"])
        self.assertEqual("Example License", component["licenseMetadata"]["declaredLicenses"][0]["name"])
        self.assertEqual("https://example.org/library", component["licenseMetadata"]["moduleUrls"][0])
        self.assertEqual(hashlib.sha256(self.license_report.read_bytes()).hexdigest(), report["licenseReportSha256"])
        self.assertEqual("retained", component["noticeStatus"])
        notice = component["notices"][0]
        self.assertEqual(content, (self.output / notice["path"]).read_bytes())
        self.assertEqual(hashlib.sha256(content).hexdigest(), notice["sha256"])
        self.assertNotIn(str(self.root), (self.output / "manifest.json").read_text())

    def test_runtime_legal_files_and_vendor_offer_are_retained_with_hashes(self):
        self.prepare([("META-INF/LICENSE.txt", b"Library license")])
        report = self.collect()
        runtime = report["runtimeLegal"]
        self.assertFalse(report["redistributionApproved"])
        self.assertEqual("unreviewed", runtime["reviewStatus"])
        self.assertEqual(
            "Visual Agent.app/Contents/app/resources/legal/java-runtime",
            runtime["packagePath"],
        )
        self.assertEqual(
            b"Zulu source offer information",
            (self.output / "runtime-legal/vendor/readme.txt").read_bytes(),
        )
        self.assertEqual(
            hashlib.sha256(b"Zulu runtime license").hexdigest(),
            next(file["sha256"] for file in runtime["files"] if file["packagePath"].endswith("java.base/LICENSE")),
        )

    def test_runtime_legal_directory_is_found_in_each_native_package_layout(self):
        self.prepare([("META-INF/LICENSE.txt", b"Library license")])
        package_paths = (
            "Visual Agent.app/Contents/app/resources/legal/java-runtime",
            "Visual Agent/app/resources/legal/java-runtime",
            "Visual Agent/lib/app/resources/legal/java-runtime",
        )
        for index, package_path in enumerate(package_paths):
            with self.subTest(package_path=package_path):
                shutil.rmtree(self.runtime_legal)
                self.runtime_legal = self.package / package_path
                (self.runtime_legal / "vendor").mkdir(parents=True)
                (self.runtime_legal / "vendor/readme.txt").write_bytes(f"offer-{index}".encode())
                self.output = self.root / f"notices-{index}"
                runtime = self.collect()["runtimeLegal"]
                self.assertEqual(package_path, runtime["packagePath"])
                self.assertEqual(
                    f"offer-{index}".encode(),
                    (self.output / "runtime-legal/vendor/readme.txt").read_bytes(),
                )

    def test_missing_runtime_legal_directory_fails_closed(self):
        self.prepare([("META-INF/LICENSE.txt", b"Library license")])
        shutil.rmtree(self.runtime_legal)
        with self.assertRaisesRegex(ValueError, "exactly one resources/legal/java-runtime"):
            self.collect()
        self.assertFalse((self.output / "manifest.json").exists())

    @unittest.skipIf(sys.platform == "win32", "Creating symlinks requires Windows developer privileges")
    def test_linked_runtime_legal_file_is_rejected(self):
        self.prepare([("META-INF/LICENSE.txt", b"Library license")])
        outside = self.root / "outside.txt"
        outside.write_text("external")
        (self.runtime_legal / "linked.txt").symlink_to(outside)
        with self.assertRaisesRegex(ValueError, "Linked runtime legal entry"):
            self.collect()

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
        report = self.collect()
        self.assertEqual([], report["components"])
        project_artifact = report["projectArtifacts"][0]
        self.assertEqual("project ':application'", project_artifact["coordinate"])
        self.assertEqual("repository-license-file", project_artifact["licenseEvidence"]["source"])
        self.assertEqual(NOTICES.sha256(self.project_license), project_artifact["licenseEvidence"]["sha256"])
        self.assertEqual(report["projectLicenseSha256"], project_artifact["licenseEvidence"]["sha256"])

    def test_missing_project_license_fails_closed(self):
        self.project_license.unlink()
        self.prepare([("LICENSE", b"MIT")])
        with self.assertRaisesRegex(ValueError, "Project license must be a regular file"):
            self.collect()

    def test_missing_exact_license_metadata_fails_closed(self):
        self.license_report.write_text('{"dependencies":[]}')
        self.prepare([("LICENSE", b"MIT")])
        with self.assertRaisesRegex(ValueError, "No exact Gradle license metadata"):
            self.collect()
        self.assertFalse((self.output / "manifest.json").exists())

    def test_declared_missing_license_is_explicit_and_unreviewed(self):
        self.license_report.write_text(
            '{"dependencies":[{"moduleName":"example:library","moduleVersion":"1",'
            '"moduleUrls":[],"moduleLicenses":[]}]}'
        )
        self.prepare([("LICENSE", b"MIT")])
        component = self.collect()["components"][0]
        self.assertEqual("no-license-declaration", component["licenseMetadata"]["status"])
        self.assertEqual([], component["licenseMetadata"]["declaredLicenses"])
        self.assertEqual("unreviewed", component["licenseMetadata"]["reviewStatus"])

    def test_null_license_name_and_url_are_preserved_from_gradle_report(self):
        self.license_report.write_text(
            '{"dependencies":[{"moduleName":"example:library","moduleVersion":"1",'
            '"moduleUrls":[],"moduleLicenses":[{"moduleLicense":null,'
            '"moduleLicenseUrl":"https://example.org/license"}]}]}'
        )
        self.prepare([("LICENSE", b"MIT")])
        license_entry = self.collect()["components"][0]["licenseMetadata"]["declaredLicenses"][0]
        self.assertIsNone(license_entry["name"])
        self.assertEqual("https://example.org/license", license_entry["url"])

    def test_bundled_jdk_uses_retained_runtime_legal_evidence(self):
        self.prepare([("LICENSE", b"runtime")], "bundled-jdk:27:jrt-fs")
        component = self.collect()["components"][0]
        self.assertEqual("see-runtimeLegal", component["licenseMetadata"]["status"])

    def test_unresolved_artifacts_cannot_be_omitted_from_the_notice_inventory(self):
        for coordinate in ("UNMATCHED", "AMBIGUOUS", ""):
            with self.subTest(coordinate=coordinate):
                self.output = self.root / ("notices-" + (coordinate or "empty"))
                self.prepare([("LICENSE", b"license")], coordinate)
                with self.assertRaisesRegex(ValueError, "no unique resolved component identity"):
                    self.collect()
                self.assertFalse((self.output / "manifest.json").exists())

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

    def test_package_inventory_lists_notice_files_without_directories_or_classes(self):
        with zipfile.ZipFile(self.archive, "w") as jar:
            for name in (
                "META-INF/LICENSE.txt",
                "META-INF/COPYRIGHT",
                "META-INF/DISCLAIMER.md",
                "AUTHORS",
                "META-INF/license/",
                "META-INF/license/AuthMethod.class",
            ):
                jar.writestr(name, "content")
        output = self.root / "inventory-output"
        subprocess.run(
            [
                "bash",
                str(pathlib.Path(__file__).with_name("generate-package-inventory.sh")),
                str(self.package),
                str(output),
            ],
            check=True,
            capture_output=True,
            text=True,
        )
        notice_entries = (output / "embedded-notices.txt").read_text().splitlines()
        self.assertEqual(
            {
                "library.jar: META-INF/LICENSE.txt",
                "library.jar: META-INF/COPYRIGHT",
                "library.jar: META-INF/DISCLAIMER.md",
                "library.jar: AUTHORS",
            },
            set(notice_entries),
        )

    def test_platform_workflow_collects_and_uploads_original_notices(self):
        workflow = (pathlib.Path(__file__).parents[1] / ".github/workflows/license-inventory.yml").read_text()
        package_workflow = (pathlib.Path(__file__).parents[1] / ".github/workflows/package-smoke.yml").read_text()
        self.assertIn("python3 scripts/collect-third-party-notices.py", workflow)
        self.assertIn("--inventory modules/desktop/build/reports/dependency-license/packaged-artifacts.csv", workflow)
        self.assertIn("--license-report build/reports/dependency-license/licenses-all.json", workflow)
        self.assertIn("--project-license LICENSE", workflow)
        self.assertIn("runtimeLegal", (pathlib.Path(__file__).with_name("collect-third-party-notices.py")).read_text())
        self.assertIn("build/reports/dependency-license/third-party-notices/", workflow)
        self.assertIn("generateLicenseReport :desktop:generateDistributionArtifactInventory", package_workflow)
        self.assertIn("--license-report build/reports/dependency-license/licenses-all.json", package_workflow)
        self.assertIn("--project-license LICENSE", package_workflow)
        self.assertIn("if: matrix.collect_notice_evidence", package_workflow)
        for platform in ("linux-deb", "linux-rpm", "linux-appimage", "macos-arm64", "macos-x64", "windows"):
            self.assertRegex(
                package_workflow,
                rf"platform: {platform}\n(?:.*\n){{1,6}}\s+collect_notice_evidence: true",
            )
        self.assertIn('"scripts/test_release_notices.py"', workflow)


if __name__ == "__main__":
    unittest.main()
