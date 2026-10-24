from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = Path('bridge/src/main/java/com/goldenegggovo/extendednoteblock/bridge')


class OptionalAudioPreparationTest(unittest.TestCase):
    def test_complete_generation_is_localized_idempotent_and_preserves_preferences(self):
        self.assertTrue((ROOT / 'scripts/prepare_paper_optional_audio.py').is_file())
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for folder in ('bridge/src', 'scripts', 'craftengine'):
                shutil.copytree(ROOT / folder, root / folder)
            preferences = root / 'plugins/ExtendedNoteBlockBridge/optional-audio-players.txt'
            preferences.parent.mkdir(parents=True)
            preferences.write_text('7056a49b-8a6d-4ce5-b4c8-33bf8d59889b\n')
            def prepare(stages):
                for stage in stages:
                    result = subprocess.run([sys.executable, str(root / f'scripts/prepare_paper_{stage}.py')],
                                            capture_output=True, text=True, encoding='utf-8')
                    self.assertEqual(0, result.returncode, result.stderr)
            stages = ('custom_model_data', 'interactions', 'render_sync', 'listener_pack',
                      'craftengine', 'schematic', 'optional_audio', 'language')
            prepare(stages)
            first = {p.name: p.read_bytes() for p in (root / JAVA).glob('*.java')}
            prepare(stages)
            self.assertEqual(first, {p.name: p.read_bytes() for p in (root / JAVA).glob('*.java')})
            prepare(('optional_audio', 'language'))
            self.assertEqual(first, {p.name: p.read_bytes() for p in (root / JAVA).glob('*.java')})
            source = first['ExtendedNoteBlockBridge.java'].decode()
            self.assertEqual(1, source.count('private OptionalAudioPreferences optionalAudioPreferences;'))
            self.assertEqual(1, source.count('new OptionalAudioPreferences('))
            self.assertEqual(1, source.count('private boolean handleAudioCommand('))
            self.assertEqual(1, source.count('combinedPack.acceptsAudioStatus(event.getPlayer())'))
            self.assertIn('!getConfig().getBoolean("resource-pack.optional-audio.enabled", false)), 40L)', source)
            self.assertIn('sender.sendMessage(EnbLanguage.text("This command requires a player."))', source)
            self.assertIn('player.sendMessage(EnbLanguage.text("ENB optional audio: "', source)
            self.assertIn('EnbLanguage.load(this);', source)
            self.assertIn('combinedPack.forget(event.getPlayer().getUniqueId())', source)
            self.assertIn('new BridgeSchematicTransfer(this,', source)
            self.assertTrue((root / JAVA / 'OptionalAudioPack.java').is_file())
            self.assertTrue((root / JAVA / 'OptionalAudioPreferences.java').is_file())
            combined = first['EnbCombinedPack.java'].decode()
            self.assertIn('engine.dataFolderPath()', combined)
            self.assertIn('resource-pack.delivery.resend-on-upload', combined)
            self.assertIn('Key.ce("send_pack")', combined)
            self.assertIn('engine.packManager().workflowNames()', combined)
            self.assertIn('Bukkit.getPlayer(p.getUniqueId())!=p', combined)
            self.assertIn('7056a49b-8a6d-4ce5-b4c8-33bf8d59889b', combined)
            self.assertIn('a452911d-2d41-4db4-8986-d9b2b8f8aeb0', combined)
            self.assertIn('"Optional ENB audio"', combined)
            self.assertEqual('7056a49b-8a6d-4ce5-b4c8-33bf8d59889b\n', preferences.read_text())

    def test_optional_audio_messages_have_english_source_and_chinese_translations(self):
        english = (ROOT / 'bridge/src/main/resources/lang/en_us.yml').read_text(encoding='utf-8')
        chinese = (ROOT / 'bridge/src/main/resources/lang/zh_cn.yml').read_text(encoding='utf-8')
        for message in ('Optional ENB audio', 'ENB optional audio is currently unavailable.',
                        'ENB optional audio: {0}; load status: {1}',
                        'Could not save audio preferences. Contact the server owner.',
                        'Optional ENB audio enabled; accept the audio resource pack.',
                        'ENB audio disabled; the base resource pack is retained.',
                        'Complete login authentication before changing ENB audio.',
                        'ENB: Disable CraftEngine automatic pack delivery (send-on-join, resend-on-upload and reload send_pack steps) to avoid duplicate requests.'):
            self.assertIn('"' + message + '": "' + message + '"', english)
            self.assertIn('"' + message + '": "', chinese)


if __name__ == '__main__':
    unittest.main()
