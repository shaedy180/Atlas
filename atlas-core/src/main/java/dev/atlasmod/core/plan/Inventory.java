package dev.atlasmod.core.plan;

import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.entry.TagResolver;

import java.util.HashMap;
import java.util.Map;

/**
 * Mutable item counts used while planning. Planning consumes items from a copy,
 * so two ingredients never claim the same stack twice.
 */
public final class Inventory {

    private final Map<String, Integer> counts;
    private final TagResolver tags;

    public Inventory(Map<String, Integer> counts, TagResolver tags) {
        this.counts = new HashMap<>(counts);
        this.tags = tags == null ? TagResolver.NONE : tags;
    }

    public static Inventory empty() {
        return new Inventory(Map.of(), TagResolver.NONE);
    }

    public Inventory copy() {
        return new Inventory(counts, tags);
    }

    public TagResolver tags() {
        return tags;
    }

    public int count(String itemId) {
        return counts.getOrDefault(itemId, 0);
    }

    /** Total count of every item accepted by the ingredient. */
    public int count(IngredientKey ingredient) {
        if (ingredient.isEmpty()) return 0;
        if (!ingredient.tagBased()) return count(ingredient.id());
        int total = 0;
        for (String member : tags.members(ingredient.id())) total += count(member);
        return total;
    }

    /** Removes up to {@code amount} of the item and returns how many were taken. */
    public int take(String itemId, int amount) {
        int have = count(itemId);
        int taken = Math.min(have, Math.max(0, amount));
        if (taken > 0) counts.put(itemId, have - taken);
        return taken;
    }

    public void add(String itemId, int amount) {
        if (amount > 0) counts.merge(itemId, amount, Integer::sum);
    }

    public Map<String, Integer> asMap() {
        return Map.copyOf(counts);
    }
}
