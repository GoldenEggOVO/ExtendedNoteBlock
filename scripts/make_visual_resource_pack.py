#!/usr/bin/env python3
"""Build the shared ExtendedNoteBlock visual resource pack for Minecraft 26.2.

The pack contains the original Full Fabric visual namespace only. It deliberately
does NOT globally replace vanilla Note Blocks, concrete, Blaze Rods or Redstone
Blocks: a vanilla resource pack cannot inspect Paper PDC/enb_type for placed
blocks. Paper 26.2 instead gives marked carrier *items* an `item_model` data
component pointing directly at these ExtendedNoteBlock item definitions.

Result:
- marked Paper bridge items + this pack -> original ENB item appearance;
- ordinary vanilla items -> unchanged;
- placed Paper bridge blocks remain vanilla without the Paper Client's future
  position-aware renderer, avoiding global texture replacement side effects.
"""

from __future__ import annotations

import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSET_ROOT = ROOT / "src" / "main" / "resources" / "assets" / "extendednoteblock"
OUT_DIR = ROOT / "build" / "visual-resource-pack"
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
    mod_version = props["mod_version"]
    mc_version = props["minecraft_version"]

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    output = OUT_DIR / f"ExtendedNoteBlock-Visuals-{mod_version}-mc{mc_version}.zip"

    metadata = {
        "pack": {
            "pack_format": RESOURCE_PACK_FORMAT,
            "description": "ExtendedNoteBlock shared visuals for Minecraft 26.2",
        }
    }

    readme = """ExtendedNoteBlock Visual Pack - Minecraft 26.2

This pack contains the original visual assets shared by the Full Fabric mod and
the Paper Client.

Paper/Purpur behavior
---------------------
The server plugin gives its marked vanilla carrier ITEMS a Minecraft 26.2
`item_model` component pointing to an `extendednoteblock:*` item model. With this
pack enabled, those marked items use the original ExtendedNoteBlock appearance.
Ordinary Blaze Rods, Note Blocks and concrete items stay vanilla.

Placed blocks
-------------
A vanilla resource pack cannot distinguish a plugin-marked block at one world
coordinate from an ordinary block of the same vanilla type. Therefore this pack
does not globally replace Note Block/concrete/redstone-block blockstates. Without
the Paper Client, placed bridge blocks intentionally remain vanilla-looking.
"""

    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        zf.writestr("pack.mcmeta", json.dumps(metadata, ensure_ascii=False, indent=2) + "\n")
        zf.writestr("README.txt", readme)

        icon = ASSET_ROOT / "icon.png"
        if icon.exists():
            zf.write(icon, "pack.png")

        for path in iter_visual_files():
            rel = path.relative_to(ASSET_ROOT)
            zf.write(path, (Path("assets") / "extendednoteblock" / rel).as_posix())

    with zipfile.ZipFile(output, "r") as check:
        names = set(check.namelist())
        if any(name.endswith(".class") for name in names):
            raise SystemExit("Visual resource pack unexpectedly contains Java classes")
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
            raise SystemExit(f"Visual resource pack is missing required entries: {missing}")
        forbidden_global_overrides = [
            name for name in names
            if name.startswith("assets/minecraft/items/") or name.startswith("assets/minecraft/blockstates/")
        ]
        if forbidden_global_overrides:
            raise SystemExit(
                "Visual pack must not globally replace vanilla carrier models: "
                + repr(forbidden_global_overrides[:20])
            )

    print(output)


if __name__ == "__main__":
    main()
