"""Regression checks for release smoke dispatch and failure handling without timed assertions."""

import importlib.util
import os
import pathlib
import re
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch

SPEC = importlib.util.spec_from_file_location("release_smoke", pathlib.Path(__file__).with_name("smoke-release-posix.py"))
SMOKE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SMOKE)


class ReleaseSmokeTest(unittest.TestCase):
    def test_linux_jar_launches_java_without_installing_a_native_package(self):
        artifact = pathlib.Path("/packages/release.jar")
        with patch.object(SMOKE, "run_checked") as execute:
            self.assertEqual(["java", "-jar", str(artifact)], SMOKE.install_linux_artifact("jar", artifact))
        execute.assert_not_called()

    def test_native_launcher_is_resolved_from_installed_package_metadata(self):
        launcher = "/opt/visual-agent/bin/Visual Agent"
        for kind, manager in (("deb", "dpkg-query"), ("rpm", "rpm")):
            with self.subTest(kind=kind), patch.object(SMOKE, "run_checked") as execute:
                execute.return_value = subprocess.CompletedProcess([], 0, stdout=f"/opt/visual-agent/lib/app.jar\n{launcher}\n")
                self.assertEqual([launcher], SMOKE.install_linux_artifact(kind, pathlib.Path(f"/packages/release.{kind}")))
                self.assertEqual(manager, execute.call_args.args[0][0])

    def test_missing_or_ambiguous_installed_launchers_fail(self):
        for inventory in ("/opt/visual-agent/lib/app.jar\n", "/a/bin/Visual Agent\n/b/bin/Visual Agent\n"):
            with self.subTest(inventory=inventory), patch.object(SMOKE, "run_checked") as execute:
                execute.return_value = subprocess.CompletedProcess([], 0, stdout=inventory)
                with self.assertRaisesRegex(RuntimeError, "Expected one installed launcher"):
                    SMOKE.install_linux_artifact("deb", pathlib.Path("/packages/release.deb"))

    def test_readiness_requires_a_marker_and_accepts_it_without_sleeping(self):
        with tempfile.TemporaryDirectory() as temporary:
            log = pathlib.Path(temporary) / "application.log"
            log.write_text(SMOKE.READY_MARKER)
            with patch.object(SMOKE.time, "sleep") as delay:
                SMOKE.wait_for_ready(Mock(), log)
            delay.assert_not_called()

    def test_process_exit_before_readiness_fails_without_a_timed_wait(self):
        with tempfile.TemporaryDirectory() as temporary:
            log = pathlib.Path(temporary) / "application.log"
            log.write_text("Starting application")
            process = Mock(returncode=1)
            process.poll.return_value = 1
            with patch.object(SMOKE.time, "sleep") as delay:
                with self.assertRaisesRegex(RuntimeError, "exited before readiness"):
                    SMOKE.wait_for_ready(process, log)
            delay.assert_not_called()

    def test_package_uninstall_failure_is_not_silently_ignored(self):
        with patch.object(SMOKE, "run_checked", side_effect=subprocess.CalledProcessError(1, "remove")):
            with self.assertRaises(subprocess.CalledProcessError):
                SMOKE.uninstall_linux_artifact("deb")

    def test_virtual_display_uses_the_x_server_readiness_signal(self):
        process = Mock()

        def start_server(command, **kwargs):
            os.write(int(command[2]), b"77\n")
            return process

        with tempfile.TemporaryDirectory() as temporary, patch.object(SMOKE.subprocess, "Popen", side_effect=start_server):
            environment = {}
            self.assertIs(process, SMOKE.start_virtual_display(environment, pathlib.Path(temporary)))
            self.assertEqual(":77", environment["DISPLAY"])

    def test_virtual_display_exit_before_readiness_is_cleaned_up(self):
        process = Mock()
        with tempfile.TemporaryDirectory() as temporary:
            with patch.object(SMOKE.subprocess, "Popen", return_value=process), patch.object(SMOKE, "stop_process") as stop:
                with self.assertRaisesRegex(RuntimeError, "without publishing a display"):
                    SMOKE.start_virtual_display({}, pathlib.Path(temporary))
                stop.assert_called_once_with(process)


class ReleaseWorkflowTest(unittest.TestCase):
    def metadata_script(self):
        workflow = (pathlib.Path(__file__).parents[1] / ".github/workflows/release.yml").read_text()
        block = re.search(r"      - name: Read package workflow metadata\n.*?        run: \|\n(.*?)(?=\n      - name:)", workflow, re.S)
        self.assertIsNotNone(block)
        return "\n".join(line[10:] for line in block.group(1).splitlines())

    def run_metadata(self, tag, publish):
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            (root / "release-metadata").mkdir()
            (root / "release-metadata/manifest").write_text(f"tag={tag}\npublish={publish}\n")
            output = root / "github-env"
            output.touch()
            result = subprocess.run(
                ["bash", "-c", "gh() { return 0; }\n" + self.metadata_script()],
                cwd=root,
                env={**os.environ, "GH_REPO": "owner/repo", "RUN_ID": "1", "GITHUB_ENV": str(output)},
                capture_output=True,
                text=True,
            )
            return result, output.read_text()

    def test_manual_smoke_without_a_tag_never_enables_upload(self):
        result, environment = self.run_metadata("", "false")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("", environment)

    def test_release_tag_enables_upload_only_when_valid(self):
        result, environment = self.run_metadata("v1.0.0", "true")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("PUBLISH_RELEASE=true", environment)
        for tag, publish in (("master", "true"), ("", "true"), ("v1.0.0", "unexpected")):
            with self.subTest(tag=tag, publish=publish):
                result, environment = self.run_metadata(tag, publish)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual("", environment)

    def test_workflow_dispatch_is_tag_free_and_release_creation_is_disabled(self):
        root = pathlib.Path(__file__).parents[1] / ".github/workflows"
        smoke = (root / "package-smoke.yml").read_text()
        self.assertIn("types: [published]", smoke)
        self.assertNotIn("inputs.", smoke)
        self.assertIn("if: github.event_name == 'release'", smoke)
        self.assertIn("PUBLISH_RELEASE: ${{ github.event_name == 'release' }}", smoke)
        self.assertEqual(6, smoke.count("ref: ${{ github.sha }}"))
        release = (root / "release.yml").read_text()
        self.assertNotIn("gh release create", release)
        self.assertIn("github.event.workflow_run.event == 'release'", release)


if __name__ == "__main__":
    unittest.main()
