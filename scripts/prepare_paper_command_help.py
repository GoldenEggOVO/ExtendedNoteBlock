#!/usr/bin/env python3
from pathlib import Path

TARGET = Path("bridge/src/main/java/com/goldenegggovo/extendednoteblock/bridge/ExtendedNoteBlockBridge.java")
text = TARGET.read_text(encoding="utf-8")

on_command = '''    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
'''

if on_command not in text:
    raise SystemExit("Could not find onCommand() marker")

if 'public List<String> onTabComplete(' not in text:
    replacement = on_command + '''        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("?")) {
            sendHelp(sender, args.length >= 2 ? args[1] : null);
            return true;
        }
'''
    text = text.replace(on_command, replacement, 1)

    insert_marker = '''    private void handleGive(Player player, String[] args) {
'''
    tab_completion = r'''    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("enb") || args.length == 0) return List.of();

        if (args.length == 1) {
            return complete(args[0], "help", "give", "set", "info", "remove", "play", "wand", "projection", "list", "reload");
        }

        String root = args[0].toLowerCase(Locale.ROOT);
        return switch (root) {
            case "help", "?" -> args.length == 2
                    ? complete(args[1], "give", "set", "info", "remove", "play", "wand", "projection", "list", "reload")
                    : List.of();
            case "give" -> switch (args.length) {
                case 2 -> complete(args[1], "note", "transmitter", "receiver", "projection", "wand", "all");
                case 3 -> complete(args[2], "1", "16", "32", "64");
                default -> List.of();
            };
            case "set" -> switch (args.length) {
                case 2 -> complete(args[1], "60", "64", "69", "72", "127");
                case 3 -> complete(args[2], "0", "24", "40", "73", "128");
                case 4 -> complete(args[3], "64", "100", "127");
                case 5 -> complete(args[4], "10", "20", "40", "100");
                case 6 -> complete(args[5], "0", "50", "100", "250", "500");
                case 7 -> complete(args[6], "0", "5", "10", "20");
                case 8 -> complete(args[7], "0", "3", "5", "10", "20");
                default -> List.of();
            };
            case "wand" -> completeWand(args);
            case "projection" -> completeProjection(args);
            default -> List.of();
        };
    }

    private List<String> completeWand(String[] args) {
        if (args.length == 2) return complete(args[1], "info", "clear", "set");
        if (!args[1].equalsIgnoreCase("set")) return List.of();
        if (args.length == 3) {
            return complete(args[2], "note", "instrument", "velocity", "sustain", "delay", "fadein", "fadeout");
        }
        if (args.length != 4) return List.of();

        String property = args[2].toLowerCase(Locale.ROOT);
        return switch (property) {
            case "note" -> complete(args[3], "60", "64", "69", "72", "127");
            case "instrument" -> complete(args[3], "0", "24", "40", "73", "128");
            case "velocity" -> complete(args[3], "64", "100", "127");
            case "sustain" -> complete(args[3], "10", "20", "40", "100");
            case "delay" -> complete(args[3], "0", "50", "100", "250", "500");
            case "fadein", "fadeout" -> complete(args[3], "0", "3", "5", "10", "20");
            default -> List.of();
        };
    }

    private List<String> completeProjection(String[] args) {
        if (args.length == 2) return complete(args[1], "info", "clear", "test", "add");
        if (!args[1].equalsIgnoreCase("add")) return List.of();
        return switch (args.length) {
            case 3 -> complete(args[2], "0", "250", "500", "1000", "2000");
            case 4 -> complete(args[3], "60", "64", "67", "69", "72");
            case 5 -> complete(args[4], "0", "24", "40", "73", "128");
            case 6 -> complete(args[5], "64", "100", "127");
            case 7 -> complete(args[6], "10", "20", "40", "100");
            case 8 -> complete(args[7], "-100", "-50", "0", "50", "100");
            default -> List.of();
        };
    }

    private List<String> complete(String current, String... candidates) {
        String prefix = current == null ? "" : current.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(prefix)) result.add(candidate);
        }
        return result;
    }

'''
    if insert_marker not in text:
        raise SystemExit("Could not find handleGive() marker")
    text = text.replace(insert_marker, tab_completion + insert_marker, 1)

