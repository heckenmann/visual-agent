"""Verify exact supplementary legal evidence without network access or approval."""

import copy
import hashlib
import importlib.util
import json
import pathlib
import tempfile
import unittest
import zipfile

from third_party_supplements import load_supplements


SPEC = importlib.util.spec_from_file_location("notices", pathlib.Path(__file__).with_name("collect-third-party-notices.py"))
NOTICES = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(NOTICES)


class SupplementalNoticesTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = pathlib.Path(temporary.name)
        self.registry = self.root / "sources.json"
        self.material = self.root / "LICENSE.txt"
        self.content = b"Original license\r\n"
        self.material.write_bytes(self.content)
        self.data = {
            "schemaVersion": 1,
            "redistributionApproved": False,
            "materials": [{"path": "LICENSE.txt", "sha256": hashlib.sha256(self.content).hexdigest(), "url": "https://example.org/LICENSE"}],
            "components": [{
                "sourceArtifacts": {"example:lib:1": "a" * 64},
                "upstreamCommit": "b" * 40,
                "reviewStatus": "unreviewed",
                "remaining": ["Source review"],
                "proposedLicense": "MIT",
                "materialPaths": ["LICENSE.txt"],
            }],
        }

    def load(self):
        self.registry.write_text(json.dumps(self.data))
        return load_supplements(self.registry)

    def test_original_material_is_retained_for_exact_version_and_hash(self):
        result = self.load()
        self.assertEqual({"example:lib:1"}, result.keys())
        self.assertEqual(self.content, result["example:lib:1"]["materials"][0]["content"])
        self.assertEqual("a" * 64, result["example:lib:1"]["expectedSourceSha256"])

    def test_changed_legal_text_is_rejected(self):
        self.material.write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "Changed supplemental"):
            self.load()

    def test_missing_material_is_rejected(self):
        self.material.unlink()
        with self.assertRaisesRegex(ValueError, "Missing or escaping"):
            self.load()

    def test_escaping_paths_are_rejected(self):
        for path in ("../LICENSE.txt", "/LICENSE.txt", "C:/LICENSE.txt", "dir\\LICENSE.txt"):
            with self.subTest(path=path):
                self.data["materials"][0]["path"] = path
                with self.assertRaisesRegex(ValueError, "Unsafe"):
                    self.load()

    def test_duplicate_coordinate_is_rejected(self):
        self.data["components"].append(copy.deepcopy(self.data["components"][0]))
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            self.load()

    def test_missing_source_identity_is_rejected(self):
        self.data["components"][0]["upstreamCommit"] = "main"
        with self.assertRaisesRegex(ValueError, "exact upstream"):
            self.load()

    def test_evidence_cannot_self_approve_publication(self):
        self.data["redistributionApproved"] = True
        with self.assertRaisesRegex(ValueError, "cannot grant"):
            self.load()

    def test_review_status_and_remaining_work_cannot_be_omitted(self):
        self.data["components"][0]["remaining"] = []
        with self.assertRaisesRegex(ValueError, "pending review"):
            self.load()

    def test_source_urls_cannot_embed_credentials(self):
        self.data["materials"][0]["url"] = "https://user:secret@example.org/LICENSE"
        with self.assertRaisesRegex(ValueError, "public HTTPS"):
            self.load()

    def test_repository_snapshots_match_the_recorded_hashes(self):
        registry = pathlib.Path(__file__).parents[1] / "docs/licensing/third-party/sources.json"
        components = load_supplements(registry)
        self.assertEqual(5, len(components))
        self.assertEqual("MPL-2.0", components["com.h2database:h2:2.5.252"]["proposedLicense"])
        self.assertEqual("EPL-2.0", components["ch.qos.logback:logback-core:1.6.6"]["proposedLicense"])
        self.assertEqual("Apache-2.0", components["net.java.dev.jna:jna:5.19.1"]["proposedLicense"])

    def test_existing_report_plugin_exports_all_license_alternatives(self):
        root = pathlib.Path(__file__).parents[1]
        build = (root / "build.gradle.kts").read_text()
        workflow = (root / ".github/workflows/license-inventory.yml").read_text()
        self.assertIn('ExtendedJsonReportRenderer("licenses-all.json", false)', build)
        self.assertIn("build/reports/dependency-license/licenses-all.json", workflow)

    def prepare_package(self):
        package = self.root / "package"
        package.mkdir()
        archive = package / "lib.jar"
        with zipfile.ZipFile(archive, "w") as jar:
            jar.writestr("library.class", b"synthetic bytecode")
        digest = NOTICES.sha256(archive)
        inventory = self.root / "inventory.csv"
        inventory.write_text("packagePath,coordinate,packageSha256,sourceSha256\nlib.jar,example:lib:1," + digest + "," + "d" * 64 + "\n")
        self.data["components"][0]["sourceArtifacts"]["example:lib:1"] = "d" * 64
        self.load()
        return package, inventory

    def test_collector_retains_supplement_without_hiding_missing_embedded_notice(self):
        package, inventory = self.prepare_package()
        output = self.root / "collected"
        report = NOTICES.collect(package, inventory, output, self.registry)
        component = report["components"][0]
        self.assertEqual("no-detected-notice", component["noticeStatus"])
        self.assertEqual("unreviewed", component["reviewStatus"])
        self.assertFalse(report["redistributionApproved"])
        self.assertNotEqual(component["packageSha256"], component["supplement"]["sourceSha256"])
        material = component["supplement"]["materials"][0]
        self.assertEqual(self.content, (output / material["path"]).read_bytes())
        self.assertEqual(["Source review"], component["supplement"]["remaining"])

    def test_collector_rejects_supplement_for_different_artifact_bytes(self):
        package, inventory = self.prepare_package()
        self.data["components"][0]["sourceArtifacts"]["example:lib:1"] = "c" * 64
        self.load()
        output = self.root / "collected"
        with self.assertRaisesRegex(ValueError, "exact source artifact"):
            NOTICES.collect(package, inventory, output, self.registry)
        self.assertFalse((output / "manifest.json").exists())

    def test_source_identity_is_required_when_using_supplements(self):
        package, inventory = self.prepare_package()
        lines = inventory.read_text().splitlines()
        inventory.write_text("\n".join(line.rsplit(",", 1)[0] for line in lines) + "\n")
        with self.assertRaisesRegex(ValueError, "exact source artifact"):
            NOTICES.collect(package, inventory, self.root / "collected", self.registry)


if __name__ == "__main__":
    unittest.main()
