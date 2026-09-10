package com.goldenegggovo.extendednoteblock.bridge;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

/** Durable original block data is committed before any automatic carrier migration. */
final class EnbMigrationJournal implements AutoCloseable {
    private final ExtendedNoteBlockBridge plugin;
    private final Path file;
    private final Map<String,String> saved=new ConcurrentHashMap<>(),pending=new ConcurrentHashMap<>();
    private BukkitTask writer;
    private volatile boolean closed;
    EnbMigrationJournal(ExtendedNoteBlockBridge plugin) throws IOException {
        this.plugin=plugin; file=plugin.getDataFolder().toPath().resolve("craftengine-original-blocks.tsv");
        if(Files.exists(file))for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)) {
            String[] parts=line.split("\t",2);
            if(parts.length==2)saved.put(parts[0],new String(Base64.getDecoder().decode(parts[1]),StandardCharsets.UTF_8));
        }
        Path backup=plugin.getDataFolder().toPath().resolve("before-craftengine");
        Files.createDirectories(backup);
        for(String name:List.of("config.yml","objects.yml","notes.yml","projections.yml")) {
            Path source=plugin.getDataFolder().toPath().resolve(name),target=backup.resolve(name);
            if(Files.exists(source)&&!Files.exists(target))Files.copy(source,target);
        }
        writer=Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,this::flush,1,2);
    }
    boolean record(String key,String blockData) {
        if(saved.containsKey(key))return true;
        pending.putIfAbsent(key,blockData);return false;
    }
    String original(String key){return saved.get(key);}
    int size(){return saved.size();}
    private synchronized void flush() {
        if(closed||pending.isEmpty())return;
        Map<String,String> batch=new HashMap<>(pending);
        try(FileChannel out=FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.APPEND)) {
            StringBuilder text=new StringBuilder();
            batch.forEach((key,data)->text.append(key).append('\t').append(Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8))).append('\n'));
            ByteBuffer bytes=StandardCharsets.UTF_8.encode(text.toString());while(bytes.hasRemaining())out.write(bytes);
            out.force(false);
            saved.putAll(batch);batch.forEach((key,data)->pending.remove(key,data));
        }catch(IOException ex){plugin.getLogger().severe("ENB migration paused: cannot persist original blocks: "+ex.getMessage());}
    }
    @Override public synchronized void close(){flush();closed=true;if(writer!=null)writer.cancel();}
}
