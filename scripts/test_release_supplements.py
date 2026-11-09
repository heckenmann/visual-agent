"""Verify exact supplementary legal evidence without network access or approval."""

import copy
import hashlib
import importlib.util
import json
import pathlib
import re
import tempfile
import unittest
import zipfile

from third_party_supplements import load_supplements


SPEC = importlib.util.spec_from_file_location("notices", pathlib.Path(__file__).with_name("collect-third-party-notices.py"))
NOTICES = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(NOTICES)


class SupplementalNoticesTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = pathlib.Path(temporary.name)
        self.registry = self.root / "sources.json"
        self.material = self.root / "LICENSE.txt"
        self.content = b"Original license\r\n"
        self.material.write_bytes(self.content)
        self.data = {
            "schemaVersion": 1,
            "redistributionApproved": False,
            "materials": [{"path": "LICENSE.txt", "sha256": hashlib.sha256(self.content).hexdigest(), "url": "https://example.org/LICENSE"}],
            "components": [{
                "sourceArtifacts": {"example:lib:1": "a" * 64},
                "upstreamCommit": "b" * 40,
                "reviewStatus": "unreviewed",
                "remaining": ["Source review"],
                "proposedLicense": "MIT",
                "materialPaths": ["LICENSE.txt"],
            }],
        }

    def load(self):
        self.registry.write_text(json.dumps(self.data))
        return load_supplements(self.registry)

    def test_original_material_is_retained_for_exact_version_and_hash(self):
        result = self.load()
        self.assertEqual({"example:lib:1"}, result.keys())
        self.assertEqual(self.content, result["example:lib:1"]["materials"][0]["content"])
        self.assertEqual("a" * 64, result["example:lib:1"]["expectedSourceSha256"])

    def test_changed_legal_text_is_rejected(self):
        self.material.write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "Changed supplemental"):
            self.load()

    def test_missing_material_is_rejected(self):
        self.material.unlink()
        with self.assertRaisesRegex(ValueError, "Missing or escaping"):
            self.load()

    def test_escaping_paths_are_rejected(self):
        for path in ("../LICENSE.txt", "/LICENSE.txt", "C:/LICENSE.txt", "dir\\LICENSE.txt"):
            with self.subTest(path=path):
                self.data["materials"][0]["path"] = path
                with self.assertRaisesRegex(ValueError, "Unsafe"):
                    self.load()

    def test_duplicate_coordinate_is_rejected(self):
        self.data["components"].append(copy.deepcopy(self.data["components"][0]))
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            self.load()

    def test_missing_source_identity_is_rejected(self):
        self.data["components"][0]["upstreamCommit"] = "main"
        with self.assertRaisesRegex(ValueError, "exact upstream"):
            self.load()

    def test_evidence_cannot_self_approve_publication(self):
        self.data["redistributionApproved"] = True
        with self.assertRaisesRegex(ValueError, "cannot grant"):
            self.load()

    def test_review_status_and_remaining_work_cannot_be_omitted(self):
        self.data["components"][0]["remaining"] = []
        with self.assertRaisesRegex(ValueError, "pending review"):
            self.load()

    def test_source_urls_cannot_embed_credentials(self):
        self.data["materials"][0]["url"] = "https://user:secret@example.org/LICENSE"
        with self.assertRaisesRegex(ValueError, "public HTTPS"):
            self.load()

    def test_repository_snapshots_match_the_recorded_hashes(self):
        registry = pathlib.Path(__file__).parents[1] / "docs/licensing/third-party/sources.json"
        components = load_supplements(registry)
        self.assertEqual(64, len(components))
        self.assertEqual("MPL-2.0", components["com.h2database:h2:2.5.252"]["proposedLicense"])
        self.assertEqual("EPL-2.0", components["ch.qos.logback:logback-core:1.6.6"]["proposedLicense"])
        self.assertEqual("Apache-2.0", components["net.java.dev.jna:jna:5.19.1"]["proposedLicense"])
        icu = components["org.graalvm.shadowed:icu4j:25.4.4.1.1"]
        self.assertEqual("Unicode/ICU License", icu["proposedLicense"])
        self.assertEqual("f1b3db8ecd39d5b3a6eff4d5641b176c7f914dfb", icu["upstreamCommit"])
        self.assertEqual(
            "02f9e8a76ed2f8fa80d53054ca4632c7058354010df27c8f4e7e7443543f7e1e",
            icu["expectedSourceSha256"],
        )
        self.assertIn(b"UNICODE LICENSE V3", icu["materials"][0]["content"])
        upl = components["org.graalvm.polyglot:polyglot:25.4.4.1.1"]
        self.assertEqual("UPL-1.0", upl["proposedLicense"])
        self.assertEqual("95ce1499c8c96ab7d5a6697c5b4bf42160f3b68b", upl["upstreamCommit"])
        self.assertIn(b"The Universal Permissive License (UPL), Version 1.0", upl["materials"][0]["content"])
        self.assertTrue(upl["remaining"])
        registry_data = json.loads(registry.read_text())
        newly_mapped_coordinates = {
            "org.antlr:ST4:4.3.4",
            "org.codehaus.mojo:animal-sniffer-annotations:1.27",
            "org.antlr:antlr-runtime:3.5.3",
            "org.antlr:antlr4-runtime:4.13.1",
            "org.ow2.asm:asm:9.11.1",
            "org.nibor.autolink:autolink:0.12.0",
            "com.github.hypfvieh:dbus-java-core:5.2.2",
            "com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.2.2",
            "io.github.vinceglb:filekit-core-jvm:0.17.0",
            "io.github.vinceglb:filekit-dialogs-compose-jvm:0.17.0",
            "io.github.vinceglb:filekit-dialogs-jvm:0.17.0",
            "com.knuddels:jtokkit:1.1.0",
            "com.google.protobuf:protobuf-java:4.36.2",
            "org.reactivestreams:reactive-streams:1.0.4",
        }
        self.assertTrue(newly_mapped_coordinates.issubset(components))
        for coordinate in newly_mapped_coordinates:
            with self.subTest(coordinate=coordinate):
                self.assertTrue(components[coordinate]["remaining"])
        antlr_runtime = components["org.antlr:antlr-runtime:3.5.3"]
        runtime_notices = next(
            material["content"]
            for material in antlr_runtime["materials"]
            if material["path"].endswith("RUNTIME-SOURCE-NOTICES.txt")
        )
        self.assertIn(b"2005-2009 Terence Parr", runtime_notices)
        self.assertIn(b"2012 Sam Harwell", runtime_notices)
        upl_record = next(
            item for item in registry_data["components"]
            if "org.graalvm.polyglot:polyglot:25.4.4.1.1" in item["sourceArtifacts"]
        )
        self.assertEqual(12, len(upl_record["sourceArtifacts"]))
        self.assertEqual("unreviewed", upl_record["reviewStatus"])
        icu_record = next(
            item for item in registry_data["components"]
            if "org.graalvm.shadowed:icu4j:25.4.4.1.1" in item["sourceArtifacts"]
        )
        self.assertEqual("unreviewed", icu_record["reviewStatus"])
        self.assertTrue(icu["remaining"])
        mimalloc = components["mimalloc2:2.1.7-r0"]
        self.assertEqual("MIT", mimalloc["proposedLicense"])
        self.assertEqual("0eed39319f139afde8515010ff59baf24de9e47ea316a315398e8027d198202d",
                         mimalloc["expectedSourceSha256"])
        self.assertTrue(mimalloc["materials"][0]["content"].startswith(b"MIT License\n"))
        self.assertIn("runtime string identifies only upstream 2.1.7", " ".join(mimalloc["remaining"]))
        self.assertIn("mimalloc2 2.1.7-r0 and its package patch", " ".join(mimalloc["remaining"]))
        zstd = components["zstd:1.5.6"]
        self.assertEqual("BSD-3-Clause", zstd["proposedLicense"])
        self.assertTrue(zstd["materials"][0]["content"].startswith(b"BSD License\n"))
        self.assertIn("zstd-static 1.5.6-r2", " ".join(zstd["remaining"]))
        self.assertIn("which declared license option applies", " ".join(zstd["remaining"]))
        zlib = components["zlib:1.3.1"]
        self.assertEqual("Zlib", zlib["proposedLicense"])
        self.assertIn(b"Jean-loup Gailly", zlib["materials"][0]["content"])
        self.assertIn("zlib-static 1.3.1-r2", " ".join(zlib["remaining"]))
        self.assertIn("exact build inputs", " ".join(zlib["remaining"]))
        runtime = components["AppImage:type2-runtime:20251108"]
        self.assertEqual("MIT", runtime["proposedLicense"])
        self.assertEqual("f5fec23be76e50e2445ed2d018bac49b367490fc483c62c4637e99ec705d27ba",
                         runtime["expectedSourceSha256"])
        self.assertIn(b"statically linked code", runtime["materials"][0]["content"])
        self.assertIn("embeds the pinned upstream source commit", " ".join(runtime["remaining"]))
        fuse = components["AppImage:libfuse:3.15.0"]
        self.assertEqual("LGPL-2.1-only", fuse["proposedLicense"])
        self.assertIn(b"GNU LESSER GENERAL PUBLIC LICENSE", fuse["materials"][1]["content"])
        self.assertIn("static-link relinking", " ".join(fuse["remaining"]))
        squashfuse = components["AppImage:squashfuse:0.5.2"]
        self.assertEqual("BSD-2-Clause", squashfuse["proposedLicense"])
        self.assertIn(b"Redistribution and use in source and binary forms",
                      squashfuse["materials"][0]["content"])
        musl = components["AppImage:musl:1.2.5"]
        self.assertEqual("MIT", musl["proposedLicense"])
        self.assertIn(b"musl as a whole is licensed", musl["materials"][0]["content"])
        graal = components["org.graalvm.js:js-isolate-linux-amd64:25.4.4.1.1"]
        self.assertEqual(
            "GraalVM Free Terms and Conditions (GFTC)",
            graal["proposedLicense"],
        )
        self.assertEqual(
            "58f1795f7d12d12260d0c0423811e846c7632649",
            graal["upstreamCommit"],
        )
        self.assertEqual(
            "81c71eea20f3c272c69d9d6ba3ed392f3651562150f3a4f34401004fcb403821",
            components["org.graalvm.js:js-isolate-darwin-aarch64:25.4.4.1.1"]["expectedSourceSha256"],
        )
        self.assertIn(b"re-signing an embedded macOS library", graal["materials"][0]["content"])
        self.assertTrue(any("unmodified-Program condition" in item for item in graal["remaining"]))
        self.assertFalse(any("no-fee redistribution condition" in item for item in graal["remaining"]))
        self.assertIn("without charging recipients", graal["materials"][0]["content"].decode())
        self.assertIn("public GitHub Releases", graal["materials"][0]["content"].decode())
        self.assertFalse(json.loads(registry.read_text())["redistributionApproved"])
        self.assertFalse(json.loads(registry.read_text())["redistributionApproved"])

    def test_current_graal_catalog_version_has_hash_bound_runtime_notices(self):
        root = pathlib.Path(__file__).parents[1]
        catalog = (root / "gradle/libs.versions.toml").read_text()
        selected = re.search(r'^graaljs\s*=\s*"([^"]+)"\s*$', catalog, re.MULTILINE)
        self.assertIsNotNone(selected, "Missing central GraalJS version")
        version = selected.group(1)
        components = load_supplements(root / "docs/licensing/third-party/sources.json")
        modules = (
            "org.graalvm.js:js-language", "org.graalvm.polyglot:polyglot",
            "org.graalvm.regex:regex", "org.graalvm.sdk:collections",
            "org.graalvm.sdk:jniutils", "org.graalvm.sdk:nativebridge",
            "org.graalvm.sdk:nativeimage", "org.graalvm.sdk:word",
            "org.graalvm.shadowed:xz", "org.graalvm.truffle:truffle-api",
            "org.graalvm.truffle:truffle-compiler", "org.graalvm.truffle:truffle-runtime",
        )
        for module in modules:
            with self.subTest(module=module, version=version):
                component = components[f"{module}:{version}"]
                self.assertEqual("UPL-1.0", component["proposedLicense"])
                self.assertIn(b"Universal Permissive License", component["materials"][0]["content"])
        icu = components[f"org.graalvm.shadowed:icu4j:{version}"]
        self.assertEqual("Unicode/ICU License", icu["proposedLicense"])
        language = components[f"org.graalvm.js:js-language:{version}"]
        self.assertIn(b"Copyright (c) 2010, 2022, Oracle", language["materials"][0]["content"])
        third_party = next(item for item in language["materials"]
                           if item["path"].endswith("THIRD_PARTY_LICENSE_GRAALJS.txt"))
        for notice in (b"Lucent Technologies", b"V8 project authors", b"Mozilla Public"):
            self.assertIn(notice, third_party["content"])
        source_notice = next(item for item in language["materials"]
                             if item["path"].endswith("SOURCE-AVAILABILITY.txt"))
        self.assertIn(f"js-language-{version}-sources.jar".encode(), source_notice["content"])
        for platform in ("linux-amd64", "darwin-aarch64", "windows-amd64"):
            with self.subTest(platform=platform, version=version):
                component = components[f"org.graalvm.js:js-isolate-{platform}:{version}"]
                self.assertEqual("GraalVM Free Terms and Conditions (GFTC)", component["proposedLicense"])
                self.assertIn(version.encode(), component["materials"][0]["content"])
                self.assertTrue(component["remaining"])

    def test_skiko_retains_original_notice_without_clearing_native_dependencies(self):
        root = pathlib.Path(__file__).parents[1]
        registry = root / "docs/licensing/third-party/sources.json"
        component = load_supplements(registry)["org.jetbrains.skiko:skiko-awt:0.150.2"]
        notice = next(item for item in component["materials"]
                      if item["path"] == "skiko-0.150.2/NOTICE")
        self.assertIn(b"The Android Open Source Project", notice["content"])
        self.assertIn(component["upstreamCommit"], notice["url"])
        remaining = " ".join(component["remaining"])
        for library in ("skia_ganesh_ext", "skunicode_icu", "harfbuzz", "webp_sse41",
                        "piex", "dng_sdk", "d3d12allocator"):
            self.assertIn(library, remaining)
        self.assertFalse(json.loads(registry.read_text())["redistributionApproved"])

    def test_skia_dependency_notices_and_required_credits_are_retained(self):
        root = pathlib.Path(__file__).parents[1]
        components = load_supplements(root / "docs/licensing/third-party/sources.json")
        expected = {
            "expat-6154446f/COPYING": "6154446fccefbf3ca644894f598969113b0c7bcd",
            "harfbuzz-9cb1fee5/COPYING": "9cb1fee51069b206effb4736e443b038d230789d",
            "d3d12allocator-169895d5/LICENSE.txt": "169895d529dfce00390a20e69c2f516066fe7a3b",
            "libwebp-845d5476/COPYING": "845d5476a866141ba35ac133f856fa62f0b7445f",
            "libwebp-845d5476/PATENTS": "845d5476a866141ba35ac133f856fa62f0b7445f",
            "libpng-d5515b5b/LICENSE": "d5515b5b8be3901aac04e5bd8bd5c89f287bcd33",
            "libjpeg-turbo-e14cbfaa/LICENSE.md": "e14cbfaa85529d47f9f55b0f104a579c1061f9ad",
            "libjpeg-turbo-e14cbfaa/README.ijg": "e14cbfaa85529d47f9f55b0f104a579c1061f9ad",
            "zlib-646b7f56/LICENSE": "646b7f569718921d7d4b5b8e22572ff6c76f2596",
            "icu-364118a1/LICENSE": "364118a1d9da24bb5b770ac3d762ac144d6da5a4",
        }
        for name in ("LICENSE.TXT", "FTL.TXT", "bdf-README", "pcf-README"):
            expected[f"freetype-264b5fbf/{name}"] = "264b5fbf5b912b39f98d038bf75d39be0a73f21b"
        for platform in ("linux-x64", "macos-arm64", "macos-x64", "windows-x64"):
            with self.subTest(platform=platform):
                component = components[f"org.jetbrains.skiko:skiko-awt-runtime-{platform}:0.150.2"]
                materials = {item["path"]: item for item in component["materials"]}
                self.assertTrue(set(expected).issubset(materials))
                for path, revision in expected.items():
                    self.assertIn(revision, materials[path]["url"])
                attribution = materials["skia-m150-a3f509bf52/ATTRIBUTIONS.txt"]["content"]
                self.assertIn(b"This software is based in part on the work of the Independent JPEG Group.", attribution)
                self.assertIn(b"1996-2026 The FreeType Project", attribution)
                self.assertIn(b"not the alternative GPL option", attribution)

    def test_angle_dependency_notices_preserve_distinct_upstream_licenses(self):
        root = pathlib.Path(__file__).parents[1]
        components = load_supplements(root / "docs/licensing/third-party/sources.json")
        component = components["org.jetbrains.skiko:skiko-awt-runtime-angle-windows-x64:0.150.2"]
        materials = {item["path"]: item for item in component["materials"]}
        expected = {
            "angle-ec4d8f8e4d/xxhash-LICENSE": "ec4d8f8e4d3f1a9e64430abd8b6b436253732adf",
            "abseil-24012838/LICENSE": "2401283880a451e9ea10787920117fb67695abb4",
            "zlib-e8ff3f35/LICENSE": "e8ff3f35551275330696606d7fef3a2a0f73a831",
            "chromium-055e258e/LICENSE": "055e258e91fc310ded1c0071f108c08f0e924a53",
        }
        for path, revision in expected.items():
            with self.subTest(path=path):
                self.assertIn(revision, materials[path]["url"])
                self.assertTrue(materials[path]["content"])
        self.assertNotEqual(
            materials["chromium-055e258e/LICENSE"]["content"],
            materials["zlib-e8ff3f35/LICENSE"]["content"],
        )

    def test_skia_embedded_component_evidence_is_pinned_and_not_approved(self):
        root = pathlib.Path(__file__).parents[1]
        registry_path = root / "docs/licensing/third-party/sources.json"
        registry = json.loads(registry_path.read_text())
        components = {item["name"]: item for item in registry["embeddedComponents"]}
        self.assertEqual({"dng_sdk", "piex", "wuffs"}, set(components))
        expected_commits = {
            "dng_sdk": "dbe0a676450d9b8c71bf00688bb306409b779e90",
            "piex": "bb217acdca1cc0c16b704669dd6f91a1b509c406",
            "wuffs": "e3f919ccfe3ef542cfc983a82146070258fb57f8",
        }
        for name, commit in expected_commits.items():
            with self.subTest(name=name):
                component = components[name]
                self.assertEqual(commit, component["upstreamCommit"])
                self.assertEqual("unreviewed", component["reviewStatus"])
                self.assertTrue(component["remaining"])
                for path in component["materialPaths"]:
                    material = next(item for item in registry["materials"] if item["path"] == path)
                    content = (registry_path.parent / path).read_bytes()
                    self.assertEqual(material["sha256"], hashlib.sha256(content).hexdigest())
        dng_platforms = components["dng_sdk"]["binaryEvidence"]
        self.assertIn("Unknown", dng_platforms["windows-x64"])
        self.assertIn("found", dng_platforms["linux-x64"])
        package = registry["packageEvidence"][0]
        self.assertEqual(42388152203, package["workflowRun"])
        self.assertEqual("historical-smoke-artifact-not-final-release-candidate", package["status"])
        self.assertEqual(
            "8596fdd05feffc299febae7ddf8833b85c725fdae66b81d6a03d88e9a91f717a",
            package["sha256"],
        )
        self.assertTrue(any("DNG SDK, Piex, Wuffs, or libffi" in item for item in package["remaining"]))
        self.assertFalse(registry["redistributionApproved"])

    def test_pdfbox_embedded_liberation_font_has_exact_upstream_license_evidence(self):
        root = pathlib.Path(__file__).parents[1]
        registry = json.loads((root / "docs/licensing/third-party/sources.json").read_text())
        asset = next(item for item in registry["embeddedAssets"]
                     if item["name"] == "LiberationSans-Regular.ttf")
        self.assertEqual("org.apache.pdfbox:pdfbox:3.0.8", asset["containerCoordinate"])
        self.assertEqual("76d04c18ea243f426b7de1f3ad208e927008f961dc5945e5aad352d0dfde8ee8",
                         asset["assetSha256"])
        self.assertEqual("OFL-1.1", asset["proposedLicense"])
        self.assertEqual("reviewed", asset["reviewStatus"])
        self.assertEqual([], asset["remaining"])
        review_notes = asset["reviewNotes"]
        self.assertIn("permits bundling the font with software", review_notes)
        self.assertIn("prohibits selling the font by itself", review_notes)
        self.assertIn("does not approve the complete release package", review_notes)
        component = load_supplements(root / "docs/licensing/third-party/sources.json")
        self.assertEqual(64, len(component))
        license_file = root / "docs/licensing/third-party/liberation-fonts-2.1.5/LICENSE"
        self.assertEqual(asset["licenseMaterialPath"],
                         "liberation-fonts-2.1.5/LICENSE")
        self.assertIn(b"SIL OPEN FONT LICENSE Version 1.1", license_file.read_bytes())
        self.assertFalse(registry["redistributionApproved"])

    def test_existing_report_plugin_exports_all_license_alternatives(self):
        root = pathlib.Path(__file__).parents[1]
        build = (root / "build.gradle.kts").read_text()
        workflow = (root / ".github/workflows/license-inventory.yml").read_text()
        self.assertIn('ExtendedJsonReportRenderer("licenses-all.json", false)', build)
        self.assertIn("build/reports/dependency-license/licenses-all.json", workflow)

    def test_jna_retains_pinned_libffi_notice_without_granting_native_clearance(self):
        root = pathlib.Path(__file__).parents[1]
        registry = root / "docs/licensing/third-party/sources.json"
        component = load_supplements(registry)["net.java.dev.jna:jna:5.19.1"]
        notice = next(item for item in component["materials"]
                      if item["path"] == "jna-5.19.1/libffi-LICENSE")
        self.assertEqual("2c9c2acb9743e6b007b91350475308aee44691d96aa20eacef8e199988c8c388",
                         notice["sha256"])
        self.assertIn(component["upstreamCommit"] + "/native/libffi/LICENSE", notice["url"])
        self.assertTrue(notice["content"].startswith(b"libffi - Copyright"))
        self.assertTrue(any("source-file-specific attribution" in item for item in component["remaining"]))
        self.assertTrue(any("libffi provenance" in item for item in component["remaining"]))
        self.assertFalse(json.loads(registry.read_text())["redistributionApproved"])

    def test_collector_includes_pinned_icu_license_for_exact_packaged_artifact(self):
        package, inventory = self.prepare_package()
        artifact_hash = "02f9e8a76ed2f8fa80d53054ca4632c7058354010df27c8f4e7e7443543f7e1e"
        inventory.write_text(
            "packagePath,coordinate,packageSha256,sourceSha256\n"
            f"lib.jar,org.graalvm.shadowed:icu4j:25.4.4.1.1,{NOTICES.sha256(package / 'lib.jar')},{artifact_hash}\n"
        )
        self.license_report.write_text(
            '{"dependencies":[{"moduleName":"org.graalvm.shadowed:icu4j",'
            '"moduleVersion":"25.4.4.1.1","moduleUrls":[],"moduleLicenses":['
            '{"moduleLicense":"Unicode/ICU License",'
            '"moduleLicenseUrl":"https://raw.githubusercontent.com/unicode-org/icu/main/LICENSE"}]}]}'
        )
        root = pathlib.Path(__file__).parents[1]
        registry = root / "docs/licensing/third-party/sources.json"
        output = self.root / "icu-collected"
        report = NOTICES.collect(
            package,
            inventory,
            output,
            self.license_report,
            self.project_license,
            registry,
        )
        component = report["components"][0]
        material = component["supplement"]["materials"][0]
        expected = root / "docs/licensing/third-party/icu4j-78.2/LICENSE"
        self.assertEqual(expected.read_bytes(), (output / material["path"]).read_bytes())
        self.assertEqual("unreviewed", component["reviewStatus"])
        self.assertTrue(component["supplement"]["remaining"])
        self.assertFalse(report["redistributionApproved"])

    def test_collector_includes_pinned_upl_text_for_exact_packaged_artifact(self):
        package, inventory = self.prepare_package()
        artifact_hash = "cbacc2628fd5f99dbcebff7d398bcddde43ea15ebadc873d1da4454c718727d8"
        inventory.write_text(
            "packagePath,coordinate,packageSha256,sourceSha256\n"
            f"lib.jar,org.graalvm.polyglot:polyglot:25.4.4.1.1,{NOTICES.sha256(package / 'lib.jar')},{artifact_hash}\n"
        )
        self.license_report.write_text(
            '{"dependencies":[{"moduleName":"org.graalvm.polyglot:polyglot",'
            '"moduleVersion":"25.4.4.1.1","moduleUrls":[],"moduleLicenses":['
            '{"moduleLicense":"UPL-1.0",'
            '"moduleLicenseUrl":"https://opensource.org/licenses/UPL"}]}]}'
        )
        root = pathlib.Path(__file__).parents[1]
        registry = root / "docs/licensing/third-party/sources.json"
        output = self.root / "upl-collected"
        report = NOTICES.collect(
            package,
            inventory,
            output,
            self.license_report,
            self.project_license,
            registry,
        )
        component = report["components"][0]
        material = component["supplement"]["materials"][0]
        expected = root / "docs/licensing/third-party/graalvm-25.4.4.1.1/UPL-1.0.txt"
        self.assertEqual(expected.read_bytes(), (output / material["path"]).read_bytes())
        self.assertEqual("UPL-1.0", component["supplement"]["proposedLicense"])
        self.assertEqual("unreviewed", component["reviewStatus"])
        self.assertTrue(component["supplement"]["remaining"])
        self.assertFalse(report["redistributionApproved"])

    def prepare_package(self):
        package = self.root / "package"
        package.mkdir()
        runtime_legal = package / "Visual Agent/resources/legal/java-runtime"
        (runtime_legal / "vendor").mkdir(parents=True)
        (runtime_legal / "vendor/readme.txt").write_text("Synthetic source-offer information")
        archive = package / "lib.jar"
        with zipfile.ZipFile(archive, "w") as jar:
            jar.writestr("library.class", b"synthetic bytecode")
        digest = NOTICES.sha256(archive)
        inventory = self.root / "inventory.csv"
        inventory.write_text("packagePath,coordinate,packageSha256,sourceSha256\nlib.jar,example:lib:1," + digest + "," + "d" * 64 + "\n")
        self.license_report = self.root / "licenses-all.json"
        self.license_report.write_text(
            '{"dependencies":[{"moduleName":"example:lib","moduleVersion":"1",'
            '"moduleUrls":[],"moduleLicenses":[{"moduleLicense":"Example",'
            '"moduleLicenseUrl":"https://example.org/license"}]}]}'
        )
        self.project_license = self.root / "LICENSE"
        self.project_license.write_text("Project license")
        self.data["components"][0]["sourceArtifacts"]["example:lib:1"] = "d" * 64
        self.load()
        return package, inventory

    def test_collector_retains_supplement_without_hiding_missing_embedded_notice(self):
        package, inventory = self.prepare_package()
        output = self.root / "collected"
        report = NOTICES.collect(package, inventory, output, self.license_report, self.project_license, self.registry)
        component = report["components"][0]
        self.assertEqual("no-detected-notice", component["noticeStatus"])
        self.assertEqual("unreviewed", component["reviewStatus"])
        self.assertFalse(report["redistributionApproved"])
        self.assertNotEqual(component["packageSha256"], component["supplement"]["sourceSha256"])
        material = component["supplement"]["materials"][0]
        self.assertEqual(self.content, (output / material["path"]).read_bytes())
        self.assertEqual(["Source review"], component["supplement"]["remaining"])

    def test_collector_rejects_supplement_for_different_artifact_bytes(self):
        package, inventory = self.prepare_package()
        self.data["components"][0]["sourceArtifacts"]["example:lib:1"] = "c" * 64
        self.load()
        output = self.root / "collected"
        with self.assertRaisesRegex(ValueError, "exact source artifact"):
            NOTICES.collect(package, inventory, output, self.license_report, self.project_license, self.registry)
        self.assertFalse((output / "manifest.json").exists())

    def test_source_identity_is_required_when_using_supplements(self):
        package, inventory = self.prepare_package()
        lines = inventory.read_text().splitlines()
        inventory.write_text("\n".join(line.rsplit(",", 1)[0] for line in lines) + "\n")
        with self.assertRaisesRegex(ValueError, "exact source artifact"):
            NOTICES.collect(package, inventory, self.root / "collected", self.license_report, self.project_license, self.registry)


if __name__ == "__main__":
    unittest.main()