old_help = '''    private void sendHelp(CommandSender sender) {
        sender.sendMessage("/enb give <note|transmitter|receiver|projection|wand|all> [amount]");
        sender.sendMessage("/enb set <note 0-127> <instrument 0-128> [velocity] [sustainTicks] [delayMs] [fadeIn] [fadeOut]");
        sender.sendMessage("/enb wand info|clear|set ...");
        sender.sendMessage("/enb projection info|clear|test|add ...");
        sender.sendMessage("/enb info | /enb remove | /enb play | /enb list | /enb reload");
    }
'''

new_help = '''    private void sendHelp(CommandSender sender) {
        sendHelp(sender, null);
    }

    private void sendHelp(CommandSender sender, String topic) {
        String normalized = topic == null ? "" : topic.toLowerCase(Locale.ROOT);
        if (!normalized.isBlank()) {
            switch (normalized) {
                case "give" -> {
                    sender.sendMessage("/enb give <note|transmitter|receiver|projection|wand|all> [amount]");
                    sender.sendMessage("Gives vanilla-safe ENB carrier items. Amount: 1-64.");
                    return;
                }
                case "set" -> {
                    sender.sendMessage("/enb set <note 0-127> <instrument 0-128> [velocity 0-127] [sustainTicks 1-400] [delayMs] [fadeIn] [fadeOut]");
                    sender.sendMessage("Configures the Extended Note Block you are looking at.");
                    return;
                }
                case "info" -> {
                    sender.sendMessage("/enb info");
                    sender.sendMessage("Shows the ENB type and configuration of the block you are looking at.");
                    return;
                }
                case "remove" -> {
                    sender.sendMessage("/enb remove");
                    sender.sendMessage("Removes ENB identity/configuration from the vanilla block you are looking at.");
                    return;
                }
                case "play" -> {
                    sender.sendMessage("/enb play");
                    sender.sendMessage("Test-plays the Extended Note Block you are looking at.");
                    return;
                }
                case "wand" -> {
                    sender.sendMessage("/enb wand info | clear | set <note|instrument|velocity|sustain|delay|fadein|fadeout> <value>");
                    sender.sendMessage("Conductor Wand: left-click = Pos1, right-click = Pos2; 'set' edits all ENB note blocks in the selection.");
                    return;
                }
                case "projection" -> {
                    sender.sendMessage("/enb projection info | clear | test | add <delayMs> <note> <instrument> [velocity] [sustainTicks] [pitchCents]");
                    sender.sendMessage("Looks at an NBS Projection Receiver and manages its playback timeline.");
                    return;
                }
                case "list" -> {
                    sender.sendMessage("/enb list");
                    sender.sendMessage("Lists every ENB logical object and its vanilla Paper carrier.");
                    return;
                }
                case "reload" -> {
                    sender.sendMessage("/enb reload");
                    sender.sendMessage("Reloads ENB notes, objects and projections from disk.");
                    return;
                }
                default -> {
                    sender.sendMessage("Unknown help topic: " + topic);
                    sender.sendMessage("Use /enb help for the command list.");
                    return;
                }
            }
        }

        sender.sendMessage("----- ExtendedNoteBlockBridge /enb -----");
        sender.sendMessage("/enb help [command] - Show help or details for one command");
        sender.sendMessage("/enb give <type> [amount] - Get ENB carrier items");
        sender.sendMessage("/enb set <note> <instrument> [...] - Configure the looked-at note block");
        sender.sendMessage("/enb info | remove | play - Inspect, detach, or test the looked-at ENB block");
        sender.sendMessage("/enb wand <info|clear|set> - Conductor selection and batch editing");
        sender.sendMessage("/enb projection <info|clear|test|add> - Projection timeline tools");
        sender.sendMessage("/enb list - List logical objects and vanilla carriers");
        sender.sendMessage("/enb reload - Reload bridge data from disk");
        sender.sendMessage("Tip: press TAB after /enb or any subcommand to see candidates.");
    }
'''

if old_help not in text:
    raise SystemExit("Could not find existing sendHelp() block")
text = text.replace(old_help, new_help, 1)

TARGET.write_text(text, encoding="utf-8")
print(f"patched {TARGET}")
