package com.goldenegggovo.extendednoteblock.bridge;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;

/** One server pack; checks the atomically published file, never a half-built CE archive. */
final class EnbCombinedPack implements AutoCloseable {
    private final ExtendedNoteBlockBridge plugin;
    private final Path file;
    private final String url, prompt;
    private final UUID id;
    private final boolean required;
    private final Map<UUID,String> offered = new HashMap<>();
    private String checked="", observed="";
    private volatile boolean closed;
    private BukkitTask watcher, offers;
    EnbCombinedPack(ExtendedNoteBlockBridge plugin) {
        this.plugin=plugin;
        file=Path.of(plugin.getConfig().getString("resource-pack.combined-file"));
        url=plugin.getConfig().getString("resource-pack.url","");
        var uri=java.net.URI.create(url);
        if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getRawQuery()!=null || uri.getRawFragment()!=null)
            throw new IllegalArgumentException("Combined pack needs an HTTP(S) URL without query or fragment");
        id=UUID.fromString(plugin.getConfig().getString("resource-pack.id","a452911d-2d41-4db4-8986-d9b2b8f8aeb0"));
        required=plugin.getConfig().getBoolean("resource-pack.required",false);
        prompt=plugin.getConfig().getString("resource-pack.prompt","ExtendedNoteBlock resources");
        plugin.listenerPackId=id;
        plugin.listenerPackSource="CraftEngine combined pack (waiting for verified publication)";
    }
    void enable() {
        watcher=Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,this::poll,20,100);
        offers=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            offered.keySet().removeIf(uuid->Bukkit.getPlayer(uuid)==null);
            for(Player p:Bukkit.getOnlinePlayers())offer(p,false);
        },60,100);
    }
    private void poll() {
        if(closed || !Files.isRegularFile(file))return;
        try {
            String signature=Files.size(file)+":"+Files.getLastModifiedTime(file).toMillis();
            if(!signature.equals(observed)){observed=signature;return;}
            if(signature.equals(checked))return;
            String hash=verify(file);
            if(!signature.equals(Files.size(file)+":"+Files.getLastModifiedTime(file).toMillis()))return;
            checked=signature;
            Bukkit.getScheduler().runTask(plugin,()->{
                if(closed || hash.equals(plugin.listenerPackSha1Hex))return;
                plugin.listenerPackId=id; plugin.listenerPackUrl=url+"?v="+hash;
                plugin.listenerPackSha1=HexFormat.of().parseHex(hash); plugin.listenerPackSha1Hex=hash;
                plugin.listenerPackRequired=required; plugin.listenerPackPrompt=Component.text(prompt);
                plugin.listenerPackSource="CraftEngine combined pack"; plugin.listenerPackEnabled=true;
                plugin.listenerPackReady.clear(); plugin.listenerPackStates.clear(); offered.clear();
                plugin.getLogger().info("ENB combined pack verified: SHA-1="+hash);
                for(Player p:Bukkit.getOnlinePlayers())offer(p,false);
            });
        }catch(IOException|RuntimeException ex){plugin.getLogger().warning("ENB combined pack not ready; retaining last valid pack: "+ex.getMessage());}
    }
    static String verify(Path file) throws IOException {
        try(ZipFile zip=new ZipFile(file.toFile())) {
            for(String name:List.of("pack.mcmeta","assets/extendednoteblock_listener/sounds.json","assets/extendednoteblock/models/block/c.json"))
                if(zip.getEntry(name)==null)throw new IOException("Missing ENB pack asset: "+name);
            for(var entries=zip.entries();entries.hasMoreElements();) {
                var entry=entries.nextElement(); if(entry.isDirectory())continue;
                CRC32 crc=new CRC32();
                try(var in=new CheckedInputStream(zip.getInputStream(entry),crc)){in.transferTo(OutputStream.nullOutputStream());}
                if(crc.getValue()!=entry.getCrc())throw new IOException("Pack CRC mismatch: "+entry.getName());
            }
        }
        try {
            var digest=MessageDigest.getInstance("SHA-1");
            try(var in=new DigestInputStream(Files.newInputStream(file),digest)){in.transferTo(OutputStream.nullOutputStream());}
            return HexFormat.of().formatHex(digest.digest());
        }catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
    void offer(Player player,boolean force) {
        if(closed || !plugin.listenerPackEnabled || !eligible(player))return;
        if(!force && plugin.listenerPackSha1Hex.equals(offered.get(player.getUniqueId())))return;
        offered.put(player.getUniqueId(),plugin.listenerPackSha1Hex);
        plugin.listenerPackReady.remove(player.getUniqueId());
        plugin.listenerPackStates.put(player.getUniqueId(),"REQUESTED");
        player.addResourcePack(id,plugin.listenerPackUrl,plugin.listenerPackSha1,prompt,required);
    }
    private boolean eligible(Player p) {
        if(!p.isOnline())return false;
        var geyser=Bukkit.getPluginManager().getPlugin("Geyser-Spigot");
        if(geyser!=null && geyser.isEnabled())try {
            var api=Class.forName("org.geysermc.geyser.api.GeyserApi",true,geyser.getClass().getClassLoader());
            if((boolean)api.getMethod("isBedrockPlayer",UUID.class).invoke(api.getMethod("api").invoke(null),p.getUniqueId()))return false;
        }catch(ReflectiveOperationException|LinkageError ex){return false;}
        var auth=Bukkit.getPluginManager().getPlugin("AuthMe");
        if(auth!=null)try {
            if(!auth.isEnabled())return false;
            var api=Class.forName("fr.xephi.authme.api.v3.AuthMeApi",true,auth.getClass().getClassLoader());
            return (boolean)api.getMethod("isAuthenticated",Player.class).invoke(api.getMethod("getInstance").invoke(null),p);
        }catch(ReflectiveOperationException|LinkageError ex){return false;}
        return true;
    }
    @Override public void close(){closed=true;if(watcher!=null)watcher.cancel();if(offers!=null)offers.cancel();}
}
