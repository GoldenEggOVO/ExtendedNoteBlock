package com.goldenegggovo.extendednoteblock.bridge;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Editable server-wide messages; untranslated text falls back to English. */
final class EnbLanguage {
    private static volatile List<Entry> entries = List.of();

    private EnbLanguage() {}

    static void load(JavaPlugin plugin) {
        File folder = new File(plugin.getDataFolder(), "lang");
        if (!folder.isDirectory() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create ENB language folder: " + folder);
        }
        for (String name : List.of("en_us", "zh_cn")) {
            File file = new File(folder, name + ".yml");
            if (!file.isFile()) plugin.saveResource("lang/" + name + ".yml", false);
        }
        String selected = plugin.getConfig().getString("language", "en_us").toLowerCase(java.util.Locale.ROOT);
        if (!selected.equals("en_us") && !selected.equals("zh_cn")) {
            plugin.getLogger().warning("Unsupported ENB language '" + selected + "'; using en_us.");
            selected = "en_us";
        }
        YamlConfiguration yaml = new YamlConfiguration();
        // Sentences contain periods, so do not treat them as nested YAML paths.
        yaml.options().pathSeparator('\u001f');
        try {
            yaml.load(new File(folder, selected + ".yml"));
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException ex) {
            plugin.getLogger().warning("Could not load ENB language file: " + ex.getMessage());
            entries = List.of();
            return;
        }
        ConfigurationSection section = yaml.getConfigurationSection("messages");
        if (section == null) {
            plugin.getLogger().warning("ENB language file has no messages section; using source English.");
            entries = List.of();
            return;
        }
        List<Entry> loaded = new ArrayList<>();
        for (Map.Entry<String, Object> item : section.getValues(false).entrySet()) {
            if (item.getValue() instanceof String value) {
                try {
                    loaded.add(new Entry(item.getKey(), value));
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Skipping invalid ENB language entry: " + ex.getMessage());
                }
            }
        }
        loaded.sort(Comparator.comparingInt((Entry entry) -> entry.staticLength).reversed());
        entries = List.copyOf(loaded);
    }

    static String text(String source) {
        if (source == null) return "";
        for (Entry entry : entries) {
            Matcher match = entry.pattern.matcher(source);
            if (!match.matches()) continue;
            String translated = entry.translation;
            for (int i = 1; i <= match.groupCount(); i++) {
                translated = translated.replace("{" + (i - 1) + "}", text(match.group(i)));
            }
            return translated;
        }
        return source;
    }

    private static final class Entry {
        final Pattern pattern;
        final String translation;
        final int staticLength;

        Entry(String template, String translation) {
            StringBuilder regex = new StringBuilder("^");
            int last = 0;
            int placeholders = 0;
            Matcher marker = Pattern.compile("\\{(\\d+)\\}").matcher(template);
            while (marker.find()) {
                if (Integer.parseInt(marker.group(1)) != placeholders++) {
                    throw new IllegalArgumentException("ENB language placeholders must be sequential: " + template);
                }
                regex.append(Pattern.quote(template.substring(last, marker.start()))).append("(.*?)");
                last = marker.end();
            }
            regex.append(Pattern.quote(template.substring(last))).append('$');
            pattern = Pattern.compile(regex.toString(), Pattern.DOTALL);
            this.translation = translation;
            staticLength = template.length() - 3 * placeholders;
        }
    }
}
