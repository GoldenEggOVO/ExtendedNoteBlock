import importlib.util
import json
import re
import sys
import unittest
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

    def test_octave_anchors_keep_full_midi_range_inside_vanilla_pitch_limit(self):
        for note in range(128):
            anchor = min(module.MELODIC_ANCHORS, key=lambda value: abs(value - note))
            pitch = 2.0 ** ((note - anchor) / 12.0)
            self.assertGreaterEqual(pitch, 0.5)
            self.assertLessEqual(pitch, 2.0)

    def test_sound_events_cover_all_programs_anchors_drums_and_voices(self):
        events = module.sound_events()
        expected = (128 * len(module.MELODIC_ANCHORS) + len(module.DRUM_NOTES)) * module.VOICE_ALIASES
        self.assertEqual(expected, len(events))
        self.assertIn("notes.0.0.v0", events)
        self.assertIn("notes.127.120.v7", events)
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


if __name__ == "__main__":
    unittest.main()
