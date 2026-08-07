package com.atemukesu.extendednoteblock.nbs;

import com.atemukesu.extendednoteblock.block.NbsProjectionReceiverBlock;
import com.atemukesu.extendednoteblock.sound.ServerSoundManager;
import com.atemukesu.extendednoteblock.util.CurvePoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class NbsProjectionPlaybackManager {
    private static final Map<ServerLevel, Map<BlockPos, Session>> SESSIONS = new WeakHashMap<>();

    private NbsProjectionPlaybackManager() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(NbsProjectionPlaybackManager::tick);
    }

    public static void start(ServerLevel level, BlockPos receiverPos, BlockPos transmitterPos,
            List<ProjectionNote> notes) {
        stop(level, receiverPos);
        if (notes.isEmpty()) {
            return;
        }
        SESSIONS.computeIfAbsent(level, ignored -> new HashMap<>())
                .put(receiverPos.immutable(), new Session(transmitterPos.immutable(), List.copyOf(notes),
                        level.getGameTime()));
    }

    public static void stop(ServerLevel level, BlockPos receiverPos) {
        Map<BlockPos, Session> sessions = SESSIONS.get(level);
        if (sessions != null) {
            sessions.remove(receiverPos);
            if (sessions.isEmpty()) {
                SESSIONS.remove(level);
            }
        }
        ServerSoundManager.stopSound(level, receiverPos);
    }

    private static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            Map<BlockPos, Session> sessions = SESSIONS.get(level);
            if (sessions == null || sessions.isEmpty()) {
                continue;
            }
            var iterator = sessions.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<BlockPos, Session> entry = iterator.next();
                BlockPos receiverPos = entry.getKey();
                if (!(level.getBlockState(receiverPos).getBlock() instanceof NbsProjectionReceiverBlock)
                        || !level.getBlockState(receiverPos).getValue(NbsProjectionReceiverBlock.POWERED)) {
                    iterator.remove();
                    ServerSoundManager.stopSound(level, receiverPos);
                    continue;
                }

                Session session = entry.getValue();
                long elapsedMs = Math.max(0L, level.getGameTime() - session.startTick) * 50L;
                while (session.nextNote < session.notes.size()
                        && session.notes.get(session.nextNote).delayMs() <= elapsedMs) {
                    play(level, receiverPos, session.transmitterPos, session.notes.get(session.nextNote));
                    session.nextNote++;
                }
                if (session.nextNote >= session.notes.size()) {
                    iterator.remove();
                }
            }
            if (sessions.isEmpty()) {
                SESSIONS.remove(level);
            }
        }
    }

    private static void play(ServerLevel level, BlockPos ownerPos, BlockPos playbackPos, ProjectionNote note) {
        if (note.pitchCents() == 0) {
            ServerSoundManager.playSound(level, ownerPos, playbackPos, note.instrumentId(), note.midiNote(),
                    note.velocity(), note.sustainTicks(), 0, 0);
            return;
        }
        float semitones = note.pitchCents() / 100.0f;
        List<CurvePoint> pitch = new ArrayList<>(2);
        pitch.add(new CurvePoint(0.0f, semitones));
        pitch.add(new CurvePoint(1.0f, semitones));
        ServerSoundManager.playAdvancedSound(level, ownerPos, playbackPos, note.instrumentId(), note.midiNote(),
                note.velocity(), note.sustainTicks(), 0, 0, pitch, List.of(), List.<Vec3>of());
    }

    public record ProjectionNote(int instrumentId, int midiNote, int velocity, int sustainTicks,
            int pitchCents, long delayMs) {
    }

    private static final class Session {
        private final BlockPos transmitterPos;
        private final List<ProjectionNote> notes;
        private final long startTick;
        private int nextNote;

        private Session(BlockPos transmitterPos, List<ProjectionNote> notes, long startTick) {
            this.transmitterPos = transmitterPos;
            this.notes = notes;
            this.startTick = startTick;
        }
    }
}
