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
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.core.plugin.config.Config;
import net.momirealms.craftengine.core.pack.host.ResourcePackDownloadData;

/** One server pack; checks the atomically published file, never a half-built CE archive. */
final class EnbCombinedPack implements AutoCloseable {
    private final ExtendedNoteBlockBridge plugin;
    private final BukkitCraftEngine engine;
    private final String prompt;
    private final UUID id;
    private final boolean required;
    private final Map<UUID,String> offered = new HashMap<>();
    private String checked="", observed="", lastProblem="";
    private final Map<UUID,Object> pending = new HashMap<>();
    private volatile boolean closed;
    private BukkitTask watcher, offers;
    EnbCombinedPack(ExtendedNoteBlockBridge plugin) {
        this.plugin=plugin;
        engine=BukkitCraftEngine.instance();
        if(engine==null)throw new IllegalStateException("CraftEngine is not ready");
        id=UUID.fromString("a452911d-2d41-4db4-8986-d9b2b8f8aeb0");
        required=plugin.getConfig().getBoolean("resource-pack.required",true);
        prompt=plugin.getConfig().getString("resource-pack.prompt","ExtendedNoteBlock resources");
        plugin.listenerPackId=id;
        plugin.listenerPackSource="CraftEngine combined pack (waiting for verified publication)";
    }
    void enable() {
        watcher=Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,this::poll,20,100);
        offers=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            offered.keySet().removeIf(uuid->Bukkit.getPlayer(uuid)==null);
            pending.keySet().removeIf(uuid->Bukkit.getPlayer(uuid)==null);
            for(Player p:Bukkit.getOnlinePlayers())offer(p,false);
        },60,100);
    }
    private void poll() {
        if(closed)return;
        Path file=engine.packManager().resourcePackPath();
        if(!Files.isRegularFile(file))return;
        try {
            String signature=file.toAbsolutePath()+":"+Files.size(file)+":"+Files.getLastModifiedTime(file).toMillis();
            if(!signature.equals(observed)){observed=signature;return;}
            if(signature.equals(checked))return;
            String hash=verify(file);
            if(!signature.equals(file.toAbsolutePath()+":"+Files.size(file)+":"+Files.getLastModifiedTime(file).toMillis()))return;
            checked=signature;
            Bukkit.getScheduler().runTask(plugin,()->{
                if(closed || hash.equals(plugin.listenerPackSha1Hex))return;
                plugin.listenerPackId=id; plugin.listenerPackUrl="Resolved from CraftEngine host for each player";
                plugin.listenerPackSha1=HexFormat.of().parseHex(hash); plugin.listenerPackSha1Hex=hash;
                plugin.listenerPackRequired=required; plugin.listenerPackPrompt=Component.text(prompt);
                plugin.listenerPackSource="CraftEngine combined pack"; plugin.listenerPackEnabled=true;
                plugin.listenerPackReady.clear(); plugin.listenerPackStates.clear(); offered.clear(); pending.clear();
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
    static ResourcePackDownloadData selectDownload(List<ResourcePackDownloadData> downloads,String hash) {
        if(downloads==null)return null;
        for(var data:downloads) {
            if(data==null || !hash.equalsIgnoreCase(data.sha1()))continue;
            try {
                var uri=java.net.URI.create(data.url());
                if(Set.of("http","https").contains(uri.getScheme()) && uri.getHost()!=null
                        && uri.getUserInfo()==null && uri.getFragment()==null)return data;
            }catch(IllegalArgumentException ignored) {}
        }
        return null;
    }
    void offer(Player player,boolean force) {
        if(closed || !plugin.listenerPackEnabled || !eligible(player))return;
        if(Config.sendPackOnJoin() || Config.sendPackOnUpload()) {
            problem("Set CraftEngine resource-pack.delivery.send-on-join and resend-on-upload to false; ENB sends the CraftEngine pack and tracks its load status.");
            if(force)player.sendMessage("ENB: Disable CraftEngine send-on-join and resend-on-upload to avoid duplicate resource-pack requests.");
            return;
        }
        UUID playerId=player.getUniqueId();
        String hash=plugin.listenerPackSha1Hex;
        if(pending.containsKey(playerId) || (!force && hash.equals(offered.get(playerId))))return;
        var user=engine.networkManager().getOnlineUser(playerId);
        if(user==null)return;
        Object request=new Object(); pending.put(playerId,request);
        try {
            engine.packManager().resourcePackHost().requestResourcePackDownloadLink(user)
                    .orTimeout(30,java.util.concurrent.TimeUnit.SECONDS).whenComplete((downloads,error)-> {
                if(closed)return;
                Bukkit.getScheduler().runTask(plugin,()-> {
                    if(closed || pending.get(playerId)!=request)return;
                    pending.remove(playerId);
                    if(!eligible(player) || !hash.equals(plugin.listenerPackSha1Hex))return;
                    var data=error==null ? selectDownload(downloads,hash) : null;
                    if(data==null) {
                        problem("CraftEngine host has no download matching the verified ENB pack. Generate/upload the complete CraftEngine pack first.");
                        if(force)player.sendMessage("ENB: The complete CraftEngine pack is missing or its hosted checksum differs. Contact the server owner.");
                        return;
                    }
                    if(Config.sendPackOnJoin() || Config.sendPackOnUpload())return;
                    lastProblem="";
                    offered.put(playerId,hash);
                    plugin.listenerPackReady.remove(playerId);
                    plugin.listenerPackStates.put(playerId,"REQUESTED");
                    // A separate request ID keeps CraftEngine from consuming the Bukkit status event.
                    // The bytes, URL and hash are exclusively the verified CraftEngine output.
                    player.addResourcePack(id,data.url(),HexFormat.of().parseHex(hash),prompt,required);
                });
            });
        }catch(RuntimeException ex) {
            pending.remove(playerId);
            problem("CraftEngine download lookup failed: " + ex.getMessage());
        }
    }
    private void problem(String message) {
        if(!message.equals(lastProblem))plugin.getLogger().warning(message);
        lastProblem=message;
    }
    void forget(UUID playerId) {
        pending.remove(playerId);
        offered.remove(playerId);
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
