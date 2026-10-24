#!/usr/bin/env python3
"""Add optional ENB audio controls after CraftEngine and schematic preparation.

Only generated Java source is changed; existing server preferences stay untouched.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / 'bridge/src/main/java/com/goldenegggovo/extendednoteblock/bridge/ExtendedNoteBlockBridge.java'
source = path.read_text(encoding='utf-8')


def replace_once(anchor: str, replacement: str) -> None:
    global source
    if anchor not in source:
        raise SystemExit('Missing optional audio preparation anchor: ' + anchor[:120])
    source = source.replace(anchor, replacement, 1)


if '    private OptionalAudioPreferences optionalAudioPreferences;' not in source:
    replace_once('    EnbCombinedPack combinedPack;',
                 '    EnbCombinedPack combinedPack;\n    private OptionalAudioPreferences optionalAudioPreferences;')
    replace_once('    public void onEnable() {', '''    public void onEnable() {
        try { optionalAudioPreferences = new OptionalAudioPreferences(getDataFolder().toPath().resolve("optional-audio-players.txt")); }
        catch (IOException | IllegalArgumentException ex) { getLogger().severe("Cannot load optional audio preferences: " + ex.getMessage()); }''')
    replace_once('sendListenerResourcePack(event.getPlayer(), true), 40L)',
                 'sendListenerResourcePack(event.getPlayer(),\n                !getConfig().getBoolean("resource-pack.optional-audio.enabled", false)), 40L)')
    replace_once('        if (listenerPackId == null || !listenerPackId.equals(event.getID())) return;',
                 '''        if (listenerPackId == null || !listenerPackId.equals(event.getID())) return;
        if (getConfig().getBoolean("resource-pack.optional-audio.enabled", false)
                && (combinedPack == null || !combinedPack.acceptsAudioStatus(event.getPlayer()))) return;''')
    replace_once('    @Override\n    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {', '''    public boolean isOptionalAudioEnabled(UUID playerId) {
        return optionalAudioPreferences != null && optionalAudioPreferences.enabled(playerId);
    }

    private boolean handleAudioCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("This command requires a player."); return true; }
        if (!player.hasPermission("extendednoteblockbridge.audio")) return true;
        if (!getConfig().getBoolean("resource-pack.optional-audio.enabled", false) || combinedPack == null || optionalAudioPreferences == null) {
            player.sendMessage("ENB optional audio is currently unavailable."); return true;
        }
        if (!combinedPack.eligible(player)) { player.sendMessage("Complete login authentication before changing ENB audio."); return true; }
        String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("status")) {
            player.sendMessage("ENB optional audio: " + (isOptionalAudioEnabled(player.getUniqueId()) ? "enabled" : "disabled")
                    + "; load status: " + listenerPackStates.getOrDefault(player.getUniqueId(), "NOT_REQUESTED")); return true;
        }
        if (!action.equals("on") && !action.equals("off")) { player.sendMessage("/enbaudio on|off|status"); return true; }
        try { optionalAudioPreferences.set(player.getUniqueId(), action.equals("on")); }
        catch (IOException ex) { player.sendMessage("Could not save audio preferences. Contact the server owner."); getLogger().warning(ex.toString()); return true; }
        if (action.equals("on")) { combinedPack.offer(player, false); player.sendMessage("Optional ENB audio enabled; accept the audio resource pack."); }
        else {
            combinedPack.disableAudio(player);
            for (ListenerPlayback playback : listenerSounds.values()) if (playback.players().contains(player.getUniqueId()))
                player.stopSound(playback.event(), SoundCategory.RECORDS);
            player.sendMessage("ENB audio disabled; the base resource pack is retained.");
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command != null && command.getName().equalsIgnoreCase("enbaudio")) return handleAudioCommand(sender, args);''')

path.write_text(source, encoding='utf-8')
print('Prepared optional ENB audio preferences, commands and isolated load status')
