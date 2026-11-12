"""Regression tests for AppImage runtime provenance validation."""

import tempfile
import unittest
from pathlib import Path

from verify_appimage_runtime_embedded import parse_digest_section_range, verify_runtime_prefix


class AppImageRuntimeVerificationTest(unittest.TestCase):
    def test_readelf_section_parser_returns_digest_offset_and_size(self):
        output = "  [ 4] .digest_md5 PROGBITS 00000000000020a0 0020a0 000010 00 WA 0 0 1\n"
        self.assertEqual((0x20A0, 0x10), parse_digest_section_range(output))

    def test_readelf_section_parser_rejects_missing_or_duplicate_sections(self):
        section = "  [ 4] .digest_md5 PROGBITS 00000000000020a0 0020a0 000010 00 WA 0 0 1\n"
        with self.assertRaisesRegex(ValueError, "found 0"):
            parse_digest_section_range("no digest section")
        with self.assertRaisesRegex(ValueError, "found 2"):
            parse_digest_section_range(section + section)

    def test_readelf_section_parser_rejects_unexpected_section_size(self):
        output = "  [ 4] .digest_md5 PROGBITS 00000000000020a0 0020a0 000008 00 WA 0 0 1\n"
        with self.assertRaisesRegex(ValueError, "size: 8"):
            parse_digest_section_range(output)

    def test_accepts_appimagetool_generated_digest_change_only(self):
        source = bytearray(b"\x7fELF" + bytes(4) + b"AI\x02" + bytes(5) + bytes(16) + b"runtime")
        packaged = bytearray(source)
        packaged[16:32] = bytes(range(1, 17))
        with tempfile.TemporaryDirectory() as directory:
            source_path = Path(directory) / "runtime"
            image_path = Path(directory) / "application.AppImage"
            source_path.write_bytes(source)
            image_path.write_bytes(packaged + b"squashfs")
            verify_runtime_prefix(source_path, image_path, (16, 16), (16, 16))

    def test_rejects_runtime_change_outside_generated_digest(self):
        source = bytearray(b"\x7fELF" + bytes(4) + b"AI\x02" + bytes(5) + bytes(16) + b"runtime")
        packaged = bytearray(source)
        packaged[16:32] = bytes(range(1, 17))
        packaged[-1] ^= 0x01
        with tempfile.TemporaryDirectory() as directory:
            source_path = Path(directory) / "runtime"
            image_path = Path(directory) / "application.AppImage"
            source_path.write_bytes(source)
            image_path.write_bytes(packaged + b"squashfs")
            with self.assertRaisesRegex(ValueError, "differs outside"):
                verify_runtime_prefix(source_path, image_path, (16, 16), (16, 16))

    def test_rejects_unmodified_digest_placeholder(self):
        source = bytearray(b"\x7fELF" + bytes(4) + b"AI\x02" + bytes(5) + bytes(16) + b"runtime")
        with tempfile.TemporaryDirectory() as directory:
            source_path = Path(directory) / "runtime"
            image_path = Path(directory) / "application.AppImage"
            source_path.write_bytes(source)
            image_path.write_bytes(source + b"squashfs")
            with self.assertRaisesRegex(ValueError, "was not generated"):
                verify_runtime_prefix(source_path, image_path, (16, 16), (16, 16))

    def test_rejects_changed_digest_section_layout(self):
        source = bytearray(b"\x7fELF" + bytes(4) + b"AI\x02" + bytes(5) + bytes(16) + b"runtime")
        with tempfile.TemporaryDirectory() as directory:
            source_path = Path(directory) / "runtime"
            image_path = Path(directory) / "application.AppImage"
            source_path.write_bytes(source)
            image_path.write_bytes(source + b"squashfs")
            with self.assertRaisesRegex(ValueError, "sections do not match"):
                verify_runtime_prefix(source_path, image_path, (16, 16), (17, 16))


if __name__ == "__main__":
    unittest.main()
