"""Ensure optimized base source still passes through the complete release generator."""
from pathlib import Path
import hashlib
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
RELATIVE_SOURCE = Path('bridge/src/main/java/com/goldenegggovo/extendednoteblock/bridge/ExtendedNoteBlockBridge.java')
STEPS = ('prepare_paper_custom_model_data.py', 'prepare_paper_interactions.py',
         'prepare_paper_render_sync.py', 'prepare_paper_listener_pack.py')

class PaperPreparationTest(unittest.TestCase):
    def test_release_generation_preserves_features_and_is_repeatable(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / RELATIVE_SOURCE
            source.parent.mkdir(parents=True)
            shutil.copy2(ROOT / RELATIVE_SOURCE, source)
            (root / 'scripts').mkdir()
            for name in (*STEPS, 'prepare_paper_command_help.py'):
                shutil.copy2(ROOT / 'scripts' / name, root / 'scripts' / name)
            def prepare():
                for name in STEPS:
                    result = subprocess.run([sys.executable, str(root / 'scripts' / name)], capture_output=True, text=True, encoding='utf-8')
                    self.assertEqual(0, result.returncode, result.stderr)
            prepare()
            generated = source.read_text(encoding='utf-8')
            for feature in ('class ExtendedNoteBlockBridge', 'record ListenerPlayback', 'record RenderObjectState',
                            'registerIncomingPluginChannel(this, NOTE_SAVE', 'customModelData.setStrings',
                            'ListenerSoundResolver.resolve', 'ListenerResourcePackConfig.resolve',
                            'syncChangedRenderStates();', 'flushPendingSaves()', 'void startTickers()',
                            'public List<String> onTabComplete'):
                self.assertIn(feature, generated)
            self.assertNotIn('        tickProjectionSessions();', generated)
            self.assertLess(generated.index('if (!flushPendingSaves())'), generated.index('                reloadConfig();'))
            digest = hashlib.sha256(source.read_bytes()).hexdigest()
            prepare()
            self.assertEqual(digest, hashlib.sha256(source.read_bytes()).hexdigest())

if __name__ == '__main__':
    unittest.main()
