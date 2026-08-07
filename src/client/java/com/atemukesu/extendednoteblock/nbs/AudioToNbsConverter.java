package com.atemukesu.extendednoteblock.nbs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AudioToNbsConverter {
    private AudioToNbsConverter() {
    }

    public static ConversionResult convert(Path input, Path songsDirectory) throws IOException {
        AudioFileDecoder.DecodedAudio audio = AudioFileDecoder.decode(input);
        String baseName = sanitizeFileName(stripExtension(input.getFileName().toString()));
        AudioPitchAnalyzer.AnalysisResult analysis = AudioPitchAnalyzer.analyze(
                audio.samples(), audio.sampleRate(), baseName);
        Files.createDirectories(songsDirectory);
        Path output = uniqueOutput(songsDirectory, baseName + "_converted");
        NbsWriter.write(analysis.song(), output);
        return new ConversionResult(analysis.song(), output, analysis.durationSeconds());
    }

    private static Path uniqueOutput(Path directory, String baseName) {
        Path candidate = directory.resolve(baseName + ".nbs");
        int suffix = 2;
        while (Files.exists(candidate)) {
            candidate = directory.resolve(baseName + "_" + suffix++ + ".nbs");
        }
        return candidate;
    }

    private static String stripExtension(String value) {
        int dot = value.lastIndexOf('.');
        return dot > 0 ? value.substring(0, dot) : value;
    }

    private static String sanitizeFileName(String value) {
        String sanitized = value.replaceAll("[<>:\"/\\\\|?*]", "_").trim();
        return sanitized.isBlank() ? "converted_audio" : sanitized;
    }

    public record ConversionResult(NbsSong song, Path output, double durationSeconds) {
    }
}
