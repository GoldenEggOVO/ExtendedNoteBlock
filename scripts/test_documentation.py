import importlib.util
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("check_documentation.py")
SPEC = importlib.util.spec_from_file_location("check_documentation", MODULE_PATH)
module = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(module)


class DocumentationTest(unittest.TestCase):
    def test_github_slug_keeps_chinese_and_removes_punctuation(self):
        self.assertEqual("paper-服务器资源包", module.github_slug("Paper 服务器资源包"))
        self.assertEqual("paper-purpur", module.github_slug("Paper / Purpur"))

    def test_versions_come_from_build_configuration(self):
        mod_version, server_version, minecraft_version = module.current_versions()
        self.assertRegex(mod_version, r"^\d+\.\d+\.\d+$")
        self.assertRegex(server_version, r"^\d+\.\d+\.\d+$")
        self.assertRegex(minecraft_version, r"^\d+\.\d+(?:\.\d+)?$")

    def test_repository_documentation_is_consistent(self):
        self.assertEqual([], module.collect_errors())


if __name__ == "__main__":
    unittest.main()
