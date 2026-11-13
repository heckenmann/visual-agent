"""Regression checks for release smoke dispatch and failure handling without timed assertions."""

import importlib.util
import io
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
    def test_busy_disk_image_detach_is_retried_until_success(self):
        busy = subprocess.CompletedProcess([], 16, stdout="", stderr='hdiutil: Resource busy\n')
        detached = subprocess.CompletedProcess([], 0, stdout="", stderr="")
        with patch.object(SMOKE.subprocess, "run", side_effect=(busy, detached)) as execute:
            with patch.object(SMOKE.time, "sleep") as delay:
                SMOKE.detach_disk_image("/dev/disk2")
        self.assertEqual(2, execute.call_count)
        delay.assert_called_once_with(1)

    def test_disk_image_detach_does_not_retry_unrelated_errors(self):
        failed = subprocess.CompletedProcess([], 1, stdout="", stderr="Invalid device\n")
        with patch.object(SMOKE.subprocess, "run", return_value=failed) as execute:
            with patch.object(SMOKE.time, "sleep") as delay:
                with patch.object(SMOKE.sys, "stderr", new=io.StringIO()):
                    with self.assertRaises(subprocess.CalledProcessError):
                        SMOKE.detach_disk_image("/dev/disk2")
        execute.assert_called_once()
        delay.assert_not_called()

    def test_invalid_disk_image_contents_use_bounded_detach_cleanup(self):
        attached = subprocess.CompletedProcess([], 0, stdout="/dev/disk2\tGUID_partition_scheme\n", stderr="")
        with tempfile.TemporaryDirectory() as temporary:
            with patch.object(SMOKE, "run_checked", return_value=attached):
                with patch.object(SMOKE, "detach_disk_image") as detach:
                    with self.assertRaisesRegex(RuntimeError, "did not contain a \.app bundle"):
                        SMOKE.macos_command("dmg", pathlib.Path("/artifact.dmg"), pathlib.Path(temporary))
        detach.assert_called_once_with("/dev/disk2")

    def test_detach_failure_does_not_mask_invalid_disk_image_error(self):
        attached = subprocess.CompletedProcess([], 0, stdout="/dev/disk2\tGUID_partition_scheme\n", stderr="")
        cleanup_error = subprocess.CalledProcessError(16, ["hdiutil", "detach"], stderr="Resource busy")
        with tempfile.TemporaryDirectory() as temporary:
            with patch.object(SMOKE, "run_checked", return_value=attached):
                with patch.object(SMOKE, "detach_disk_image", side_effect=cleanup_error):
                    with self.assertRaisesRegex(RuntimeError, "did not contain a \.app bundle") as raised:
                        SMOKE.macos_command("dmg", pathlib.Path("/artifact.dmg"), pathlib.Path(temporary))
        self.assertIs(raised.exception.__cause__, cleanup_error)

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
    def test_ci_retains_live_test_identities_without_standard_output(self):
        build = (pathlib.Path(__file__).parents[1] / "build.gradle.kts").read_text()
        self.assertIn('providers.environmentVariable("GITHUB_ACTIONS")', build)
        self.assertIn('providers.gradleProperty("testLifecycleLogging")', build)
        self.assertIn('events("started", "passed", "skipped", "failed")', build)
        self.assertIn("showStandardStreams = false", build)

    def test_release_contracts_run_independently_without_reducing_test_discovery(self):
        workflow = (pathlib.Path(__file__).parents[1] / ".github/workflows/test.yml").read_text()
        jobs = dict(re.findall(r"^  ([a-z-]+):\n(.*?)(?=^  [a-z-]+:|\Z)", workflow, re.M | re.S))
        contracts = jobs["release-contracts"]
        command = "python3 -m unittest discover -s scripts -p 'test_release_*.py'"
        self.assertIn(command, contracts)
        self.assertNotIn(command, jobs["test"])
        self.assertNotRegex(contracts, re.compile(r"^    needs:", re.M))
        self.assertIn("cache-read-only: true", contracts)
        self.assertIn("gradle/actions/wrapper-validation", contracts)
        self.assertIn('ZULU_JDK_VERSION: "27.0.1.0.3+1"', workflow)
        self.assertIn("java-version: ${{ env.ZULU_JDK_VERSION }}", contracts)
        self.assertIn("timeout-minutes: 15", jobs["test"])

    def test_native_smoke_fixtures_include_desktop_menus_and_process_tree_checks(self):
        root = pathlib.Path(__file__).parents[1]
        smoke = (root / ".github/workflows/package-smoke.yml").read_text()
        ubuntu = re.search(
            r"- name: Install Ubuntu desktop runtime dependencies\n(.*?)(?=\n      - name:)", smoke, re.S
        )
        self.assertIsNotNone(ubuntu)
        self.assertIn("gnome-menus", ubuntu.group(1))
        self.assertIn("./scripts/test-windows-smoke-processes.ps1", smoke)
        ci = (root / ".github/workflows/test.yml").read_text()
        self.assertIn("name: Windows smoke process contract", ci)
        self.assertIn("./scripts/test-windows-smoke-processes.ps1", ci)
        windows = (root / "scripts/smoke-release-windows.ps1").read_text()
        self.assertIn("Get-SmokeProcessIds -RootProcessId $app.Id", windows)
        self.assertIn("Close-SmokeWindows -Processes", windows)
        self.assertIn('throw "The application did not exit cleanly', windows)

    def test_gradle_quality_gates_and_package_verification_do_not_use_a_display(self):
        root = pathlib.Path(__file__).parents[1]
        for name in ("test.yml", "package-smoke.yml"):
            workflow = (root / ".github/workflows" / name).read_text()
            commands = re.findall(r"^        run: (.*\./gradlew (?:ktlintCheck|verifyPackages).*)$", workflow, re.M)
            self.assertTrue(commands, f"No verification command found in {name}")
            for command in commands:
                with self.subTest(workflow=name, command=command):
                    self.assertNotIn("xvfb", command.lower())
                    self.assertIn("env -u DISPLAY -u WAYLAND_DISPLAY ./gradlew", command)
        build = (root / "build.gradle.kts").read_text()
        self.assertIn('tasks.withType<Test>().configureEach', build)
        self.assertIn('jvmArgs("-Djava.awt.headless=true")', build)
        self.assertIn('environment.remove("DISPLAY")', build)
        self.assertIn('environment.remove("WAYLAND_DISPLAY")', build)

    def test_only_the_real_desktop_jar_launch_is_wrapped_in_xvfb(self):
        workflow = (pathlib.Path(__file__).parents[1] / ".github/workflows/test.yml").read_text()
        self.assertIn('local launch_command=("$java_cmd" -Djava.awt.headless=true)', workflow)
        self.assertRegex(
            workflow,
            r'if \[\[ "\$require_spring_startup" == "false" \]\]; then\s+'
            r'launch_command=\(xvfb-run -a "\$java_cmd" -Dskiko.renderApi=SOFTWARE\)',
        )

    def metadata_script(self):
        workflow = (pathlib.Path(__file__).parents[1] / ".github/workflows/package-smoke.yml").read_text()
        block = re.search(r"      - name: Prepare release workflow metadata\n.*?        run: \|\n(.*?)(?=\n      - name:)", workflow, re.S)
        self.assertIsNotNone(block)
        return "\n".join(line[10:] for line in block.group(1).splitlines())

    def run_metadata(self, tag):
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            result = subprocess.run(
                ["bash", "-c", "gh() { return 0; }\n" + self.metadata_script()],
                cwd=root,
                env={**os.environ, "RELEASE_TAG": tag, "GITHUB_SHA": "a" * 40},
                capture_output=True,
                text=True,
            )
            manifest = root / "release-metadata/manifest"
            return result, manifest.read_text() if manifest.is_file() else ""

    def test_manual_smoke_without_a_tag_remains_an_unversioned_non_candidate(self):
        result, manifest = self.run_metadata("")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("tag=\n", manifest)
        self.assertIn("candidate=false\n", manifest)
        self.assertIn(f"source_commit={'a' * 40}\n", manifest)

    def test_valid_semver_tag_builds_a_candidate_without_enabling_publication(self):
        result, manifest = self.run_metadata("v1.0.0")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("tag=v1.0.0\n", manifest)
        self.assertIn("candidate=true\n", manifest)
        self.assertNotIn("publish=true", manifest)
        for tag in ("master", "", "v1.0.0/unsafe"):
            if not tag:
                continue
            with self.subTest(tag=tag):
                result, manifest = self.run_metadata(tag)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual("", manifest)

    def test_semver_prerelease_and_build_metadata_keep_the_candidate_tag(self):
        for tag in ("v2.3.4-beta.1+build.7", "2.3.4"):
            with self.subTest(tag=tag):
                result, manifest = self.run_metadata(tag)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertIn(f"tag={tag}\n", manifest)

    def test_release_version_input_applies_to_every_gradle_job_without_shell_interpolation(self):
        workflow = (pathlib.Path(__file__).parents[1] / ".github/workflows/package-smoke.yml").read_text()
        environment = workflow.split("\njobs:", 1)[0]
        self.assertIn(
            "ORG_GRADLE_PROJECT_releaseTag: ${{ inputs.release_tag || '' }}",
            environment,
        )
        self.assertNotIn('expected_tag="v${gradle_version}"', workflow)
        self.assertNotRegex(workflow, r"run:.*\$\{\{ github.event.release.tag_name")
        desktop = (pathlib.Path(__file__).parents[1] / "modules/desktop/build.gradle.kts").read_text()
        self.assertRegex(
            desktop,
            r"packageVersion\s*=\s*project\.version\s*\.toString\(\)\s*\.substringBefore\('-'\)\s*\.substringBefore\('\+'\)",
        )

    def test_all_builds_use_one_jdk_and_retain_the_supported_bytecode_target(self):
        root = pathlib.Path(__file__).parents[1]
        desktop = (root / "modules/desktop/build.gradle.kts").read_text()
        catalog = (root / "gradle/libs.versions.toml").read_text()
        self.assertIn('java-toolchain = "27"', catalog)
        self.assertIn('java-bytecode = "24"', catalog)
        self.assertRegex(
            desktop,
            r"val desktopJavaVersion\s*=\s*libs\s*\.versions\s*\.java\s*\.toolchain\s*\.get\(\)\s*\.toInt\(\)",
        )
        self.assertIn("languageVersion.set(JavaLanguageVersion.of(desktopJavaVersion))", desktop)
        self.assertIn("jvmToolchain(desktopJavaVersion)", desktop)
        self.assertIn("majorVersion == desktopJavaVersion.toString()", desktop)
        self.assertNotIn("installerVersions", desktop)
        for build in [root / "application/build.gradle.kts", *sorted((root / "modules").glob("*/build.gradle.kts"))]:
            text = build.read_text()
            with self.subTest(module=build.parent.name):
                self.assertNotIn("jvmToolchain(24)", text)
                self.assertNotIn("JavaLanguageVersion.of(24)", text)
                if "jvmToolchain(" in text:
                    self.assertRegex(
                        text,
                        r"JvmTarget\s*\.fromTarget\(\s*libs\.versions\.java\.bytecode\s*\.get\(\),?\s*\)",
                    )
                    self.assertIn('-Xjdk-release=${libs.versions.java.bytecode.get()}', text)
        for workflow in (root / ".github/workflows").glob("*.yml"):
            versions = re.findall(r'java-version:\s*"(\d+)"', workflow.read_text())
            self.assertTrue(all(version == "27" for version in versions), workflow.name)
            distributions = re.findall(r'distribution:\s*(\S+)', workflow.read_text())
            self.assertTrue(all(distribution == "zulu" for distribution in distributions), workflow.name)
        build = (root / "build.gradle.kts").read_text()
        self.assertIn("options.release.set(javaBytecodeVersion)", build)
        self.assertIn("dependsOn(verifyJavaToolchains)", build)

    def test_native_package_failures_show_and_preserve_jpackage_logs(self):
        workflow = (pathlib.Path(__file__).parents[1] / ".github/workflows/package-smoke.yml").read_text()
        block = re.search(
            r"      - name: Show native packaging failure logs\n(.*?)(?=\n      - name: Preserve)", workflow, re.S
        )
        self.assertIsNotNone(block)
        self.assertIn("if: failure()", block.group(1))
        script = block.group(1).split("        run: |\n", 1)[1]
        script = "\n".join(line[10:] for line in script.splitlines())
        with tempfile.TemporaryDirectory() as temporary:
            logs = pathlib.Path(temporary) / "modules/desktop/build/compose/logs/createDistributable"
            logs.mkdir(parents=True)
            (logs / "jpackage-err.txt").write_text("A specific packaging failure\n")
            result = subprocess.run(["bash", "-c", script], cwd=temporary, text=True, capture_output=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn("A specific packaging failure", result.stdout)
        self.assertRegex(workflow, r"name: Preserve native packaging failure logs\n\s+if: failure\(\)")
        self.assertIn("name: packaging-logs-${{ matrix.platform }}", workflow)

    def test_candidate_build_and_release_publication_are_separate_workflows(self):
        root = pathlib.Path(__file__).parents[1] / ".github/workflows"
        smoke = (root / "package-smoke.yml").read_text()
        self.assertIn("release_tag:", smoke)
        self.assertIn("type: string", smoke)
        self.assertIn("inputs.release_tag", smoke)
        self.assertNotIn("  release:\n", smoke)
        self.assertIn("format('release-candidate-{0}', inputs.release_tag)", smoke)
        self.assertIn("path: release-metadata/manifest", smoke)
        self.assertEqual(7, smoke.count("ref: ${{ github.sha }}"))
        release = (root / "release.yml").read_text()
        self.assertRegex(release, r"on:\n  workflow_dispatch:\n    inputs:\n      release_tag:")
        self.assertNotRegex(release, r"(?m)^  release:")
        self.assertIn('GITHUB_DEFAULT_BRANCH: ${{ github.event.repository.default_branch }}', release)
        self.assertIn('refs/heads/$GITHUB_DEFAULT_BRANCH', release)
        self.assertIn(".tag_name == $tag and .draft == true", release)
        self.assertIn('compare/$RELEASE_TAG...$GITHUB_DEFAULT_BRANCH', release)
        self.assertIn('"$branch_relation" == "ahead"', release)
        self.assertIn('"$branch_relation" == "identical"', release)
        self.assertIn('gh api --paginate --method GET "repos/$GH_REPO/actions/artifacts"', release)
        self.assertIn('read -r _created_at run_id candidate_sha', release)
        self.assertIn('"$candidate_sha" == "$source_commit"', release)
        self.assertIn(".workflowName == \"Build and smoke release packages\"", release)
        self.assertIn(".conclusion == \"success\"", release)
        self.assertIn(".headSha == $sha", release)
        self.assertIn('gh run download "$candidate_run" --repo "$GH_REPO" --name "$artifact_name" --dir candidate-metadata', release)
        self.assertIn('grep -Fx "tag=$RELEASE_TAG" candidate-metadata/manifest', release)
        self.assertNotIn("candidate-metadata/release-metadata/manifest", release)
        self.assertIn('gh run download "$CANDIDATE_RUN_ID"', release)
        self.assertIn("gh release upload", release)
        self.assertIn("--method PATCH", release)
        self.assertLess(release.index("scripts/verify-release-assets.py"), release.index("gh release upload"))
        self.assertLess(release.index("gh release upload"), release.index("--method PATCH"))
        self.assertNotIn("gh release create", release)
        self.assertIn("scripts/verify-release-assets.py", release)


if __name__ == "__main__":
    unittest.main()
