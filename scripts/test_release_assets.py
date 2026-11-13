"""Tests for the small recipient-asset check used by the publisher."""

import importlib.util
import pathlib
import sys
import tempfile
import unittest
import zipfile


ROOT = pathlib.Path(__file__).parents[1]
SPEC = importlib.util.spec_from_file_location("release_assets", ROOT / "scripts/verify-release-assets.py")
CHECK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECK)


class ReleaseAssetTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.assets = pathlib.Path(self.temporary.name) / "assets"
        self.assets.mkdir()
        for name in CHECK.REQUIRED_ASSETS:
            file = self.assets / name
            if name.endswith(".zip"):
                with zipfile.ZipFile(file, "w") as archive:
                    archive.writestr("README.txt", "Recipient materials")
            else:
                file.write_bytes(name.encode())

    def test_complete_candidate_is_accepted(self):
        CHECK.verify(self.assets)

    def test_missing_or_extra_assets_are_rejected(self):
        (self.assets / "visual-agent-windows.msi").unlink()
        with self.assertRaisesRegex(ValueError, "exactly the ten packages"):
            CHECK.verify(self.assets)
        (self.assets / "unexpected.txt").write_text("extra")
        with self.assertRaisesRegex(ValueError, "exactly the ten packages"):
            CHECK.verify(self.assets)

    def test_empty_assets_are_rejected(self):
        (self.assets / "visual-agent-windows.msi").write_bytes(b"")
        with self.assertRaisesRegex(ValueError, "empty"):
            CHECK.verify(self.assets)

    def test_both_archives_must_be_valid_and_not_review_only(self):
        for name in ("visual-agent-legal.zip", "visual-agent-corresponding-sources.zip"):
            file = self.assets / name
            other = self.assets / (
                "visual-agent-corresponding-sources.zip"
                if name == "visual-agent-legal.zip"
                else "visual-agent-legal.zip"
            )
            with zipfile.ZipFile(other, "w") as archive:
                archive.writestr("README.txt", "Recipient materials")
            file.write_bytes(b"not a zip")
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, "Invalid release archive"):
                CHECK.verify(self.assets)
            with zipfile.ZipFile(file, "w") as archive:
                archive.writestr("nested/REVIEW-ONLY.txt", "Internal")
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, "Review-only archive"):
                CHECK.verify(self.assets)

    @unittest.skipIf(sys.platform == "win32", "Creating symlinks requires Windows developer privileges")
    def test_symlink_assets_are_rejected(self):
        target = self.assets / "visual-agent-windows.msi"
        content = target.read_bytes()
        target.unlink()
        external = self.assets.parent / "outside.msi"
        external.write_bytes(content)
        target.symlink_to(external)
        with self.assertRaisesRegex(ValueError, "symbolic-link"):
            CHECK.verify(self.assets)

    def test_publisher_checks_assets_before_upload_and_publishes_last(self):
        workflow = (ROOT / ".github/workflows/release.yml").read_text()
        check = workflow.index("scripts/verify-release-assets.py")
        upload = workflow.index("gh release upload")
        publish = workflow.index("--method PATCH")
        self.assertLess(check, upload)
        self.assertLess(upload, publish)
        self.assertNotIn("release-approvals.json", workflow)
        self.assertIn("visual-agent-corresponding-sources", workflow)


if __name__ == "__main__":
    unittest.main()
