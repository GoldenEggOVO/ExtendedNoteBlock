#!/usr/bin/env python3
"""Inject the CraftEngine backend after the other Paper preparation steps.

Only build source is modified. Server data/configuration is never read or written.
"""
from pathlib import Path
import shutil
import re

ROOT = Path(__file__).resolve().parents[1]
base = ROOT / 'bridge/src/main/java/com/goldenegggovo/extendednoteblock/bridge'
p = base / 'ExtendedNoteBlockBridge.java'
s = p.read_text(encoding='utf-8')

def replace(a, b):
    global s
    if a not in s:
        raise SystemExit('Missing CraftEngine preparation anchor: ' + a[:120])
    s = s.replace(a, b)

if '    EnbCraftEngine craftEngine;' not in s:
    replace('    private NamespacedKey bridgeTypeKey;', '    private NamespacedKey bridgeTypeKey;\n    EnbCraftEngine craftEngine;')
    replace('        startTickers();\n        projectionImporter', '''        if (getConfig().getBoolean("craftengine.enabled", true)
                    && Bukkit.getPluginManager().isPluginEnabled("CraftEngine")) {
                craftEngine = new EnbCraftEngine(this);
                craftEngine.enable();
            }
            startTickers();
            projectionImporter''')
    replace('    public void onDisable() {','    public void onDisable() {\n        if (craftEngine != null) craftEngine.close();')
    # Package-private hooks are limited to the optional, same-package integration.
    for name in ['objects','notes','projectionNotes','transmitterProjectionTarget','transmitterPower','globalPower','lastRenderStates']:
        import re
        s=re.sub(r'    private final (Map<[^\n]+> '+name+r' =)',r'    final \1',s)
    for signature in ['enum BridgeItemType','record RenderObjectState','record BlockRef','record NoteConfig']:
        replace('    private '+signature,'    '+signature)
    for name in ['id','displayName','carrier','placeable']:
        s=s.replace('        private final String '+name+';', '        final String '+name+';').replace('        private final Material '+name+';', '        final Material '+name+';').replace('        private final boolean '+name+';', '        final boolean '+name+';')
    for signature in ['String key(Block block)','Block getLoadedBlock(String key)','BlockRef parseKey(String key)','void indexObject(String key, BridgeItemType type)','void unindexObject(String key, BridgeItemType type)','void requestSave(String section)','NoteConfig defaultNoteConfig()','void stopActive(String key)','void stopProjection(String receiverKey)','void startConfiguredSound(Block block, NoteConfig cfg)','RenderObjectState currentRenderState(String objectKey, BridgeItemType type)','ItemStack createBridgeItem(BridgeItemType type, int amount)','BridgeItemType getBridgeItemType(ItemStack stack)']:
        # Some existing signatures are static or use a different argument name.
        method=signature.split('(')[0].split()[-1]
        s=re.sub(r'    private (static )?([^\n]+\b'+method+r'\()',lambda m:'    '+(m.group(1) or '')+m.group(2),s)

    # Preserve ordinary carrier placement; CE uses its own final cancellable event.
    replace('        if (block.getType() != type.carrier) return;', '''        if (craftEngine != null && craftEngine.managed(block)) return;
            if (block.getType() != type.carrier) return;''')
    replace('        String key = key(block);\n        BridgeItemType type = objects.remove(key);', '''        if (craftEngine != null && craftEngine.managed(block)) return;
            String key = key(block);
            BridgeItemType type = objects.remove(key);''')
    replace('block.getType() != Material.NOTE_BLOCK','!isNoteCarrier(block)')
    replace('transmitter.getType() == Material.RED_CONCRETE', 'isCarrier(transmitter, BridgeItemType.GLOBAL_REDSTONE_TRANSMITTER)')
    replace('    private void setReceiverPowered(Block block, boolean powered) {', '''    private void setReceiverPowered(Block block, boolean powered) {
            if (craftEngine != null && craftEngine.powerReceiver(block, powered)) return;''')
    replace('            RenderObjectState next = currentRenderState(entry.getKey(), type);', '''            RenderObjectState next = currentRenderState(entry.getKey(), type);
                if (craftEngine != null) craftEngine.sync(entry.getKey(), type, next);''')
    replace('    private void syncChangedRenderStates() {','    private void syncChangedRenderStates() {\n        if (craftEngine != null) craftEngine.beginTick();')
    replace('        ItemStack stack = new ItemStack(type.carrier, amount);', '''        ItemStack stack = craftEngine == null ? null : craftEngine.item(type.id, amount);
            if (stack != null) {
                var ceMeta = stack.getItemMeta();
                ceMeta.getPersistentDataContainer().set(bridgeTypeKey, PersistentDataType.STRING, type.id);
                var ceModel = ceMeta.getCustomModelDataComponent();
                ceModel.setStrings(List.of("extendednoteblock:" + type.id));
                ceMeta.setCustomModelDataComponent(ceModel); stack.setItemMeta(ceMeta);
                return stack;
            }
            stack = new ItemStack(type.carrier, amount);''')
    replace('    BridgeItemType getBridgeItemType(ItemStack stack) {','''    BridgeItemType getBridgeItemType(ItemStack stack) {
            if (craftEngine != null) {
                BridgeItemType custom = craftEngine.itemType(stack);
                if (custom != null) return custom;
            }''')
    # Removing ENB identity restores a vanilla carrier first, retaining the original command semantics.
    replace('        BridgeItemType removed = objects.remove(key);', '''        if (craftEngine != null) craftEngine.restoreCarrier(block);
            BridgeItemType removed = objects.remove(key);''')
    anchor='    private Block targetNoteBlock(Player player, boolean autoConvert) {'
    replace(anchor,'''    boolean isCarrier(Block block, BridgeItemType type) {
            return block != null && (craftEngine != null && craftEngine.matches(block, type)
                    || block.getType() == type.carrier);
        }
        boolean isNoteCarrier(Block block) { return isCarrier(block, BridgeItemType.EXTENDED_NOTE_BLOCK); }

    '''+anchor)
    # The combined-pack watcher validates/hash-checks the published file off the server thread.
    replace('    EnbCraftEngine craftEngine;', '    EnbCraftEngine craftEngine;\n    EnbCombinedPack combinedPack;')
    replace('        if (craftEngine != null) craftEngine.close();', '        if (craftEngine != null) craftEngine.close();\n        if (combinedPack != null) combinedPack.close();')
    replace('    private void loadListenerResourcePackSettings() {', '''    private void loadListenerResourcePackSettings() {
            if (combinedPack != null) { combinedPack.close(); combinedPack = null; }
            if (!getConfig().getString("resource-pack.combined-file", "").isBlank()
                    && getConfig().getBoolean("resource-pack.enabled", true)) {
                listenerPackEnabled = false;
                listenerPackReady.clear(); listenerPackStates.clear();
                try {
                    combinedPack = new EnbCombinedPack(this);
                    combinedPack.enable();
                } catch (IllegalArgumentException invalid) {
                    getLogger().severe("Combined pack disabled: " + invalid.getMessage());
                }
                return;
            }''')
    replace('    private void sendListenerResourcePack(Player player) {', '''    /** Optional interop for the server's existing pack buttons. */
        public boolean requestCombinedResourcePack(Player player, boolean retry) {
            if (combinedPack == null) return false;
            combinedPack.offer(player, retry);
            return true;
        }

        private void sendListenerResourcePack(Player player) {''')
    replace('        if (!listenerPackEnabled || !player.isOnline()) return;', '''        if (combinedPack != null) { combinedPack.offer(player, announce); return; }
            if (!listenerPackEnabled || !player.isOnline()) return;''')
    replace('player.setResourcePack(listenerPackId, listenerPackUrl, listenerPackSha1,','player.addResourcePack(listenerPackId, listenerPackUrl, listenerPackSha1,')
    replace('                listenerPackPrompt, listenerPackRequired);', '                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(listenerPackPrompt), listenerPackRequired);')
    for name in ['listenerPackReady','listenerPackStates']:
        s=re.sub(r'    private final (.+ '+name+r' =)',r'    final \1',s)
    for name in ['listenerPackEnabled','listenerPackRequired','listenerPackId','listenerPackUrl','listenerPackSha1','listenerPackSha1Hex','listenerPackSource','listenerPackPrompt']:
        s=re.sub(r'    private (\S+ '+name+r';)',r'    \1',s)
    replace('        if (!(sender instanceof Player) && (args.length == 0', '''        if (args.length > 0 && args[0].equalsIgnoreCase("ce")) {
                if (craftEngine == null) sender.sendMessage("CraftEngine ENB integration is disabled.");
                else craftEngine.command(sender, args);
                return true;
            }
            if (!(sender instanceof Player) && (args.length == 0''')

    p.write_text(s, encoding='utf-8')
# The render preparer detects its private record and may reinsert it after the
# CE backend exposes that record to the same package. Keep just the shared one.
if '    record RenderObjectState(' in s:
    s = re.sub(r'    private record RenderObjectState\([^\n]+\) \{\n    \}\n\n', '', s)
p.write_text(s, encoding='utf-8')
for integration in (ROOT / 'craftengine/integration').glob('*.java'):
    shutil.copy2(integration, base / integration.name)
print('Prepared required CraftEngine backend (existing ENB data preserved).')
