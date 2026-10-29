"""Exercise the real compiled Gradle legal-material task against isolated fake JDKs."""

import hashlib
import os
import pathlib
import subprocess
import tempfile
import unittest


ROOT = pathlib.Path(__file__).parents[1]


class RuntimeLegalMaterialsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        result = subprocess.run(
            [str(ROOT / "gradlew"), ":desktop:prepareRuntimeLegalMaterials", "--offline", "--quiet", "--no-daemon"],
            capture_output=True, text=True,
        )
        if result.returncode:
            raise RuntimeError(result.stderr)

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = pathlib.Path(self.temporary.name)
        self.jdk = self.root / "fixture-jdk"
        self.jdk.mkdir()
        (self.jdk / "release").write_text(
            'JAVA_VERSION="27.0.1"\nJAVA_RUNTIME_VERSION="27.0.1+9"\n'
            'IMPLEMENTOR="Synthetic Vendor"\nOS_NAME="FixtureOS"\nOS_ARCH="x86_64"\n'
            'PRIVATE_HOST_PATH="/private/not-for-distribution"\n'
        )
        for name in ("LICENSE", "ADDITIONAL_LICENSE_INFO", "ASSEMBLY_EXCEPTION"):
            file = self.jdk / "legal/java.base" / name
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(f"Synthetic {name} notice\n")
        for relative in ("lib/modules", "bin/keytool", "bin/jlink", "bin/jpackage"):
            file = self.jdk / relative
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_bytes(f"Synthetic {relative} input".encode())
        (self.jdk / "readme.txt").write_bytes(b"Synthetic vendor source-offer information\r\n")
        (self.root / "project-LICENSE").write_text("MIT License\nSynthetic project copyright\n")
        (self.root / "settings.gradle.kts").write_text('rootProject.name = "runtime-legal-fixture"\n')
        logic = str(ROOT / "buildSrc/build/libs/buildSrc.jar").replace("\\", "/")
        (self.root / "build.gradle.kts").write_text(
            'import de.heckenmann.visualagent.gradle.PrepareRuntimeLegalMaterials\n'
            'import de.heckenmann.visualagent.gradle.verifyPackagedRuntimeLegalMaterials\n'
            f'buildscript {{ dependencies {{ classpath(files("{logic}")) }} }}\n'
            'tasks.register<PrepareRuntimeLegalMaterials>("prepareRuntimeLegalMaterials") {\n'
            ' jdkHome.set(layout.projectDirectory.dir("fixture-jdk"))\n'
            ' projectLicense.set(layout.projectDirectory.file("project-LICENSE"))\n'
            ' outputDirectory.set(layout.buildDirectory.dir("materials"))\n}\n'
            'tasks.register("verifyRuntimeFixture") { doLast {\n'
            ' val report = verifyPackagedRuntimeLegalMaterials(\n'
            '  file("build/materials/common"), file("packaged-runtime"), file("fixture-jdk"), file("project-LICENSE"))\n'
            ' file("verified.properties").writeText(report.entries.joinToString("\\n") { (key, value) -> "$key=$value" })\n'
            '} }\n'
        )
        self.output = self.root / "build/materials/common/legal/java-runtime"
        packaged = self.root / "packaged-runtime"
        (packaged / "bin").mkdir(parents=True)
        (packaged / "lib").mkdir()
        (packaged / "release").write_text('JAVA_VERSION="27.0.1"\n')
        (packaged / "bin/keytool").write_bytes(b"Synthetic post-packaging keytool")
        (packaged / "lib/modules").write_bytes(b"Synthetic trimmed modules")

    def prepare(self, task="prepareRuntimeLegalMaterials"):
        return subprocess.run(
            [str(ROOT / "gradlew"), "-p", str(self.root), task, "--offline", "--quiet", "--no-daemon"],
            capture_output=True, text=True,
        )

    def assert_prepared(self):
        result = self.prepare()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_real_task_retains_verbatim_notices_without_host_paths_or_executables(self):
        self.assert_prepared()
        self.assertEqual((self.jdk / "readme.txt").read_bytes(), (self.output / "vendor/readme.txt").read_bytes())
        for name in ("LICENSE", "ADDITIONAL_LICENSE_INFO", "ASSEMBLY_EXCEPTION"):
            self.assertEqual((self.jdk / "legal/java.base" / name).read_bytes(), (self.output / "legal/java.base" / name).read_bytes())
        metadata = (self.output / "source-jdk.properties").read_text()
        self.assertIn('JAVA_RUNTIME_VERSION="27.0.1+9"', metadata)
        self.assertNotIn("PRIVATE_HOST_PATH", metadata)
        self.assertNotIn("/private/not-for-distribution", metadata)
        self.assertNotIn(str(self.root), metadata)
        inputs = (self.output / "source-inputs.sha256").read_text()
        self.assertIn(hashlib.sha256((self.jdk / "bin/keytool").read_bytes()).hexdigest() + "  bin/keytool", inputs)
        self.assertFalse((self.output / "lib/modules").exists())
        self.assertIn("not complete corresponding source", (self.output / "README.txt").read_text())
        own = self.root / "build/materials/common/legal/visual-agent/LICENSE"
        self.assertEqual((self.root / "project-LICENSE").read_bytes(), own.read_bytes())

    def test_changed_toolchain_inputs_invalidate_the_materials(self):
        self.assert_prepared()
        before = (self.output / "source-inputs.sha256").read_text()
        (self.jdk / "bin/keytool").write_bytes(b"A different source-JDK keytool")
        self.assert_prepared()
        self.assertNotEqual(before, (self.output / "source-inputs.sha256").read_text())

    def test_source_and_packaged_transformations_have_separate_recorded_hashes(self):
        self.assert_prepared()
        result = self.prepare("verifyRuntimeFixture")
        self.assertEqual(0, result.returncode, result.stderr)
        report = (self.root / "verified.properties").read_text()
        source_hash = hashlib.sha256((self.jdk / "bin/keytool").read_bytes()).hexdigest()
        packaged_hash = hashlib.sha256((self.root / "packaged-runtime/bin/keytool").read_bytes()).hexdigest()
        self.assertNotEqual(source_hash, packaged_hash)
        self.assertIn("source.keytool.sha256=" + source_hash, report)
        self.assertIn("packaged.keytool.sha256=" + packaged_hash, report)
        self.assertNotIn(str(self.root), report)

    def test_notice_tampering_fails_verification(self):
        self.assert_prepared()
        (self.output / "legal/java.base/LICENSE").write_text("Changed notice")
        result = self.prepare("verifyRuntimeFixture")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Retained JDK notice changed", result.stderr)

    def test_unrecorded_source_jdk_change_fails_verification(self):
        self.assert_prepared()
        (self.jdk / "bin/keytool").write_bytes(b"Changed source after preparing materials")
        result = self.prepare("verifyRuntimeFixture")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Source JDK changed", result.stderr)

    def test_provenance_cannot_drop_a_vendor_notice(self):
        self.assert_prepared()
        manifest = self.output / "source-inputs.sha256"
        manifest.write_text("\n".join(line for line in manifest.read_text().splitlines() if not line.endswith("  readme.txt")) + "\n")
        result = self.prepare("verifyRuntimeFixture")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Incomplete or divergent", result.stderr)

    def test_missing_required_legal_file_fails(self):
        (self.jdk / "legal/java.base/LICENSE").unlink()
        result = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Missing or escaping source-JDK file", result.stderr)

    def test_missing_exact_build_identity_fails(self):
        (self.jdk / "release").write_text('JAVA_VERSION="27"\nIMPLEMENTOR="Synthetic Vendor"\n')
        result = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("JAVA_RUNTIME_VERSION", result.stderr)

    def test_windows_tool_names_are_recorded_without_assuming_posix_launchers(self):
        for name in ("keytool", "jlink", "jpackage"):
            (self.jdk / "bin" / name).rename(self.jdk / "bin" / f"{name}.exe")
        self.assert_prepared()
        inputs = (self.output / "source-inputs.sha256").read_text()
        self.assertIn("  bin/keytool.exe\n", inputs)
        self.assertIn("  bin/jpackage.exe\n", inputs)

    def test_metadata_cannot_inject_arbitrary_host_properties(self):
        with (self.jdk / "release").open("a") as metadata:
            metadata.write('IMPLEMENTOR=Vendor\\nPRIVATE_HOST_PATH=not-for-distribution\n')
        result = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Invalid control character", result.stderr)

    def test_removing_a_vendor_notice_does_not_leave_stale_output(self):
        self.assert_prepared()
        (self.jdk / "readme.txt").unlink()
        self.assert_prepared()
        self.assertFalse((self.output / "vendor/readme.txt").exists())
        self.assertNotIn("  readme.txt\n", (self.output / "source-inputs.sha256").read_text())

    @unittest.skipIf(os.name == "nt", "Creating symlinks requires Windows developer privileges")
    def test_external_legal_file_symlink_fails(self):
        external = self.root / "private.txt"
        external.write_text("Must not be copied")
        (self.jdk / "legal/java.base/escape.txt").symlink_to(external)
        result = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("escaping source-JDK file", result.stderr)

    @unittest.skipIf(os.name == "nt", "Creating symlinks requires Windows developer privileges")
    def test_internal_vendor_license_symlink_is_copied_as_content(self):
        directory = self.jdk / "legal/java.compiler"
        directory.mkdir()
        (directory / "LICENSE").symlink_to("../java.base/LICENSE")
        self.assert_prepared()
        copied = self.output / "legal/java.compiler/LICENSE"
        self.assertFalse(copied.is_symlink())
        self.assertEqual((self.jdk / "legal/java.base/LICENSE").read_bytes(), copied.read_bytes())


if __name__ == "__main__":
    unittest.main()
