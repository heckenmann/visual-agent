"""Verify real Gradle source-variant collection without releasing artifacts."""

import hashlib
import os
import pathlib
import re
import subprocess
import tarfile
import tempfile
import unittest
import zipfile
from urllib.parse import urlsplit

from third_party_supplements import load_supplements


ROOT = pathlib.Path(__file__).parents[1]
OUTPUT = ROOT / "modules/desktop/build/reports/dependency-license/library-source-materials"
RELEASE_OUTPUT = ROOT / "modules/desktop/build/reports/dependency-license/release-source-materials"


class LibrarySourceMaterialsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        result = subprocess.run(
            [str(ROOT / "gradlew"), ":desktop:prepareReleaseSourceMaterials", "--quiet", "--no-daemon"],
            capture_output=True, text=True,
        )
        if result.returncode:
            raise RuntimeError(result.stdout + result.stderr)

    def test_source_archives_match_verified_exact_version_inputs(self):
        document = (ROOT / "docs/licensing/source-delivery.md").read_text()
        expected = dict(re.findall(r"\[([^\]]+-sources\.jar)\].*?`([0-9a-f]{64})`", document))
        self.assertEqual(6, len(expected))
        self.assertIn("no libffi native source tree", document)
        normalized_document = " ".join(document.split())
        self.assertIn(
            "do not, on the current evidence, make that full archive a corresponding-source release blocker",
            normalized_document,
        )
        self.assertIn("not the native Skia/ANGLE source trees", document)
        actual = {path.name: hashlib.sha256(path.read_bytes()).hexdigest()
                  for path in (OUTPUT / "archives").iterdir() if path.is_file()}
        self.assertEqual(expected, actual)

    def test_zulu_java_source_package_is_checksum_pinned_and_included(self):
        package = RELEASE_OUTPUT / "archives/zulu-jdk/zulu27-ca-src_27.0.1.0.3-5_amd64.deb"
        self.assertEqual(
            "c803bd7fa69e94c8f1a7370e00dd98212480d8648d6e27eed06c27b97bd4b6b4",
            hashlib.sha256(package.read_bytes()).hexdigest(),
        )
        document = (ROOT / "docs/licensing/source-delivery.md").read_text()
        self.assertIn("15,166 `.java` files", document)
        self.assertIn("not a substitute for", document)
        self.assertIn("does not contain native JDK source files", document)

    def test_appimage_source_archives_match_pinned_upstream_hashes(self):
        document = (ROOT / "docs/licensing/source-delivery.md").read_text()
        section = document.split("| AppImage type2-runtime commit", 1)[1].split("\n\n", 1)[0]
        expected = dict(re.findall(r"\[source archive\]\(([^)]+)\).*?`([0-9a-f]{64})`", section))
        self.assertEqual(11, len(expected))
        archives = OUTPUT / "archives/appimage-runtime"
        self.assertEqual(11, len(list(archives.iterdir())))
        for url, expected_hash in expected.items():
            parsed_url = urlsplit(url)
            name = pathlib.PurePosixPath(parsed_url.path).name
            if name == "dd6cebedcbddde9c82f89b011e8e1d40b6e43868":
                name = "type2-runtime-dd6cebed.tar.gz"
            elif name == "0.5.2.tar.gz":
                name = "squashfuse-0.5.2.tar.gz"
            elif name == "v2.1.7.tar.gz":
                name = "mimalloc-2.1.7.tar.gz"
            elif name == "v1.5.6.tar.gz":
                name = "zstd-1.5.6.tar.gz"
            elif parsed_url.hostname == "github.com" and parsed_url.path.startswith("/alpinelinux/aports/archive/"):
                name = {
                    "efd4d5df7be0ea48d98512671f676fc5ceb14899.tar.gz": "alpine-musl-package.tar.gz",
                    "5a1a5dc3216a69e3cd66a57ee07cb5ef01b8be39.tar.gz": "alpine-mimalloc-package.tar.gz",
                    "5c2ddf18f193dafdd25ae7391d96c7e30766eecd.tar.gz": "alpine-zstd-package.tar.gz",
                    "4c2722e7ecc1c5e3ba882429044a101497121223.tar.gz": "alpine-zlib-package.tar.gz",
                }.get(name, name)
            path = archives / name
            self.assertEqual(expected_hash, hashlib.sha256(path.read_bytes()).hexdigest())
            with tarfile.open(path, "r:*") as archive:
                members = archive.getmembers()
                recipe_archives = {
                    "alpine-musl-package.tar.gz": ("/main/musl/APKBUILD", "1.2.5", "9"),
                    "alpine-mimalloc-package.tar.gz": ("/community/mimalloc2/APKBUILD", "2.1.7", "0"),
                    "alpine-zstd-package.tar.gz": ("/main/zstd/APKBUILD", "1.5.6", "2"),
                    "alpine-zlib-package.tar.gz": ("/main/zlib/APKBUILD", "1.3.1", "2"),
                }
                if name in recipe_archives:
                    recipe_suffix, expected_version, expected_pkgrel = recipe_archives[name]
                    apkbuild = next(member for member in members if member.name.endswith(recipe_suffix))
                    source = archive.extractfile(apkbuild).read().decode()
                    self.assertIn(f"pkgver={expected_version}", source)
                    self.assertIn(f"pkgrel={expected_pkgrel}", source)
                    if name == "alpine-mimalloc-package.tar.gz":
                        self.assertIn("license=\"MIT\"", source)
                        self.assertTrue(any(member.name.endswith("/cmake-add-insecure-suffix.patch") for member in members))
                    elif name == "alpine-musl-package.tar.gz":
                        self.assertIn("license=\"MIT\"", source)
                        self.assertTrue(any(member.name.endswith("/CVE-2025-26519.patch") for member in members))
                    elif name == "alpine-zstd-package.tar.gz":
                        self.assertIn("license=\"BSD-3-Clause OR GPL-2.0-or-later\"", source)
                    else:
                        self.assertIn("license=\"Zlib\"", source)
                else:
                    self.assertTrue(any(member.name.endswith(("LICENSE", "COPYING", "COPYRIGHT")) for member in members))

    def test_fuse_relink_guide_matches_the_retained_patched_build_recipe(self):
        guide = (ROOT / "docs/licensing/third-party/libfuse-3.15.0/SOURCE-AND-RELINK.txt").read_text()
        for reference in ("LGPL-2.1 section 6(a)", "fuse-3.15.0.tar.xz",
                          "type2-runtime-dd6cebed.tar.gz", "mount.c.diff", "--runtime-file"):
            self.assertIn(reference, guide)
        archives = OUTPUT / "archives/appimage-runtime"
        with tarfile.open(archives / "type2-runtime-dd6cebed.tar.gz", "r:gz") as archive:
            prefix = "type2-runtime-dd6cebedcbddde9c82f89b011e8e1d40b6e43868/"
            for name in ("BUILD.md", "patches/libfuse/mount.c.diff",
                         "scripts/docker/build-with-docker.sh", "src/runtime/runtime.c",
                         "src/runtime/Makefile"):
                self.assertIsNotNone(archive.getmember(prefix + name))
            dockerfile = archive.extractfile(prefix + "scripts/docker/Dockerfile").read()
            self.assertIn(b"COPY patches/ /tmp/patches/", dockerfile)
            installer = archive.extractfile(prefix + "scripts/common/install-dependencies.sh").read()
            self.assertIn(b"70589cfd5e1cff7ccd6ac91c86c01be340b227285c5e200baa284e401eea2ca0", installer)
            self.assertIn(b"patch -p1", installer)
            self.assertIn(b"--default-library static", installer)

    def test_liberation_font_source_archive_is_pinned_and_included(self):
        document = (ROOT / "docs/licensing/source-delivery.md").read_text()
        archive_path = OUTPUT / "archives/liberation-fonts/liberation-fonts-2.1.5.tar.gz"
        self.assertEqual("fb22c2109dcbb25202aeb6202c0a58fd2e536dbf7c72879c4bbfa2b8a9a75d60",
                         hashlib.sha256(archive_path.read_bytes()).hexdigest())
        self.assertIn("liberation-fonts-2.1.5.tar.gz", document)
        with tarfile.open(archive_path, "r:gz") as archive:
            self.assertTrue(any(member.name.endswith("/src/LiberationSans-Regular.sfd")
                                for member in archive.getmembers()))
            license_file = archive.extractfile("liberation-fonts-2.1.5/LICENSE")
            self.assertIn(b"SIL OPEN FONT LICENSE Version 1.1", license_file.read())
            self.assertFalse(any(member.name.endswith("/LiberationSans-Regular.ttf")
                                 for member in archive.getmembers()))

    def test_archives_contain_sources_not_binary_runtime_jars(self):
        for path in (OUTPUT / "archives").iterdir():
            if not path.is_file():
                continue
            with self.subTest(archive=path.name), zipfile.ZipFile(path) as archive:
                names = archive.namelist()
                self.assertTrue(any(name.endswith((".java", ".kt")) for name in names))
                self.assertFalse(any(name.endswith(".class") for name in names))

    def test_netty_native_dns_license_evidence_is_historical_and_unreviewed(self):
        source_delivery = " ".join((ROOT / "docs/licensing/source-delivery.md").read_text().split())
        self.assertIn("historical review evidence", source_delivery)
        self.assertIn("not included in the new release source bundle", source_delivery)
        self.assertIn(
            "98a33e5adbbe58cb084ed4ca61350fa0a6311f4f8e54bc8cd823a402d0609c42",
            source_delivery,
        )
        third_party = ROOT / "docs/licensing/third-party/netty-4.2.20.Final"
        header_license = (third_party / "LICENSE.dnsinfo.txt").read_bytes()
        apsl_license = (third_party / "APSL-2.0.txt").read_bytes()
        self.assertEqual(
            "f0bdf5682bf317b1f85c80ed32cb4c7bf77b5a231dee485aa92d983673b7c58f",
            hashlib.sha256(header_license).hexdigest(),
        )
        self.assertEqual(
            "2d0c12edaaf8d31fec6796fef99291b12e833b6bef28100975120a53d209e06d",
            hashlib.sha256(apsl_license).hexdigest(),
        )
        self.assertIn(b"APPLE PUBLIC SOURCE LICENSE", apsl_license)
        registry = ROOT / "docs/licensing/third-party/sources.json"
        supplements = load_supplements(registry)
        materials = supplements["io.netty:netty-resolver-dns-native-macos:4.2.20.Final"]["materials"]
        self.assertEqual(3, len(materials))
        self.assertTrue(all("Apple Public Source License" in material["content"].decode("utf-8")
                            for material in materials[1:]))
        self.assertTrue(supplements["io.netty:netty-resolver-dns-native-macos:4.2.20.Final"]["remaining"])

    def test_pinned_appimage_runtime_archive_preserves_the_libfuse_relink_path(self):
        archive_path = OUTPUT / "archives/appimage-runtime/type2-runtime-dd6cebed.tar.gz"
        with tarfile.open(archive_path, "r:gz") as archive:
            root = "type2-runtime-dd6cebedcbddde9c82f89b011e8e1d40b6e43868/"

            def source(path):
                entry = archive.extractfile(root + path)
                self.assertIsNotNone(entry, path)
                return entry.read().decode()

            build_guide = source("BUILD.md")
            runtime = source("src/runtime/runtime.c")
            makefile = source("src/runtime/Makefile")
            install_script = source("scripts/common/install-dependencies.sh")
            patch = source("patches/libfuse/mount.c.diff")
            self.assertIn("build-with-docker.sh", build_guide)
            self.assertIn("int main", runtime)
            self.assertIn("-static-pie", makefile)
            self.assertIn("-lfuse3", makefile)
            self.assertIn("70589cfd5e1cff7ccd6ac91c86c01be340b227285c5e200baa284e401eea2ca0", install_script)
            self.assertIn("patch -p1", install_script)
            self.assertIn("mount", patch)
        delivery = " ".join((ROOT / "docs/licensing/source-delivery.md").read_text().split())
        self.assertIn("lgpl-2.1.en.html", delivery)
        self.assertIn("the complete libfuse source and changes", delivery)
        self.assertIn("runtime source (or suitable object code)", delivery)
        self.assertIn("instructions verified against a working build", delivery)

    def test_release_workflow_rebuilds_the_pinned_appimage_runtime(self):
        workflow = (ROOT / ".github/workflows/package-smoke.yml").read_text()
        verifier = (ROOT / "scripts/verify-appimage-runtime-build.sh").read_text()
        desktop_build = (ROOT / "modules/desktop/build.gradle.kts").read_text()
        self.assertIn('tasks.register<Exec>("buildAppImageRuntime")', desktop_build)
        self.assertIn("dependsOn(downloadAppImageTool, buildAppImageRuntime, prepareAppImage)", desktop_build)
        self.assertIn("input.readNBytes(runtimeBytes.size)", desktop_build)
        self.assertIn("packagedRuntimePrefix.contentEquals(runtimeBytes)", desktop_build)
        self.assertIn("install -m 755 \"$runtime\" \"$output_runtime\"", verifier)
        self.assertIn("build-with-docker.sh", verifier)
        self.assertIn("readelf -l", verifier)
        self.assertIn("--appimage-help", verifier)
        self.assertIn("task: packageAppImage", workflow)

    def test_appimage_runtime_verifier_copies_verified_output_to_package_path(self):
        archive = OUTPUT / "archives/appimage-runtime/type2-runtime-dd6cebed.tar.gz"
        verifier = ROOT / "scripts/verify-appimage-runtime-build.sh"
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_path = pathlib.Path(temporary_directory)
            fake_bin = temporary_path / "bin"
            fake_bin.mkdir()
            docker = fake_bin / "docker"
            docker.write_text(
                "#!/bin/sh\n"
                "set -eu\n"
                "if [ \"$1\" = build ]; then exit 0; fi\n"
                "[ \"$1\" = run ]\n"
                "for argument do\n"
                "  case \"$argument\" in\n"
                "    *:/ws) source_directory=\"${argument%:/ws}\" ;;\n"
                "    *:/ws/out) output_directory=\"${argument%:/ws/out}\" ;;\n"
                "  esac\n"
                "done\n"
                "test -n \"${source_directory:-}\"\n"
                "test -n \"${output_directory:-}\"\n"
                "source_version=\"$(cat \"$source_directory/src/runtime/version\")\"\n"
                "cat > \"$output_directory/runtime-x86_64\" <<EOF\n"
                "#!/bin/sh\n"
                "case \"\$1\" in\n"
                "  --appimage-help) echo --appimage-extract ;;\n"
                "  --appimage-version) echo \"AppImage runtime version: $source_version\" ;;\n"
                "esac\n"
                "EOF\n"
                "chmod +x \"$output_directory/runtime-x86_64\"\n"
            )
            docker.chmod(0o755)
            (fake_bin / "file").write_text(
                "#!/bin/sh\necho 'runtime: ELF 64-bit LSB pie executable, x86-64'\n"
            )
            (fake_bin / "file").chmod(0o755)
            (fake_bin / "readelf").write_text("#!/bin/sh\nexit 0\n")
            (fake_bin / "readelf").chmod(0o755)
            package_runtime = temporary_path / "package/runtime-x86_64"
            environment = os.environ.copy()
            environment["PATH"] = str(fake_bin) + os.pathsep + environment["PATH"]

            result = subprocess.run(
                ["bash", str(verifier), str(archive), str(package_runtime)],
                capture_output=True,
                text=True,
                env=environment,
            )
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)

            self.assertIn("AppImage runtime version: https://github.com/AppImage/type2-runtime/commit/dd6cebe",
                          package_runtime.read_text())
            self.assertTrue(os.access(package_runtime, os.X_OK))

    def test_platform_inventory_collects_and_uploads_partial_source_evidence(self):
        workflow = (ROOT / ".github/workflows/license-inventory.yml").read_text()
        self.assertIn(":desktop:prepareLibrarySourceMaterials", workflow)
        self.assertNotIn(":desktop:prepareReleaseSourceMaterials", workflow)
        self.assertIn("modules/desktop/build/reports/dependency-license/library-source-materials/", workflow)
        for platform in ("linux-x64", "macos-arm64", "macos-x64", "windows-x64"):
            self.assertIn(f"platform: {platform}", workflow)
        self.assertNotIn("continue-on-error:", workflow)

    def test_original_legal_inputs_are_retained_and_scope_is_explicit(self):
        source = ROOT / "docs/licensing/third-party"
        expected = {path.relative_to(source): path.read_bytes()
                    for path in source.rglob("*") if path.is_file()}
        actual = {path.relative_to(OUTPUT / "legal"): path.read_bytes()
                  for path in (OUTPUT / "legal").rglob("*") if path.is_file()}
        self.assertEqual(expected, actual)
        document = " ".join((OUTPUT / "source-delivery.md").read_text().split())
        self.assertIn("not yet part of a recipient-facing source bundle", document)
        self.assertEqual((ROOT / "docs/licensing/source-delivery.md").read_bytes(),
                         (OUTPUT / "source-delivery.md").read_bytes())

    def test_missing_required_runtime_component_fails_instead_of_silent_omission(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            init = root / "missing-source.init.gradle.kts"
            destination = (root / "output").as_posix()
            init.write_text(
                'allprojects { configurations.configureEach { '
                'exclude(group = "com.h2database", module = "h2") } }\n'
                'gradle.projectsEvaluated { rootProject.findProject(":desktop")'
                f'?.layout?.buildDirectory?.set(java.io.File("{destination}"))'
                ' }\n'
            )
            result = subprocess.run(
                [str(ROOT / "gradlew"), "-I", str(init), ":desktop:prepareLibrarySourceMaterials",
                 "--quiet", "--no-daemon", "--offline"], capture_output=True, text=True,
            )
            self.assertNotEqual(0, result.returncode)
            self.assertIn("Missing required selected-library source artifacts", result.stdout + result.stderr)
            output = root / "output/reports/dependency-license/library-source-materials"
            self.assertFalse(any(path.is_file() for path in output.rglob("*")))


if __name__ == "__main__":
    unittest.main()
