package com.atemukesu.extendednoteblock.nbs.vanilla;

import com.atemukesu.extendednoteblock.nbs.NbsSong;
import com.atemukesu.extendednoteblock.nbs.vanilla.VanillaNotePlanner.Event;
import com.atemukesu.extendednoteblock.nbs.vanilla.VanillaNotePlanner.PlannedNote;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class VanillaDatapackExporter {
    private VanillaDatapackExporter() {
    }

    public static ExportResult write(NbsSong song, VanillaExportOptions options, Path requestedOutput)
            throws IOException {
        VanillaNotePlanner.Plan plan = VanillaNotePlanner.plan(song, options);
        Path output = nextAvailablePath(requestedOutput);
        Files.createDirectories(output.getParent());
        Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
        String namespace = options.namespace();
        String root = "data/" + namespace + "/function/";
        List<String> paths = new ArrayList<>();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary), StandardCharsets.UTF_8)) {
            put(zip, "pack.mcmeta", "{\n  \"pack\": {\n    \"min_format\": [101, 1],\n"
                    + "    \"max_format\": 101,\n    \"description\": \"ExtendedNoteBlock vanilla music\"\n  }\n}\n");
            put(zip, root + "play.mcfunction", playFunction(plan, options));
            put(zip, root + "stop.mcfunction", stopFunction(plan, options));
            put(zip, root + "join.mcfunction", "tag @s add " + namespace + "_listener\n");
            put(zip, root + "leave.mcfunction", "tag @s remove " + namespace + "_listener\n");
            paths.add("/function " + namespace + ":play");
            paths.add("/function " + namespace + ":stop");
            for (int index = 0; index < plan.events().size(); index++) {
                put(zip, root + "timeline/" + index + ".mcfunction",
                        timelineFunction(plan.events(), index, options));
            }
            put(zip, "README.txt", readme(song, options, plan));
        } catch (IOException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
        try {
            Files.move(temporary, output, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(temporary, output);
        }
        return new ExportResult(output, plan.noteCount(), plan.shiftedNotes(), plan.durationSteps(), paths);
    }

    private static String playFunction(VanillaNotePlanner.Plan plan, VanillaExportOptions options) {
        String namespace = options.namespace();
        StringBuilder value = new StringBuilder("# Start generated music\n");
        if (!options.sharedPlayback()) value.append("tag @s add ").append(namespace).append("_listener\n");
        if (plan.events().isEmpty()) return value.append("tellraw @s {\"text\":\"No playable notes\",\"color\":\"red\"}\n").toString();
        int delay = toGameTicks(plan.events().get(0).step(), options.stepsPerSecond());
        value.append("schedule function ").append(namespace).append(":timeline/0 ")
                .append(Math.max(1, delay)).append("t replace\n");
        return value.toString();
    }

    private static String stopFunction(VanillaNotePlanner.Plan plan, VanillaExportOptions options) {
        StringBuilder value = new StringBuilder("# Stop generated music\n");
        for (int i = 0; i < plan.events().size(); i++) {
            value.append("schedule clear ").append(options.namespace()).append(":timeline/").append(i).append('\n');
        }
        value.append("stopsound @a ").append(options.soundCategory()).append('\n');
        value.append("tag @a remove ").append(options.namespace()).append("_listener\n");
        return value.toString();
    }

    private static String timelineFunction(List<Event> events, int index, VanillaExportOptions options) {
        Event event = events.get(index);
        String target = options.sharedPlayback() ? "@a" : "@a[tag=" + options.namespace() + "_listener]";
        StringBuilder value = new StringBuilder("# Music event ").append(index).append('\n');
        for (PlannedNote note : event.notes()) {
            double pitch = Math.pow(2.0, (note.vanillaPitch() - 12) / 12.0);
            double volume = Math.max(0.05, Math.min(1.0, note.velocity() / 100.0));
            value.append("playsound ").append(note.instrument().soundId()).append(' ')
                    .append(options.soundCategory()).append(' ').append(target)
                    .append(" ~ ~ ~ ").append(format(volume)).append(' ')
                    .append(format(pitch)).append(" 0\n");
        }
        if (index + 1 < events.size()) {
            int delta = events.get(index + 1).step() - event.step();
            value.append("schedule function ").append(options.namespace()).append(":timeline/")
                    .append(index + 1).append(' ').append(Math.max(1, toGameTicks(delta, options.stepsPerSecond())))
                    .append("t replace\n");
        } else if (options.loop()) {
            value.append("schedule function ").append(options.namespace()).append(":timeline/0 1t replace\n");
        } else if (!options.sharedPlayback()) {
            value.append("tag @a remove ").append(options.namespace()).append("_listener\n");
        }
        return value.toString();
    }

    private static int toGameTicks(int steps, int stepsPerSecond) {
        return (int) Math.round(steps * 20.0 / stepsPerSecond);
    }

    private static String readme(NbsSong song, VanillaExportOptions options, VanillaNotePlanner.Plan plan) {
        return "ExtendedNoteBlock 2.0.0\r\nSong: " + song.name() + "\r\nNotes: " + plan.noteCount()
                + "\r\nShifted chord notes: " + plan.shiftedNotes() + "\r\n\r\nCommands:\r\n/function "
                + options.namespace() + ":play\r\n/function " + options.namespace() + ":stop\r\n/function "
                + options.namespace() + ":join\r\n/function " + options.namespace() + ":leave\r\n";
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.5f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static void put(ZipOutputStream zip, String path, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static Path nextAvailablePath(Path requested) {
        if (!Files.exists(requested)) return requested;
        String name = requested.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; i < 10_000; i++) {
            Path candidate = requested.resolveSibling(base + "-" + i + extension);
            if (!Files.exists(candidate)) return candidate;
        }
        return requested.resolveSibling(base + "-" + System.currentTimeMillis() + extension);
    }

    public record ExportResult(Path output, int noteCount, int shiftedNotes, int durationSteps,
            List<String> commands) {
    }
}
