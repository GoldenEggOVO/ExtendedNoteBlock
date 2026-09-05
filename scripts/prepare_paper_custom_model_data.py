#!/usr/bin/env python3
"""Inject Minecraft 26.2 CustomModelData strings into Paper bridge carrier items.

PDC remains the authoritative server-side identity. CustomModelData is visual-only:
clients without the ENB resource pack ignore it and still render the vanilla carrier.
Clients with the Visuals pack / Paper Client use the string to select the original
ExtendedNoteBlock model.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "bridge" / "src" / "main" / "java" / "com" / "goldenegggovo" / "extendednoteblock" / "bridge" / "ExtendedNoteBlockBridge.java"

text = SOURCE.read_text(encoding="utf-8")
original = text

anchor = """        meta.getPersistentDataContainer().set(bridgeTypeKey, PersistentDataType.STRING, type.id);\n        stack.setItemMeta(meta);\n        return stack;\n"""
replacement = """        meta.getPersistentDataContainer().set(bridgeTypeKey, PersistentDataType.STRING, type.id);\n\n        // Visual identity only. The server still sees an ordinary minecraft:* carrier.\n        // Minecraft 26.2 resource packs can select a model from the strings list of\n        // minecraft:custom_model_data. Clients without the pack simply ignore it.\n        var customModelData = meta.getCustomModelDataComponent();\n        customModelData.setStrings(List.of(\"extendednoteblock:\" + type.id));\n        meta.setCustomModelDataComponent(customModelData);\n\n        stack.setItemMeta(meta);\n        return stack;\n"""

if "customModelData.setStrings(List.of(\"extendednoteblock:\" + type.id))" not in text:
    if anchor not in text:
        raise SystemExit("Could not find createBridgeItem CustomModelData patch anchor")
    text = text.replace(anchor, replacement, 1)

if text != original:
    SOURCE.write_text(text, encoding="utf-8")
    print(f"patched {SOURCE.relative_to(ROOT)}")
else:
    print("Paper bridge CustomModelData source already prepared")
