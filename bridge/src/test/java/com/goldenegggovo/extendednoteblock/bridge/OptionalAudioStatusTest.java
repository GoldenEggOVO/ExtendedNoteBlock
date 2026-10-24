package com.goldenegggovo.extendednoteblock.bridge;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import java.util.*;
class OptionalAudioStatusTest {
 @Test void unselectedSuccessCannotEnableListener() {
  var server=MockBukkit.mock();
  try {var plugin=MockBukkit.load(ExtendedNoteBlockBridge.class);var player=server.addPlayer();plugin.getConfig().set("resource-pack.optional-audio.enabled",true);plugin.listenerPackId=UUID.randomUUID();
   plugin.onPlayerResourcePackStatus(new PlayerResourcePackStatusEvent(player,plugin.listenerPackId,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
   assertFalse(plugin.listenerPackReady.contains(player.getUniqueId()));
  } finally {MockBukkit.unmock();}
 }
}
