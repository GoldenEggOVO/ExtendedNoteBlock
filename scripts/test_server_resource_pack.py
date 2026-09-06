import importlib.util
import json
import re
import struct
import sys
import tempfile
import unittest
import wave
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "make_server_resource_pack", ROOT / "scripts" / "make_server_resource_pack.py"
)
module = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = module
SPEC.loader.exec_module(module)


class ServerResourcePackTest(unittest.TestCase):
    def test_program_mapping_preserves_every_gm_id_with_32_physical_timbres(self):
        mapped = {program: module.representative_for(program) for program in range(128)}
        self.assertEqual(set(range(0, 128, 4)), set(mapped.values()))
        for program, representative in mapped.items():
            self.assertLessEqual(representative, program)
            self.assertLess(program - representative, 4)

    def test_half_octave_anchors_keep_full_midi_range_with_small_pitch_shifts(self):
        for note in range(128):
            anchor = min(module.MELODIC_ANCHORS, key=lambda value: abs(value - note))
            pitch = 2.0 ** ((note - anchor) / 12.0)
            self.assertGreaterEqual(pitch, 2.0 ** (-3.0 / 12.0))
            self.assertLessEqual(pitch, 2.0 ** (3.0 / 12.0))

    def test_sound_events_cover_all_programs_anchors_drums_and_voices(self):
        events = module.sound_events()
        expected = (128 * len(module.MELODIC_ANCHORS) + len(module.DRUM_NOTES)) * module.VOICE_ALIASES
        self.assertEqual(expected, len(events))
        self.assertIn("notes.0.0.v0", events)
        self.assertIn("notes.127.126.v7", events)
        self.assertIn("notes.128.35.v0", events)
        self.assertIn("notes.128.81.v7", events)
        # Program 7 shares physical program 4 without duplicating the OGG.
        shared = events["notes.7.60.v0"]["sounds"][0]["name"]
        self.assertEqual("extendednoteblock_listener:melodic/4.60", shared)
        self.assertEqual(
            module.ATTENUATION_DISTANCE,
            events["notes.7.60.v0"]["sounds"][0]["attenuation_distance"],
        )

    def test_java_resolver_and_pack_schema_use_the_same_alias_count(self):
        source = (ROOT / "bridge" / "src" / "main" / "java" / "com" / "goldenegggovo"
                  / "extendednoteblock" / "bridge" / "ListenerSoundResolver.java").read_text()
        match = re.search(r"VOICE_ALIASES\s*=\s*(\d+)", source)
        self.assertIsNotNone(match)
        self.assertEqual(module.VOICE_ALIASES, int(match.group(1)))

    def test_pack_metadata_matches_minecraft_26_2(self):
        meta = module.pack_metadata("test")
        self.assertEqual(module.RESOURCE_PACK_FORMAT, meta["pack"]["pack_format"])
        self.assertEqual(module.RESOURCE_PACK_FORMAT, meta["pack"]["min_format"])
        self.assertEqual(module.RESOURCE_PACK_FORMAT, meta["pack"]["max_format"])
        self.assertEqual("26.2", module.metadata(False)["minecraft"])
        self.assertEqual(6, module.metadata(False)["anchor_step_semitones"])
        self.assertEqual(5, module.metadata(False)["ogg_vorbis_quality"])
        self.assertEqual(50_000_000, module.metadata(False)["maximum_pack_bytes"])
        self.assertEqual(30_000, module.metadata(False)["sample_peak_normalization"]["maximum_encoded_pcm16"])

    def test_built_server_pack_keeps_world_blocks_unmodified(self):
        tasks = module.render_tasks(smoke=True)
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            ogg_dir = root / "ogg"
            ogg_dir.mkdir()
            for task in tasks:
                (ogg_dir / task.ogg_name).write_bytes(b"OggS-test-fixture")
            output = root / "server-resources.zip"
            module.build_zip(tasks, ogg_dir, output, smoke=True)
            module.validate(output, tasks, smoke=True)

            with zipfile.ZipFile(output) as pack:
                names = set(pack.namelist())
                self.assertNotIn("assets/minecraft/blockstates/note_block.json", names)
                self.assertNotIn(f"assets/{module.NAMESPACE}/models/block/enb.json", names)
                self.assertNotIn(f"assets/{module.NAMESPACE}/models/block/enb_on.json", names)
                self.assertIn("assets/minecraft/items/note_block.json", names)

    def test_quiet_edge_samples_receive_bounded_normalization(self):
        self.assertEqual(module.MAX_NORMALIZATION_GAIN, module.normalization_gain(1))
        self.assertEqual(1.0, module.normalization_gain(module.TARGET_SOURCE_PEAK))
        self.assertEqual(0.5, module.normalization_gain(module.TARGET_SOURCE_PEAK * 2))
        with self.assertRaises(ValueError):
            module.normalization_gain(0)

    def test_listener_peak_uses_centered_main_channel_without_phase_cancellation(self):
        with tempfile.TemporaryDirectory() as temporary:
            wav = Path(temporary) / "opposite-phase.wav"
            with wave.open(str(wav), "wb") as output:
                output.setnchannels(2)
                output.setsampwidth(2)
                output.setframerate(44_100)
                output.writeframes(struct.pack("<hhhh", 1_000, -1_000, -750, 750))
            self.assertEqual(1_000, module.listener_channel_peak_pcm16(wav))

    def test_transient_only_edge_anchor_uses_nearest_healthy_octave(self):
        low = module.RenderTask(0, 0, 0, "low")
        low_healthy = module.RenderTask(0, 0, 12, "low-healthy")
        high_healthy = module.RenderTask(0, 0, 108, "high-healthy")
        high = module.RenderTask(0, 0, 120, "high")
        tasks = [low, low_healthy, high_healthy, high]
        peaks = {low: 135, low_healthy: 4_000, high_healthy: 4_000, high: 204}

        source, factor = module.choose_source(low, tasks, peaks)
        self.assertEqual(low_healthy, source)
        self.assertEqual(0.5, factor)
        source, factor = module.choose_source(high, tasks, peaks)
        self.assertEqual(high_healthy, source)
        self.assertEqual(2.0, factor)

    def test_extreme_anchors_are_generated_from_the_healthy_source_window(self):
        low = module.RenderTask(0, 20, 0, "low")
        low_source = module.RenderTask(0, 20, 12, "low-source")
        high_source = module.RenderTask(0, 20, 120, "high-source")
        high = module.RenderTask(0, 20, 126, "high")
        tasks = [low, low_source, high_source, high]
        peaks = {task: 4_000 for task in tasks}

        source, factor = module.choose_source(low, tasks, peaks)
        self.assertEqual(low_source, source)
        self.assertEqual(0.5, factor)
        source, factor = module.choose_source(high, tasks, peaks)
        self.assertEqual(high_source, source)
        self.assertAlmostEqual(2.0 ** 0.5, factor)


if __name__ == "__main__":
    unittest.main()
