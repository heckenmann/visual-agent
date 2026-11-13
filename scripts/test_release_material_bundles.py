"""Verify deterministic review-only release material archives."""

import hashlib
import json
import pathlib
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile


ROOT = pathlib.Path(__file__).parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import build_release_material_bundles as bundler


class ReleaseMaterialBundleTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = pathlib.Path(self.temporary.name)
        self.project = self.root / "project"
        self.materials = self.root / "source-materials"
        self.notice_evidence = self.root / "package-notices"
        self.output = self.root / "output"
        self.test_platform = "linux-appimage"
        runtime_source_archive = b"pinned runtime source"
        runtime_source_hash = hashlib.sha256(runtime_source_archive).hexdigest()
        self.runtime_source_hash = runtime_source_hash
        (self.project / "docs/licensing/third-party/vendor").mkdir(parents=True)
        (self.project / "docs/licensing/third-party/historical").mkdir(parents=True)
        (self.materials / "archives/vendor").mkdir(parents=True)
        (self.materials / "archives/appimage-runtime").mkdir(parents=True)
        (self.materials / "legal/vendor").mkdir(parents=True)
        (self.project / "LICENSE").write_text("Project license")
        (self.project / "docs/licensing/README.md").write_text("Review status")
        (self.project / "docs/licensing/source-delivery.md").write_text("Scope remains partial")
        (self.project / "docs/licensing/third-party/vendor/LICENSE").write_text("Vendor license")
        (self.project / "docs/licensing/third-party/historical/LICENSE").write_text("Historical license")
        (self.project / "docs/licensing/third-party/Apache-2.0.txt").write_text("Apache license")
        license_bytes = b"Vendor license"
        historical_license_bytes = b"Historical license"
        apache_license_bytes = b"Apache license"
        (self.project / "docs/licensing/third-party/sources.json").write_text(
            json.dumps(
                {
                    "redistributionApproved": False,
                    "components": [
                        {
                            "reviewStatus": "unreviewed",
                            "materialPaths": ["vendor/LICENSE"],
                            "proposedLicense": "Apache-2.0",
                            "licenseMetadata": {"declaredLicenses": [{"name": "Apache-2.0"}]},
                            "sourceArtifacts": {
                                "example:runtime:1": runtime_source_hash,
                                "example:static:1": "c" * 64,
                            },
                            "upstreamCommit": "a" * 40,
                        },
                        {
                            "reviewStatus": "unreviewed",
                            "materialPaths": ["historical/LICENSE"],
                            "sourceArtifacts": {"example:historical:1": "b" * 64},
                            "upstreamCommit": "a" * 40,
                        },
                    ],
                    "runtimePackages": [{
                        "package": "linux-appimage",
                        "runtimeSourceArtifact": "example:runtime:1",
                            "sourceArchivePath": "archives/appimage-runtime/runtime.tar.gz",
                            "sourceArchives": [{
                                "path": "archives/appimage-runtime/runtime.tar.gz",
                                "sha256": runtime_source_hash,
                            }],
                        "linkedComponents": ["example:static:1"],
                    }],
                    "embeddedAssets": [],
                    "materials": [
                        {
                            "path": "vendor/LICENSE",
                            "sha256": hashlib.sha256(license_bytes).hexdigest(),
                        },
                        {
                            "path": "historical/LICENSE",
                            "sha256": hashlib.sha256(historical_license_bytes).hexdigest(),
                        },
                        {
                            "path": "Apache-2.0.txt",
                            "sha256": hashlib.sha256(apache_license_bytes).hexdigest(),
                        },
                    ],
                },
            ),
        )
        (self.materials / "archives/vendor/source.tar.gz").write_bytes(b"source archive bytes")
        (self.materials / "archives/appimage-runtime/runtime.tar.gz").write_bytes(runtime_source_archive)
        (self.materials / "legal/vendor/LICENSE").write_text("Vendor license copy")
        (self.materials / "source-delivery.md").write_text("Not complete or approved")
        for platform in sorted(bundler.EXPECTED_NOTICE_PLATFORMS):
            self.add_notice_evidence(platform, f"{platform} dependency notice".encode())

    def test_project_license_bytes_survive_a_windows_style_git_checkout(self):
        checkout = self.root / "windows-checkout"
        checkout.mkdir()
        empty_global_attributes = self.root / "empty-global-attributes"
        empty_global_attributes.write_bytes(b"")
        git = ["git", "-c", f"core.attributesFile={empty_global_attributes}",
               "-c", "core.safecrlf=false"]
        license_bytes = b"Project license\nCopyright notice\n"
        (checkout / ".gitattributes").write_bytes((ROOT / ".gitattributes").read_bytes())
        (checkout / "LICENSE").write_bytes(license_bytes)
        (checkout / "ordinary.txt").write_bytes(b"Ordinary text\n")
        for command in (
            ["init", "--quiet"],
            ["config", "core.autocrlf", "true"],
            ["config", "core.eol", "crlf"],
            ["add", ".gitattributes", "LICENSE", "ordinary.txt"],
        ):
            subprocess.run(git + command, cwd=checkout, check=True, capture_output=True)
        (checkout / "LICENSE").unlink()
        (checkout / "ordinary.txt").unlink()
        subprocess.run(git + ["checkout-index", "--all"], cwd=checkout,
                       check=True, capture_output=True)
        self.assertEqual(b"Ordinary text\r\n", (checkout / "ordinary.txt").read_bytes())
        self.assertEqual(license_bytes, (checkout / "LICENSE").read_bytes())

    def add_notice_evidence(self, platform, notice_text):
        notice_directory = self.notice_evidence / platform / "artifacts/package-hash"
        notice_directory.mkdir(parents=True)
        (notice_directory / "LICENSE").write_bytes(notice_text)
        runtime_notice = b"Runtime legal file"
        runtime_path = self.notice_evidence / platform / "runtime-legal/vendor/readme.txt"
        runtime_path.parent.mkdir(parents=True)
        runtime_path.write_bytes(runtime_notice)
        notice_manifest = {
            "redistributionApproved": False,
            "projectLicenseSha256": hashlib.sha256((self.project / "LICENSE").read_bytes()).hexdigest(),
            "components": [
                {
                    "coordinate": "example:component:1",
                    "reviewStatus": "unreviewed",
                    "licenseMetadata": {"declaredLicenses": [{"name": "Apache-2.0"}]},
                    "noticeStatus": "retained",
                    "notices": [
                        {
                            "path": "artifacts/package-hash/LICENSE",
                            "sha256": hashlib.sha256(notice_text).hexdigest(),
                        },
                    ],
                },
            ],
            "runtimeLegal": {
                "packagePath": "Visual Agent/resources/legal/java-runtime",
                "reviewStatus": "unreviewed",
                "files": [
                    {
                        "packagePath": "Visual Agent/resources/legal/java-runtime/vendor/readme.txt",
                        "path": "runtime-legal/vendor/readme.txt",
                        "sha256": hashlib.sha256(runtime_notice).hexdigest(),
                        "size": len(runtime_notice),
                    },
                ],
            },
        }
        if platform == "linux-appimage":
            appimage_notice = b"AppImage runtime component license"
            appimage_notice_path = self.notice_evidence / platform / "appimage-runtime-legal/example-static-1/LICENSE"
            appimage_notice_path.parent.mkdir(parents=True)
            appimage_notice_path.write_bytes(appimage_notice)
            notice_manifest["appImageRuntime"] = {
                "reviewStatus": "unreviewed",
                "packageSha256": "d" * 64,
                "runtimeSha256": "e" * 64,
                "sourceArchiveSha256": self.runtime_source_hash,
                "sourceCommit": "a" * 40,
                "sourceArchives": [{
                    "path": "archives/appimage-runtime/runtime.tar.gz",
                    "sha256": self.runtime_source_hash,
                }],
                "embeddedPrefixVerified": True,
                "components": [
                    {
                        "coordinate": "example:runtime:1",
                        "sourceArtifactSha256": self.runtime_source_hash,
                        "upstreamCommit": "a" * 40,
                        "reviewStatus": "unreviewed",
                        "materials": [],
                    },
                    {
                        "coordinate": "example:static:1",
                        "sourceArtifactSha256": "c" * 64,
                        "upstreamCommit": "a" * 40,
                        "reviewStatus": "unreviewed",
                        "materials": [{
                            "path": "appimage-runtime-legal/example-static-1/LICENSE",
                            "sha256": hashlib.sha256(appimage_notice).hexdigest(),
                        }],
                    },
                ],
            }
        (self.notice_evidence / platform / "manifest.json").write_text(json.dumps(notice_manifest))

    def build(self, recipient_candidate=False):
        return bundler.build_bundles(
            self.project,
            self.materials,
            self.notice_evidence,
            self.output,
            recipient_candidate,
        )

    def test_archives_include_review_status_and_hash_manifests(self):
        legal, sources = self.build()

        with zipfile.ZipFile(legal) as archive:
            self.assertEqual(
                {
                    "LICENSE",
                    "licensing/README.md",
                    "licensing/source-delivery.md",
                    "licensing/third-party/vendor/LICENSE",
                    "licensing/third-party/historical/LICENSE",
                    "licensing/third-party/Apache-2.0.txt",
                    "licensing/third-party/sources.json",
                    "MANIFEST.sha256",
                    "REVIEW-ONLY.txt",
                },
                set(archive.namelist())
                - {
                    name
                    for platform in bundler.EXPECTED_NOTICE_PLATFORMS
                    for name in (
                        f"package-notice-evidence/{platform}/manifest.json",
                        f"package-notice-evidence/{platform}/artifacts/package-hash/LICENSE",
                        f"package-notice-evidence/{platform}/runtime-legal/vendor/readme.txt",
                        *(
                            [
                                "package-notice-evidence/linux-appimage/"
                                "appimage-runtime-legal/example-static-1/LICENSE",
                            ]
                            if platform == "linux-appimage"
                            else []
                        ),
                    )
                },
            )
            self.assertIn(b"must not be published", archive.read("REVIEW-ONLY.txt"))
            for platform in bundler.EXPECTED_NOTICE_PLATFORMS:
                self.assertEqual(
                    f"{platform} dependency notice".encode(),
                    archive.read(f"package-notice-evidence/{platform}/artifacts/package-hash/LICENSE"),
                )
                self.assertEqual(
                    b"Runtime legal file",
                    archive.read(f"package-notice-evidence/{platform}/runtime-legal/vendor/readme.txt"),
                )
            self.assertEqual(
                b"AppImage runtime component license",
                archive.read(
                    "package-notice-evidence/linux-appimage/"
                    "appimage-runtime-legal/example-static-1/LICENSE",
                ),
            )
            self.assertIn(
                f"{hashlib.sha256(b'Project license').hexdigest()}  LICENSE".encode(),
                archive.read("MANIFEST.sha256"),
            )

        with zipfile.ZipFile(sources) as archive:
            self.assertEqual(
                {
                    "source-materials/archives/vendor/source.tar.gz",
                    "source-materials/archives/appimage-runtime/runtime.tar.gz",
                    "source-materials/legal/vendor/LICENSE",
                    "source-materials/source-delivery.md",
                    "MANIFEST.sha256",
                    "REVIEW-ONLY.txt",
                },
                set(archive.namelist()),
            )
            self.assertEqual(b"source archive bytes", archive.read("source-materials/archives/vendor/source.tar.gz"))

    def test_archives_are_reproducible(self):
        first = self.build()
        first_hashes = [hashlib.sha256(path.read_bytes()).digest() for path in first]
        second = self.build()
        self.assertEqual(first_hashes, [hashlib.sha256(path.read_bytes()).digest() for path in second])

    def test_downloaded_artifact_folder_names_are_normalized(self):
        for platform in bundler.EXPECTED_NOTICE_PLATFORMS:
            (self.notice_evidence / platform).rename(self.notice_evidence / f"license-notices-{platform}")

        legal, _ = self.build()

        with zipfile.ZipFile(legal) as archive:
            for platform in bundler.EXPECTED_NOTICE_PLATFORMS:
                self.assertIn(
                    f"package-notice-evidence/{platform}/manifest.json",
                    archive.namelist(),
                )

    def test_duplicate_normalized_platform_evidence_fails_closed(self):
        self.add_notice_evidence("license-notices-linux-appimage", b"Duplicate platform")

        with self.assertRaisesRegex(ValueError, "Duplicate package notice evidence"):
            self.build()

    def test_recipient_candidates_omit_review_marker_but_do_not_change_review_registry(self):
        legal, sources = self.build(recipient_candidate=True)

        with zipfile.ZipFile(legal) as archive:
            names = set(archive.namelist())
            self.assertNotIn("REVIEW-ONLY.txt", names)
            self.assertIn("README-LICENSES.txt", names)
            self.assertIn("third-party/vendor/LICENSE", names)
            self.assertIn("third-party/Apache-2.0.txt", names)
            self.assertNotIn("third-party/historical/LICENSE", names)
            self.assertIn(
                "platforms/linux-appimage/appimage-runtime/example-static-1/LICENSE",
                names,
            )
            self.assertIn(
                "platforms/linux-appimage/notices/example_component_1/"
                f"{hashlib.sha256(b'linux-appimage dependency notice').hexdigest()}/LICENSE",
                names,
            )
            self.assertIn(
                "platforms/windows/java-runtime/vendor/readme.txt",
                names,
            )
            self.assertTrue(archive.read("MANIFEST.sha256"))
            self.assertNotIn("licensing/README.md", names)
            self.assertNotIn("licensing/source-delivery.md", names)
            self.assertNotIn("third-party/sources.json", names)
            self.assertFalse(any(name.endswith("/manifest.json") for name in names))

        with zipfile.ZipFile(sources) as archive:
            names = set(archive.namelist())
            self.assertNotIn("REVIEW-ONLY.txt", names)
            self.assertIn("README-SOURCES.txt", names)
            self.assertIn("source-materials/archives/vendor/source.tar.gz", names)
            self.assertIn("source-materials/archives/appimage-runtime/runtime.tar.gz", names)
            self.assertNotIn("source-materials/source-delivery.md", names)
            self.assertNotIn("source-materials/legal/vendor/LICENSE", names)
            self.assertTrue(archive.read("MANIFEST.sha256"))
            self.assertIn(
                b"source-materials/archives/appimage-runtime/runtime.tar.gz",
                archive.read("README-SOURCES.txt"),
            )

        registry = json.loads((self.project / "docs/licensing/third-party/sources.json").read_text())
        self.assertFalse(registry["redistributionApproved"])
        self.assertIn("must not be published as a release asset", bundler.REVIEW_ONLY_NOTICE)

    def test_recipient_candidate_preserves_distinct_notices_for_repeated_coordinates(self):
        platform_root = self.notice_evidence / "linux-appimage"
        duplicate_bytes = b"second license for repeated component coordinate"
        duplicate_path = platform_root / "artifacts/another-package-hash/LICENSE"
        duplicate_path.parent.mkdir(parents=True)
        duplicate_path.write_bytes(duplicate_bytes)
        manifest_path = platform_root / "manifest.json"
        manifest = json.loads(manifest_path.read_text())
        duplicate_component = json.loads(json.dumps(manifest["components"][0]))
        duplicate_component["notices"][0] = {
            "path": "artifacts/another-package-hash/LICENSE",
            "sha256": hashlib.sha256(duplicate_bytes).hexdigest(),
        }
        manifest["components"].append(duplicate_component)
        manifest_path.write_text(json.dumps(manifest))

        legal, _ = self.build(recipient_candidate=True)

        with zipfile.ZipFile(legal) as archive:
            same_component = "platforms/linux-appimage/notices/example_component_1"
            self.assertEqual(
                b"linux-appimage dependency notice",
                archive.read(
                    f"{same_component}/{hashlib.sha256(b'linux-appimage dependency notice').hexdigest()}/LICENSE",
                ),
            )
            self.assertEqual(
                duplicate_bytes,
                archive.read(
                    f"{same_component}/{hashlib.sha256(duplicate_bytes).hexdigest()}/LICENSE",
                ),
            )

    def test_missing_source_archive_fails_closed(self):
        (self.materials / "archives/vendor/source.tar.gz").unlink()
        (self.materials / "archives/appimage-runtime/runtime.tar.gz").unlink()
        with self.assertRaisesRegex(ValueError, "archive directory is empty"):
            self.build()

    def test_missing_runtime_legal_material_fails_closed(self):
        manifest = self.notice_evidence / self.test_platform / "manifest.json"
        data = json.loads(manifest.read_text())
        del data["runtimeLegal"]
        manifest.write_text(json.dumps(data))
        with self.assertRaisesRegex(ValueError, "Runtime legal evidence is missing"):
            self.build()

    def test_appimage_evidence_without_embedded_runtime_mapping_fails_closed(self):
        manifest = self.notice_evidence / "linux-appimage/manifest.json"
        data = json.loads(manifest.read_text())
        del data["appImageRuntime"]
        manifest.write_text(json.dumps(data))
        with self.assertRaisesRegex(ValueError, "AppImage runtime evidence is missing"):
            self.build()

    def test_missing_platform_notice_evidence_fails_closed(self):
        missing_platform = sorted(bundler.EXPECTED_NOTICE_PLATFORMS)[0]
        shutil.rmtree(self.notice_evidence / missing_platform)
        with self.assertRaisesRegex(ValueError, "Platform notice evidence mismatch"):
            self.build()

    def test_unexpected_platform_notice_evidence_fails_closed(self):
        self.add_notice_evidence("unexpected-platform", b"Unexpected platform")
        with self.assertRaisesRegex(ValueError, "Platform notice evidence mismatch"):
            self.build()

    def test_changed_runtime_legal_material_fails_closed(self):
        (self.notice_evidence / self.test_platform / "runtime-legal/vendor/readme.txt").write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "Runtime legal evidence hash mismatch"):
            self.build()

    @unittest.skipIf(sys.platform == "win32", "Creating symlinks requires Windows developer privileges")
    def test_symlinked_source_material_is_rejected(self):
        linked = self.materials / "archives/vendor/linked.tar.gz"
        linked.symlink_to(self.materials / "archives/vendor/source.tar.gz")
        with self.assertRaisesRegex(ValueError, "Linked release material"):
            bundler.build_bundles(self.project, self.materials, self.notice_evidence, self.output)

    @unittest.skipIf(sys.platform == "win32", "Creating symlinks requires Windows developer privileges")
    def test_symlinked_source_root_is_rejected(self):
        linked_root = self.root / "linked-source-materials"
        linked_root.symlink_to(self.materials, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "directory is missing or linked"):
            bundler.build_bundles(self.project, linked_root, self.notice_evidence, self.output)

    def test_output_cannot_be_written_inside_source_materials(self):
        with self.assertRaisesRegex(ValueError, "must not be inside source materials"):
            bundler.build_bundles(self.project, self.materials, self.notice_evidence, self.materials / "output")

    def test_notice_evidence_hash_mismatch_fails_closed(self):
        (self.notice_evidence / self.test_platform / "artifacts/package-hash/LICENSE").write_text("changed")
        with self.assertRaisesRegex(ValueError, "hash mismatch"):
            self.build()

    def test_curated_legal_material_hash_mismatch_fails_closed(self):
        registry_path = self.project / "docs/licensing/third-party/sources.json"
        registry = json.loads(registry_path.read_text())
        registry["materials"][0]["sha256"] = "0" * 64
        registry_path.write_text(json.dumps(registry))
        with self.assertRaisesRegex(ValueError, "Curated legal material is missing or has a hash mismatch"):
            self.build()

    def test_undeclared_component_material_fails_closed(self):
        registry_path = self.project / "docs/licensing/third-party/sources.json"
        registry = json.loads(registry_path.read_text())
        registry["materials"] = []
        registry_path.write_text(json.dumps(registry))
        with self.assertRaisesRegex(ValueError, "Curated materials are not declared in the registry"):
            self.build()

    def test_notice_evidence_cannot_claim_review_or_redistribution_approval(self):
        manifest_path = self.notice_evidence / self.test_platform / "manifest.json"
        manifest = json.loads(manifest_path.read_text())
        manifest["redistributionApproved"] = True
        manifest_path.write_text(json.dumps(manifest))
        with self.assertRaisesRegex(ValueError, "not explicitly unreviewed"):
            self.build()

    def test_notice_evidence_must_match_the_project_license_in_the_bundle(self):
        manifest_path = self.notice_evidence / self.test_platform / "manifest.json"
        manifest = json.loads(manifest_path.read_text())
        manifest["projectLicenseSha256"] = "0" * 64
        manifest_path.write_text(json.dumps(manifest))
        with self.assertRaisesRegex(ValueError, "does not match the legal bundle"):
            self.build()

    def test_package_workflow_uploads_both_review_only_archives(self):
        workflow = (ROOT / ".github/workflows/package-smoke.yml").read_text()
        self.assertIn("prepareReleaseSourceMaterials", workflow)
        self.assertEqual(
            2,
            workflow.count("--source-materials modules/desktop/build/reports/dependency-license/release-source-materials"),
        )
        self.assertIn(":desktop:generateDistributionArtifactInventory", workflow)
        self.assertIn("actions/setup-python@v7", workflow)
        self.assertIn("collect-third-party-notices.py", workflow)
        self.assertIn("--package-root modules/desktop/build/compose/binaries/main/app", workflow)
        self.assertIn("--inventory modules/desktop/build/reports/dependency-license/packaged-artifacts.csv", workflow)
        self.assertEqual(2, workflow.count("--notice-evidence release-materials/package-notices"))
        self.assertIn("--output-directory release-materials/review-only", workflow)
        self.assertIn("--output-directory release-materials/recipient-candidates", workflow)
        self.assertIn("--recipient-candidate", workflow)
        self.assertIn(":desktop:generateDistributionArtifactInventory", workflow)
        self.assertIn("needs: [verify, package]", workflow)
        self.assertIn("pattern: license-notices-*", workflow)
        self.assertIn("if: matrix.collect_notice_evidence", workflow)
        for platform in ("linux-deb", "linux-rpm", "linux-appimage", "macos-arm64", "macos-x64", "windows"):
            self.assertIn(f"license-notices-${{{{ matrix.platform }}}}", workflow)
            self.assertRegex(
                workflow,
                rf"platform: {platform}\n(?:.*\n){{1,6}}\s+collect_notice_evidence: true",
            )
        self.assertIn("scripts/build_release_material_bundles.py", workflow)
        self.assertIn("name: visual-agent-legal-review", workflow)
        self.assertIn("name: visual-agent-corresponding-sources-review", workflow)
        self.assertIn("name: visual-agent-legal\n", workflow)
        self.assertIn("name: visual-agent-corresponding-sources\n", workflow)


if __name__ == "__main__":
    unittest.main()
