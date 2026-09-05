import importlib.util
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "configure_release_resource_pack", ROOT / "scripts" / "configure_release_resource_pack.py"
)
module = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = module
SPEC.loader.exec_module(module)


class ConfigureReleaseResourcePackTest(unittest.TestCase):
    def make_jar(self, directory: Path, config: str | None = None,
                 release_metadata: str | None = None) -> Path:
        path = directory / "server.jar"
        with zipfile.ZipFile(path, "w") as jar:
            jar.writestr("plugin.yml", "name: test\n")
            if config is not None:
                jar.writestr("config.yml", config)
            if release_metadata is not None:
                jar.writestr(module.RELEASE_METADATA, release_metadata)
            jar.writestr("example.bin", b"unchanged")
        return path

    def test_rewrites_only_release_placeholders(self):
        with tempfile.TemporaryDirectory() as temporary:
            jar = self.make_jar(
                Path(temporary),
                f'url: "{module.URL_TOKEN}"\nsha1: "{module.SHA1_TOKEN}"\n',
                f'url={module.URL_TOKEN}\nsha1={module.SHA1_TOKEN}\n',
            )
            url = "https://github.com/example/project/releases/download/v1/pack.zip"
            digest = "ABCDEF0123456789ABCDEF0123456789ABCDEF01"

            module.configure_jar(jar, url, digest)

            with zipfile.ZipFile(jar) as configured:
                config = configured.read("config.yml").decode()
                metadata = configured.read(module.RELEASE_METADATA).decode()
                self.assertIn(url, config)
                self.assertIn(digest.lower(), config)
                self.assertIn(url, metadata)
                self.assertIn(digest.lower(), metadata)
                self.assertEqual(b"unchanged", configured.read("example.bin"))

    def test_rejects_bad_hash_url_and_missing_placeholders(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            valid_config = f"{module.URL_TOKEN}\n{module.SHA1_TOKEN}\n"
            valid_metadata = f"{module.URL_TOKEN}\n{module.SHA1_TOKEN}\n"
            with self.assertRaises(ValueError):
                module.configure_jar(self.make_jar(directory, valid_config, valid_metadata), "http://example/pack.zip", "0" * 40)
            with self.assertRaises(ValueError):
                module.configure_jar(self.make_jar(directory, valid_config, valid_metadata), "https://example/pack.zip", "bad")
            with self.assertRaises(ValueError):
                module.configure_jar(self.make_jar(directory, "url: none\n", valid_metadata), "https://example/pack.zip", "0" * 40)

    def test_rejects_jar_without_release_metadata(self):
        with tempfile.TemporaryDirectory() as temporary:
            valid_config = f"{module.URL_TOKEN}\n{module.SHA1_TOKEN}\n"
            with self.assertRaises(KeyError):
                module.configure_jar(
                    self.make_jar(Path(temporary), valid_config),
                    "https://example/pack.zip", "0" * 40,
                )

    def test_rejects_jar_without_config(self):
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaises(KeyError):
                module.configure_jar(
                    self.make_jar(Path(temporary)), "https://example/pack.zip", "0" * 40
                )


if __name__ == "__main__":
    unittest.main()
