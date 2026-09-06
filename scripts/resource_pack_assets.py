#!/usr/bin/env python3
"""Shared item-resource helpers for Minecraft 26.2 packs.

Paper bridge items stay ordinary vanilla ItemStacks. The plugin writes one
namespaced string to CustomModelData and these selectors choose the matching
ENB item model while explicitly falling back to the vanilla carrier model.

The helpers are shared by the Paper Client's built-in item pack and the
server-delivered listener pack. They do not build a standalone Visuals ZIP.
"""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSET_ROOT = ROOT / "src" / "main" / "resources" / "assets" / "extendednoteblock"
RESOURCE_PACK_FORMAT = 88
ITEM_ASSET_DIRS = ("blockstates", "items", "lang", "models", "textures")
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


def iter_item_asset_files():
    """Yield assets required by ENB inventory item models.

    Some item models reference block models and block textures, so the complete
    ENB model/texture tree is retained. Nothing here overrides a vanilla world
    blockstate; the only minecraft-namespace entries are item selectors built
    by :func:`carrier_selector`.
    """
    for directory in ITEM_ASSET_DIRS:
        base = ASSET_ROOT / directory
        if not base.exists():
            continue
        for path in sorted(base.rglob("*")):
            if path.is_file():
                yield path


def custom_model_key(logical_id: str) -> str:
    return f"{CMD_NAMESPACE}:{logical_id}"


def pack_metadata(description: str) -> dict:
    return {"pack": {
        "pack_format": RESOURCE_PACK_FORMAT,
        "min_format": RESOURCE_PACK_FORMAT,
        "max_format": RESOURCE_PACK_FORMAT,
        "description": description,
    }}


def carrier_selector(logical_id: str, vanilla_model: str) -> bytes:
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
