package com.atemukesu.extendednoteblock.sound;

import com.atemukesu.extendednoteblock.network.ModMessages;
import com.atemukesu.extendednoteblock.util.CurvePoint;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public class ServerSoundManager {
    private static final ConcurrentHashMap<UUID, ActiveSoundFader> activeSounds = new ConcurrentHashMap<>();

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(ServerSoundManager::tick);
    }

    public static void playSound(ServerLevel world, BlockPos pos, int instrumentId, int note, int velocity,
            int sustainTicks, int fadeInTicks, int fadeOutTicks) {
        playSound(world, pos, pos, instrumentId, note, velocity, sustainTicks, fadeInTicks, fadeOutTicks);
    }

    public static void playSound(ServerLevel world, BlockPos ownerPos, BlockPos playbackPos, int instrumentId,
            int note, int velocity, int sustainTicks, int fadeInTicks, int fadeOutTicks) {
        UUID soundId = UUID.randomUUID();
        ActiveSoundFader fader = new ActiveSoundFader(world, ownerPos, playbackPos, soundId, velocity, sustainTicks,
                fadeInTicks, fadeOutTicks);
        activeSounds.put(soundId, fader);
        float initialVolume = fadeInTicks <= 1 ? velocity / 127.0f : 0.001f;
        ModMessages.sendStartSoundToClients(world, playbackPos, soundId, instrumentId, note, velocity, initialVolume);
    }

    public static void playAdvancedSound(ServerLevel world, BlockPos pos, int instrumentId, int note, int velocity,
            int sustainTicks, int fadeInTicks, int fadeOutTicks, List<CurvePoint> pitchBendPoints,
            List<CurvePoint> volumePoints, List<Vec3> soundPath) {
        playAdvancedSound(world, pos, pos, instrumentId, note, velocity, sustainTicks, fadeInTicks, fadeOutTicks,
                pitchBendPoints, volumePoints, soundPath);
    }

    public static void playAdvancedSound(ServerLevel world, BlockPos ownerPos, BlockPos playbackPos, int instrumentId,
            int note, int velocity, int sustainTicks, int fadeInTicks, int fadeOutTicks,
            List<CurvePoint> pitchBendPoints, List<CurvePoint> volumePoints, List<Vec3> soundPath) {
        UUID soundId = UUID.randomUUID();
        ActiveSoundFader fader = new ActiveSoundFader(world, ownerPos, playbackPos, soundId, velocity, sustainTicks,
                fadeInTicks, fadeOutTicks);
        fader.setPitchBendPoints(pitchBendPoints);
        fader.setVolumePoints(volumePoints);
        fader.setSoundPath(soundPath);
        activeSounds.put(soundId, fader);

        ActiveSoundFader.SoundState initial = fader.calculateStateAt(0.0f);
        ModMessages.sendStartAdvancedSoundToClients(world, playbackPos, soundId, instrumentId, note,
                initial.volume, initial.pitch, initial.x, initial.y, initial.z);
    }

    public static void stopSound(ServerLevel world, BlockPos ownerPos) {
        activeSounds.values().stream()
                .filter(fader -> fader.getOwnerPos().equals(ownerPos))
                .forEach(ActiveSoundFader::startFadeOut);
    }

    private static void tick(MinecraftServer server) {
        if (activeSounds.isEmpty()) {
            return;
        }

        activeSounds.forEach((uuid, fader) -> {
            if (fader.tick()) {
                activeSounds.remove(uuid);
                ModMessages.sendStopSoundToClients(fader.getWorld(), fader.getPos(), uuid);
            }
        });
    }
}
