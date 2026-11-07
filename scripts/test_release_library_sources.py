"""Verify real Gradle source-variant collection without releasing artifacts."""

import hashlib
import pathlib
import re
import subprocess
import tempfile
import unittest
import zipfile


ROOT = pathlib.Path(__file__).parents[1]
OUTPUT = ROOT / "modules/desktop/build/reports/dependency-license/library-source-materials"


class LibrarySourceMaterialsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        result = subprocess.run(
            [str(ROOT / "gradlew"), ":desktop:prepareLibrarySourceMaterials", "--quiet", "--no-daemon"],
            capture_output=True, text=True,
        )
        if result.returncode:
            raise RuntimeError(result.stdout + result.stderr)

    def test_source_archives_match_verified_exact_version_inputs(self):
        document = (ROOT / "docs/licensing/source-delivery.md").read_text()
        expected = dict(re.findall(r"\[([^\]]+-sources\.jar)\].*?`([0-9a-f]{64})`", document))
        self.assertEqual(3, len(expected))
        actual = {path.name: hashlib.sha256(path.read_bytes()).hexdigest()
                  for path in (OUTPUT / "archives").iterdir()}
        self.assertEqual(expected, actual)

    def test_archives_contain_sources_not_binary_runtime_jars(self):
        for path in (OUTPUT / "archives").iterdir():
            with self.subTest(archive=path.name), zipfile.ZipFile(path) as archive:
                names = archive.namelist()
                self.assertTrue(any(name.endswith(".java") for name in names))
                self.assertFalse(any(name.endswith(".class") for name in names))

    def test_platform_inventory_collects_and_uploads_partial_source_evidence(self):
        workflow = (ROOT / ".github/workflows/license-inventory.yml").read_text()
        self.assertIn(":desktop:prepareLibrarySourceMaterials", workflow)
        self.assertIn("modules/desktop/build/reports/dependency-license/library-source-materials/", workflow)
        for platform in ("linux-x64", "macos-arm64", "macos-x64", "windows-x64"):
            self.assertIn(f"platform: {platform}", workflow)
        self.assertNotIn("continue-on-error:", workflow)

    def test_original_legal_inputs_are_retained_and_scope_is_explicit(self):
        source = ROOT / "docs/licensing/third-party"
        expected = {path.relative_to(source): path.read_bytes()
                    for path in source.rglob("*") if path.is_file()}
        actual = {path.relative_to(OUTPUT / "legal"): path.read_bytes()
                  for path in (OUTPUT / "legal").rglob("*") if path.is_file()}
        self.assertEqual(expected, actual)
        document = (OUTPUT / "source-delivery.md").read_text()
        self.assertIn("not yet implemented", document)
        self.assertEqual((ROOT / "docs/licensing/source-delivery.md").read_bytes(),
                         (OUTPUT / "source-delivery.md").read_bytes())

    def test_missing_required_runtime_component_fails_instead_of_silent_omission(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            init = root / "missing-source.init.gradle.kts"
            destination = (root / "output").as_posix()
            init.write_text(
                'allprojects { configurations.configureEach { '
                'exclude(group = "com.h2database", module = "h2") } }\n'
                'gradle.projectsEvaluated { rootProject.findProject(":desktop")'
                f'?.layout?.buildDirectory?.set(java.io.File("{destination}"))'
                ' }\n'
            )
            result = subprocess.run(
                [str(ROOT / "gradlew"), "-I", str(init), ":desktop:prepareLibrarySourceMaterials",
                 "--quiet", "--no-daemon", "--offline"], capture_output=True, text=True,
            )
            self.assertNotEqual(0, result.returncode)
            self.assertIn("Missing required H2/Logback source artifacts", result.stdout + result.stderr)
            output = root / "output/reports/dependency-license/library-source-materials"
            self.assertFalse(any(path.is_file() for path in output.rglob("*")))


if __name__ == "__main__":
    unittest.main()
