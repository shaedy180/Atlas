package dev.atlasmod.ui;

import dev.atlasmod.core.entry.EntryKey;

import java.util.*;

/**
 * Manages the player's "shopping list" — pinned target items whose crafting
 * paths, missing ingredients, and alternatives are tracked across sessions.
 */
public final class PinnedPlanManager {

    private final List<PinnedPlan> plans = new ArrayList<>();

    public void pin(EntryKey target, int count) {
        plans.add(new PinnedPlan(target, count));
    }

    public void unpin(EntryKey target) {
        plans.removeIf(p -> p.target().equals(target));
    }

    public List<PinnedPlan> plans() {
        return Collections.unmodifiableList(plans);
    }

    public void clear() {
        plans.clear();
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
