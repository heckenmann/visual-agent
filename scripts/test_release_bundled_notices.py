"""Exercise actual Gradle legal-material verifiers against synthetic native/JAR images."""

import os
import pathlib
import shutil
import subprocess
import tempfile
import unittest
import warnings
import zipfile


ROOT = pathlib.Path(__file__).parents[1]


class BundledLegalMaterialsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        result = subprocess.run(
            [str(ROOT / "gradlew"), ":desktop:prepareRuntimeLegalMaterials", "--offline", "--quiet", "--no-daemon"],
            capture_output=True, text=True,
        )
        if result.returncode:
            raise RuntimeError(result.stderr)

    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = pathlib.Path(temporary.name)
        self.upstream = self.root / "docs/licensing/third-party"
        self.upstream.mkdir(parents=True)
        (self.root / "LICENSE").write_bytes(b"Synthetic project MIT\n")
        (self.upstream / "sources.json").write_bytes(b'{"redistributionApproved":false}\n')
        (self.upstream / "LICENSE.txt").write_bytes(b"Original upstream terms\r\n")
        native = self.root / "native/legal"
        shutil.copytree(self.upstream, native / "third-party")
        (native / "visual-agent").mkdir()
        shutil.copyfile(self.root / "LICENSE", native / "visual-agent/LICENSE")
        self.entries = {
            "META-INF/visual-agent/legal/visual-agent/LICENSE": (self.root / "LICENSE").read_bytes(),
            "META-INF/visual-agent/legal/third-party/sources.json": (self.upstream / "sources.json").read_bytes(),
            "META-INF/visual-agent/legal/third-party/LICENSE.txt": (self.upstream / "LICENSE.txt").read_bytes(),
        }
        self.write_jar()
        (self.root / "settings.gradle.kts").write_text('rootProject.name = "bundled-legal-fixture"\n')
        logic = str(ROOT / "buildSrc/build/libs/buildSrc.jar").replace("\\", "/")
        (self.root / "build.gradle.kts").write_text(
            'import de.heckenmann.visualagent.gradle.verifyNativeProjectLegalMaterials\n'
            'import de.heckenmann.visualagent.gradle.verifyJarProjectLegalMaterials\n'
            'import java.util.jar.JarFile\n'
            f'buildscript {{ dependencies {{ classpath(files("{logic}")) }} }}\n'
            'tasks.register("verifyNative") { doLast { verifyNativeProjectLegalMaterials(file("native"), projectDir) } }\n'
            'tasks.register("verifyJar") { doLast { JarFile(file("app.jar")).use { verifyJarProjectLegalMaterials(it, projectDir) } } }\n'
        )

    def write_jar(self):
        with zipfile.ZipFile(self.root / "app.jar", "w") as archive:
            for path, content in self.entries.items():
                archive.writestr(path, content)

    def verify(self, task):
        return subprocess.run(
            [str(ROOT / "gradlew"), "-p", str(self.root), task, "--offline", "--quiet", "--no-daemon"],
            capture_output=True, text=True,
        )

    def assert_rejected(self, task, message):
        result = self.verify(task)
        self.assertNotEqual(0, result.returncode)
        self.assertIn(message, result.stderr)

    def test_native_retains_original_bytes_without_modification(self):
        before = (self.root / "native/legal/third-party/LICENSE.txt").read_bytes()
        result = self.verify("verifyNative")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(before, (self.root / "native/legal/third-party/LICENSE.txt").read_bytes())

    def test_jar_retains_original_bytes(self):
        before = (self.root / "app.jar").read_bytes()
        result = self.verify("verifyJar")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(before, (self.root / "app.jar").read_bytes())

    def test_native_missing_notice_fails(self):
        (self.root / "native/legal/third-party/LICENSE.txt").unlink()
        self.assert_rejected("verifyNative", "missing or changed")

    def test_native_changed_notice_fails(self):
        (self.root / "native/legal/third-party/LICENSE.txt").write_bytes(b"Changed")
        self.assert_rejected("verifyNative", "missing or changed")

    def test_jar_missing_project_license_fails(self):
        del self.entries["META-INF/visual-agent/legal/visual-agent/LICENSE"]
        self.write_jar()
        self.assert_rejected("verifyJar", "missing or changed")

    def test_jar_changed_notice_fails(self):
        self.entries["META-INF/visual-agent/legal/third-party/LICENSE.txt"] = b"Changed"
        self.write_jar()
        self.assert_rejected("verifyJar", "missing or changed")

    def test_duplicate_jar_notice_fails(self):
        with zipfile.ZipFile(self.root / "app.jar", "a") as archive, warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            archive.writestr("META-INF/visual-agent/legal/third-party/LICENSE.txt", b"Ambiguous")
        self.assert_rejected("verifyJar", "Duplicate bundled legal entry")

    @unittest.skipIf(os.name == "nt", "Symbolic links require Windows privileges")
    def test_linked_native_notice_fails(self):
        notice = self.root / "native/legal/third-party/LICENSE.txt"
        notice.unlink()
        notice.symlink_to(self.upstream / "LICENSE.txt")
        self.assert_rejected("verifyNative", "linked")

    def test_missing_source_registry_fails(self):
        (self.upstream / "sources.json").unlink()
        self.assert_rejected("verifyNative", "registry is missing")


if __name__ == "__main__":
    unittest.main()
