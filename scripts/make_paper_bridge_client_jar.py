#!/usr/bin/env python3
"""Create a registry-safe client-only JAR for Paper/Purpur bridge servers.

The normal ExtendedNoteBlock JAR is a content mod: it contains and registers
custom blocks/items. That is correct for single-player/Fabric servers, but unsafe
against a vanilla-registry Paper/Purpur server.

This script starts from Loom's remapped JAR and emits a strict whitelist JAR.
It also embeds the same visual pack that is published as a standalone resource
pack under resourcepacks/bridge_visuals/, so the Paper Client can register it as
an always-enabled built-in pack.
"""

from __future__ import annotations

import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LIBS = ROOT / "build" / "libs"
OUT_DIR = ROOT / "build" / "paper-bridge-client"
ASSET_ROOT = ROOT / "src" / "main" / "resources" / "assets" / "extendednoteblock"
RESOURCE_PACK_FORMAT = 88
VISUAL_DIRS = ("blockstates/", "items/", "lang/", "models/", "textures/")


def read_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def find_runtime_jar() -> Path:
    jars = [
        p
        for p in LIBS.glob("*.jar")
        if not p.name.endswith("-sources.jar")
        and "bridgeclient" not in p.name.lower()
        and "bridge-client" not in p.name.lower()
        and "paper-client" not in p.name.lower()
    ]
    if len(jars) != 1:
        raise SystemExit(f"Expected exactly one runtime JAR in {LIBS}, found: {[p.name for p in jars]}")
    return jars[0]


def built_in_pack_metadata() -> bytes:
    return json.dumps(
        {
            "pack": {
                "pack_format": RESOURCE_PACK_FORMAT,
                "description": "ExtendedNoteBlock Paper Client built-in visuals",
            }
        },
        ensure_ascii=False,
        indent=2,
    ).encode("utf-8")


props = read_properties(ROOT / "gradle.properties")
mc_version = props["minecraft_version"]
mod_version = props["mod_version"]
loader_version = props["loader_version"]
fabric_api_version = props["fabric_api_version"]
java_version = props["java_version"]

source_jar = find_runtime_jar()
OUT_DIR.mkdir(parents=True, exist_ok=True)
out_jar = OUT_DIR / f"ExtendedNoteBlock-Paper-Client-Fabric-{mod_version}-mc{mc_version}.jar"

CLASS_PREFIX = "com/atemukesu/extendednoteblock/"
ALLOWED_CLASSES = (
    CLASS_PREFIX + "bridgeclient/",
    CLASS_PREFIX + "config/ConfigManager",
    CLASS_PREFIX + "config/ModConfig",
    CLASS_PREFIX + "sound/ClientSoundManager",
    CLASS_PREFIX + "sound/StoppablePositionalSoundInstance",
    CLASS_PREFIX + "sound/SoundPackManager",
    CLASS_PREFIX + "sound/SoundPackInfo",
    CLASS_PREFIX + "client/gui/screen/NbsWorkshopScreen",
    CLASS_PREFIX + "client/gui/screen/VanillaExportScreen",
    CLASS_PREFIX + "client/gui/screen/VanillaBlockMappingScreen",
    CLASS_PREFIX + "client/gui/widget/NbsProjectionPreviewWidget",
    CLASS_PREFIX + "nbs/NbsSong",
    CLASS_PREFIX + "nbs/NbsReader",
    CLASS_PREFIX + "nbs/NbsWriter",
    CLASS_PREFIX + "nbs/NbsProjectionOptions",
    CLASS_PREFIX + "nbs/NbsProjectionWriter",
    CLASS_PREFIX + "nbs/MidiToNbsConverter",
    CLASS_PREFIX + "nbs/AudioPitchAnalyzer",
    CLASS_PREFIX + "nbs/AudioFileDecoder",
    CLASS_PREFIX + "nbs/AudioToNbsConverter",
    CLASS_PREFIX + "nbs/NbsPreviewPlayer",
    CLASS_PREFIX + "nbs/vanilla/",
    CLASS_PREFIX + "map/InstrumentMap",
)


def keep_entry(name: str) -> bool:
    if name == "META-INF/MANIFEST.MF":
        return True
    if name.startswith("assets/extendednoteblock/"):
        return True
    if name.startswith("LICENSE"):
        return True
    if name.startswith("META-INF/jars/jlayer-") and name.endswith(".jar"):
        return True
    if name.endswith(".class") and any(name.startswith(prefix) for prefix in ALLOWED_CLASSES):
        return True
    return False


def is_visual_asset(name: str) -> bool:
    prefix = "assets/extendednoteblock/"
    if not name.startswith(prefix):
        return False
    relative = name[len(prefix):]
    return any(relative.startswith(directory) for directory in VISUAL_DIRS)


with zipfile.ZipFile(source_jar, "r") as source_check:
    source_names = set(source_check.namelist())
    jlayer_jars = sorted(
        name for name in source_names
        if name.startswith("META-INF/jars/jlayer-") and name.endswith(".jar")
    )

