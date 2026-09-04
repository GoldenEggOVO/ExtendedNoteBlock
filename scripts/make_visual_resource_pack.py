#!/usr/bin/env python3
"""Build the optional ExtendedNoteBlock visual compatibility resource pack.

The pack reuses the exact visual assets from the Full Fabric mod and maps the
Paper/Purpur bridge's vanilla carriers to those models. It intentionally does
not contain any mod code or registry entries.

Important limitation: a vanilla resource pack cannot inspect Bukkit/Paper PDC
or plugin-side ENB object identity. Therefore carrier block/item overrides are
visual-only and global for clients that enable this pack. The Paper bridge
client can later replace this with position-aware rendering once object sync is
implemented.
"""

from __future__ import annotations

import json
import shutil
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSET_ROOT = ROOT / "src" / "main" / "resources" / "assets" / "extendednoteblock"
OUT_DIR = ROOT / "build" / "visual-resource-pack"


def read_properties(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        result[key.strip()] = value.strip()
    return result


props = read_properties(ROOT / "gradle.properties")
mod_version = props["mod_version"]
mc_version = props["minecraft_version"]

OUT_DIR.mkdir(parents=True, exist_ok=True)
out = OUT_DIR / f"ExtendedNoteBlock-Visuals-{mod_version}-mc{mc_version}.zip"

pack_meta = {
    "pack": {
        "min_format": 88,
        "max_format": 88,
        "description": "ExtendedNoteBlock shared visuals for Full Fabric and Paper Bridge (Minecraft 26.2)"
    }
}

# Paper bridge carriers -> Full Fabric visual model.
# These are deliberately ordinary minecraft:* resources so a vanilla resource
# pack can affect the carrier without registering any custom content.
item_overrides = {
    "note_block": "extendednoteblock:item/extended_note_block",
    "blaze_rod": "extendednoteblock:item/conductor_wand",
    "red_concrete": "extendednoteblock:item/global_redstone_transmitter",
    "green_concrete": "extendednoteblock:item/global_redstone_receiver",
    "purple_concrete": "extendednoteblock:item/nbs_projection_receiver",
}

blockstate_overrides = {
    "red_concrete": {
        "variants": {"": {"model": "extendednoteblock:block/global_redstone_transmitter"}}
    },
    "green_concrete": {
        "variants": {"": {"model": "extendednoteblock:block/global_redstone_receiver"}}
    },
    "purple_concrete": {
        "variants": {"": {"model": "extendednoteblock:block/nbs_projection_receiver"}}
    },
    # Ignore vanilla note/instrument properties and only mirror ENB's powered visual.
    "note_block": {
        "multipart": [
            {"when": {"powered": "false"}, "apply": {"model": "extendednoteblock:block/c"}},
            {"when": {"powered": "true"}, "apply": {"model": "extendednoteblock:block/c_on"}},
        ]
    },
    # The bridge temporarily uses a redstone block for a receiver's real 15-level output.
    # This makes powered receivers visually match the Full Fabric receiver, at the cost
    # of also changing ordinary redstone blocks for users who enable this optional pack.
    "redstone_block": {
        "variants": {"": {"model": "extendednoteblock:block/global_redstone_receiver_on"}}
    },
}

readme = """ExtendedNoteBlock Visual Compatibility Pack - Minecraft 26.2

Purpose
-------
This pack reuses the Full Fabric mod's original ExtendedNoteBlock textures and
models so the Paper/Purpur Bridge can look as close as possible to Full Fabric.

Carrier mapping
---------------
NOTE_BLOCK       -> Extended Note Block
BLAZE_ROD        -> Conductor Wand (item)
RED_CONCRETE     -> Global Redstone Transmitter
GREEN_CONCRETE   -> Global Redstone Receiver (off)
REDSTONE_BLOCK   -> Global Redstone Receiver (on)
PURPLE_CONCRETE  -> NBS Projection Receiver

Limitation
----------
A vanilla resource pack cannot read Paper PDC/enb_type, so these carrier visual
overrides apply to every matching vanilla carrier on clients that enable this
pack. Players who do not enable it still see normal vanilla blocks/items.

A later Paper Bridge Client renderer can make this position-aware so only real
ENB bridge objects receive the ENB appearance.
"""

with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    z.writestr("pack.mcmeta", json.dumps(pack_meta, ensure_ascii=False, indent=2) + "\n")
    z.writestr("README.txt", readme)

    icon = ASSET_ROOT / "icon.png"
    if icon.exists():
        z.write(icon, "pack.png")

    # Copy the Full Fabric visual namespace. Keep language files too so names/UI
    # remain consistent between the two client variants.
    for folder in ("models", "textures", "items", "blockstates", "lang"):
        base = ASSET_ROOT / folder
        if not base.exists():
            continue
        for path in base.rglob("*"):
            if path.is_file():
                rel = path.relative_to(ROOT / "src" / "main" / "resources")
                z.write(path, rel.as_posix())

    for vanilla_id, model in item_overrides.items():
        data = {"model": {"type": "minecraft:model", "model": model}}
        z.writestr(
            f"assets/minecraft/items/{vanilla_id}.json",
            json.dumps(data, ensure_ascii=False, indent=2) + "\n",
        )

    for vanilla_id, data in blockstate_overrides.items():
        z.writestr(
            f"assets/minecraft/blockstates/{vanilla_id}.json",
            json.dumps(data, ensure_ascii=False, indent=2) + "\n",
        )

# CI assertions: the resource pack must remain code-free and contain all five mappings.
with zipfile.ZipFile(out, "r") as check:
    names = set(check.namelist())
    if any(name.endswith(".class") for name in names):
        raise SystemExit("Visual resource pack unexpectedly contains Java classes")
    for required in (
        "pack.mcmeta",
        "assets/minecraft/items/note_block.json",
        "assets/minecraft/items/blaze_rod.json",
        "assets/minecraft/blockstates/note_block.json",
        "assets/minecraft/blockstates/red_concrete.json",
        "assets/minecraft/blockstates/green_concrete.json",
        "assets/minecraft/blockstates/purple_concrete.json",
        "assets/extendednoteblock/models/item/conductor_wand.json",
    ):
        if required not in names:
            raise SystemExit(f"Missing required resource-pack entry: {required}")

print(out)
