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
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.YamlUtils;
import net.momirealms.craftengine.core.pack.host.ResourcePackDownloadData;

/** One server pack; checks the atomically published file, never a half-built CE archive. */
final class EnbCombinedPack implements AutoCloseable {
    static final UUID AUDIO_ID = UUID.fromString("7056a49b-8a6d-4ce5-b4c8-33bf8d59889b");
    private final boolean optional;
    private final Path directory;
    private final String publicUrl;
    private OptionalAudioPack.Published published;
    private volatile String sourceHash = "";
    private final Map<UUID,String> audioOffered = new HashMap<>();
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
        optional=plugin.getConfig().getBoolean("resource-pack.optional-audio.enabled",false);
        directory=Path.of(plugin.getConfig().getString("resource-pack.optional-audio.directory",plugin.getDataFolder()+"/optional-audio"));
        publicUrl=plugin.getConfig().getString("resource-pack.optional-audio.public-url","");
        if(optional) {
            var uri=java.net.URI.create(publicUrl);
            if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null
                    || uri.getQuery()!=null || uri.getFragment()!=null || !publicUrl.endsWith("/"))
                throw new IllegalArgumentException("optional-audio.public-url must be an HTTP(S) directory URL ending with / without query");
        }
        engine=BukkitCraftEngine.instance();
        if(engine==null)throw new IllegalStateException("CraftEngine is not ready");
        id=UUID.fromString("a452911d-2d41-4db4-8986-d9b2b8f8aeb0");
        required=plugin.getConfig().getBoolean("resource-pack.required",true);
        prompt=plugin.getConfig().getString("resource-pack.prompt","ExtendedNoteBlock resources");
        plugin.listenerPackId=optional?AUDIO_ID:id;
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
        try {
            Path file=packSettings().path();
            if(file==null || !Files.isRegularFile(file))return;
            String signature=file.toAbsolutePath()+":"+Files.size(file)+":"+Files.getLastModifiedTime(file).toMillis();
            if(!signature.equals(observed)){observed=signature;return;}
            if(signature.equals(checked))return;
            String hash=verify(file);
            if(optional && hash.equals(sourceHash)){checked=signature;return;}
            var split=optional ? OptionalAudioPack.split(file,directory) : null;
            if(!signature.equals(file.toAbsolutePath()+":"+Files.size(file)+":"+Files.getLastModifiedTime(file).toMillis()))return;
            checked=signature;
            Bukkit.getScheduler().runTask(plugin,()->{
                if(closed)return;
                if(optional) {
                    published=split; sourceHash=hash;
                    plugin.listenerPackId=AUDIO_ID; plugin.listenerPackSha1Hex=split.audioHash();
                    plugin.listenerPackSha1=HexFormat.of().parseHex(split.audioHash());
                    plugin.listenerPackUrl=publicUrl+split.audio().getFileName();
                    plugin.listenerPackRequired=false; plugin.listenerPackPrompt=Component.text(EnbLanguage.text("Optional ENB audio"));
                    plugin.listenerPackSource="CraftEngine split optional audio"; plugin.listenerPackEnabled=true;
                    for(Player p:Bukkit.getOnlinePlayers())offer(p,false);
                    return;
                }
                if(hash.equals(plugin.listenerPackSha1Hex))return;
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
    record PackSettings(Path path, boolean automaticDelivery) {}

    // CE 26.9.1 has no resourcePackPath accessor; 26.9.2 derives it from the first ZIP workflow step.
    // Both versions expose loaded workflow names and the live settings document.
    private PackSettings packSettings() {
        return packSettings(engine.dataFolderPath(), YamlUtils.reader(Config.instance().settings()),
                engine.packManager().workflowNames(), Config.sendPackOnJoin());
    }
    boolean automaticDelivery() { return packSettings().automaticDelivery(); }
    static PackSettings packSettings(Path directory, YamlUtils.Reader settings,
                                     Collection<String> loadedWorkflows, boolean sendOnJoin) {
        Path path=null;
        boolean automatic=sendOnJoin || settings.getBoolean("resource-pack.delivery.resend-on-upload",false);
        Object definitions=settings.getValue("resource-pack.workflows");
        if(definitions instanceof Map<?,?> workflows)for(var definition:workflows.entrySet()) {
            if(!loadedWorkflows.contains(definition.getKey()) || !(definition.getValue() instanceof Map<?,?> workflow))continue;
            Object trigger=workflow.get("trigger");
            boolean onReload="reload_pack".equals(trigger)
                    || trigger instanceof Collection<?> triggers && triggers.contains("reload_pack");
            if(!(workflow.get("steps") instanceof List<?> steps))continue;
            for(Object value:steps) {
                if(!(value instanceof Map<?,?> step) || !(step.get("type") instanceof String type))continue;
                if(path==null && Key.ce("zip").equals(Key.ce(type)) && step.get("path") instanceof String output)
                    path=directory.resolve(output).toAbsolutePath().normalize();
                if(onReload && Key.ce("send_pack").equals(Key.ce(type)))automatic=true;
            }
        }
        return new PackSettings(path,automatic);
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
        if(automaticDelivery()) {
            problem("Disable CraftEngine send-on-join, resend-on-upload and automatic send_pack workflow steps; ENB sends the pack and tracks its load status.");
            if(force)player.sendMessage("ENB: Disable CraftEngine automatic pack delivery (send-on-join, resend-on-upload and reload send_pack steps) to avoid duplicate requests.");
            return;
        }
        if(optional){offerSplit(player,force);return;}
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
                    if(automaticDelivery())return;
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
    private void offerSplit(Player player, boolean force) {
        if(published==null)return;
        UUID uuid=player.getUniqueId();
        if(force || !published.baseHash().equals(offered.get(uuid))) {
            offered.put(uuid,published.baseHash());
            player.addResourcePack(id,publicUrl+published.base().getFileName(),HexFormat.of().parseHex(published.baseHash()),prompt,required);
        }
        if(plugin.isOptionalAudioEnabled(uuid) && (force || !published.audioHash().equals(audioOffered.get(uuid)))) {
            audioOffered.put(uuid,published.audioHash());
            plugin.listenerPackReady.remove(uuid);
            plugin.listenerPackStates.put(uuid,"REQUESTED");
            player.addResourcePack(AUDIO_ID,publicUrl+published.audio().getFileName(),HexFormat.of().parseHex(published.audioHash()),EnbLanguage.text("Optional ENB audio"),false);
        }
    }
    boolean acceptsAudioStatus(Player player) {
        return optional && eligible(player) && plugin.isOptionalAudioEnabled(player.getUniqueId())
                && published!=null && published.audioHash().equals(audioOffered.get(player.getUniqueId()));
    }
    void disableAudio(Player player) {
        audioOffered.remove(player.getUniqueId());
        plugin.listenerPackReady.remove(player.getUniqueId());
        plugin.listenerPackStates.remove(player.getUniqueId());
        player.removeResourcePack(AUDIO_ID);
    }
    private void problem(String message) {
        if(!message.equals(lastProblem))plugin.getLogger().warning(message);
        lastProblem=message;
    }
    void forget(UUID playerId) {
        pending.remove(playerId);
        offered.remove(playerId);
        audioOffered.remove(playerId);
    }
    boolean eligible(Player p) {
        if(!p.isOnline() || Bukkit.getPlayer(p.getUniqueId())!=p)return false;
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
