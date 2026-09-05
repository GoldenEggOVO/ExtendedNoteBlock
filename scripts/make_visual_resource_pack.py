#!/usr/bin/env python3
"""Build the shared ExtendedNoteBlock visual resource pack for Minecraft 26.2.

Paper/Purpur bridge items remain ordinary vanilla carrier ItemStacks. PDC is the
server-side logical identity, while minecraft:custom_model_data is visual-only.
The Paper plugin writes one namespaced string into CustomModelData, for example:

  extendednoteblock:conductor_wand

Minecraft 26.2 item definitions can select on the strings list of
minecraft:custom_model_data. This pack therefore overrides only the five carrier
item definitions with a safe selector:

  matching ENB CustomModelData string -> original ExtendedNoteBlock model
  anything else / no CustomModelData  -> original vanilla model

Placed blocks are intentionally not globally overridden; a plain resource pack
cannot distinguish plugin identity by world coordinate.
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
CMD_NAMESPACE = "extendednoteblock"

# carrier item id -> (ENB logical id, vanilla fallback baked model)
CARRIER_ITEMS = {
    "note_block": ("extended_note_block", "minecraft:block/note_block"),
    "blaze_rod": ("conductor_wand", "minecraft:item/blaze_rod"),
    "red_concrete": ("global_redstone_transmitter", "minecraft:block/red_concrete"),
    "green_concrete": ("global_redstone_receiver", "minecraft:block/green_concrete"),
    "purple_concrete": ("nbs_projection_receiver", "minecraft:block/purple_concrete"),
}


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


def custom_model_key(logical_id: str) -> str:
    return f"{CMD_NAMESPACE}:{logical_id}"


def carrier_selector(logical_id: str, vanilla_model: str) -> bytes:
    """Create a 26.2 item model selector keyed by CustomModelData strings[0]."""
    model = {
        "model": {
            "type": "minecraft:select",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "cases": [
                {
                    "when": custom_model_key(logical_id),
                    "model": {
                        "type": "minecraft:model",
                        "model": f"extendednoteblock:item/{logical_id}",
                    },
                }
            ],
            "fallback": {
                "type": "minecraft:model",
                "model": vanilla_model,
            },
        }
    }
    return (json.dumps(model, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


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

This pack contains the original visual assets shared by Full Fabric and Paper Client.

Paper/Purpur item behavior
--------------------------
Bridge items remain vanilla ItemStacks. The Paper plugin keeps its authoritative
`enb_type` PDC marker and also writes a visual-only CustomModelData string such as:

    extendednoteblock:conductor_wand

The resource pack reads strings[0] from minecraft:custom_model_data:
- matching ENB string -> original ExtendedNoteBlock model
- ordinary/no CustomModelData -> explicit vanilla fallback model

Therefore players without this resource pack still see completely normal vanilla
carriers, and ordinary Blaze Rods / Note Blocks / concrete items are unchanged.
Paper Client embeds this same pack automatically.

Placed blocks
-------------
A plain resource pack cannot distinguish plugin metadata by world coordinate, so
placed bridge blocks intentionally remain vanilla-looking for now. Paper Client can
later add position-aware rendering without changing ordinary vanilla blocks.
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

        # Safe vanilla item-definition overrides: each selector has a vanilla
        # fallback and switches only for the exact ENB CustomModelData string.
        for carrier, (logical_id, vanilla_model) in CARRIER_ITEMS.items():
            zf.writestr(
                f"assets/minecraft/items/{carrier}.json",
                carrier_selector(logical_id, vanilla_model),
            )

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
            *(f"assets/minecraft/items/{carrier}.json" for carrier in CARRIER_ITEMS),
        }
        missing = sorted(required - names)
        if missing:
            raise SystemExit(f"Visual resource pack is missing required entries: {missing}")

        # Never globally replace vanilla blockstates/models/textures. Only the
        # five item-definition selectors above are allowed in minecraft namespace.
        allowed_minecraft = {f"assets/minecraft/items/{carrier}.json" for carrier in CARRIER_ITEMS}
        unexpected_minecraft_entries = sorted(
            name for name in names
            if name.startswith("assets/minecraft/") and name not in allowed_minecraft
        )
        if unexpected_minecraft_entries:
            raise SystemExit(
                "Visual pack has unexpected global vanilla overrides: "
                + repr(unexpected_minecraft_entries[:20])
            )

        for carrier, (logical_id, vanilla_model) in CARRIER_ITEMS.items():
            raw = check.read(f"assets/minecraft/items/{carrier}.json").decode("utf-8")
            key = custom_model_key(logical_id)
            if '"property": "minecraft:custom_model_data"' not in raw or key not in raw or vanilla_model not in raw:
                raise SystemExit(f"Carrier selector {carrier} is missing CustomModelData match or vanilla fallback")

    print(output)


if __name__ == "__main__":
    main()
