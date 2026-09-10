from pathlib import Path
import subprocess, sys, tempfile, shutil, unittest, zipfile, json
ROOT = Path(__file__).resolve().parents[1]
MAIN = Path('bridge/src/main/java/com/goldenegggovo/extendednoteblock/bridge/ExtendedNoteBlockBridge.java')
class CraftEnginePreparationTest(unittest.TestCase):
    def test_complete_generation_is_idempotent_and_preserves_existing_data(self):
        self.assertTrue((ROOT/'scripts/prepare_paper_craftengine.py').is_file(), 'CraftEngine preparation is missing')
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            shutil.copytree(ROOT/'bridge/src',root/'bridge/src')
            shutil.copytree(ROOT/'scripts',root/'scripts')
            shutil.copytree(ROOT/'craftengine',root/'craftengine')
            data=root/'plugins/ExtendedNoteBlockBridge/notes.yml'; data.parent.mkdir(parents=True); data.write_text('existing music')
            steps=['custom_model_data','interactions','render_sync','listener_pack','craftengine']
            def prepare():
                for step in steps:
                    result=subprocess.run([sys.executable,str(root/f'scripts/prepare_paper_{step}.py')],capture_output=True,text=True,encoding='utf-8')
                    self.assertEqual(0,result.returncode,result.stderr)
            prepare(); first=(root/MAIN).read_bytes(); prepare()
            self.assertEqual(first,(root/MAIN).read_bytes())
            self.assertEqual('existing music',data.read_text())
            text=first.decode(); self.assertIn('craftEngine.sync(entry.getKey(), type, next)',text)
            self.assertIn('getBoolean("craftengine.enabled", true)',text)
            self.assertEqual(1,text.count('EnbCraftEngine craftEngine;'))
    def test_copy_hooks_preserve_foreign_blocks_and_ignore_migration_budget(self):
        source=(ROOT/'craftengine/integration/EnbCraftEngine.java').read_text(encoding='utf-8')
        self.assertIn('boolean acceptsCopy(Block b,BridgeItemType type)',source)
        self.assertIn('boolean placeCopy(Block b,BridgeItemType type,int midi)',source)
    def test_public_metadata_requires_craftengine_and_defaults_to_safe_migration(self):
        descriptor=(ROOT/'bridge/src/main/resources/plugin.yml').read_text(encoding='utf-8')
        self.assertIn('depend: [CraftEngine]',descriptor)
        config=(ROOT/'bridge/src/main/resources/config.yml').read_text(encoding='utf-8')
        self.assertIn('craftengine:\n  enabled: true',config)
        self.assertIn('migrate-known-blocks: true',config)
        self.assertNotIn('combined-file:',config)
if __name__=='__main__': unittest.main()
