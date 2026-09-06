#!/usr/bin/env python3
"""Build the auto-delivered ENB listener resource pack for Minecraft 26.2.

The pack combines the existing Paper visual assets with a compact General MIDI
listener bank. All 128 GM program numbers remain addressable, but each group of
four related programs shares one physical SoundFont rendering. Eleven octave
anchors keep every MIDI note inside vanilla's 0.5x-2.0x pitch range.

Eight logical voice aliases point to each physical sample. The Paper plugin can
therefore stop overlapping notes independently without copying audio files.
"""

from __future__ import annotations

import argparse
import array
import concurrent.futures
import hashlib
import json
import os
import shutil
import subprocess
import sys
import wave
import zipfile
from dataclasses import dataclass
from pathlib import Path

from make_visual_resource_pack import (
    ASSET_ROOT,
    CARRIER_ITEMS,
    RESOURCE_PACK_FORMAT,
    carrier_selector,
    iter_visual_files,
    pack_metadata,
    read_properties,
)

ROOT = Path(__file__).resolve().parents[1]
SOUNDFONT = ROOT / "tools" / "audio" / "GeneralUser_GS_2.0.2.sf2"
SOUNDFONT_SHA256 = "c278464b823daf9c52106c0957f752817da0e52964817ff682fe3a8d2f8446ce"
RENDERER = ROOT / "scripts" / "RenderSoundFontSamples.java"
NOTICE = ROOT / "tools" / "audio" / "GeneralUser_GS_NOTICE.txt"
OUT_DIR = ROOT / "build" / "server-resource-pack"
WORK_DIR = ROOT / "build" / "server-resource-pack-work"

NAMESPACE = "extendednoteblock_listener"
# Two deliberately reserved Note Block states are used only in client-side
# block-change packets sent by the Paper plugin.  The world itself remains a
# normal Note Block, while an unmodified client with this pack can render an
# ENB without any display entities.  Every other vanilla Note Block state maps
# straight back to the original model.
NOTE_BLOCK_INSTRUMENTS = (
    "harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar",
    "chime", "xylophone", "iron_xylophone", "cow_bell", "didgeridoo", "bit",
    "banjo", "pling", "trumpet", "trumpet_exposed", "trumpet_weathered",
    "trumpet_oxidized", "zombie", "skeleton", "creeper", "dragon",
    "wither_skeleton", "piglin", "custom_head",
)
VANILLA_ENB_INSTRUMENT = "custom_head"
VANILLA_ENB_NOTE = 24
VANILLA_ENB_OFF_MODEL = f"{NAMESPACE}:block/enb"
VANILLA_ENB_ON_MODEL = f"{NAMESPACE}:block/enb_on"
REPRESENTATIVE_PROGRAMS = tuple(range(0, 128, 4))
MELODIC_ANCHORS = tuple(range(0, 121, 12))
DRUM_NOTES = tuple(range(35, 82))
VOICE_ALIASES = 8
ATTENUATION_DISTANCE = 64
ENCODE_CACHE_VERSION = 6
HOLD_SECONDS = 10.0
TAIL_SECONDS = 2.0
MIN_PREFERRED_SOURCE_PEAK = 512
TARGET_SOURCE_PEAK = 16_384
MAX_NORMALIZATION_GAIN = 128.0
MIN_ENCODED_PEAK = 256


@dataclass(frozen=True)
class RenderTask:
    bank: int
    program: int
    note: int
    stem: str

    @property
    def wav_name(self) -> str:
        return self.stem + ".wav"

    @property
    def ogg_name(self) -> str:
        return self.stem + ".ogg"


