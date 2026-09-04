#!/usr/bin/env python3
"""Create a registry-safe client-only JAR for Paper/Purpur bridge servers.

The normal ExtendedNoteBlock JAR is a content mod: it contains and registers
custom blocks/items. That is correct for Fabric servers, but unsafe against a
vanilla-registry Paper/Purpur server (creative slot sync can contain unknown
items and the server will disconnect while decoding the packet).

This script starts from Loom's remapped JAR and emits a strict whitelist JAR
containing only the Paper bridge client initializer, the three S2C payloads,
client config/sound code, and ExtendedNoteBlock assets.
"""

from __future__ import annotations

import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LIBS = ROOT / "build" / "libs"
OUT_DIR = ROOT / "build" / "paper-bridge-client"


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
    ]
    if len(jars) != 1:
        raise SystemExit(f"Expected exactly one runtime JAR in {LIBS}, found: {[p.name for p in jars]}")
    return jars[0]


props = read_properties(ROOT / "gradle.properties")
mc_version = props["minecraft_version"]
mod_version = props["mod_version"]
loader_version = props["loader_version"]
fabric_api_version = props["fabric_api_version"]
java_version = props["java_version"]

source_jar = find_runtime_jar()
OUT_DIR.mkdir(parents=True, exist_ok=True)
out_jar = OUT_DIR / f"ExtendedNoteBlockBridgeClient-Fabric-{mod_version}-mc{mc_version}.jar"

metadata = {
    "schemaVersion": 1,
    "id": "extendednoteblock_bridge_client",
    "version": f"{mod_version}+mc{mc_version}",
    "name": "ExtendedNoteBlock Bridge Client",
    "description": "Client-only sound companion for ExtendedNoteBlockBridge on Paper/Purpur servers.",
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

CLASS_PREFIX = "com/atemukesu/extendednoteblock/"
ALLOWED_CLASSES = (
    CLASS_PREFIX + "bridgeclient/",
    CLASS_PREFIX + "config/ConfigManager",
    CLASS_PREFIX + "config/ModConfig",
    CLASS_PREFIX + "sound/ClientSoundManager",
    CLASS_PREFIX + "sound/StoppablePositionalSoundInstance",
    CLASS_PREFIX + "sound/SoundPackManager",
    CLASS_PREFIX + "sound/SoundPackInfo",
)


def keep_entry(name: str) -> bool:
    if name == "META-INF/MANIFEST.MF":
        return True
    if name.startswith("assets/extendednoteblock/"):
        return True
    if name.startswith("LICENSE"):
        return True
    if name.endswith(".class") and any(name.startswith(prefix) for prefix in ALLOWED_CLASSES):
        return True
    return False


with zipfile.ZipFile(source_jar, "r") as zin, zipfile.ZipFile(
    out_jar, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9
) as zout:
    for info in zin.infolist():
        if info.filename == "fabric.mod.json":
            continue
        if not keep_entry(info.filename):
            continue
        zout.writestr(info, zin.read(info.filename))

    zout.writestr(
        "fabric.mod.json",
        json.dumps(metadata, ensure_ascii=False, indent=2).encode("utf-8"),
    )

# Safety assertions: fail CI rather than publish a content-mod bridge client.
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
        raise SystemExit(f"Registry-unsafe classes leaked into bridge client: {leaked[:20]}")

    for required in (
        CLASS_PREFIX + "bridgeclient/PaperBridgeClient.class",
        CLASS_PREFIX + "bridgeclient/BridgeClientPayloads.class",
        CLASS_PREFIX + "sound/ClientSoundManager.class",
        CLASS_PREFIX + "sound/SoundPackManager.class",
        "fabric.mod.json",
    ):
        if required not in names:
            raise SystemExit(f"Missing required bridge-client entry: {required}")

    full_mod_entrypoint = CLASS_PREFIX + "ExtendedNoteBlock.class"
    if full_mod_entrypoint in names:
        raise SystemExit("Full content-mod initializer must not exist in the Paper bridge client JAR")

print(out_jar)
