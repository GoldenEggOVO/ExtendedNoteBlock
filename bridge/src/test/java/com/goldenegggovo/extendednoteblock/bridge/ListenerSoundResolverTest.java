package com.goldenegggovo.extendednoteblock.bridge;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ListenerSoundResolverTest {
    @Test void everyGmProgramAndMidiNoteUsesAnIncludedAnchorAndVanillaPitch() {
        Set<Integer> anchors = Set.of(0, 12, 24, 36, 48, 60, 72, 84, 96, 108, 120);
        for (int instrument = 0; instrument < 128; instrument++) {
            for (int note = 0; note < 128; note++) {
                ListenerSoundResolver.Resolved resolved = ListenerSoundResolver.resolve(instrument, note, 0, 0);
                assertTrue(anchors.contains(resolved.anchor()));
                assertTrue(resolved.pitch() >= 0.5f && resolved.pitch() <= 2.0f);
                assertEquals("extendednoteblock_listener:notes." + instrument + "." + resolved.anchor() + ".v0",
                        resolved.event());
            }
        }
    }

    @Test void octaveAnchorsReconstructTheRequestedPitch() {
        for (int note = 0; note < 128; note++) {
            ListenerSoundResolver.Resolved resolved = ListenerSoundResolver.resolve(0, note, 0, 3);
            double reconstructed = resolved.anchor() + 12.0 * Math.log(resolved.pitch()) / Math.log(2.0);
            assertEquals(note, reconstructed, 0.000_01);
            assertEquals(3, resolved.voice());
        }
    }

    @Test void aliasesCycleWithoutChangingThePhysicalPitch() {
        Set<String> events = new HashSet<>();
        for (int voice = 0; voice < ListenerSoundResolver.VOICE_ALIASES; voice++) {
            events.add(ListenerSoundResolver.resolve(4, 61, 25, voice).event());
        }
        assertEquals(ListenerSoundResolver.VOICE_ALIASES, events.size());
        assertEquals(
                ListenerSoundResolver.resolve(4, 61, 25, 0).pitch(),
                ListenerSoundResolver.resolve(4, 61, 25, ListenerSoundResolver.VOICE_ALIASES).pitch());
    }

    @Test void percussionKeepsItsOwnNoteAndNeverChangesPitch() {
        assertEquals("extendednoteblock_listener:notes.128.35.v7",
                ListenerSoundResolver.resolve(128, 0, -2400, -1).event());
        assertEquals("extendednoteblock_listener:notes.128.81.v1",
                ListenerSoundResolver.resolve(128, 127, 2400, 9).event());
        assertEquals(1.0f, ListenerSoundResolver.resolve(128, 60, 0, 0).pitch());
    }

    @Test void centsStayInRangeOrClampOnlyBeyondTheMidiEdges() {
        assertEquals(72, ListenerSoundResolver.resolve(0, 60, 1200, 0).anchor());
        assertEquals(1.0f, ListenerSoundResolver.resolve(0, 60, 1200, 0).pitch(), 0.000_01f);
        assertEquals(0.5f, ListenerSoundResolver.resolve(0, 0, -2400, 0).pitch());
        assertEquals(2.0f, ListenerSoundResolver.resolve(0, 127, 2400, 0).pitch());
    }
}
