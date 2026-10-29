"""Publication-integrity tests; synthetic approvals never approve a real release."""

import importlib.util
import json
import pathlib
import subprocess
import sys
import tempfile
import unittest


ROOT = pathlib.Path(__file__).parents[1]
SPEC = importlib.util.spec_from_file_location("release_licenses", ROOT / "scripts/verify-release-licenses.py")
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)


class ReleaseLicenseTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = pathlib.Path(self.temporary.name)
        self.assets = self.root / "assets"
        self.assets.mkdir()
        self.policy = self.root / "approvals.json"
        self.commit = "a" * 40
        evidence = self.root / "review.md"
        evidence.write_text("Synthetic review fixture, not a real legal approval.")
        for name in GATE.PACKAGE_NAMES | GATE.LEGAL_NAMES:
            (self.assets / name).write_bytes(name.encode())
        self.review = {
            "sourceCommit": self.commit,
            "reviewedBy": "Synthetic reviewer",
            "reviewedAt": "2026-10-29",
            "evidenceFile": evidence.name,
            "evidenceSha256": GATE.digest(evidence),
            "sha256": {file.name: GATE.digest(file) for file in self.assets.iterdir()},
        }
        self.write_policy()

    def write_policy(self):
        self.policy.write_text(json.dumps({"schemaVersion": 1, "releases": {"v0.1.0": self.review}}))

    def verify(self, **kwargs):
        GATE.verify(self.policy, self.assets, kwargs.get("tag", "v0.1.0"), kwargs.get("commit", self.commit))

    def test_exact_reviewed_payload_is_accepted(self):
        self.verify()

    def test_repository_does_not_approve_a_release_by_default(self):
        with self.assertRaisesRegex(ValueError, "No completed redistribution review"):
            GATE.verify(ROOT / "docs/licensing/release-approvals.json", self.assets, "v0.1.0", self.commit)

    def test_another_tag_or_source_commit_cannot_reuse_a_review(self):
        for arguments in ({"tag": "v0.2.0"}, {"commit": "b" * 40}, {"commit": "master"}):
            with self.subTest(arguments=arguments), self.assertRaises(ValueError):
                self.verify(**arguments)

    def test_every_package_and_legal_archive_is_mandatory(self):
        for name in GATE.PACKAGE_NAMES | GATE.LEGAL_NAMES:
            file = self.assets / name
            content = file.read_bytes()
            file.unlink()
            with self.subTest(name=name), self.assertRaises(ValueError):
                self.verify()
            file.write_bytes(content)

    def test_changing_a_package_invalidates_its_review(self):
        (self.assets / "visual-agent-windows.msi").write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "changed after review"):
            self.verify()

    def test_extra_files_cannot_be_published(self):
        (self.assets / "unexpected.jar").write_bytes(b"unreviewed")
        with self.assertRaisesRegex(ValueError, "reviewed file set"):
            self.verify()

    def test_missing_or_changed_review_evidence_blocks_publication(self):
        (self.root / "review.md").write_text("Changed")
        with self.assertRaisesRegex(ValueError, "evidence changed"):
            self.verify()
        (self.root / "review.md").unlink()
        with self.assertRaisesRegex(ValueError, "Missing, empty"):
            self.verify()

    def test_missing_review_attribution_is_rejected(self):
        for field in ("reviewedBy", "reviewedAt"):
            previous = self.review.pop(field)
            self.write_policy()
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.verify()
            self.review[field] = previous

    def test_unreviewed_source_archive_or_invalid_hash_is_rejected(self):
        name = "visual-agent-corresponding-sources.zip"
        self.review["sha256"].pop(name)
        self.write_policy()
        with self.assertRaisesRegex(ValueError, "Review must cover"):
            self.verify()
        self.review["sha256"][name] = "not a hash"
        self.write_policy()
        with self.assertRaisesRegex(ValueError, "Invalid reviewed SHA-256"):
            self.verify()

    def test_evidence_cannot_escape_the_trusted_policy_directory(self):
        for name in ("../review.md", "/tmp/review.md", "nested/review.md"):
            self.review["evidenceFile"] = name
            self.write_policy()
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, "Unsafe"):
                self.verify()

    @unittest.skipIf(sys.platform == "win32", "Creating symlinks requires Windows developer privileges")
    def test_symlink_payload_is_not_accepted(self):
        target = self.assets / "visual-agent-windows.msi"
        content = target.read_bytes()
        target.unlink()
        external = self.root / "external.msi"
        external.write_bytes(content)
        target.symlink_to(external)
        with self.assertRaisesRegex(ValueError, "symbolic-link"):
            self.verify()

    def test_cli_fails_for_the_real_unapproved_release(self):
        result = subprocess.run(
            [sys.executable, str(ROOT / "scripts/verify-release-licenses.py"),
             "--policy", str(ROOT / "docs/licensing/release-approvals.json"),
             "--assets", str(self.assets), "--tag", "v0.1.0", "--source-commit", self.commit],
            text=True, capture_output=True,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("publication blocked", result.stderr)

    def test_publication_checks_the_trusted_policy_before_uploading(self):
        workflow = (ROOT / ".github/workflows/release.yml").read_text()
        self.assertIn("ref: ${{ github.event.repository.default_branch }}", workflow)
        gate = workflow.index("- name: Verify exact release license clearance")
        upload = workflow.index("- name: Attach artifacts to existing GitHub release")
        self.assertLess(gate, upload)
        self.assertIn("scripts/verify-release-licenses.py", workflow[gate:upload])
        self.assertNotIn("continue-on-error", workflow)
        self.assertIn("visual-agent-corresponding-sources", workflow)
        self.assertIn("visual-agent-legal", workflow)


if __name__ == "__main__":
    unittest.main()
