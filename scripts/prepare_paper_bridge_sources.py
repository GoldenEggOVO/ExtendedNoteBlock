#!/usr/bin/env python3
"""Prepare the Paper bridge source for current 26.2 runtime-only APIs.

The bridge keeps its source compatible/readable while CI applies the version-specific
Paper DataComponent call that gives marked vanilla carrier items their ENB item model.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "bridge" / "src" / "main" / "java" / "com" / "goldenegggovo" / "extendednoteblock" / "bridge" / "ExtendedNoteBlockBridge.java"

text = SOURCE.read_text(encoding="utf-8")
original = text

import_anchor = "import org.bukkit.scheduler.BukkitTask;\n"
imports = (
    "import org.bukkit.scheduler.BukkitTask;\n"
    "import io.papermc.paper.datacomponent.DataComponentTypes;\n"
    "import net.kyori.adventure.key.Key;\n"
)
if "io.papermc.paper.datacomponent.DataComponentTypes" not in text:
    if import_anchor not in text:
        raise SystemExit("Could not find Paper bridge import anchor")
    text = text.replace(import_anchor, imports, 1)

old = """        stack.setItemMeta(meta);\n        return stack;\n    }\n\n    private BridgeItemType getBridgeItemType"""
new = """        stack.setItemMeta(meta);\n        // Minecraft 26.2 item_model points this vanilla carrier at the original\n        // ExtendedNoteBlock item model. Clients without the resource pack simply\n        // fall back to the carrier; ordinary unmarked carrier items are untouched.\n        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(\"extendednoteblock\", type.id));\n        return stack;\n    }\n\n    private BridgeItemType getBridgeItemType"""
if "stack.setData(DataComponentTypes.ITEM_MODEL" not in text:
    if old not in text:
        raise SystemExit("Could not find createBridgeItem patch anchor")
    text = text.replace(old, new, 1)

if text != original:
    SOURCE.write_text(text, encoding="utf-8")
    print(f"patched {SOURCE.relative_to(ROOT)}")
else:
    print("Paper bridge source already prepared")
