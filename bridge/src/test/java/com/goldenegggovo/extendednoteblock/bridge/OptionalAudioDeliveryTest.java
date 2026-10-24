package com.goldenegggovo.extendednoteblock.bridge;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.core.plugin.config.Config;
import java.nio.file.*;
import java.util.*;
class OptionalAudioDeliveryTest {
 @Test void defaultsBaseOnlyAndOptInOffAndDeclineRemainIsolated() throws Exception {
  var server=MockBukkit.mock();
  try {
   var plugin=MockBukkit.load(ExtendedNoteBlockBridge.class);
   plugin.getConfig().set("resource-pack.optional-audio.enabled",true);
   try(var config=mockStatic(Config.class)) {
    var combined=mock(EnbCombinedPack.class,CALLS_REAL_METHODS);
    doReturn(false).when(combined).automaticDelivery();
    for(var entry:Map.<String,Object>of("optional",true,"plugin",plugin,"offered",new HashMap<>(),"audioOffered",new HashMap<>(),"publicUrl","http://localhost/","id",UUID.randomUUID(),"required",true,"prompt","base","pending",new HashMap<>()).entrySet()) {
     var field=EnbCombinedPack.class.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(combined,entry.getValue());
    }
    plugin.combinedPack=combined;plugin.listenerPackEnabled=true;plugin.listenerPackId=EnbCombinedPack.AUDIO_ID;
    var pub=EnbCombinedPack.class.getDeclaredField("published");pub.setAccessible(true);
    pub.set(combined,new OptionalAudioPack.Published(Path.of("base.zip"),Path.of("audio.zip"),"a".repeat(40),"b".repeat(40)));
    Player p=mock(Player.class);UUID id=UUID.randomUUID();when(p.getUniqueId()).thenReturn(id);when(p.hasPermission(anyString())).thenReturn(true);doReturn(true).when(combined).eligible(p);
    doReturn(true).when(combined).automaticDelivery();
    combined.offer(p,false);
    verify(p,never()).addResourcePack(any(),anyString(),any(),anyString(),anyBoolean());
    doReturn(false).when(combined).automaticDelivery();
    combined.offer(p,false);
    verify(p,times(1)).addResourcePack(any(),anyString(),any(),anyString(),eq(true));
    verify(p,never()).addResourcePack(eq(EnbCombinedPack.AUDIO_ID),anyString(),any(),anyString(),anyBoolean());
    plugin.onCommand(p,new org.bukkit.command.Command("enbaudio") {public boolean execute(org.bukkit.command.CommandSender s,String l,String[] a){return true;}},"enbaudio",new String[]{"on"});assertTrue(plugin.isOptionalAudioEnabled(id));
    verify(p,times(1)).addResourcePack(eq(EnbCombinedPack.AUDIO_ID),anyString(),any(),anyString(),eq(false));
    plugin.onPlayerResourcePackStatus(new PlayerResourcePackStatusEvent(p,EnbCombinedPack.AUDIO_ID,PlayerResourcePackStatusEvent.Status.DECLINED));
    combined.offer(p,false);verify(p,times(1)).addResourcePack(eq(EnbCombinedPack.AUDIO_ID),anyString(),any(),anyString(),eq(false));
    plugin.onCommand(p,new org.bukkit.command.Command("enbaudio") {public boolean execute(org.bukkit.command.CommandSender s,String l,String[] a){return true;}},"enbaudio",new String[]{"off"});verify(p).removeResourcePack(EnbCombinedPack.AUDIO_ID);assertFalse(plugin.isOptionalAudioEnabled(id));
    plugin.onPlayerResourcePackStatus(new PlayerResourcePackStatusEvent(p,EnbCombinedPack.AUDIO_ID,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));assertFalse(plugin.listenerPackReady.contains(id));
    combined.offer(p,false);verify(p,times(1)).addResourcePack(any(),anyString(),any(),anyString(),eq(true));
    plugin.onCommand(p,new org.bukkit.command.Command("enbaudio") {public boolean execute(org.bukkit.command.CommandSender s,String l,String[] a){return true;}},"enbaudio",new String[]{"on"});
    combined.forget(id);combined.offer(p,false);
    assertTrue(plugin.isOptionalAudioEnabled(id));verify(p,times(3)).addResourcePack(eq(EnbCombinedPack.AUDIO_ID),anyString(),any(),anyString(),eq(false));
    plugin.onPlayerResourcePackStatus(new PlayerResourcePackStatusEvent(p,EnbCombinedPack.AUDIO_ID,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));assertTrue(plugin.listenerPackReady.contains(id));
    pub.set(combined,new OptionalAudioPack.Published(Path.of("base.zip"),Path.of("audio2.zip"),"a".repeat(40),"c".repeat(40)));
    combined.offer(p,false);assertFalse(plugin.listenerPackReady.contains(id));
    verify(p,times(2)).addResourcePack(any(),anyString(),any(),anyString(),eq(true));
   }
  } finally {MockBukkit.unmock();}
 }
}
