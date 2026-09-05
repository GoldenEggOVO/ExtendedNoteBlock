import com.sun.media.sound.AudioSynthesizer;
import com.sun.media.sound.SoftSynthesizer;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Soundbank;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedReader;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Offline SoundFont renderer used by make_server_resource_pack.py.
 *
 * <p>The JDK's software synthesizer is used deliberately so the official pack
 * build does not depend on a platform-specific FluidSynth package. The input
 * plan is TSV with: bank, program, note, output wav file name.</p>
 */
public final class RenderSoundFontSamples {
    private static final float SAMPLE_RATE = 44_100.0f;
    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
    private static final long ATTACK_LEAD_MICROS = 20_000L;

    private RenderSoundFontSamples() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            System.err.println("Usage: RenderSoundFontSamples <soundfont.sf2> <plan.tsv> <wav-dir> <hold-seconds> <tail-seconds>");
            System.exit(2);
        }

        Path soundFontPath = Path.of(args[0]);
        Path planPath = Path.of(args[1]);
        Path outputDirectory = Path.of(args[2]);
        double holdSeconds = Double.parseDouble(args[3]);
        double tailSeconds = Double.parseDouble(args[4]);
        if (holdSeconds <= 0.0 || tailSeconds < 0.0) {
            throw new IllegalArgumentException("hold and tail durations must be positive");
        }
        Files.createDirectories(outputDirectory);

        Soundbank soundbank = MidiSystem.getSoundbank(soundFontPath.toFile());
        if (soundbank == null) {
            throw new IllegalArgumentException("Unsupported SoundFont: " + soundFontPath);
        }

        AudioSynthesizer synth = new SoftSynthesizer();
        Map<String, Object> properties = new HashMap<>();
        properties.put("reverb", true);
        properties.put("chorus", true);
        properties.put("auto gain control", true);
        AudioInputStream stream = synth.openStream(FORMAT, properties);
        if (synth.getDefaultSoundbank() != null) {
            synth.unloadAllInstruments(synth.getDefaultSoundbank());
        }
        if (!synth.loadAllInstruments(soundbank)) {
            throw new IllegalStateException("Could not load SoundFont instruments");
        }

        Receiver receiver = synth.getReceiver();
        long cursorFrames = 0L;
        long clipMicros = Math.round((ATTACK_LEAD_MICROS / 1_000_000.0 + holdSeconds + tailSeconds) * 1_000_000.0);
        long clipFrames = Math.round(clipMicros * SAMPLE_RATE / 1_000_000.0);
        int rendered = 0;

        try (BufferedReader reader = Files.newBufferedReader(planPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split("\\t", -1);
                if (fields.length != 4) {
                    throw new IllegalArgumentException("Invalid render-plan line: " + line);
                }
                int bank = Integer.parseInt(fields[0]);
                int program = Integer.parseInt(fields[1]);
                int note = Integer.parseInt(fields[2]);
                String outputName = fields[3];
                if (bank < 0 || bank > 16_383 || program < 0 || program > 127 || note < 0 || note > 127
                        || !outputName.matches("[A-Za-z0-9._-]+\\.wav")) {
                    throw new IllegalArgumentException("Unsafe or out-of-range render-plan line: " + line);
                }

                long startMicros = framesToMicros(cursorFrames);
                int channel = bank == 128 ? 9 : 0;
                send(receiver, ShortMessage.CONTROL_CHANGE, channel, 120, 0, startMicros);
                send(receiver, ShortMessage.CONTROL_CHANGE, channel, 121, 0, startMicros);
                send(receiver, ShortMessage.CONTROL_CHANGE, channel, 7, 100, startMicros);
                send(receiver, ShortMessage.CONTROL_CHANGE, channel, 10, 64, startMicros);
                send(receiver, ShortMessage.CONTROL_CHANGE, channel, 0, (bank >>> 7) & 0x7f, startMicros);
                send(receiver, ShortMessage.CONTROL_CHANGE, channel, 32, bank & 0x7f, startMicros);
                send(receiver, ShortMessage.PROGRAM_CHANGE, channel, program, 0, startMicros);
                send(receiver, ShortMessage.NOTE_ON, channel, note, 100, startMicros + ATTACK_LEAD_MICROS);
                send(receiver, ShortMessage.NOTE_OFF, channel, note, 0,
                        startMicros + ATTACK_LEAD_MICROS + Math.round(holdSeconds * 1_000_000.0));
                send(receiver, ShortMessage.CONTROL_CHANGE, channel, 120, 0,
                        startMicros + clipMicros - 1_000L);

                File output = outputDirectory.resolve(outputName).toFile();
                AudioInputStream clip = new AudioInputStream(stream, FORMAT, clipFrames);
                AudioSystem.write(clip, AudioFileFormat.Type.WAVE, output);
                cursorFrames += clipFrames;
                rendered++;
                if (rendered % 25 == 0) {
                    System.out.println("Rendered " + rendered + " WAV samples");
                }
            }
        } finally {
            receiver.close();
            synth.close();
        }
        System.out.println("Rendered " + rendered + " WAV samples to " + outputDirectory);
    }

    private static long framesToMicros(long frames) {
        return Math.round(frames * 1_000_000.0 / SAMPLE_RATE);
    }

    private static void send(Receiver receiver, int command, int channel, int data1, int data2, long timestamp)
            throws Exception {
        receiver.send(new ShortMessage(command, channel, data1, data2), timestamp);
    }
}
