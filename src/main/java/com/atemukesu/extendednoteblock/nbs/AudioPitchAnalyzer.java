package com.atemukesu.extendednoteblock.nbs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class AudioPitchAnalyzer {
    public static final int TARGET_SAMPLE_RATE = 12_000;
    private static final int WINDOW_SIZE = 2_048;
    private static final int TICKS_PER_SECOND = 20;
    private static final int MIN_MIDI = 24;
    private static final int MAX_MIDI = 108;
    private static final int MAX_POLYPHONY = 4;

    private AudioPitchAnalyzer() {
    }

    public static AnalysisResult analyze(float[] samples, int sampleRate, String songName) throws IOException {
        if (sampleRate < 8_000 || samples.length < sampleRate / 4) {
            throw new IOException("Audio is too short or has an unsupported sample rate");
        }

        int hopSize = Math.max(1, sampleRate / TICKS_PER_SECOND);
        int frameCount = Math.max(1, (samples.length + hopSize - 1) / hopSize);
        double[] rmsValues = new double[frameCount];
        double maximumRms = 0.0;
        for (int frame = 0; frame < frameCount; frame++) {
            double rms = frameRms(samples, frame * hopSize, hopSize);
            rmsValues[frame] = rms;
            maximumRms = Math.max(maximumRms, rms);
        }
        if (maximumRms < 1.0e-5) {
            throw new IOException("Audio contains no detectable signal");
        }

        double[] sortedRms = rmsValues.clone();
        Arrays.sort(sortedRms);
        double noiseFloor = sortedRms[Math.min(sortedRms.length - 1, sortedRms.length / 5)];
        double silenceThreshold = Math.max(1.0e-5, noiseFloor + (maximumRms - noiseFloor) * 0.06);
        double[] window = hannWindow();
        double[] real = new double[WINDOW_SIZE];
        double[] imaginary = new double[WINDOW_SIZE];
        boolean[] active = new boolean[MAX_MIDI + 1];
        int[] missingFrames = new int[MAX_MIDI + 1];
        List<NbsSong.Note> notes = new ArrayList<>();

        for (int frame = 0; frame < frameCount; frame++) {
            boolean[] present = new boolean[MAX_MIDI + 1];
            if (rmsValues[frame] >= silenceThreshold) {
                fillWindow(samples, frame * hopSize, window, real, imaginary);
                fft(real, imaginary);
                List<PitchCandidate> candidates = findCandidates(real, imaginary, sampleRate);
                double strongest = candidates.isEmpty() ? 0.0 : candidates.getFirst().score();
                int layer = 0;
                for (PitchCandidate candidate : candidates) {
                    if (layer >= MAX_POLYPHONY || candidate.score() < strongest * 0.20) {
                        break;
                    }
                    int midi = candidate.midi();
                    present[midi] = true;
                    if (!active[midi]) {
                        int velocity = velocity(rmsValues[frame], maximumRms, candidate.score(), strongest);
                        notes.add(new NbsSong.Note(frame, layer, 0, midi - 21, velocity, 100, 0));
                    }
                    active[midi] = true;
                    missingFrames[midi] = 0;
                    layer++;
                }
            }

            for (int midi = MIN_MIDI; midi <= MAX_MIDI; midi++) {
                if (present[midi]) {
                    continue;
                }
                if (++missingFrames[midi] >= 4) {
                    active[midi] = false;
                }
            }
        }

        if (notes.isEmpty()) {
            throw new IOException("No stable musical pitches were detected");
        }

        List<NbsSong.Layer> layers = new ArrayList<>(MAX_POLYPHONY);
        for (int i = 0; i < MAX_POLYPHONY; i++) {
            layers.add(new NbsSong.Layer("Voice " + (i + 1), false, 100, 100));
        }
        String name = songName == null || songName.isBlank() ? "Converted audio" : songName;
        NbsSong song = new NbsSong(5, 16, frameCount, MAX_POLYPHONY, name,
                "ExtendedNoteBlock", "", "Automatically converted from audio", TICKS_PER_SECOND, 4,
                NbsSong.LoopSettings.NONE, notes, layers, List.of());
        return new AnalysisResult(song, samples.length / (double) sampleRate, frameCount);
    }

    private static double frameRms(float[] samples, int start, int length) {
        int end = Math.min(samples.length, start + length);
        if (start >= end) {
            return 0.0;
        }
        double sum = 0.0;
        for (int i = start; i < end; i++) {
            sum += samples[i] * samples[i];
        }
        return Math.sqrt(sum / (end - start));
    }

    private static double[] hannWindow() {
        double[] window = new double[WINDOW_SIZE];
        for (int i = 0; i < window.length; i++) {
            window[i] = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (window.length - 1));
        }
        return window;
    }

    private static void fillWindow(float[] samples, int center, double[] window, double[] real, double[] imaginary) {
        int start = center - WINDOW_SIZE / 2;
        for (int i = 0; i < WINDOW_SIZE; i++) {
            int sampleIndex = start + i;
            real[i] = (sampleIndex >= 0 && sampleIndex < samples.length ? samples[sampleIndex] : 0.0) * window[i];
            imaginary[i] = 0.0;
        }
    }

    private static List<PitchCandidate> findCandidates(double[] real, double[] imaginary, int sampleRate) {
        double[] scores = new double[MAX_MIDI + 1];
        double[] fundamentals = new double[MAX_MIDI + 1];
        double strongestFundamental = 0.0;
        for (int midi = MIN_MIDI; midi <= MAX_MIDI; midi++) {
            double fundamental = 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
            if (fundamental >= sampleRate * 0.45) {
                continue;
            }
            double fundamentalMagnitude = magnitudeNear(real, imaginary, fundamental * WINDOW_SIZE / sampleRate);
            fundamentals[midi] = fundamentalMagnitude;
            strongestFundamental = Math.max(strongestFundamental, fundamentalMagnitude);
            double score = fundamentalMagnitude;
            for (int harmonic = 2; harmonic <= 4; harmonic++) {
                double frequency = fundamental * harmonic;
                if (frequency >= sampleRate / 2.0) {
                    break;
                }
                score += magnitudeNear(real, imaginary, frequency * WINDOW_SIZE / sampleRate)
                        / Math.pow(harmonic, 0.85);
            }
            scores[midi] = score;
        }

        List<PitchCandidate> candidates = new ArrayList<>();
        for (int midi = MIN_MIDI; midi <= MAX_MIDI; midi++) {
            double score = scores[midi];
            if (score > 0.0 && fundamentals[midi] >= strongestFundamental * 0.08
                    && score >= scores[Math.max(MIN_MIDI, midi - 1)]
                    && score >= scores[Math.min(MAX_MIDI, midi + 1)]) {
                candidates.add(new PitchCandidate(midi, score));
            }
        }
        candidates.sort(Comparator.comparingDouble(PitchCandidate::score).reversed());

        List<PitchCandidate> selected = new ArrayList<>(MAX_POLYPHONY);
        for (PitchCandidate candidate : candidates) {
            boolean nearSelected = selected.stream().anyMatch(value -> Math.abs(value.midi() - candidate.midi()) <= 1);
            if (!nearSelected) {
                selected.add(candidate);
            }
            if (selected.size() >= MAX_POLYPHONY) {
                break;
            }
        }
        return selected;
    }

    private static double magnitudeNear(double[] real, double[] imaginary, double exactBin) {
        int center = (int) Math.round(exactBin);
        double magnitude = 0.0;
        for (int bin = Math.max(1, center - 1); bin <= Math.min(WINDOW_SIZE / 2 - 1, center + 1); bin++) {
            magnitude += Math.hypot(real[bin], imaginary[bin]);
        }
        return magnitude;
    }

    private static int velocity(double rms, double maximumRms, double score, double strongestScore) {
        double loudness = Math.sqrt(Math.max(0.0, Math.min(1.0, rms / maximumRms)));
        double prominence = strongestScore <= 0.0 ? 1.0 : Math.max(0.35, Math.min(1.0, score / strongestScore));
        return Math.max(1, Math.min(100, (int) Math.round((30.0 + 70.0 * loudness) * prominence)));
    }

    private static void fft(double[] real, double[] imaginary) {
        int length = real.length;
        for (int i = 1, j = 0; i < length; i++) {
            int bit = length >> 1;
            while ((j & bit) != 0) {
                j ^= bit;
                bit >>= 1;
            }
            j ^= bit;
            if (i < j) {
                double value = real[i];
                real[i] = real[j];
                real[j] = value;
            }
        }

        for (int size = 2; size <= length; size <<= 1) {
            double angle = -2.0 * Math.PI / size;
            double stepReal = Math.cos(angle);
            double stepImaginary = Math.sin(angle);
            for (int offset = 0; offset < length; offset += size) {
                double twiddleReal = 1.0;
                double twiddleImaginary = 0.0;
                for (int i = 0; i < size / 2; i++) {
                    int even = offset + i;
                    int odd = even + size / 2;
                    double oddReal = real[odd] * twiddleReal - imaginary[odd] * twiddleImaginary;
                    double oddImaginary = real[odd] * twiddleImaginary + imaginary[odd] * twiddleReal;
                    real[odd] = real[even] - oddReal;
                    imaginary[odd] = imaginary[even] - oddImaginary;
                    real[even] += oddReal;
                    imaginary[even] += oddImaginary;
                    double nextReal = twiddleReal * stepReal - twiddleImaginary * stepImaginary;
                    twiddleImaginary = twiddleReal * stepImaginary + twiddleImaginary * stepReal;
                    twiddleReal = nextReal;
                }
            }
        }
    }

    public record AnalysisResult(NbsSong song, double durationSeconds, int analyzedFrames) {
    }

    private record PitchCandidate(int midi, double score) {
    }
}