metadata = {
    "schemaVersion": 1,
    "id": "extendednoteblock_bridge_client",
    "version": f"{mod_version}+mc{mc_version}",
    "name": "ExtendedNoteBlock Paper Client",
    "description": "Registry-safe client companion for ExtendedNoteBlockBridge on Paper/Purpur.",
    "authors": ["Atemukesu", "BF_skt", "GoldenEggOVO"],
    "license": "MIT",
    "icon": "assets/extendednoteblock/icon.png",
    "environment": "client",
    "entrypoints": {
        "client": ["com.atemukesu.extendednoteblock.bridgeclient.PaperBridgeClient"]
    },
    "depends": {
        "fabricloader": f">={loader_version}",
        "minecraft": mc_version,
        "java": f">={java_version}",
        "fabric-api": f">={fabric_api_version}",
    },
}
if jlayer_jars:
    metadata["jars"] = [{"file": name} for name in jlayer_jars]

# Read the icon from the source tree instead of re-reading the already-streamed
# Loom JAR entry. Some remapped JARs contain duplicate asset entries and Python's
# name lookup can then land on a stale local-header offset.
pack_icon = (ASSET_ROOT / "icon.png").read_bytes() if (ASSET_ROOT / "icon.png").exists() else None

with zipfile.ZipFile(source_jar, "r") as zin, zipfile.ZipFile(
    out_jar, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9
) as zout:
    for info in zin.infolist():
        if info.filename == "fabric.mod.json":
            continue
        if not keep_entry(info.filename):
            continue
        data = zin.read(info)
        zout.writestr(info, data)

        if is_visual_asset(info.filename):
            zout.writestr("resourcepacks/bridge_visuals/" + info.filename, data)

    if pack_icon is not None:
        zout.writestr("resourcepacks/bridge_visuals/pack.png", pack_icon)
    zout.writestr("resourcepacks/bridge_visuals/pack.mcmeta", built_in_pack_metadata())
    zout.writestr(
        "fabric.mod.json",
        json.dumps(metadata, ensure_ascii=False, indent=2).encode("utf-8"),
    )

with zipfile.ZipFile(out_jar, "r") as check:
    names = set(check.namelist())
    forbidden_prefixes = (
        CLASS_PREFIX + "block/",
        CLASS_PREFIX + "item/",
        CLASS_PREFIX + "screen/",
        CLASS_PREFIX + "command/",
        CLASS_PREFIX + "network/",
    )
    leaked = sorted(name for name in names if name.endswith(".class") and name.startswith(forbidden_prefixes))
    if leaked:
        raise SystemExit(f"Registry-unsafe classes leaked into Paper Client: {leaked[:20]}")

    for required in (
        CLASS_PREFIX + "bridgeclient/PaperBridgeClient.class",
        CLASS_PREFIX + "bridgeclient/BridgeClientPayloads.class",
        CLASS_PREFIX + "sound/ClientSoundManager.class",
        CLASS_PREFIX + "sound/SoundPackManager.class",
        CLASS_PREFIX + "client/gui/screen/NbsWorkshopScreen.class",
        CLASS_PREFIX + "client/gui/screen/VanillaExportScreen.class",
        CLASS_PREFIX + "nbs/NbsReader.class",
        CLASS_PREFIX + "nbs/MidiToNbsConverter.class",
        CLASS_PREFIX + "nbs/vanilla/VanillaStructureGenerator.class",
        "resourcepacks/bridge_visuals/pack.mcmeta",
        "resourcepacks/bridge_visuals/assets/extendednoteblock/items/conductor_wand.json",
        "fabric.mod.json",
    ):
        if required not in names:
            raise SystemExit(f"Missing required Paper Client entry: {required}")

    if CLASS_PREFIX + "ExtendedNoteBlock.class" in names:
        raise SystemExit("Full content-mod initializer must not exist in the Paper Client JAR")

    forbidden_bytecode_refs = (
        b"com/atemukesu/extendednoteblock/block/",
        b"com/atemukesu/extendednoteblock/item/",
        b"com/atemukesu/extendednoteblock/screen/",
        b"com/atemukesu/extendednoteblock/network/",
    )
    bad_refs: list[tuple[str, str]] = []
    for name in sorted(names):
        if not name.endswith(".class"):
            continue
        data = check.read(name)
        for marker in forbidden_bytecode_refs:
            if marker in data:
                bad_refs.append((name, marker.decode("ascii")))
    if bad_refs:
        raise SystemExit(f"Registry/server bytecode references leaked into Paper Client: {bad_refs[:20]}")

    if not jlayer_jars:
        raise SystemExit("NBS audio import requires the bundled JLayer dependency, but no jlayer jar was found")
    for jar_name in jlayer_jars:
        if jar_name not in names:
            raise SystemExit(f"Missing JLayer nested dependency: {jar_name}")

print(out_jar)