def representative_for(program: int) -> int:
    if not 0 <= program <= 127:
        raise ValueError(f"GM program must be 0-127, got {program}")
    return (program // 4) * 4


def render_tasks(smoke: bool = False) -> list[RenderTask]:
    programs = (0,) if smoke else REPRESENTATIVE_PROGRAMS
    anchors = (60,) if smoke else MELODIC_ANCHORS
    drums = (35,) if smoke else DRUM_NOTES
    tasks = [
        RenderTask(0, program, note, f"melodic.{program}.{note}")
        for program in programs
        for note in anchors
    ]
    tasks.extend(RenderTask(128, 0, note, f"drum.{note}") for note in drums)
    return tasks


def sound_events(smoke: bool = False) -> dict[str, dict]:
    instruments = range(1) if smoke else range(128)
    anchors = (60,) if smoke else MELODIC_ANCHORS
    drums = (35,) if smoke else DRUM_NOTES
    events: dict[str, dict] = {}
    for instrument in instruments:
        representative = representative_for(instrument)
        for note in anchors:
            physical = f"{NAMESPACE}:melodic/{representative}.{note}"
            for voice in range(VOICE_ALIASES):
                events[f"notes.{instrument}.{note}.v{voice}"] = {
                    "sounds": [{
                        "name": physical,
                        "stream": False,
                        "attenuation_distance": ATTENUATION_DISTANCE,
                    }],
                }
    for note in drums:
        physical = f"{NAMESPACE}:drums/{note}"
        for voice in range(VOICE_ALIASES):
            events[f"notes.128.{note}.v{voice}"] = {
                "sounds": [{
                    "name": physical,
                    "stream": False,
                    "attenuation_distance": ATTENUATION_DISTANCE,
                }],
            }
    return events


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def java_tool(name: str) -> str:
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidate = Path(java_home) / "bin" / name
        if candidate.is_file():
            return str(candidate)
    executable = shutil.which(name)
    if executable:
        return executable
    raise SystemExit(f"Missing {name}; Java is required to render the listener pack")


def render_wavs(tasks: list[RenderTask], wav_dir: Path, plan: Path, classes: Path) -> None:
    if not SOUNDFONT.is_file():
        raise SystemExit(f"Missing reviewed GeneralUser GS SoundFont: {SOUNDFONT}")
    if sha256(SOUNDFONT) != SOUNDFONT_SHA256:
        raise SystemExit("GeneralUser GS SoundFont checksum does not match the reviewed source")
    if not NOTICE.is_file():
        raise SystemExit(f"Missing GeneralUser GS attribution notice: {NOTICE}")
    missing = [task for task in tasks if not (wav_dir / task.wav_name).is_file()]
    if not missing:
        return

    classes.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [
            java_tool("javac"),
            "--add-exports", "java.desktop/com.sun.media.sound=ALL-UNNAMED",
            "-d", str(classes),
            str(RENDERER),
        ],
        check=True,
    )
    plan.write_text(
        "# bank\tprogram\tnote\toutput\n"
        + "".join(f"{t.bank}\t{t.program}\t{t.note}\t{t.wav_name}\n" for t in missing),
        encoding="utf-8",
    )
    wav_dir.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [
            java_tool("java"),
            "--add-exports", "java.desktop/com.sun.media.sound=ALL-UNNAMED",
            "-cp", str(classes),
            "RenderSoundFontSamples",
            str(SOUNDFONT),
            str(plan),
            str(wav_dir),
            str(HOLD_SECONDS),
            str(TAIL_SECONDS),
        ],
        check=True,
    )


def peak_pcm16(path: Path) -> int:
    peak = 0
    with wave.open(str(path), "rb") as source:
        if source.getsampwidth() != 2:
            raise ValueError(f"Expected 16-bit PCM WAV: {path}")
        while True:
            raw = source.readframes(65_536)
            if not raw:
                break
            samples = array.array("h")
            samples.frombytes(raw)
            if sys.byteorder != "little":
                samples.byteswap()
            if samples:
                peak = max(peak, max(abs(sample) for sample in samples))
    return peak


def normalization_gain(source_peak: int) -> float:
    if source_peak <= 0:
        raise ValueError("Cannot normalize a silent sample")
    return min(MAX_NORMALIZATION_GAIN, TARGET_SOURCE_PEAK / source_peak)


