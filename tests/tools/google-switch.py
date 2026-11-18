#!/usr/bin/env python3
"""Build and exercise provider switching against local files and a fake package CLI."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import unittest
import zipfile


REPO = Path(__file__).resolve().parents[2]
SERVICES = "com.google.android.gms"
STORE = "com.android.vending"
FRAMEWORK = "com.google.android.gsf"
MICROG = "9bd06727e62796c0130eb6dab39b73157451582cbd138e86c468acc395d14165"
GOOGLE = "7CE83C1B71F3D572FED04C8D40C5CB10FF75E6D87D9DF6FBD53F0468C2905053"

FAKE_CLI = """#!/usr/bin/env python3
import os
from pathlib import Path
import shutil
import sys

assert sys.argv[1:3] == ["--instance", "switch-fixture"]
action = sys.argv[3]
root = Path(os.environ["SWITCH_FIXTURE"])
apps = root / "data/apklayer/apps"
if action == "uninstall":
    package = sys.argv[4]
else:
    assert action == "install"
    info = Path(sys.argv[-1]).read_text()
    package = info.splitlines()[0].removeprefix("package=")
with (root / "calls").open("a") as calls:
    calls.write(f"{action} {package}\\n")
failure = root / "fail-once"
if failure.exists() and failure.read_text() == f"{action} {package}":
    failure.unlink()
    sys.exit(1)
if action == "uninstall":
    shutil.rmtree(apps / package)
else:
    installed = apps / package / "info.properties"
    if installed.exists():
        assert installed.read_text().splitlines()[1] == info.splitlines()[1]
    installed.parent.mkdir(parents=True, exist_ok=True)
    installed.write_text(info)
