package dev.atlasmod.core.availability;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable snapshot of the player's current context used by the
 * AvailabilityEngine to evaluate UnlockConditions.
 * Captured once per evaluation cycle and shared across all checks.
 *
 * @param inventory       item id to total count across the player's inventory
 * @param nearbyBlockIds  block ids found around the player (workstations)
 * @param lockedRecipes   recipe ids the player has not unlocked yet
 * @param limitedCrafting whether locked recipes can not be crafted at all
 * @param stages          extra progression stages reported by other mods
 */
public record ContextSnapshot(
        String dimensionId,
        String biomeId,
        Set<String> biomeTags,
        Set<String> advancements,
        Map<String, Integer> inventory,
        Set<String> nearbyBlockIds,
        Set<String> lockedRecipes,
        boolean limitedCrafting,
        Set<String> stages
) {
    public ContextSnapshot {
        Objects.requireNonNull(dimensionId);
        Objects.requireNonNull(biomeId);
        biomeTags = Set.copyOf(biomeTags);
        advancements = Set.copyOf(advancements);
        inventory = Map.copyOf(inventory);
        nearbyBlockIds = Set.copyOf(nearbyBlockIds);
        lockedRecipes = Set.copyOf(lockedRecipes);
        stages = Set.copyOf(stages);
    }

    /** Original 0.2 constructor. Every listed inventory item counts once. */
    public ContextSnapshot(String dimensionId, String biomeId, Set<String> advancements,
                           Set<String> inventoryItemIds, Set<String> nearbyBlockIds) {
        this(dimensionId, biomeId, Set.of(), advancements, countOnce(inventoryItemIds),
                nearbyBlockIds, Set.of(), false, Set.of());
    }

    public Set<String> inventoryItemIds() {
        return inventory.keySet();
    }

    public int count(String itemId) {
        return inventory.getOrDefault(itemId, 0);
    }

    public boolean hasStage(String key) {
        return advancements.contains(key) || stages.contains(key);
    }

    /**
     * Returns a minimal empty snapshot for when no player context is available.
     */
    public static ContextSnapshot empty() {
        return new ContextSnapshot("", "", Set.of(), Set.of(), Map.of(), Set.of(), Set.of(), false, Set.of());
    }

    private static Map<String, Integer> countOnce(Set<String> ids) {
        Map<String, Integer> map = new HashMap<>();
        for (String id : ids) map.put(id, 1);
        return map;
    }
}