def convert_one(
    task: RenderTask,
    source_task: RenderTask,
    pitch_factor: float,
    source_peak: int,
    wav_dir: Path,
    ogg_dir: Path,
    ffmpeg: str,
) -> None:
    source = wav_dir / source_task.wav_name
    target = ogg_dir / task.ogg_name
    if target.is_file() and target.stat().st_size > 1_000:
        return
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_name(target.stem + ".part.ogg")
    if temporary.exists():
        temporary.unlink()
    pitch_filter = ""
    if abs(pitch_factor - 1.0) > 0.000_001:
        pitch_filter = f"asetrate=44100*{pitch_factor:.9f},aresample=44100,"
    try:
        gain = normalization_gain(source_peak)
        subprocess.run(
            [
                ffmpeg,
                "-hide_banner", "-loglevel", "error", "-nostdin",
                "-i", str(source),
                "-af",
                pitch_filter + "pan=mono|c0=0.5*c0+0.5*c1,"
                f"volume={gain:.9f},"
                "areverse,silenceremove=start_periods=1:start_duration=0:start_threshold=-65dB,"
                "areverse,apad=pad_dur=0.08",
                "-c:a", "libvorbis", "-q:a", "3", "-y", str(temporary),
            ],
            check=True,
        )
        if temporary.stat().st_size <= 1_000:
            raise RuntimeError(f"Encoded OGG is unexpectedly small: {temporary}")
        temporary.replace(target)
    finally:
        if temporary.exists():
            temporary.unlink()


