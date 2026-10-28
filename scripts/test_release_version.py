"""Exercise the actual Gradle settings override without application dependencies or timing assertions."""

import os
import pathlib
import subprocess
import tempfile
import unittest


ROOT = pathlib.Path(__file__).parents[1]


class ReleaseVersionTest(unittest.TestCase):
    def resolve(self, tag):
        with tempfile.TemporaryDirectory() as temporary:
            project = pathlib.Path(temporary)
            (project / "gradle").mkdir()
            (project / "child").mkdir()
            (project / "gradle/release-version.settings.gradle.kts").write_text(
                (ROOT / "gradle/release-version.settings.gradle.kts").read_text()
            )
            (project / "gradle/libs.versions.toml").write_text('[versions]\nvisual-agent = "1.0.0"\n')
            (project / "settings.gradle.kts").write_text(
                'rootProject.name = "fixture"\n'
                'apply(from = "gradle/release-version.settings.gradle.kts")\ninclude(":child")\n'
            )
            (project / "child/build.gradle.kts").write_text('version = libs.versions.visual.agent.get()\n')
            (project / "build.gradle.kts").write_text(
                'version = libs.versions.visual.agent.get()\n'
                'tasks.register("printVersions") { doLast { allprojects.forEach { println("${it.path}=${it.version}") } } }\n'
            )
            environment = {key: value for key, value in os.environ.items() if not key.startswith("ORG_GRADLE_PROJECT_")}
            if tag is not None:
                environment["ORG_GRADLE_PROJECT_releaseTag"] = tag
            return subprocess.run(
                [str(ROOT / "gradlew"), "-p", str(project), "--offline", "--quiet", "printVersions", "--no-daemon"],
                env=environment,
                capture_output=True,
                text=True,
                check=False,
            )

    def assert_version(self, tag, expected):
        result = self.resolve(tag)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual([f":={expected}", f":child={expected}"], result.stdout.strip().splitlines())

    def test_local_build_uses_the_checked_in_default(self):
        self.assert_version(None, "1.0.0")

    def test_manual_workflow_empty_tag_keeps_the_default(self):
        self.assert_version("", "1.0.0")

    def test_prefixed_tag_overrides_the_catalog_for_all_projects(self):
        self.assert_version("v2.3.4", "2.3.4")

    def test_unprefixed_prerelease_preserves_build_metadata(self):
        self.assert_version("2.3.4-beta.1+build.7", "2.3.4-beta.1+build.7")

    def test_invalid_semver_is_rejected(self):
        result = self.resolve("v1.02.3")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Release tag must be a semantic version", result.stderr)

    def test_invalid_prerelease_is_rejected(self):
        result = self.resolve("v1.2.3-beta.01")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Release tag must be a semantic version", result.stderr)

    def test_installer_overflow_is_rejected_before_packaging(self):
        result = self.resolve("v256.2.3")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Release version exceeds native installer limits", result.stderr)


if __name__ == "__main__":
    unittest.main()
