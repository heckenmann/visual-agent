"""Tests for the small recipient-asset check used by the publisher."""

import importlib.util
import hashlib
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

    def uploaded(self):
        records = []
        for file in self.assets.iterdir():
            records.append({"name": file.name, "state": "uploaded", "size": file.stat().st_size,
                            "digest": "sha256:" + hashlib.sha256(file.read_bytes()).hexdigest()})
        return [records[:6], records[6:]]

    def test_uploaded_files_match_across_pages_and_allow_unrelated_assets(self):
        pages = self.uploaded()
        pages.append([{"name": "maintainer-notes.txt"}])
        CHECK.verify_uploaded(self.assets, pages)

    def test_missing_duplicate_or_changed_uploaded_files_are_rejected(self):
        for field, value in (("state", "starter"), ("size", 0), ("digest", None)):
            pages = self.uploaded()
            pages[0][0][field] = value
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, "does not match"):
                CHECK.verify_uploaded(self.assets, pages)
        for duplicate in (False, True):
            pages = self.uploaded()
            removed = pages[0].pop()
            if duplicate:
                pages[0].extend([removed, removed])
            with self.subTest(duplicate=duplicate), self.assertRaisesRegex(ValueError, "Missing or duplicate"):
                CHECK.verify_uploaded(self.assets, pages)

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

    def test_draft_attachment_checks_assets_and_never_publishes(self):
        script = (ROOT / "scripts/attach-release-draft.sh").read_text()
        self.assertLess(script.index("scripts/verify-release-assets.py"), script.index("gh release upload"))
        self.assertIn("--draft", script)
        self.assertNotIn("draft=false", script)
        self.assertNotIn("release-approvals.json", script)


if __name__ == "__main__":
    unittest.main()
