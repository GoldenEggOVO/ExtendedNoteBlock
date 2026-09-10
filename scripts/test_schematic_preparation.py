from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
MAIN = Path('bridge/src/main/java/com/goldenegggovo/extendednoteblock/bridge/ExtendedNoteBlockBridge.java')

class SchematicPreparationTest(unittest.TestCase):
    def test_full_release_preparation_retains_schematic_transport_on_second_run(self):
        self.assertTrue((ROOT / 'scripts/prepare_paper_schematic.py').exists(), 'Schematic integration is missing')
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for folder in ('bridge/src', 'scripts', 'craftengine'):
                shutil.copytree(ROOT / folder, root / folder)
            def prepare():
                for stage in ('custom_model_data', 'interactions', 'render_sync', 'listener_pack', 'craftengine', 'schematic'):
                    result = subprocess.run([sys.executable, str(root / f'scripts/prepare_paper_{stage}.py')], capture_output=True, text=True, encoding='utf-8')
                    self.assertEqual(0, result.returncode, result.stderr)
            prepare()
            first = (root / MAIN).read_bytes()
            prepare()
            self.assertEqual(first, (root / MAIN).read_bytes())
            self.assertEqual(1, first.decode().count('new BridgeSchematicTransfer(this,'))
            self.assertIn('captureSchematic', first.decode())

if __name__ == '__main__':
    unittest.main()
