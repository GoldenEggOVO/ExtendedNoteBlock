from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
MAIN = Path('bridge/src/main/java/com/goldenegggovo/extendednoteblock/bridge/ExtendedNoteBlockBridge.java')


class PerformancePreparationTest(unittest.TestCase):
    def test_complete_chain_keeps_loaded_work_set_on_second_run(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for folder in ('bridge/src', 'scripts', 'craftengine'):
                shutil.copytree(ROOT / folder, root / folder)
            stages = ('custom_model_data', 'interactions', 'render_sync', 'listener_pack',
                      'craftengine', 'schematic', 'optional_audio', 'performance', 'language')
            def prepare():
                for stage in stages:
                    result = subprocess.run([sys.executable, str(root / f'scripts/prepare_paper_{stage}.py')],
                                            capture_output=True, text=True, encoding='utf-8')
                    self.assertEqual(0, result.returncode, result.stderr)
            prepare()
            first = (root / MAIN).read_bytes()
            prepare()
            self.assertEqual(first, (root / MAIN).read_bytes())
            source = first.decode('utf-8')
            self.assertIn('craftEngine.sync(key, type, next, block)', source)
            self.assertIn('renderIndex.activeKeys()', source)
            self.assertNotIn('new HashSet<>(lastRenderStates.keySet())', source)
            self.assertEqual(1, source.count('void onRenderChunkLoad('))
            workflow = (ROOT / '.github/workflows/build-26.3.yml').read_text(encoding='utf-8')
            block = workflow.split('      - name: Verify vanilla-safe identity, interaction, render and listener bridge\n', 1)[1]
            block = block.split('      - name:', 1)[0].split('        run: |\n', 1)[1]
            commands = '\n'.join(line[10:] for line in block.splitlines() if line.strip())
            bash = shutil.which('bash')
            if sys.platform == 'win32' and Path('C:/Program Files/Git/bin/bash.exe').exists():
                bash = 'C:/Program Files/Git/bin/bash.exe'
            self.assertIsNotNone(bash, 'Bash is required to validate the actual CI bridge checks')
            result = subprocess.run([bash, '-e', '-c', commands], cwd=root / 'bridge', capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)


if __name__ == '__main__':
    unittest.main()
