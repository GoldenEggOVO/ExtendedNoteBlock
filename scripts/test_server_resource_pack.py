import importlib.util
import json
import re
import sys
import tempfile
import unittest
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

    def test_only_two_reserved_note_block_states_select_enb_models(self):
        blockstate = json.loads(module.listener_note_blockstate())
        variants = blockstate["variants"]
        self.assertEqual(len(module.NOTE_BLOCK_INSTRUMENTS) * 25 * 2, len(variants))

        custom = {
            key: value["model"] for key, value in variants.items()
            if value["model"] != "minecraft:block/note_block"
        }
        self.assertEqual({
            module.note_block_variant_key(module.VANILLA_ENB_INSTRUMENT, module.VANILLA_ENB_NOTE, False):
                module.VANILLA_ENB_OFF_MODEL,
            module.note_block_variant_key(module.VANILLA_ENB_INSTRUMENT, module.VANILLA_ENB_NOTE, True):
                module.VANILLA_ENB_ON_MODEL,
        }, custom)
        self.assertEqual(
            {"model": "minecraft:block/note_block"},
            variants[module.note_block_variant_key("harp", 0, False)],
        )

    def test_vanilla_enb_models_use_a_top_on_all_six_faces(self):
        for texture, emissive in (
            ("extendednoteblock:block/a_top", False),
            ("extendednoteblock:block/a_top_on", True),
        ):
            model = json.loads(module.listener_enb_model(texture, emissive=emissive))
            self.assertEqual(texture, model["textures"]["all"])
            element = model["elements"][0]
            self.assertEqual({"down", "up", "north", "south", "west", "east"}, set(element["faces"]))
            self.assertTrue(all(face["texture"] == "#all" for face in element["faces"].values()))
            if emissive:
                self.assertEqual(15, element["light_emission"])
                self.assertFalse(element["shade"])
            else:
                self.assertNotIn("light_emission", element)

    def test_built_server_pack_contains_entity_free_placed_visual_entries(self):
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
                self.assertIn("assets/minecraft/blockstates/note_block.json", names)
                self.assertIn(
                    f"assets/{module.NAMESPACE}/models/block/enb.json", names
                )
                self.assertIn(
                    f"assets/{module.NAMESPACE}/models/block/enb_on.json", names
                )
                self.assertIn("assets/extendednoteblock/textures/block/a_top.png", names)
                self.assertIn("assets/extendednoteblock/textures/block/a_top_on.png", names)

    def test_quiet_edge_samples_receive_bounded_normalization(self):
        self.assertEqual(module.MAX_NORMALIZATION_GAIN, module.normalization_gain(1))
        self.assertEqual(1.0, module.normalization_gain(module.TARGET_SOURCE_PEAK))
        self.assertEqual(0.5, module.normalization_gain(module.TARGET_SOURCE_PEAK * 2))
        with self.assertRaises(ValueError):
            module.normalization_gain(0)

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


if __name__ == "__main__":
    unittest.main()
