"""Exercise release guards and draft uploads with a local GitHub CLI fixture."""

import os
import json
import pathlib
import re
import subprocess
import tempfile
import unittest
import zipfile


ROOT = pathlib.Path(__file__).parents[1]
WORKFLOW = ROOT / ".github/workflows/release.yml"
FAKE_GH = r'''#!/usr/bin/env bash
set -eu
printf '%s\n' "$*" >> "$FIXTURE/log"
case "$*" in
  "api "*"/commits/"*) echo "$TAG_COMMIT" ;;
  "api "*"/compare/"*) echo "$RELATION" ;;
  "api --paginate --slurp "*)
    python3 -c 'import hashlib,json,os,pathlib; print(json.dumps([[{"name":p.name,"state":"uploaded","size":p.stat().st_size,"digest":"sha256:"+hashlib.sha256(p.read_bytes()).hexdigest()} for p in (pathlib.Path(os.environ["FIXTURE"])/"assets").iterdir()]]))' ;;
  "api --paginate "*)
    [[ "$EXISTING" != error ]] || exit 43
    if [[ "$EXISTING" != none || -f "$FIXTURE/created" ]]; then
      jq -n --arg tag "$RELEASE_TAG" '{id:7,tag_name:$tag}'
    fi ;;
  "api "*"/releases/7")
    draft=true; [[ "$EXISTING" != published ]] || draft=false
    jq -n --arg tag "$RELEASE_TAG" --arg title "${RELEASE_TITLE:-$RELEASE_TAG}" --argjson draft "$draft" --argjson preview "$PREVIEW" \
      '{id:7,name:$title,tag_name:$tag,draft:$draft,prerelease:$preview,body:"Maintainer notes",assets:[{name:"extra.txt"}]}' ;;
  "api --method PATCH "*) ;;
  "release create "*) touch "$FIXTURE/created" ;;
  "release view "*) echo 7 ;;
  "release upload "*) ;;
  *) echo "Unexpected gh arguments: $*" >&2; exit 99 ;;
esac
'''


class ReleaseWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.fixture = pathlib.Path(self.temporary.name)
        gh = self.fixture / "gh"
        gh.write_text(FAKE_GH)
        gh.chmod(0o755)
        self.assets = self.fixture / "assets"
        self.assets.mkdir()
        names = re.findall(r'"(visual-agent-[^"]+)"', (ROOT / "scripts/verify-release-assets.py").read_text().split("def verify_archive")[0])
        self.assertEqual(12, len(names))
        for name in names:
            file = self.assets / name
            if name.endswith(".zip"):
                with zipfile.ZipFile(file, "w") as archive:
                    archive.writestr("README.txt", "Recipient materials")
            else:
                file.write_bytes(b"package")
        self.environment = {
            **os.environ, "PATH": str(self.fixture) + os.pathsep + os.environ["PATH"],
            "FIXTURE": str(self.fixture), "GH_REPO": "owner/repo", "RELEASE_TAG": "v1.2.3",
            "SOURCE_COMMIT": "a" * 40, "TAG_COMMIT": "a" * 40, "RELATION": "ahead",
            "EXISTING": "none", "PREVIEW": "false",
        }

    def run_script(self, name, **overrides):
        if "GITHUB_EVENT_PATH" not in overrides:
            event = self.fixture / "push-event.json"
            event.write_text(json.dumps({
                "ref": "refs/tags/" + overrides.get("RELEASE_TAG", self.environment["RELEASE_TAG"]),
                "created": True, "forced": False, "deleted": False,
            }))
            overrides["GITHUB_EVENT_PATH"] = str(event)
        args = ["bash", str(ROOT / "scripts" / name)]
        if name == "attach-release-draft.sh":
            args.append(str(self.assets))
        return subprocess.run(args, cwd=ROOT, env={**self.environment, **overrides}, capture_output=True, text=True)

    def log(self):
        path = self.fixture / "log"
        return path.read_text() if path.exists() else ""

    def test_semver_and_ancestry_guards(self):
        for tag in ("v1.2.3", "v1.2.3-beta.1+build.7"):
            self.assertEqual(0, self.run_script("verify-release-ref.sh", RELEASE_TAG=tag).returncode)
        for tag in ("1.2.3", "v01.2.3", "v1.2.3-01", "v1.2.3/unsafe", "v1.2.3+"):
            self.assertNotEqual(0, self.run_script("verify-release-ref.sh", RELEASE_TAG=tag).returncode)
        for relation in ("behind", "diverged"):
            self.assertNotEqual(0, self.run_script("verify-release-ref.sh", RELATION=relation).returncode)
        self.assertEqual(0, self.run_script("verify-release-ref.sh", RELATION="identical").returncode)

    def test_moved_tag_prevents_creation_and_upload(self):
        result = self.run_script("attach-release-draft.sh", TAG_COMMIT="b" * 40)
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("release create", self.log())
        self.assertNotIn("release upload", self.log())

    def test_original_push_must_create_the_exact_tag_without_force_or_deletion(self):
        event = self.fixture / "invalid-event.json"
        valid = {"ref": "refs/tags/v1.2.3", "created": True, "forced": False, "deleted": False}
        invalid = [
            {**valid, "created": False, "forced": True},
            {**valid, "created": False},
            {**valid, "forced": True},
            {**valid, "deleted": True},
            {**valid, "ref": "refs/heads/v1.2.3"},
            {"ref": valid["ref"]},
        ]
        for payload in invalid:
            event.write_text(json.dumps(payload))
            with self.subTest(payload=payload):
                result = self.run_script(
                    "attach-release-draft.sh", GITHUB_EVENT_PATH=str(event),
                    SOURCE_COMMIT="b" * 40, TAG_COMMIT="b" * 40, EXISTING="draft",
                )
                self.assertNotEqual(0, result.returncode)
                self.assertIn("initial tag creation", result.stderr)
        self.assertEqual("", self.log())

    def test_missing_or_invalid_original_event_fails_closed(self):
        event = self.fixture / "missing-event.json"
        result = self.run_script("verify-release-ref.sh", GITHUB_EVENT_PATH=str(event))
        self.assertNotEqual(0, result.returncode)
        event.write_text("not JSON")
        result = self.run_script("verify-release-ref.sh", GITHUB_EVENT_PATH=str(event))
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("", self.log())

    def test_missing_file_stops_before_any_github_operation(self):
        (self.assets / "visual-agent-windows.msi").unlink()
        self.assertNotEqual(0, self.run_script("attach-release-draft.sh").returncode)
        self.assertEqual("", self.log())

    def test_new_draft_contains_all_twelve_files_and_retry_reuses_it(self):
        for attempt in ("1", "2"):
            result = self.run_script("attach-release-draft.sh", GITHUB_RUN_ATTEMPT=attempt)
            self.assertEqual(0, result.returncode, result.stderr)
        log = self.log()
        self.assertEqual(1, log.count("release create"))
        self.assertEqual(2, log.count("release upload"))
        for file in self.assets.iterdir():
            self.assertIn(str(file), log)
        self.assertIn("--draft --prerelease=false", log)
        self.assertIn("--title v1.2.3 --generate-notes", log)
        self.assertNotIn("Visual Agent v1.2.3", log)

    def test_existing_draft_preserves_notes_and_unrelated_assets(self):
        result = self.run_script("attach-release-draft.sh", EXISTING="draft")
        self.assertEqual(0, result.returncode, result.stderr)
        log = self.log()
        self.assertNotIn("release create", log)
        self.assertNotIn("release edit", log)
        self.assertNotIn("PATCH", log)
        self.assertNotIn("extra.txt", log)

    def test_existing_draft_title_is_normalized_without_changing_notes(self):
        result = self.run_script("attach-release-draft.sh", EXISTING="draft", RELEASE_TITLE="Visual Agent v1.2.3")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("api --method PATCH repos/owner/repo/releases/7 -f name=v1.2.3 --silent", self.log())
        self.assertNotIn("body=", self.log())
        self.assertNotIn("release create", self.log())

    def test_published_release_and_api_failure_prevent_writes(self):
        for existing in ("published", "error"):
            result = self.run_script("attach-release-draft.sh", EXISTING=existing)
            self.assertNotEqual(0, result.returncode)
        self.assertNotIn("release upload", self.log())
        self.assertNotIn("release create", self.log())

    def test_prerelease_and_build_metadata_classification(self):
        result = self.run_script("attach-release-draft.sh", RELEASE_TAG="v1.2.3-beta.1+build.7", PREVIEW="true")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("--prerelease=true", self.log())
        result = self.run_script("attach-release-draft.sh", RELEASE_TAG="v1.2.3+build.7")
        self.assertEqual(0, result.returncode, result.stderr)
        result = self.run_script("attach-release-draft.sh", EXISTING="draft", PREVIEW="true")
        self.assertNotEqual(0, result.returncode)

    def test_workflow_gates_upload_on_every_job_and_uses_only_this_run(self):
        workflow = WORKFLOW.read_text()
        jobs = dict(re.findall(r"^  ([a-z-]+):\n(.*?)(?=^  [a-z-]+:|\Z)", workflow.split("\njobs:\n")[1], re.M | re.S))
        draft = jobs["draft"]
        needs = re.search(r"needs: \[(.*?)\]", draft).group(1).split(", ")
        self.assertEqual(set(jobs) - {"draft"}, set(needs))
        self.assertNotIn("always()", draft)
        self.assertIn("if: github.event_name == 'push'", draft)
        self.assertIn('gh run download "$GITHUB_RUN_ID"', draft)
        self.assertIn("ref: ${{ github.sha }}", draft)
        self.assertIn("push:\n    tags:\n      - 'v*'", workflow)
        self.assertIn("  workflow_dispatch:\n", workflow)
        self.assertIn("cancel-in-progress: false", workflow)
        self.assertIn("group: desktop-release-${{ github.ref }}", workflow)
        for removed in ("workflow_run", "publish_release", "release-candidate", "inputs.release_tag"):
            self.assertNotIn(removed, workflow)
        self.assertNotIn("contents: write", workflow.split("\njobs:")[0])
        self.assertEqual(1, workflow.count("contents: write"))
        self.assertEqual(workflow.count("uses: actions/upload-artifact@"), workflow.count("overwrite: true"))
        self.assertEqual(8, workflow.count("ref: ${{ github.sha }}"))
        self.assertFalse((ROOT / ".github/workflows/package-smoke.yml").exists())


if __name__ == "__main__":
    unittest.main()
