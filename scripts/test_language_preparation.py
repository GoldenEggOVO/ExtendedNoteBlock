import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("prepare_paper_language", ROOT / "scripts/prepare_paper_language.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class LanguagePreparationTest(unittest.TestCase):
    def test_wraps_nested_message_once(self):
        source = 'sender.sendMessage("Given " + count + "x " + type.name());'
        expected = 'sender.sendMessage(EnbLanguage.text("Given " + count + "x " + type.name()));'
        self.assertEqual(expected, module.prepare(source))
        self.assertEqual(expected, module.prepare(module.prepare(source)))

    def test_keeps_other_calls_unchanged(self):
        source = 'logger.info("starting"); player.sendMessage("ready");'
        self.assertEqual('logger.info("starting"); player.sendMessage(EnbLanguage.text("ready"));',
                         module.prepare(source))


if __name__ == '__main__':
    unittest.main()
