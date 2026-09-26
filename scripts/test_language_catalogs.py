import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANG = ROOT / "src/main/resources/assets/extendednoteblock/lang"
SERVER_LANG = ROOT / "bridge/src/main/resources/lang"


class LanguageCatalogTest(unittest.TestCase):
    def test_client_has_matching_english_and_chinese_keys(self):
        self.assertEqual({"en_us.json", "zh_cn.json"}, {path.name for path in LANG.glob("*.json")})
        english = json.loads((LANG / "en_us.json").read_text(encoding="utf-8"))
        chinese = json.loads((LANG / "zh_cn.json").read_text(encoding="utf-8"))
        self.assertEqual(english.keys(), chinese.keys())
        self.assertFalse(any(re.search(r"[\u3400-\u9fff]", value) for value in english.values()))

    def test_paper_language_files_cover_same_messages(self):
        def keys(path):
            return set(re.findall(r'^  ("(?:\\.|[^"\\])*"):',
                                  path.read_text(encoding="utf-8"), re.MULTILINE))
        self.assertEqual(keys(SERVER_LANG / "en_us.yml"), keys(SERVER_LANG / "zh_cn.yml"))
        self.assertGreater(len(keys(SERVER_LANG / "en_us.yml")), 100)


if __name__ == "__main__":
    unittest.main()
