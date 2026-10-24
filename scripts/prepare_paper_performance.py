#!/usr/bin/env python3
"""Install bounded render polling after CraftEngine, schematic and audio preparation."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "bridge/src/main/java/com/goldenegggovo/extendednoteblock/bridge/ExtendedNoteBlockBridge.java"


def replace_method(source: str, name: str, replacement: str) -> str:
    match = re.search(r"^    (?:private |public |protected )?[^\n]+\b" + name + r"\([^\n]*\) \{\n", source, re.MULTILINE)
    if match is None:
        raise ValueError("Missing render preparation method: " + name)
    end = source.index("\n    }\n", match.end()) + len("\n    }\n")
    return source[:match.start()] + replacement + source[end:]


def main() -> None:
    source = PATH.read_text(encoding="utf-8")
    if "BridgeChunkIndex renderIndex" in source:
        return
    anchor = "    final Map<String, RenderObjectState> lastRenderStates = new HashMap<>();\n"
    if anchor not in source:
        raise ValueError("Performance preparation requires CraftEngine render hooks")
    source = source.replace(anchor, anchor + "    private final BridgeChunkIndex renderIndex = new BridgeChunkIndex();\n"
                            "    private final Set<String> dirtyRenderObjects = new HashSet<>();\n", 1)
    source = source.replace("        blockRefs.clear();", "        blockRefs.clear();\n        renderIndex.clear();\n        dirtyRenderObjects.addAll(lastRenderStates.keySet());", 1)
    anchor = "    void indexObject(String key, BridgeItemType type) {\n        BlockRef ref = parseKey(key);"
    if anchor not in source:
        raise ValueError("Missing object registration hook")
    source = source.replace(anchor, anchor + "\n        indexRenderObject(key, ref);", 1)
    anchor = "    void unindexObject(String key, BridgeItemType type) {\n        BlockRef ref = blockRefs.remove(key);"
    if anchor not in source:
        raise ValueError("Missing object removal hook")
    source = source.replace(anchor, anchor + "\n        renderIndex.remove(key);\n        markRenderRouteDirty(key);\n        if (craftEngine != null) craftEngine.forget(key);", 1)
    source = source.replace("                globalPower.put(worldId, newPower);", "                globalPower.put(worldId, newPower);\n                dirtyRenderObjects.addAll(receiversByWorld.getOrDefault(worldId, Set.of()));", 1)
    anchor = "            boolean targetChanged = !Objects.equals(previousProjectionTarget, projectionTarget);"
    source = source.replace(anchor, anchor + """
            if (wasPowered != powered || targetChanged) {
                dirtyRenderObjects.add(transmitterKey);
                if (previousProjectionTarget != null) dirtyRenderObjects.add(previousProjectionTarget);
                if (projectionTarget != null) dirtyRenderObjects.add(projectionTarget);
            }""", 1)
    template = (ROOT / "scripts/templates/render_polling.java").read_text(encoding="utf-8")
    parts = template.split("    // METHOD: ")
    for part in parts[1:]:
        name, method = part.split("\n", 1)
        source = replace_method(source, name.strip(), method)
    helpers = (ROOT / "scripts/templates/render_polling_helpers.java").read_text(encoding="utf-8")
    source = source.replace("    private boolean isProjectionPowered(String receiverKey) {", helpers + "\n    private boolean isProjectionPowered(String receiverKey) {", 1)
    PATH.write_text(source, encoding="utf-8")
    print("Prepared loaded-chunk render polling and shared CraftEngine block queries")


if __name__ == "__main__":
    main()
