#!/usr/bin/env python3
"""Wire the bounded schematic transport into the generated Paper bridge."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / 'bridge/src/main/java/com/goldenegggovo/extendednoteblock/bridge/ExtendedNoteBlockBridge.java'
text = path.read_text(encoding='utf-8')
if '    private BridgeSchematicTransfer schematicTransfer;' not in text:
    text = text.replace('import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport;',
                        'import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport;\nimport com.atemukesu.extendednoteblock.bridgeprotocol.SchematicTransfer;')
    text = text.replace('    private BridgeProjectionImporter projectionImporter;',
                        '    private BridgeProjectionImporter projectionImporter;\n    private BridgeSchematicTransfer schematicTransfer;')
    anchor = '        getLogger().info("ExtendedNoteBlockBridge enabled for Paper/Purpur 26.2");'
    if anchor not in text:
        raise SystemExit('Schematic preparation requires the Paper bridge enable anchor')
    text = text.replace(anchor, '''        schematicTransfer = new BridgeSchematicTransfer(this, new BridgeSchematicTransfer.Target() {
            @Override public SchematicTransfer.Document capture(World world, List<SchematicTransfer.Box> boxes) {
                return captureSchematic(world, boxes);
            }
            @Override public boolean accepts(World world, SchematicTransfer.Entry entry) {
                return acceptsSchematic(world, entry);
            }
            @Override public boolean commit(World world, SchematicTransfer.Document document) {
                return restoreSchematic(world, document);
            }
        });
''' + anchor)
    text = text.replace('    public void onDisable() {',
                        '    public void onDisable() {\n        if (schematicTransfer != null) schematicTransfer.close();')
    fragment = (ROOT / 'scripts/templates/schematic_methods.java').read_text(encoding='utf-8')
    text = text.replace('    private int interactionRange() {', fragment + '\n    private int interactionRange() {')
    path.write_text(text, encoding='utf-8')
print('Prepared Paper schematic capture and automatic import')
