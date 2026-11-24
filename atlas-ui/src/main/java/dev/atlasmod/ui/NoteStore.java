package dev.atlasmod.ui;

import dev.atlasmod.core.entry.EntryKey;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The player's own notes per item, kept across sessions.
 * File format: one note per line as "type|id|text", with backslash, pipe and
 * newline escaped in the text.
 */
public final class NoteStore {

    public static final int MAX_NOTE_LENGTH = 256;

    private final Map<EntryKey, List<String>> notes = new LinkedHashMap<>();

    public List<String> notes(EntryKey entry) {
        return Collections.unmodifiableList(notes.getOrDefault(entry, List.of()));
    }

    public void add(EntryKey entry, String text) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.isEmpty()) return;
        if (trimmed.length() > MAX_NOTE_LENGTH) trimmed = trimmed.substring(0, MAX_NOTE_LENGTH);
        notes.computeIfAbsent(entry, k -> new ArrayList<>()).add(trimmed);
    }

    public void remove(EntryKey entry, int index) {
        List<String> list = notes.get(entry);
        if (list == null || index < 0 || index >= list.size()) return;
        list.remove(index);
        if (list.isEmpty()) notes.remove(entry);
    }

    public boolean hasNotes(EntryKey entry) {
        return notes.containsKey(entry);
    }

    public int size() {
        return notes.values().stream().mapToInt(List::size).sum();
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (var entry : notes.entrySet()) {
                EntryKey key = entry.getKey();
                if (key.type().contains("|") || key.id().contains("|")) continue;
                for (String note : entry.getValue()) {
                    writer.write(key.type() + "|" + key.id() + "|" + escape(note));
                    writer.newLine();
                }
            }
        }
    }

    /** Replaces all notes with the file's content. Malformed lines are skipped. */
    public void load(Path file) throws IOException {
        notes.clear();
        if (!Files.exists(file)) return;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] parts = line.split("\\|", 3);
                if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty()) continue;
                add(new EntryKey(parts[0], parts[1]), unescape(parts[2]));
            }
        }
    }

    static String escape(String text) {
        return text.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n");
    }

    static String unescape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                out.append(switch (next) {
                    case 'n' -> '\n';
                    case 'p' -> '|';
                    default -> next;
                });
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
