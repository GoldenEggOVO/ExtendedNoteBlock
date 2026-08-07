package com.atemukesu.extendednoteblock.nbs;

import com.atemukesu.extendednoteblock.sound.ClientSoundManager;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;

public final class NbsPreviewPlayer {
    private final Set<UUID> activeSounds = new HashSet<>();
    private List<NbsProjectionWriter.ProjectedNote> notes = List.of();
    private int nextNote;
    private long startedAtMs;
    private long finishAtMs;
    private boolean playing;

    public void start(NbsSong song, NbsProjectionOptions options) {
        stop();
        notes = NbsProjectionWriter.plan(song, options);
        nextNote = 0;
        startedAtMs = Util.getMillis();
        long lastDelay = notes.stream().mapToLong(NbsProjectionWriter.ProjectedNote::delayMs).max().orElse(0L);
        finishAtMs = lastDelay + options.sustainTicks() * 50L;
        playing = !notes.isEmpty();
    }

    public void tick() {
        if (!playing) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            stop();
            return;
        }

        long elapsed = Util.getMillis() - startedAtMs;
        BlockPos pos = minecraft.player.blockPosition();
        while (nextNote < notes.size() && notes.get(nextNote).delayMs() <= elapsed) {
            play(notes.get(nextNote++), pos);
        }
        if (nextNote >= notes.size() && elapsed >= finishAtMs) {
            stop();
        }
    }

    private void play(NbsProjectionWriter.ProjectedNote note, BlockPos pos) {
        UUID soundId = UUID.randomUUID();
        activeSounds.add(soundId);
        float volume = note.velocity() / 127.0f;
        if (note.pitchCents() == 0) {
            ClientSoundManager.playSound(pos, soundId, note.gmInstrument(), note.midiNote(), note.velocity(), volume);
            return;
        }

        float pitchMultiplier = (float) Math.pow(2.0, note.pitchCents() / 1200.0);
        ClientSoundManager.playAdvancedSound(pos, soundId, note.gmInstrument(), note.midiNote(), volume,
                pitchMultiplier, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    public void stop() {
        activeSounds.forEach(ClientSoundManager::stopSound);
        activeSounds.clear();
        notes = List.of();
        nextNote = 0;
        playing = false;
    }

    public boolean isPlaying() {
        return playing;
    }

    public int playedNotes() {
        return nextNote;
    }

    public int totalNotes() {
        return notes.size();
    }
}