def probe_duration(path: Path, ffprobe: str) -> float:
    checked = subprocess.run(
        [
            ffprobe, "-v", "error", "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1", str(path),
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    return float(checked.stdout.strip())


def probe_encoded_peak(path: Path, ffmpeg: str) -> int:
    decoded = subprocess.run(
        [
            ffmpeg, "-v", "error", "-i", str(path), "-map", "0:a:0",
            "-ac", "1", "-c:a", "pcm_s16le", "-f", "s16le", "pipe:1",
        ],
        check=True,
        capture_output=True,
    ).stdout
    samples = array.array("h")
    samples.frombytes(decoded)
    if sys.byteorder != "little":
        samples.byteswap()
    return max((abs(sample) for sample in samples), default=0)


def choose_source(task: RenderTask, tasks: list[RenderTask], peaks: dict[RenderTask, int]) -> tuple[RenderTask, float]:
    if task.bank == 128:
        if peaks[task] < 8:
            raise RuntimeError(f"SoundFont rendered an inaudible percussion sample: {task.stem}")
        return task, 1.0
    if peaks[task] >= MIN_PREFERRED_SOURCE_PEAK:
        return task, 1.0

    candidates = [
        candidate for candidate in tasks
        if candidate.bank == task.bank and candidate.program == task.program
        and peaks[candidate] >= MIN_PREFERRED_SOURCE_PEAK
    ]
    if not candidates:
        candidates = [
            candidate for candidate in tasks
            if candidate.bank == task.bank and candidate.program == task.program and peaks[candidate] >= 8
        ]
    if not candidates:
        raise RuntimeError(f"SoundFont rendered no audible samples for GM program {task.program}")
    source = min(candidates, key=lambda candidate: abs(candidate.note - task.note))
    # A few SoundFont instruments contain only a transient/noise at their MIDI
    # edges. Re-pitch a nearby healthy octave offline instead of shipping an
    # event that technically exists but is effectively silent.
    return source, 2.0 ** ((task.note - source.note) / 12.0)


def convert_oggs(tasks: list[RenderTask], wav_dir: Path, ogg_dir: Path) -> None:
    ffmpeg = shutil.which("ffmpeg")
    ffprobe = shutil.which("ffprobe")
    if not ffmpeg or not ffprobe:
        raise SystemExit("ffmpeg and ffprobe with libvorbis are required to build the listener pack")
    peaks = {task: peak_pcm16(wav_dir / task.wav_name) for task in tasks}
    sources = {task: choose_source(task, tasks, peaks) for task in tasks}

    workers = min(8, max(1, os.cpu_count() or 1))
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as executor:
        futures = [
            executor.submit(
                convert_one, task, sources[task][0], sources[task][1], peaks[sources[task][0]],
                wav_dir, ogg_dir, ffmpeg,
            )
            for task in tasks
        ]
        for future in concurrent.futures.as_completed(futures):
            future.result()

        probes = {
            executor.submit(probe_duration, ogg_dir / task.ogg_name, ffprobe): task
            for task in tasks
        }
        for future in concurrent.futures.as_completed(probes):
            duration = future.result()
            if duration <= 0.081:
                raise RuntimeError(
                    f"Encoded OGG contains no audible content beyond the safety pad: "
                    f"{probes[future].ogg_name} ({duration:.3f}s)"
                )

        encoded_peaks = {
            executor.submit(probe_encoded_peak, ogg_dir / task.ogg_name, ffmpeg): task
            for task in tasks
        }
        for future in concurrent.futures.as_completed(encoded_peaks):
            peak = future.result()
            if peak < MIN_ENCODED_PEAK:
                raise RuntimeError(
                    f"Encoded OGG is effectively inaudible: {encoded_peaks[future].ogg_name} "
                    f"(PCM peak {peak})"
                )


def zip_info(name: str, compressed: bool = True) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
    info.compress_type = zipfile.ZIP_DEFLATED if compressed else zipfile.ZIP_STORED
    info.external_attr = 0o100644 << 16
    return info


def write_bytes(zf: zipfile.ZipFile, name: str, data: bytes, compressed: bool = True) -> None:
    zf.writestr(zip_info(name, compressed), data)


def metadata(smoke: bool) -> dict:
    return {
        "schema": 1,
        "namespace": NAMESPACE,
        "minecraft": "26.2",
        "representative_programs": list((0,) if smoke else REPRESENTATIVE_PROGRAMS),
        "program_group_size": 4,
        "melodic_anchors": list((60,) if smoke else MELODIC_ANCHORS),
        "drum_notes": list((35,) if smoke else DRUM_NOTES),
        "voice_aliases": VOICE_ALIASES,
        "attenuation_distance": ATTENUATION_DISTANCE,
        "vanilla_enb_visual": {
            "carrier": "minecraft:note_block",
            "reserved_instrument": VANILLA_ENB_INSTRUMENT,
            "reserved_note": VANILLA_ENB_NOTE,
            "off_texture": "extendednoteblock:block/a_top",
            "on_texture": "extendednoteblock:block/a_top_on",
        },
        "maximum_rendered_hold_seconds": HOLD_SECONDS,
        "sample_peak_normalization": {
            "minimum_preferred_source_pcm16": MIN_PREFERRED_SOURCE_PEAK,
            "target_pcm16": TARGET_SOURCE_PEAK,
            "minimum_encoded_pcm16": MIN_ENCODED_PEAK,
        },
        "soundfont": "GeneralUser GS 2.0.2",
        "soundfont_sha256": SOUNDFONT_SHA256,
    }


def note_block_variant_key(instrument: str, note: int, powered: bool) -> str:
    return f"instrument={instrument},note={note},powered={'true' if powered else 'false'}"


def listener_note_blockstate() -> bytes:
    """Keep vanilla Note Blocks vanilla except for the two reserved fake states."""
    variants: dict[str, dict[str, str]] = {}
    for instrument in NOTE_BLOCK_INSTRUMENTS:
        for note in range(25):
            for powered in (False, True):
                model = "minecraft:block/note_block"
                if instrument == VANILLA_ENB_INSTRUMENT and note == VANILLA_ENB_NOTE:
                    model = VANILLA_ENB_ON_MODEL if powered else VANILLA_ENB_OFF_MODEL
                variants[note_block_variant_key(instrument, note, powered)] = {"model": model}
    return (json.dumps({"variants": variants}, ensure_ascii=False, separators=(",", ":")) + "\n").encode()


def listener_enb_model(texture: str, *, emissive: bool) -> bytes:
    """Build a full cube whose six faces all use the requested ENB texture."""
    faces = {
        face: {"texture": "#all", "cullface": face}
        for face in ("down", "up", "north", "south", "west", "east")
    }
    element: dict[str, object] = {
        "from": [0, 0, 0],
        "to": [16, 16, 16],
        "shade": not emissive,
        "faces": faces,
    }
    if emissive:
        # Model emission makes the active texture render at full brightness;
        # it does not mutate the real world's light or redstone state.
        element["light_emission"] = 15
    model = {
        "ambientocclusion": not emissive,
        "textures": {"all": texture, "particle": texture},
        "elements": [element],
    }
    return (json.dumps(model, ensure_ascii=False, indent=2) + "\n").encode()


def build_zip(tasks: list[RenderTask], ogg_dir: Path, output: Path, smoke: bool) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    events = sound_events(smoke)
    description = "ExtendedNoteBlock server visuals + compact listener sounds (Minecraft 26.2)"
    readme = f"""ExtendedNoteBlock Server Resources - Minecraft 26.2

This combined pack is sent automatically by ExtendedNoteBlockBridge.
It contains the shared ENB item/model textures and a compact General MIDI bank
for players using an unmodified client. Players with Paper Client continue to
use the mod protocol for full fades, moving sound sources and pitch curves.

Audio layout
------------
- 128 GM program numbers mapped to {len(REPRESENTATIVE_PROGRAMS)} representative timbres
- {len(MELODIC_ANCHORS)} octave anchors spanning MIDI 0-127
- {len(DRUM_NOTES)} General MIDI percussion notes
- {VOICE_ALIASES} logical aliases per sample for overlapping-note stop control

Placed Paper bridge blocks remain vanilla registry carriers. Custom inventory
items use safe CustomModelData selectors. For players without Paper Client, the
plugin sends coordinate-local fake Note Block states: every face uses a_top.png,
or a_top_on.png at full brightness while powered. The real block remains a Note
Block, no display entity is spawned, and ordinary blocks retain vanilla models.
Paper Client users keep the original pitch-specific position-aware models.
"""

    entries: list[tuple[str, bytes, bool]] = [
        ("pack.mcmeta", (json.dumps(pack_metadata(description), ensure_ascii=False, indent=2) + "\n").encode(), True),
        ("README.txt", readme.encode(), True),
        ("enb_server_pack.json", (json.dumps(metadata(smoke), ensure_ascii=False, indent=2) + "\n").encode(), True),
        (f"assets/{NAMESPACE}/sounds.json", (json.dumps(events, ensure_ascii=False, separators=(",", ":")) + "\n").encode(), True),
    ]
    if NOTICE.is_file():
        entries.append(("THIRD_PARTY_NOTICES.txt", NOTICE.read_bytes(), True))
    icon = ASSET_ROOT / "icon.png"
    if icon.is_file():
        entries.append(("pack.png", icon.read_bytes(), True))
    for path in iter_visual_files():
        rel = path.relative_to(ASSET_ROOT)
        entries.append(((Path("assets") / "extendednoteblock" / rel).as_posix(), path.read_bytes(), True))
    for carrier, (logical_id, vanilla_model) in CARRIER_ITEMS.items():
        entries.append((f"assets/minecraft/items/{carrier}.json", carrier_selector(logical_id, vanilla_model), True))
    entries.extend((
        ("assets/minecraft/blockstates/note_block.json", listener_note_blockstate(), True),
        (f"assets/{NAMESPACE}/models/block/enb.json",
         listener_enb_model("extendednoteblock:block/a_top", emissive=False), True),
        (f"assets/{NAMESPACE}/models/block/enb_on.json",
         listener_enb_model("extendednoteblock:block/a_top_on", emissive=True), True),
    ))
    for task in tasks:
        folder = "drums" if task.bank == 128 else "melodic"
        entries.append((f"assets/{NAMESPACE}/sounds/{folder}/{task.ogg_name.split('.', 1)[1]}",
                        (ogg_dir / task.ogg_name).read_bytes(), False))

    with zipfile.ZipFile(output, "w", allowZip64=True) as zf:
        for name, data, compressed in sorted(entries, key=lambda entry: entry[0]):
            write_bytes(zf, name, data, compressed)


def validate(output: Path, tasks: list[RenderTask], smoke: bool) -> None:
    expected_events = len(sound_events(smoke))
    with zipfile.ZipFile(output) as zf:
        names = set(zf.namelist())
        required = {
            "pack.mcmeta",
            "enb_server_pack.json",
            f"assets/{NAMESPACE}/sounds.json",
            "assets/extendednoteblock/items/extended_note_block.json",
            "assets/minecraft/blockstates/note_block.json",
            f"assets/{NAMESPACE}/models/block/enb.json",
            f"assets/{NAMESPACE}/models/block/enb_on.json",
            *(f"assets/minecraft/items/{carrier}.json" for carrier in CARRIER_ITEMS),
        }
        missing = sorted(required - names)
        if missing:
            raise SystemExit(f"Server resource pack is missing: {missing}")
        if any(name.endswith(".class") or name.endswith(".sf2") for name in names):
            raise SystemExit("Server resource pack leaked build classes or the source SoundFont")
        parsed_events = json.loads(zf.read(f"assets/{NAMESPACE}/sounds.json"))
        if len(parsed_events) != expected_events:
            raise SystemExit(f"Expected {expected_events} sound events, found {len(parsed_events)}")
        audio = [name for name in names if name.endswith(".ogg")]
        if len(audio) != len(tasks):
            raise SystemExit(f"Expected {len(tasks)} physical samples, found {len(audio)}")
        note_block = json.loads(zf.read("assets/minecraft/blockstates/note_block.json"))
        variants = note_block.get("variants", {})
        expected_variants = len(NOTE_BLOCK_INSTRUMENTS) * 25 * 2
        if len(variants) != expected_variants:
            raise SystemExit(f"Expected {expected_variants} Note Block variants, found {len(variants)}")
        custom = {
            key: value["model"] for key, value in variants.items()
            if value.get("model") != "minecraft:block/note_block"
        }
        expected_custom = {
            note_block_variant_key(VANILLA_ENB_INSTRUMENT, VANILLA_ENB_NOTE, False): VANILLA_ENB_OFF_MODEL,
            note_block_variant_key(VANILLA_ENB_INSTRUMENT, VANILLA_ENB_NOTE, True): VANILLA_ENB_ON_MODEL,
        }
        if custom != expected_custom:
            raise SystemExit(f"Unexpected reserved Note Block model mapping: {custom}")
        if not smoke:
            for instrument in range(128):
                for anchor in MELODIC_ANCHORS:
                    if f"notes.{instrument}.{anchor}.v{VOICE_ALIASES - 1}" not in parsed_events:
                        raise SystemExit(f"Missing listener event for instrument {instrument}, anchor {anchor}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--smoke", action="store_true", help="render one melodic and one drum sample")
    parser.add_argument("--output", type=Path, help="override output ZIP path")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    props = read_properties(ROOT / "gradle.properties")
    suffix = "Smoke" if args.smoke else props["mod_version"]
    output = args.output or OUT_DIR / f"ExtendedNoteBlock-Server-Resources-{suffix}-mc{props['minecraft_version']}.zip"
    task_list = render_tasks(args.smoke)
    variant = "smoke" if args.smoke else "full"
    work = WORK_DIR / variant
    wav_dir = work / "wav"
    ogg_dir = work / f"ogg-v{ENCODE_CACHE_VERSION}"
    render_wavs(task_list, wav_dir, work / "render-plan.tsv", work / "classes")
    convert_oggs(task_list, wav_dir, ogg_dir)
    build_zip(task_list, ogg_dir, output, args.smoke)
    validate(output, task_list, args.smoke)
    print(f"{output} ({output.stat().st_size / 1024 / 1024:.2f} MiB)")


if __name__ == "__main__":
    main()
