#!/usr/bin/env python3
"""Build the standalone visual resource pack shared by Full Fabric and Paper Client.

The pack intentionally contains only visual/UI resources. The large default sound-pack ZIP
stays in the mods themselves and is not duplicated here.
"""
from __future__ import annotations

import json
import shutil
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSET_ROOT = ROOT / "src" / "main" / "resources" / "assets" / "extendednoteblock"
OUT_DIR = ROOT / "build" / "bridge-resources"
RESOURCE_PACK_FORMAT = 88
VISUAL_DIRS = ("blockstates", "items", "lang", "models", "textures")


def read_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def pack_metadata() -> bytes:
    # Minecraft 26.2 final uses Resource Pack version 88.0.
    return json.dumps(
        {
            "pack": {
                "pack_format": RESOURCE_PACK_FORMAT,
                "description": "ExtendedNoteBlock shared visuals for Minecraft 26.2",
            }
        },
        ensure_ascii=False,
        indent=2,
    ).encode("utf-8")


def iter_visual_files():
    for directory in VISUAL_DIRS:
        base = ASSET_ROOT / directory
        if not base.exists():
            continue
        for path in sorted(base.rglob("*")):
            if path.is_file():
                yield path


def main() -> None:
    props = read_properties(ROOT / "gradle.properties")
    version = props["mod_version"]
    mc = props["minecraft_version"]
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    output = OUT_DIR / f"ExtendedNoteBlock-Bridge-Resources-{version}-mc{mc}.zip"

    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        zf.writestr("pack.mcmeta", pack_metadata())
        icon = ASSET_ROOT / "icon.png"
        if icon.exists():
            zf.write(icon, "pack.png")
        for path in iter_visual_files():
            rel = path.relative_to(ASSET_ROOT)
            zf.write(path, Path("assets") / "extendednoteblock" / rel)

    # Basic integrity checks so CI never publishes an empty/broken pack.
    with zipfile.ZipFile(output, "r") as check:
        names = set(check.namelist())
        required = {
            "pack.mcmeta",
            "assets/extendednoteblock/items/conductor_wand.json",
            "assets/extendednoteblock/items/extended_note_block.json",
            "assets/extendednoteblock/items/global_redstone_transmitter.json",
            "assets/extendednoteblock/items/global_redstone_receiver.json",
            "assets/extendednoteblock/items/nbs_projection_receiver.json",
        }
        missing = sorted(required - names)
        if missing:
            raise SystemExit(f"Resource pack is missing required entries: {missing}")

    print(output)


if __name__ == "__main__":
    main()