"""


def metadata(package, signer, version):
    return f"package={package}\nsigner={signer}\nversionCode={version}\n"


class SwitchTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        build = tempfile.TemporaryDirectory(prefix="apklayer-switch-build-")
        cls.addClassCleanup(build.cleanup)
        cls.driver = Path(build.name) / "google-switch"
        subprocess.run([
            "c++", "-std=c++17", "-ffunction-sections", "-fdata-sections", "-Wl,--gc-sections",
            "-Icli", "-Icommon", "-Ibridge/hals/include", "-o", cls.driver,
            "tests/tools/google-switch.cpp", "cli/gapps.cpp", "cli/util.cpp",
            "common/downloads.cpp", "common/fetch.cpp", "common/json.cpp", "common/proc.cpp",
            "common/releases.cpp",
        ], cwd=REPO, check=True)

    def setUp(self):
        fixture = tempfile.TemporaryDirectory(prefix="apklayer-switch-fixture-")
        self.addCleanup(fixture.cleanup)
        self.root = Path(fixture.name)
        self.apps = self.root / "data/apklayer/apps"
        self.marker = self.root / "data/apklayer/google-apps/installed"
        (self.root / "tools").mkdir()
        shutil.copyfile(REPO / "tools/release/gapps.sh", self.root / "tools/gapps.sh")
        self.fake = self.root / "fake-cli"
        self.fake.write_text(FAKE_CLI)
        self.fake.chmod(0o700)
        (self.root / "downloads.json").write_text('{"fixture": true}')
        self.make_bundle()
        self.make_index()

    def make_bundle(self, include_store=True, include_config=True, store_package=STORE):
        archive = self.root / "files.tar.xz"
        with tarfile.open(archive, "w:xz") as tar:
            for name, package in [("GoogleServicesFramework", FRAMEWORK), ("GmsCore", SERVICES),
                                  ("Phonesky", STORE)]:
                if package == STORE and not include_store:
                    continue
                source = self.root / f"{name}.apk"
                source.write_text(metadata(store_package if package == STORE else package, GOOGLE, 100))
                tar.add(source, f"cpu/sdk/system/product/app/{name}/{name}.apk")
            if include_config:
                config = self.root / "google.xml"
                config.write_text("<config/>")
                tar.add(config, "cpu/sdk/system/product/etc/sysconfig/google.xml")
        self.bundle = self.root / "bundle.zip"
        with zipfile.ZipFile(self.bundle, "w") as bundle:
            bundle.write(archive, "files/files.tar.xz")

    def make_index(self):
        packages = {}
        for package in [SERVICES, STORE]:
            apk = self.root / f"{package}.apk"
            apk.write_text(metadata(package, MICROG, 10))
            packages[package] = [{
                "apkName": apk.name, "hashType": "sha256",
                "hash": hashlib.sha256(apk.read_bytes()).hexdigest(), "versionCode": 10,
            }]
        (self.root / "index.json").write_text(json.dumps({
            "repo": {"address": self.root.as_uri()}, "packages": packages,
        }))

    def seed(self, package, signer, version=1000):
        info = self.apps / package / "info.properties"
        info.parent.mkdir(parents=True, exist_ok=True)
        info.write_text(metadata(package, signer, version))

    def switch(self, provider, success=True):
        result = subprocess.run([self.driver, self.root, provider], check=False, capture_output=True,
                                text=True, env={
            **os.environ, "APKLAYER_CLI": str(self.fake), "SWITCH_FIXTURE": str(self.root),
            "XDG_CONFIG_HOME": str(self.root / "config"), "APKLAYER_GAPPS_URL": self.bundle.as_uri(),
            "APKLAYER_GAPPS_SHA256": hashlib.sha256(self.bundle.read_bytes()).hexdigest(),
        })
        self.assertEqual(result.returncode, 0 if success else 1, result.stdout + result.stderr)
        return result.stderr

    def test_interrupted_companion_uninstall(self):
        # Resume a legacy switch that already removed Services.
        self.seed(STORE, MICROG)
        (self.root / "fail-once").write_text(f"uninstall {STORE}")
        self.switch("gapps", success=False)
        self.assertFalse(self.marker.exists())
        self.switch("gapps")
        self.assertTrue(self.marker.exists())
        calls = (self.root / "calls").read_text()
        self.assertEqual(calls.count(f"uninstall {STORE}\n"), 2)
        self.assertTrue(calls.endswith(f"uninstall {STORE}\ninstall {STORE}\n"))
        self.switch("gapps")
        self.assertEqual((self.root / "calls").read_text(), calls)

    def test_unknown_shared_package_blocks_switch(self):
        self.seed(SERVICES, MICROG)
        self.seed(STORE, "unexpected")
        for provider in ["gapps", "microg"]:
            self.assertIn(STORE, self.switch(provider, success=False))
            self.assertFalse((self.root / "calls").exists())

    def test_missing_gapps_payload_keeps_microg(self):
        self.seed(SERVICES, MICROG)
        self.seed(STORE, MICROG)
        self.make_bundle(include_store=False)
        self.assertIn("Phonesky.apk", self.switch("gapps", success=False))
        self.assertFalse((self.root / "calls").exists())

    def test_gapps_unpack_failure_preserves_microg_until_retry(self):
        provider_data = b"existing microG data"
        for package in [SERVICES, STORE]:
            self.seed(package, MICROG)
            (self.apps / package / "fixture-data").write_bytes(provider_data)

        # switch() pins the hash of these bytes so the failure happens in the unpacker.
        self.bundle.write_bytes(b"not a ZIP archive")
        self.assertIn("Unpacking the Google apps failed", self.switch("gapps", success=False))
        self.assertFalse((self.root / "calls").exists())
        self.assertFalse(self.marker.exists())
        for package in [SERVICES, STORE]:
            installed = self.apps / package
            self.assertEqual((installed / "info.properties").read_text(), metadata(package, MICROG, 1000))
            self.assertEqual((installed / "fixture-data").read_bytes(), provider_data)

        self.make_bundle()
        self.switch("gapps")
        self.assertEqual(self.marker.read_text().strip(), hashlib.sha256(self.bundle.read_bytes()).hexdigest())
        for package in [SERVICES, STORE, FRAMEWORK]:
            self.assertEqual((self.apps / package / "info.properties").read_text(), metadata(package, GOOGLE, 100))

    def test_missing_config_preserves_existing_marker_and_config(self):
        self.seed(SERVICES, MICROG)
        self.seed(STORE, MICROG)
        etc = self.marker.parent / "etc/sysconfig"
        etc.mkdir(parents=True)
        original = etc / "google.xml"
        original.write_text("<config>old</config>")
        self.marker.write_text("old-bundle-digest")
        self.make_bundle(include_config=False)
        self.assertIn("no supported configuration XML", self.switch("gapps", success=False))
        self.assertEqual(self.marker.read_text(), "old-bundle-digest")
        self.assertEqual(original.read_text(), "<config>old</config>")
        self.assertFalse((self.root / "calls").exists())

    def test_mislabeled_store_does_not_complete_switch(self):
        self.make_bundle(store_package="org.example.unrelated")
        self.assertIn(STORE, self.switch("gapps", success=False))
        self.assertFalse(self.marker.exists())

    def test_microg_staging_and_retry(self):
        for package in [SERVICES, STORE, FRAMEWORK]:
            self.seed(package, GOOGLE)
        (self.root / f"{STORE}.apk").unlink()
        self.switch("microg", success=False)
        self.assertFalse((self.root / "calls").exists())
        self.make_index()
        (self.root / "fail-once").write_text(f"install {STORE}")
        self.switch("microg", success=False)
        self.assertTrue((self.apps / FRAMEWORK).exists())
        self.switch("microg")
        calls = (self.root / "calls").read_text()
        self.assertEqual(calls.count(f"uninstall {SERVICES}\n"), 1)
        self.assertTrue(calls.endswith(f"install {STORE}\nuninstall {FRAMEWORK}\n"))
        self.assertFalse((self.apps / FRAMEWORK).exists())


if __name__ == "__main__":
    unittest.main()
