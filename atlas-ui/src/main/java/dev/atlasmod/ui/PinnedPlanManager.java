package dev.atlasmod.ui;

import dev.atlasmod.core.entry.EntryKey;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Manages the player's "shopping list" - pinned target items whose crafting
 * paths, missing ingredients, and alternatives are tracked across sessions.
 *
 * Persists to a simple line-based text file so plans survive game restarts.
 * Format: one plan per line as "type|id|count"
 */
public final class PinnedPlanManager {

    private final List<PinnedPlan> plans = new ArrayList<>();

    public void pin(EntryKey target, int count) {
        // Prevent duplicates: update count if already pinned
        for (int i = 0; i < plans.size(); i++) {
            if (plans.get(i).target().equals(target)) {
                plans.set(i, new PinnedPlan(target, count));
                return;
            }
        }
        plans.add(new PinnedPlan(target, count));
    }

    public void unpin(EntryKey target) {
        plans.removeIf(p -> p.target().equals(target));
    }

    public boolean isPinned(EntryKey target) {
        return plans.stream().anyMatch(p -> p.target().equals(target));
    }

    public List<PinnedPlan> plans() {
        return Collections.unmodifiableList(plans);
    }

    public void clear() {
        plans.clear();
    }

    // -- Persistence -------------------------------------------------------

    /**
     * Saves all pinned plans to the given file path.
     * Creates parent directories if needed. Overwrites existing file.
     */
    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (PinnedPlan plan : plans) {
                // Validate no pipe characters in type/id to prevent format corruption
                String type = plan.target().type();
                String id = plan.target().id();
                if (type.contains("|") || id.contains("|")) continue;

                writer.write(type + "|" + id + "|" + plan.count());
                writer.newLine();
            }
        }
    }

    /**
     * Loads pinned plans from the given file path.
     * Silently skips malformed lines. Clears existing plans first.
     */
    public void load(Path file) throws IOException {
        plans.clear();
        if (!Files.exists(file)) return;

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\|", 3);
                if (parts.length != 3) continue;

                try {
                    String type = parts[0];
                    String id = parts[1];
                    int count = Integer.parseInt(parts[2]);
                    if (count < 1) continue;
                    plans.add(new PinnedPlan(new EntryKey(type, id), count));
                } catch (NumberFormatException ignored) {
                    // Skip malformed count
                }
            }
        }
    }

    /**
     * A single pinned build plan.
     *
     * @param target the goal item
     * @param count  desired quantity
     */
    public record PinnedPlan(EntryKey target, int count) {
        public PinnedPlan {
            Objects.requireNonNull(target);
            if (count < 1) throw new IllegalArgumentException("count must be >= 1");
        }
    }
}
