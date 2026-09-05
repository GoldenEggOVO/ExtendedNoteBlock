"""Regression cases for the published 2.8.0 IllegalClassLoadError."""

import io
import json
import unittest
import zipfile

from verify_mixin_packages import verify_mixin_packages


class MixinPackageTest(unittest.TestCase):
    def check_layout(self, package, extra_classes=(), missing_mixin=False):
        data = io.BytesIO()
        with zipfile.ZipFile(data, "w") as jar:
            jar.writestr("fabric.mod.json", json.dumps({
                "entrypoints": {"client": ["example.bridge.Client"]},
                "mixins": [{"config": "bridge.mixins.json", "environment": "client"}],
            }))
            jar.writestr("bridge.mixins.json", json.dumps({
                "package": package, "client": ["SoundMixin"],
            }))
            for name in ("example.bridge.Client", *extra_classes):
                jar.writestr(name.replace(".", "/") + ".class", b"")
            if not missing_mixin:
                jar.writestr(package.replace(".", "/") + "/SoundMixin.class", b"")
            verify_mixin_packages(jar)

    def test_rejects_280_entrypoint_collision(self):
        with self.assertRaisesRegex(ValueError, "entrypoint.*Mixin-owned"):
            self.check_layout("example.bridge")

    def test_rejects_ordinary_classes_in_child_packages(self):
        with self.assertRaisesRegex(ValueError, "ordinary classes"):
            self.check_layout("example.bridge.mixin", ["example.bridge.mixin.helpers.Screen"])

    def test_requires_declared_class_to_be_packaged(self):
        with self.assertRaisesRegex(ValueError, "missing mixin"):
            self.check_layout("example.bridge.mixin", missing_mixin=True)

    def test_accepts_isolated_mixin_and_its_inner_class(self):
        self.check_layout("example.bridge.mixin", [
            "example.bridge.mixin.SoundMixin$Inner", "example.bridge.mixinextras.Screen",
        ])


if __name__ == "__main__":
    unittest.main()
