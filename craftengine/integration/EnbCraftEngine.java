package com.goldenegggovo.extendednoteblock.bridge;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.command.CommandSender;
import net.momirealms.craftengine.bukkit.api.*;
import net.momirealms.craftengine.bukkit.api.event.*;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.core.block.*;
import net.momirealms.craftengine.core.block.behavior.*;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.item.ItemBuildContext;
import net.momirealms.craftengine.core.util.Key;
import static com.goldenegggovo.extendednoteblock.bridge.ExtendedNoteBlockBridge.BridgeItemType;

/** CraftEngine backend. Authoritative music data remains in ENB. */
final class EnbCraftEngine implements Listener, AutoCloseable {
    private final ExtendedNoteBlockBridge plugin;
    private final Map<String, BlockDefinition> definitions = new HashMap<>();
    private final Map<String, Boolean> notePower = new HashMap<>();
    private final Set<String> migrated = new HashSet<>();
    private final Map<String, ExtendedNoteBlockBridge.RenderObjectState> synced = new HashMap<>();
    private EnbMigrationJournal journal;
    private BukkitTask refresh;
    private int migrationBudget;
    private boolean ready;
    private int tick, restored;

    EnbCraftEngine(ExtendedNoteBlockBridge plugin) { this.plugin=plugin; }
    void enable() {
        try {journal=new EnbMigrationJournal(plugin);}
        catch(java.io.IOException|IllegalArgumentException ex){plugin.getLogger().severe("ENB automatic migration disabled: backup/journal unavailable: "+ex.getMessage());}
        BlockBehaviors.register(Key.of("enb:receiver"), (block, section) -> new ReceiverBehavior(block));
        Bukkit.getPluginManager().registerEvents(this,plugin);
        refresh=Bukkit.getScheduler().runTaskTimer(plugin,this::refreshDefinitions,40,100);
    }
    private void refreshDefinitions() {
        boolean complete=true;
        for (BridgeItemType type:BridgeItemType.values()) {
            if (!type.placeable) continue;
            BlockDefinition def=CraftEngineBlocks.byId(Key.of("enb:"+type.id));
            if (def==null) complete=false; else definitions.put(type.id,def);
        }
        if (complete && !ready) plugin.getLogger().info("CraftEngine ENB ready: 4 functional blocks, 30 original appearances, 5 items.");
        ready=complete;
    }
    @EventHandler public void reloaded(CraftEngineReloadEvent e) {
        Bukkit.getScheduler().runTask(plugin,()->{definitions.clear();synced.clear();ready=false;refreshDefinitions();});
    }
    void beginTick(){tick++;migrationBudget=Math.min(128,Math.max(1,plugin.getConfig().getInt("craftengine.migrations-per-tick",32)));}
    boolean managed(Block b) {
        if (b==null) return false;
        var state=CraftEngineBlocks.getCustomBlockState(b);
        return state!=null && type(state.owner().value().id().toString())!=null;
    }
    boolean matches(Block b,BridgeItemType t) {
        var state=CraftEngineBlocks.getCustomBlockState(b);
        return state!=null && state.owner().value().id().toString().equals("enb:"+t.id);
    }
    boolean acceptsCopy(Block b,BridgeItemType type) {
        if (b==null || !type.placeable)return false;
        var custom=CraftEngineBlocks.getCustomBlockState(b);
        if(custom!=null)return custom.owner().value().id().toString().equals("enb:"+type.id);
        return b.getType()==type.carrier
                || type==BridgeItemType.GLOBAL_REDSTONE_RECEIVER && b.getType()==Material.REDSTONE_BLOCK;
    }
    boolean placeCopy(Block b,BridgeItemType type,int midi) {
        if(!ready || plugin.getConfig().getBoolean("craftengine.rollback",false) || !acceptsCopy(b,type))return false;
        return update(b,state(type,false,midi));
    }
    BridgeItemType itemType(ItemStack stack) {
        if (stack==null || stack.getType().isAir())return null;
        Key key=CraftEngineItems.getCustomItemId(stack);
        return key==null?null:type(key.toString());
    }
    private static BridgeItemType type(String key) {
        for (BridgeItemType type:BridgeItemType.values())if(key.equals("enb:"+type.id))return type;
        return null;
    }
    ItemStack item(String id,int amount) {
        if (!ready)return null;
        var def=CraftEngineItems.byId("enb:"+id);
        return def==null?null:(ItemStack)def.buildItem(ItemBuildContext.empty(),amount).platformItem();
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private ImmutableBlockState state(BridgeItemType type,boolean powered,int pitch) {
        BlockDefinition def=definitions.get(type.id);
        if(def==null)return null;
        ImmutableBlockState state=def.defaultState().with((Property)def.getProperty("powered"),powered);
        if(type==BridgeItemType.EXTENDED_NOTE_BLOCK)state=state.with((Property)def.getProperty("pitch"),Math.floorMod(pitch,12));
        return state;
    }
    boolean powerReceiver(Block b,boolean powered) {
        if(plugin.getConfig().getBoolean("craftengine.rollback",false))return false;
        if(!matches(b,BridgeItemType.GLOBAL_REDSTONE_RECEIVER))return false;
        if(!ready)return true;
        update(b,state(BridgeItemType.GLOBAL_REDSTONE_RECEIVER,powered,0));
        return true;
    }
    private boolean update(Block b,ImmutableBlockState desired) {
        if(desired==null)return false;
        if(desired==CraftEngineBlocks.getCustomBlockState(b))return true;
        return CraftEngineBlocks.place(b.getLocation(),desired,UpdateFlags.UPDATE_ALL,false);
    }
    void sync(String key,BridgeItemType type,ExtendedNoteBlockBridge.RenderObjectState render) {
        if(!ready)return;
        Block b=plugin.getLoadedBlock(key);
        if(b==null){synced.remove(key);notePower.remove(key);return;}
        if(plugin.getConfig().getBoolean("craftengine.rollback",false)){
            if(migrationBudget>0 && matches(b,type)){restoreCarrier(b);migrationBudget--;restored++;}
            return;
        }
        // Reconcile stable states once per second, spread across ticks. Edits/power edges update immediately.
        if(render.equals(synced.get(key)) && Math.floorMod(key.hashCode(),20)!=Math.floorMod(tick,20))return;
        boolean custom=matches(b,type);
        if(!custom) {
            synced.remove(key);
            if(journal==null || !plugin.getConfig().getBoolean("craftengine.migrate-known-blocks",true) || migrationBudget<=0)return;
            // Only the ENB identity ledger authorizes conversion; never scan materials in a world.
            // A stale ledger must not claim another custom block with the same vanilla carrier.
            if(!acceptsCopy(b,type))return;
            migrationBudget--;
            if(!journal.record(key,b.getBlockData().getAsString()))return;
        }
        if(type==BridgeItemType.EXTENDED_NOTE_BLOCK) {
            Boolean previous=notePower.put(key,render.powered());
            if(custom && Boolean.FALSE.equals(previous) && render.powered()) {
                var cfg=plugin.notes.get(key);
                if(cfg!=null) {
                    plugin.stopActive(key);
                    Bukkit.getScheduler().runTaskLater(plugin,()->{
                        if(plugin.objects.get(key)==type && matches(b,type))plugin.startConfiguredSound(b,cfg);
                    },Math.max(0L,Math.round(cfg.delayMs()/50.0)));
                }
            }
        }
        if(update(b,state(type,render.powered(),render.variant()))) {
            synced.put(key,render);
            if(!custom)migrated.add(key);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void placed(CustomBlockPlaceEvent event) {
        BridgeItemType type=type(event.customBlock().id().toString());
        if(type==null || !type.placeable)return;
        Block b=event.bukkitBlock();
        // Later CE/region protection callbacks may still roll the placement back.
        Bukkit.getScheduler().runTask(plugin,()->{
            if(event.isCancelled() || !matches(b,type))return;
            String key=plugin.key(b);
            synced.remove(key);notePower.remove(key);
            plugin.objects.put(key,type);plugin.indexObject(key,type);
            if(type==BridgeItemType.EXTENDED_NOTE_BLOCK) {
                plugin.notes.putIfAbsent(key,plugin.defaultNoteConfig());plugin.requestSave("notes");
            } else if(type==BridgeItemType.NBS_PROJECTION_RECEIVER) {
                plugin.projectionNotes.putIfAbsent(key,new ArrayList<>());plugin.requestSave("projections");
            }
            plugin.requestSave("objects");
        });
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void broken(CustomBlockBreakEvent event) {
        if(type(event.customBlock().id().toString())==null)return;
        Block block=event.bukkitBlock();String key=plugin.key(block);
        Bukkit.getScheduler().runTask(plugin,()->{
            if(event.isCancelled() || managed(block))return;
            BridgeItemType type=plugin.objects.remove(key);
            if(type==null)return;
            plugin.unindexObject(key,type);plugin.notes.remove(key);plugin.stopActive(key);plugin.stopProjection(key);
            plugin.transmitterPower.remove(key);notePower.remove(key);
            synced.remove(key);plugin.transmitterProjectionTarget.remove(key);
            if(plugin.projectionNotes.remove(key)!=null)plugin.requestSave("projections");
            plugin.requestSave("objects");plugin.requestSave("notes");
        });
    }
    void restoreCarrier(Block block) {
        BridgeItemType type=plugin.objects.get(plugin.key(block));
        if(type!=null && matches(block,type)) {
            String original=journal==null?null:journal.original(plugin.key(block));
            if(original!=null)block.setBlockData(Bukkit.createBlockData(original),true);
            else block.setType(type.carrier,true);
            synced.remove(plugin.key(block));notePower.remove(plugin.key(block));
        }
    }
    void command(CommandSender sender,String[] args) {
        if(!sender.hasPermission("extendednoteblockbridge.admin")){sender.sendMessage("You do not have permission.");return;}
        String action=args.length>1?args[1]:"status";
        if(action.equalsIgnoreCase("rollback")||action.equalsIgnoreCase("resume")) {
            boolean rollback=action.equalsIgnoreCase("rollback");
            plugin.getConfig().set("craftengine.rollback",rollback);plugin.saveConfig();synced.clear();notePower.clear();
            sender.sendMessage(rollback?"ENB rollback enabled. Loaded blocks restore gradually; unloaded blocks restore when visited. Keep both plugins installed until finished.":"ENB CraftEngine conversion resumed.");
        }
        sender.sendMessage("ENB CraftEngine: ready="+ready+", original blocks journal="+(journal==null?"unavailable":journal.size())+", migrated this run="+migrated.size()+", restored="+restored+", rollback="+plugin.getConfig().getBoolean("craftengine.rollback",false));
    }
    @Override public void close(){if(refresh!=null)refresh.cancel();if(journal!=null)journal.close();HandlerList.unregisterAll(this);}

    /** Like a redstone block: weak signal in all directions, no extra strong signal. */
    private static final class ReceiverBehavior extends BukkitBlockBehavior {
        final Property<Boolean> powered;
        @SuppressWarnings("unchecked") ReceiverBehavior(BlockDefinition block) {
            super(block);powered=(Property<Boolean>)block.getProperty("powered");
        }
        @Override public boolean isSignalSource(Object self,Object[] args){return true;}
        @Override public int getSignal(Object self,Object[] args){
            var state=BlockStateUtils.getOptionalCustomBlockState(args[0]).orElse(null);
            return state!=null && state.get(powered)?15:0;
        }
        @Override public int getDirectSignal(Object self,Object[] args){return 0;}
    }
}
