package com.atemukesu.extendednoteblock.nbs;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.sounds.JOrbisAudioStream;

public final class AudioFileDecoder {
    private static final long MAX_FILE_SIZE = 512L * 1024L * 1024L;
    private static final int MAX_DURATION_SECONDS = 20 * 60;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("wav", "mp3", "ogg", "aiff", "aif", "au");

    private AudioFileDecoder() {
    }

    public static boolean supports(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && SUPPORTED_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    public static DecodedAudio decode(Path path) throws IOException {
        long size = Files.size(path);
        if (size <= 0 || size > MAX_FILE_SIZE) {
            throw new IOException("Invalid audio file size: " + size);
        }

        String extension = extension(path);
        try {
            return switch (extension) {
                case "mp3" -> decodeMp3(path);
                case "ogg" -> decodeOgg(path);
                default -> decodeJavaSound(path);
            };
        } catch (AudioTooLongException exception) {
            throw new IOException("Audio exceeds the 20 minute conversion limit");
        } catch (RuntimeException exception) {
            throw new IOException("Unable to decode audio: " + shortMessage(exception), exception);
        }
    }

    private static DecodedAudio decodeJavaSound(Path path) throws IOException {
        try (AudioInputStream source = AudioSystem.getAudioInputStream(path.toFile())) {
            AudioFormat inputFormat = source.getFormat();
            validateFormat(inputFormat);
            AudioFormat pcmFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    inputFormat.getSampleRate(), 16, inputFormat.getChannels(),
                    inputFormat.getChannels() * 2, inputFormat.getSampleRate(), false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(pcmFormat, source)) {
                StreamingDownsampler downsampler = new StreamingDownsampler(
                        pcmFormat.getSampleRate(), pcmFormat.getChannels());
                byte[] bytes = new byte[16_384 - (16_384 % pcmFormat.getFrameSize())];
                int read;
                while ((read = pcm.read(bytes)) >= 0) {
                    int sampleCount = read / 2;
                    for (int i = 0; i < sampleCount; i++) {
                        int offset = i * 2;
                        short sample = (short) ((bytes[offset] & 0xFF) | (bytes[offset + 1] << 8));
                        downsampler.accept(sample / 32768.0f);
                    }
                }
                return downsampler.finish();
            }
        } catch (UnsupportedAudioFileException | IllegalArgumentException exception) {
            throw new IOException("Unsupported WAV/AIFF audio encoding", exception);
        }
    }

    private static DecodedAudio decodeMp3(Path path) throws IOException {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            Bitstream bitstream = new Bitstream(input);
            Decoder decoder = new Decoder();
            StreamingDownsampler downsampler = null;
            Header header;
            while ((header = bitstream.readFrame()) != null) {
                SampleBuffer buffer = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                if (downsampler == null) {
                    downsampler = new StreamingDownsampler(buffer.getSampleFrequency(), buffer.getChannelCount());
                }
                short[] samples = buffer.getBuffer();
                for (int i = 0; i < buffer.getBufferLength(); i++) {
                    downsampler.accept(samples[i] / 32768.0f);
                }
                bitstream.closeFrame();
            }
            bitstream.close();
            if (downsampler == null) {
                throw new IOException("MP3 contains no audio frames");
            }
            return downsampler.finish();
        } catch (javazoom.jl.decoder.JavaLayerException exception) {
            throw new IOException("Invalid MP3 stream", exception);
        }
    }

    private static DecodedAudio decodeOgg(Path path) throws IOException {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path));
                JOrbisAudioStream stream = new JOrbisAudioStream(input)) {
            AudioFormat format = stream.getFormat();
            validateFormat(format);
            StreamingDownsampler downsampler = new StreamingDownsampler(format.getSampleRate(), format.getChannels());
            while (stream.readChunk(downsampler::accept)) {
                // JOrbis supplies interleaved floating-point samples through the consumer.
            }
            return downsampler.finish();
        }
    }

    private static void validateFormat(AudioFormat format) throws IOException {
        if (format.getSampleRate() < 8_000 || format.getChannels() < 1 || format.getChannels() > 8) {
            throw new IOException("Unsupported audio format: " + format);
        }
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String shortMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    public record DecodedAudio(float[] samples, int sampleRate) {
    }

    private static final class StreamingDownsampler {
        private final int inputChannels;
        private final int outputSampleRate;
        private final double inputFramesPerOutput;
        private final FloatBuilder output;
        private int channelIndex;
        private double channelSum;
        private long inputFrames;
        private double nextOutputBoundary;
        private double bucketSum;
        private int bucketCount;

        private StreamingDownsampler(float inputSampleRate, int inputChannels) throws IOException {
            if (!Float.isFinite(inputSampleRate) || inputSampleRate < 8_000 || inputChannels < 1) {
                throw new IOException("Invalid decoded audio format");
            }
            this.inputChannels = inputChannels;
            this.outputSampleRate = Math.min(AudioPitchAnalyzer.TARGET_SAMPLE_RATE, Math.round(inputSampleRate));
            this.inputFramesPerOutput = inputSampleRate / outputSampleRate;
            this.nextOutputBoundary = inputFramesPerOutput;
            this.output = new FloatBuilder(outputSampleRate * 30, outputSampleRate * MAX_DURATION_SECONDS);
        }

        private void accept(float sample) {
            channelSum += Math.max(-1.0f, Math.min(1.0f, sample));
            channelIndex++;
            if (channelIndex < inputChannels) {
                return;
            }

            float mono = (float) (channelSum / inputChannels);
            channelIndex = 0;
            channelSum = 0.0;
            bucketSum += mono;
            bucketCount++;
            inputFrames++;
            if (inputFrames >= nextOutputBoundary) {
                output.add((float) (bucketSum / bucketCount));
                bucketSum = 0.0;
                bucketCount = 0;
                do {
                    nextOutputBoundary += inputFramesPerOutput;
                } while (nextOutputBoundary <= inputFrames);
            }
        }

        private DecodedAudio finish() throws IOException {
            if (bucketCount > 0) {
                output.add((float) (bucketSum / bucketCount));
            }
            float[] samples = output.toArray();
            if (samples.length < outputSampleRate / 4) {
                throw new IOException("Decoded audio is too short");
            }
            removeDcOffset(samples);
            return new DecodedAudio(samples, outputSampleRate);
        }

        private static void removeDcOffset(float[] samples) {
            double sum = 0.0;
            for (float sample : samples) {
                sum += sample;
            }
            float average = (float) (sum / samples.length);
            for (int i = 0; i < samples.length; i++) {
                samples[i] = Math.max(-1.0f, Math.min(1.0f, samples[i] - average));
            }
        }
    }

    private static final class FloatBuilder {
        private float[] values;
        private final int maximumSize;
        private int size;

        private FloatBuilder(int initialSize, int maximumSize) {
            this.values = new float[Math.min(initialSize, maximumSize)];
            this.maximumSize = maximumSize;
        }

        private void add(float value) {
            if (size >= maximumSize) {
                throw new AudioTooLongException();
            }
            if (size == values.length) {
                values = Arrays.copyOf(values, Math.min(maximumSize, values.length * 2));
            }
            values[size++] = value;
        }

        private float[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }

    private static final class AudioTooLongException extends RuntimeException {
    }
}
